package com.example.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.AdminRecord
import com.example.data.model.AnnouncementSeverity
import com.example.data.model.CampusAnnouncement
import com.example.data.model.ChatMessage
import com.example.data.model.ChatSender
import com.example.data.model.DirectoryOffice
import com.example.data.model.HelpdeskTicket
import com.example.data.model.ScheduleFormatter
import com.example.data.model.ScheduleItem
import com.example.data.model.ScheduleItemType
import com.example.data.model.TicketCategory
import com.example.data.model.TicketPriority
import com.example.data.model.TicketStatus
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.repository.CampusAiRepository
import com.example.data.repository.CampusKnowledgeBase
import com.example.data.repository.CampusKnowledgeData
import com.example.data.repository.CampusRepository
import com.example.notifications.CampusNotificationManager
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

private const val VM_TAG = "CampusBotViewModel"

@OptIn(ExperimentalCoroutinesApi::class)
class CampusBotViewModel(
    private val appContext: Context,
    private val repository: CampusRepository,
    private val knowledgeBase: CampusKnowledgeBase,
    private val isGuestMode: Boolean = false,
    private val aiRepository: CampusAiRepository = CampusAiRepository(knowledgeBase)
) : ViewModel() {

    private val _knowledgeData = MutableStateFlow(CampusKnowledgeData())
    val knowledgeData: StateFlow<CampusKnowledgeData> = _knowledgeData.asStateFlow()

    private val _guestProfile = MutableStateFlow(
        UserProfile(
            id = "guest",
            userId = "guest",
            displayName = "Guest Student",
            email = "",
            department = "Guest Session (Local)",
            notificationsEnabled = true,
            reminderLeadMinutes = 30
        )
    )
    private val _guestSchedule = MutableStateFlow<List<ScheduleItem>>(emptyList())
    private val _guestChatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())

    val userProfile: StateFlow<UserProfile?> = if (isGuestMode) {
        _guestProfile.asStateFlow()
    } else {
        repository.observeUserProfile()
            .catch { error ->
                val fsErr = unwrapFirestoreError(error)
                if (fsErr == null && error is CancellationException) throw error
                Log.e(VM_TAG, "Profile stream error", error)
                emit(null)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)
    }

    val isAdmin: StateFlow<Boolean> = if (isGuestMode) {
        MutableStateFlow(false).asStateFlow()
    } else {
        repository.observeAdminStatus()
            .catch { error ->
                val fsErr = unwrapFirestoreError(error)
                if (fsErr == null && error is CancellationException) throw error
                emit(false)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), false)
    }

    val adminRecords: StateFlow<List<AdminRecord>> = if (isGuestMode) {
        MutableStateFlow(emptyList<AdminRecord>()).asStateFlow()
    } else {
        isAdmin
            .flatMapLatest { admin ->
                if (admin) repository.observeAdminRecords() else flowOf(emptyList())
            }
            .catch { error ->
                val fsErr = unwrapFirestoreError(error)
                if (fsErr == null && error is CancellationException) throw error
                emit(emptyList())
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())
    }

    val scheduleState: StateFlow<UiState<List<ScheduleItem>>> = if (isGuestMode) {
        _guestSchedule
            .map<List<ScheduleItem>, UiState<List<ScheduleItem>>> { UiState.Success(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Success(emptyList()))
    } else {
        repository.observeScheduleItems().toUiStateFlow("Failed to load schedule")
    }

    val directoryState: StateFlow<UiState<List<DirectoryOffice>>> = if (isGuestMode) {
        MutableStateFlow<UiState<List<DirectoryOffice>>>(UiState.Success(emptyList())).asStateFlow()
    } else {
        repository.observeDirectory().toUiStateFlow("Failed to load campus directory")
    }

    val myTicketsState: StateFlow<UiState<List<HelpdeskTicket>>> = if (isGuestMode) {
        MutableStateFlow<UiState<List<HelpdeskTicket>>>(UiState.Success(emptyList())).asStateFlow()
    } else {
        repository.observeMyTickets().toUiStateFlow("Failed to load helpdesk tickets")
    }

    val allTicketsState: StateFlow<UiState<List<HelpdeskTicket>>> = if (isGuestMode) {
        MutableStateFlow<UiState<List<HelpdeskTicket>>>(UiState.Success(emptyList())).asStateFlow()
    } else {
        isAdmin
            .flatMapLatest { admin ->
                if (admin) {
                    repository.observeAllTicketsAdmin().toUiStateFlowInner("Failed to load ticket queue")
                } else {
                    flowOf(UiState.Success(emptyList()))
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)
    }

    val announcementsState: StateFlow<UiState<List<CampusAnnouncement>>> = if (isGuestMode) {
        MutableStateFlow<UiState<List<CampusAnnouncement>>>(UiState.Success(emptyList())).asStateFlow()
    } else {
        repository.observeAnnouncements().toUiStateFlow("Failed to load campus alerts")
    }

    val chatMessagesState: StateFlow<UiState<List<ChatMessage>>> = if (isGuestMode) {
        _guestChatMessages
            .map<List<ChatMessage>, UiState<List<ChatMessage>>> { UiState.Success(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Success(emptyList()))
    } else {
        repository.observeChatMessages().toUiStateFlow("Failed to load chat history")
    }

    private val _aiLoading = MutableStateFlow(false)
    val aiLoading: StateFlow<Boolean> = _aiLoading.asStateFlow()

    private val _aiStreamingText = MutableStateFlow<String?>(null)
    val aiStreamingText: StateFlow<String?> = _aiStreamingText.asStateFlow()

    private val _aiError = MutableStateFlow<String?>(null)
    val aiError: StateFlow<String?> = _aiError.asStateFlow()

    private val _liveAlertBanner = MutableStateFlow<CampusAnnouncement?>(null)
    val liveAlertBanner: StateFlow<CampusAnnouncement?> = _liveAlertBanner.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private val _importingTimetable = MutableStateFlow(false)
    val importingTimetable: StateFlow<Boolean> = _importingTimetable.asStateFlow()

    private val _extractingSchedule = MutableStateFlow(false)
    val extractingSchedule: StateFlow<Boolean> = _extractingSchedule.asStateFlow()

    private val _extractionError = MutableStateFlow<String?>(null)
    val extractionError: StateFlow<String?> = _extractionError.asStateFlow()

    private val _lastImportSummary = MutableStateFlow<String?>(null)
    val lastImportSummary: StateFlow<String?> = _lastImportSummary.asStateFlow()

    private val seenAnnouncementIds = mutableSetOf<String>()
    private var announcementsBaselineLoaded = false
    private var directorySeedAttempted = false

    init {
        CampusNotificationManager.ensureChannels(appContext)
        viewModelScope.launch {
            _knowledgeData.value = withContext(Dispatchers.IO) {
                val loaded = knowledgeBase.load()
                runCatching { aiRepository.prewarm() }
                loaded
            }
        }
        if (!isGuestMode) {
            viewModelScope.launch {
                repository.ensureUserProfile()
            }

            // Seed starter campus directory once when an Admin signs in and directory is empty
            viewModelScope.launch {
                isAdmin.collect { admin ->
                    if (admin && !directorySeedAttempted) {
                        directorySeedAttempted = true
                        repository.seedStarterDirectoryIfEmpty()
                    }
                }
            }

            // Real-time push notification bridge for live campus announcements
            viewModelScope.launch {
                announcementsState.collect { state ->
                    if (state is UiState.Success) {
                        val list = state.data
                        if (!announcementsBaselineLoaded) {
                            seenAnnouncementIds.addAll(list.map { it.id })
                            announcementsBaselineLoaded = true
                        } else {
                            val newestUnseen = list.firstOrNull { it.id.isNotBlank() && it.id !in seenAnnouncementIds }
                            seenAnnouncementIds.addAll(list.map { it.id })
                            if (newestUnseen != null) {
                                _liveAlertBanner.value = newestUnseen
                                val notificationsOn = userProfile.value?.notificationsEnabled ?: true
                                if (notificationsOn) {
                                    CampusNotificationManager.showAnnouncementNotification(appContext, newestUnseen)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Keep local Android reminders synchronized with schedule items
        viewModelScope.launch {
            scheduleState.collect { state ->
                if (state is UiState.Success) {
                    state.data.forEach { item ->
                        if (item.id.isNotBlank()) {
                            CampusNotificationManager.syncScheduleReminder(appContext, item)
                        }
                    }
                }
            }
        }
    }

    // --- AI Campus Q&A Actions ---

    fun sendAiPrompt(rawPrompt: String) {
        val prompt = rawPrompt.trim()
        if (prompt.isEmpty() || _aiLoading.value) return

        viewModelScope.launch {
            _aiError.value = null
            _aiLoading.value = true
            _aiStreamingText.value = ""

            val history = (chatMessagesState.value as? UiState.Success)?.data.orEmpty()
            val schedule = (scheduleState.value as? UiState.Success)?.data.orEmpty()
            val alerts = (announcementsState.value as? UiState.Success)?.data.orEmpty()
            val offices = (directoryState.value as? UiState.Success)?.data.orEmpty()

            if (isGuestMode) {
                val userMsg = ChatMessage(
                    id = "guest_u_${UUID.randomUUID()}",
                    userId = "guest",
                    sender = ChatSender.USER.name,
                    content = prompt,
                    createdAt = Timestamp.now()
                )
                _guestChatMessages.value = _guestChatMessages.value + userMsg
            } else {
                // Persist user prompt concurrently so Gemini streaming begins with zero Firestore round-trip delay
                launch {
                    repository.addChatMessage(ChatSender.USER, prompt).onFailure { err ->
                        Log.w(VM_TAG, "Non-blocking user message persist failed", err)
                    }
                }
            }

            var finalReply = ""
            try {
                aiRepository.streamCampusReply(
                    userPrompt = prompt,
                    recentHistory = history,
                    scheduleItems = schedule,
                    announcements = alerts,
                    directory = offices
                ).collect { partial ->
                    finalReply = partial
                    _aiStreamingText.value = partial
                }
                if (finalReply.isNotBlank()) {
                    if (isGuestMode) {
                        val botMsg = ChatMessage(
                            id = "guest_a_${UUID.randomUUID()}",
                            userId = "guest",
                            sender = ChatSender.ASSISTANT.name,
                            content = finalReply.trim(),
                            createdAt = Timestamp.now()
                        )
                        _guestChatMessages.value = _guestChatMessages.value + botMsg
                    } else {
                        repository.addChatMessage(ChatSender.ASSISTANT, finalReply)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(VM_TAG, "AI Q&A failed", e)
                _aiError.value = e.localizedMessage ?: "CampusBot encountered an error."
            } finally {
                _aiLoading.value = false
                _aiStreamingText.value = null
            }
        }
    }

    fun clearChatHistory() {
        if (isGuestMode) {
            _guestChatMessages.value = emptyList()
            _snackbarMessage.value = "Conversation cleared."
            return
        }
        viewModelScope.launch {
            repository.clearChatHistory()
                .onSuccess { _snackbarMessage.value = "Conversation cleared." }
                .onFailure { _snackbarMessage.value = it.localizedMessage ?: "Failed to clear chat." }
        }
    }

    fun clearAiError() {
        _aiError.value = null
    }

    // --- Schedule & Exam Reminder Actions ---

    fun saveScheduleItem(item: ScheduleItem, onComplete: () -> Unit = {}) {
        if (isGuestMode) {
            val savedId = item.id.ifBlank { "guest_sched_${UUID.randomUUID()}" }
            val synced = item.copy(id = savedId, userId = "guest")
            val current = _guestSchedule.value
            _guestSchedule.value = if (current.any { it.id == savedId }) {
                current.map { if (it.id == savedId) synced else it }
            } else {
                current + synced
            }
            CampusNotificationManager.syncScheduleReminder(appContext, synced)
            _snackbarMessage.value = if (synced.reminderEnabled) {
                "Saved locally with reminder (${synced.reminderLeadMinutes}m before)."
            } else {
                "Schedule item saved locally."
            }
            onComplete()
            return
        }
        viewModelScope.launch {
            repository.upsertScheduleItem(item)
                .onSuccess { savedId ->
                    val synced = item.copy(id = savedId)
                    CampusNotificationManager.syncScheduleReminder(appContext, synced)
                    _snackbarMessage.value = if (synced.reminderEnabled) {
                        "Saved with reminder (${synced.reminderLeadMinutes}m before)."
                    } else {
                        "Schedule item saved."
                    }
                    onComplete()
                }
                .onFailure {
                    _snackbarMessage.value = it.localizedMessage ?: "Failed to save schedule item."
                }
        }
    }

    fun toggleScheduleReminder(item: ScheduleItem) {
        val nextEnabled = !item.reminderEnabled
        if (isGuestMode) {
            val updated = item.copy(reminderEnabled = nextEnabled)
            _guestSchedule.value = _guestSchedule.value.map { if (it.id == item.id) updated else it }
            CampusNotificationManager.syncScheduleReminder(appContext, updated)
            _snackbarMessage.value = if (nextEnabled) {
                "Reminder enabled for ${item.courseCode}."
            } else {
                "Reminder paused for ${item.courseCode}."
            }
            return
        }
        viewModelScope.launch {
            repository.toggleScheduleReminder(item.id, nextEnabled)
                .onSuccess {
                    CampusNotificationManager.syncScheduleReminder(
                        appContext,
                        item.copy(reminderEnabled = nextEnabled)
                    )
                    _snackbarMessage.value = if (nextEnabled) {
                        "Reminder enabled for ${item.courseCode}."
                    } else {
                        "Reminder paused for ${item.courseCode}."
                    }
                }
                .onFailure {
                    _snackbarMessage.value = it.localizedMessage ?: "Could not update reminder."
                }
        }
    }

    fun deleteScheduleItem(item: ScheduleItem) {
        if (isGuestMode) {
            CampusNotificationManager.cancelScheduleReminder(appContext, item.id)
            _guestSchedule.value = _guestSchedule.value.filterNot { it.id == item.id }
            _snackbarMessage.value = "Removed ${item.courseCode}."
            return
        }
        viewModelScope.launch {
            CampusNotificationManager.cancelScheduleReminder(appContext, item.id)
            repository.deleteScheduleItem(item.id)
                .onSuccess { _snackbarMessage.value = "Removed ${item.courseCode}." }
                .onFailure { _snackbarMessage.value = it.localizedMessage ?: "Failed to delete item." }
        }
    }

    fun sendPreviewReminder(item: ScheduleItem) {
        if (!CampusNotificationManager.hasNotificationPermission(appContext)) {
            _snackbarMessage.value = "Enable notification permission in Alerts to test push alerts."
            return
        }
        CampusNotificationManager.showScheduleReminderNow(
            context = appContext,
            notificationId = ("preview_${item.id}").hashCode(),
            title = "Reminder Preview: ${item.courseCode}",
            body = "${item.title}${if (item.location.isNotBlank()) " · ${item.location}" else ""}"
        )
        _snackbarMessage.value = "Preview reminder dispatched to system tray."
    }

    fun importIot2BTimetable() {
        if (_importingTimetable.value) return
        viewModelScope.launch {
            _importingTimetable.value = true
            try {
                val leadMinutes = userProfile.value?.reminderLeadMinutes ?: 30
                val candidates = withContext(Dispatchers.IO) {
                    knowledgeBase.buildIot2BScheduleItems(leadMinutes)
                }
                if (candidates.isEmpty()) {
                    _snackbarMessage.value = "No IoT 2B timetable entries found."
                    return@launch
                }
                val existing = (scheduleState.value as? UiState.Success)?.data.orEmpty()
                val existingKeys = existing.map {
                    Triple(it.dayOfWeek, it.startTimeMinutes, it.title.trim().lowercase())
                }.toSet()

                val toInsert = candidates.filterNot {
                    Triple(it.dayOfWeek, it.startTimeMinutes, it.title.trim().lowercase()) in existingKeys
                }
                if (toInsert.isEmpty()) {
                    _snackbarMessage.value = "IoT 2B timetable is already in your schedule."
                    return@launch
                }

                if (isGuestMode) {
                    val localized = toInsert.map { item ->
                        item.copy(
                            id = "guest_iot_${UUID.randomUUID()}",
                            userId = "guest"
                        )
                    }
                    _guestSchedule.value = _guestSchedule.value + localized
                    localized.forEach { CampusNotificationManager.syncScheduleReminder(appContext, it) }
                    _snackbarMessage.value = "Imported ${localized.size} IoT 2B classes locally with push reminders."
                    return@launch
                }

                var importedCount = 0
                toInsert.forEach { item ->
                    repository.upsertScheduleItem(item).onSuccess { savedId ->
                        importedCount++
                        CampusNotificationManager.syncScheduleReminder(
                            appContext,
                            item.copy(id = savedId)
                        )
                    }
                }
                _snackbarMessage.value = if (importedCount > 0) {
                    "Imported $importedCount IoT 2B classes with push reminders."
                } else {
                    "Could not import IoT 2B timetable. Please try again."
                }
            } finally {
                _importingTimetable.value = false
            }
        }
    }

    fun importScheduleFromUri(uri: Uri) {
        if (_extractingSchedule.value) return
        viewModelScope.launch {
            _extractingSchedule.value = true
            _extractionError.value = null
            _lastImportSummary.value = null
            try {
                val leadMinutes = userProfile.value?.reminderLeadMinutes ?: 30
                val extracted = aiRepository.extractScheduleFromUri(
                    appContext = appContext,
                    uri = uri,
                    defaultLeadMinutes = leadMinutes
                )
                if (extracted.isEmpty()) {
                    _extractionError.value = "No classes or exam entries were found in the selected file. Try a sharper image or PDF timetable."
                    return@launch
                }

                val existing = (scheduleState.value as? UiState.Success)?.data.orEmpty()
                val existingKeys = existing.map { item ->
                    "${item.itemType}_${item.dayOfWeek}_${item.startTimeMinutes}_${item.examEpochMillis}_${item.title.trim().lowercase()}"
                }.toSet()

                val toInsert = extracted
                    .distinctBy { item ->
                        "${item.itemType}_${item.dayOfWeek}_${item.startTimeMinutes}_${item.examEpochMillis}_${item.title.trim().lowercase()}"
                    }
                    .filterNot { item ->
                        "${item.itemType}_${item.dayOfWeek}_${item.startTimeMinutes}_${item.examEpochMillis}_${item.title.trim().lowercase()}" in existingKeys
                    }

                if (toInsert.isEmpty()) {
                    _lastImportSummary.value = "All ${extracted.size} entries from that file are already in your schedule."
                    _snackbarMessage.value = "Schedule entries already imported."
                    return@launch
                }

                var addedClasses = 0
                var addedExams = 0
                val importedClassDays = sortedSetOf<Int>()

                if (isGuestMode) {
                    val localized = toInsert.map { item ->
                        if (item.parsedType == ScheduleItemType.EXAM) {
                            addedExams++
                        } else {
                            addedClasses++
                            importedClassDays.add(item.dayOfWeek)
                        }
                        item.copy(
                            id = "guest_scan_${UUID.randomUUID()}",
                            userId = "guest"
                        )
                    }
                    _guestSchedule.value = _guestSchedule.value + localized
                    localized.forEach { CampusNotificationManager.syncScheduleReminder(appContext, it) }
                } else {
                    toInsert.chunked(10).forEach { batch ->
                        val results = coroutineScope {
                            batch.map { item ->
                                async { item to repository.upsertScheduleItem(item) }
                            }.awaitAll()
                        }
                        results.forEach { (item, res) ->
                            res.onSuccess { savedId ->
                                if (item.parsedType == ScheduleItemType.EXAM) {
                                    addedExams++
                                } else {
                                    addedClasses++
                                    importedClassDays.add(item.dayOfWeek)
                                }
                                CampusNotificationManager.syncScheduleReminder(
                                    appContext,
                                    item.copy(id = savedId)
                                )
                            }
                        }
                    }
                }

                val totalAdded = addedClasses + addedExams
                if (totalAdded > 0) {
                    val daySpan = when {
                        importedClassDays.isEmpty() -> ""
                        importedClassDays.size == 1 -> " (${ScheduleFormatter.dayShort(importedClassDays.first())})"
                        else -> " (${ScheduleFormatter.dayShort(importedClassDays.first())}–${ScheduleFormatter.dayShort(importedClassDays.last())})"
                    }
                    val summary = buildString {
                        append("Auto-added ")
                        val parts = buildList {
                            if (addedClasses > 0) add("$addedClasses ${if (addedClasses == 1) "class" else "classes"}$daySpan")
                            if (addedExams > 0) add("$addedExams ${if (addedExams == 1) "exam" else "exams"}")
                        }
                        append(parts.joinToString(" and "))
                        append(" with push reminders.")
                    }
                    _lastImportSummary.value = summary
                    _snackbarMessage.value = summary
                } else {
                    _extractionError.value = "Extracted entries could not be saved. Please try again."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(VM_TAG, "Schedule PDF/Image extraction failed", e)
                _extractionError.value = e.localizedMessage ?: "Failed to extract schedule from document."
            } finally {
                _extractingSchedule.value = false
            }
        }
    }

    fun clearExtractionError() {
        _extractionError.value = null
    }

    fun dismissImportSummary() {
        _lastImportSummary.value = null
    }

    // --- Helpdesk Ticket Actions ---

    fun submitTicket(
        category: TicketCategory,
        priority: TicketPriority,
        subject: String,
        description: String,
        location: String,
        onComplete: () -> Unit = {}
    ) {
        if (isGuestMode) {
            _snackbarMessage.value = "Sign in with Google to submit helpdesk tickets."
            return
        }
        viewModelScope.launch {
            val authorName = userProfile.value?.displayName?.ifBlank { "Student" } ?: "Student"
            repository.createTicket(
                authorName = authorName,
                category = category,
                priority = priority,
                subject = subject,
                description = description,
                location = location
            ).onSuccess {
                _snackbarMessage.value = "Helpdesk ticket submitted."
                onComplete()
            }.onFailure {
                _snackbarMessage.value = it.localizedMessage ?: "Failed to submit ticket."
            }
        }
    }

    fun closeOwnTicket(ticketId: String) {
        if (isGuestMode) {
            _snackbarMessage.value = "Sign in with Google to manage helpdesk tickets."
            return
        }
        viewModelScope.launch {
            repository.closeOwnTicket(ticketId)
                .onSuccess { _snackbarMessage.value = "Ticket closed." }
                .onFailure { _snackbarMessage.value = it.localizedMessage ?: "Could not close ticket." }
        }
    }

    fun triageTicketAsAdmin(
        ticketId: String,
        status: TicketStatus,
        priority: TicketPriority,
        adminResponse: String,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            repository.updateTicketAsAdmin(ticketId, status, priority, adminResponse)
                .onSuccess {
                    _snackbarMessage.value = "Ticket updated to ${status.label}."
                    onComplete()
                }
                .onFailure {
                    _snackbarMessage.value = it.localizedMessage ?: "Failed to update ticket."
                }
        }
    }

    // --- Admin Broadcast, Directory & Role Management ---

    fun broadcastAnnouncement(
        severity: AnnouncementSeverity,
        title: String,
        body: String,
        department: String,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val authorName = userProfile.value?.displayName?.ifBlank { "Campus Admin" } ?: "Campus Admin"
            repository.broadcastAnnouncement(
                severity = severity,
                title = title,
                body = body,
                department = department,
                authorName = authorName
            ).onSuccess {
                _snackbarMessage.value = "Alert broadcast live to all connected students."
                onComplete()
            }.onFailure {
                _snackbarMessage.value = it.localizedMessage ?: "Failed to publish announcement."
            }
        }
    }

    fun deleteAnnouncement(announcementId: String) {
        viewModelScope.launch {
            repository.deleteAnnouncement(announcementId)
                .onSuccess { _snackbarMessage.value = "Announcement deleted." }
                .onFailure { _snackbarMessage.value = it.localizedMessage ?: "Failed to delete alert." }
        }
    }

    fun saveDirectoryOffice(office: DirectoryOffice, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.upsertDirectoryOffice(office)
                .onSuccess {
                    _snackbarMessage.value = "Campus directory updated."
                    onComplete()
                }
                .onFailure {
                    _snackbarMessage.value = it.localizedMessage ?: "Failed to save office."
                }
        }
    }

    fun deleteDirectoryOffice(officeId: String) {
        viewModelScope.launch {
            repository.deleteDirectoryOffice(officeId)
                .onSuccess { _snackbarMessage.value = "Directory entry removed." }
                .onFailure { _snackbarMessage.value = it.localizedMessage ?: "Failed to remove office." }
        }
    }

    fun grantAdminAccess(targetUid: String, label: String, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.grantAdminAccess(targetUid, label)
                .onSuccess {
                    _snackbarMessage.value = "Campus Admin role granted."
                    onComplete()
                }
                .onFailure {
                    _snackbarMessage.value = it.localizedMessage ?: "Failed to grant admin role."
                }
        }
    }

    fun revokeAdminAccess(targetUid: String) {
        viewModelScope.launch {
            repository.revokeAdminAccess(targetUid)
                .onSuccess { _snackbarMessage.value = "Campus Admin role revoked." }
                .onFailure { _snackbarMessage.value = it.localizedMessage ?: "Failed to revoke role." }
        }
    }

    // --- Preferences & UI Feedback ---

    fun updateProfilePreferences(
        displayName: String,
        department: String,
        notificationsEnabled: Boolean,
        reminderLeadMinutes: Int,
        onComplete: () -> Unit = {}
    ) {
        if (isGuestMode) {
            _guestProfile.value = _guestProfile.value.copy(
                displayName = displayName.trim().ifBlank { "Guest Student" }.take(80),
                department = department.trim().take(80),
                notificationsEnabled = notificationsEnabled,
                reminderLeadMinutes = reminderLeadMinutes.coerceIn(5, 1440)
            )
            _snackbarMessage.value = "Guest preferences saved for this session."
            onComplete()
            return
        }
        viewModelScope.launch {
            repository.updateUserProfile(
                displayName = displayName,
                department = department,
                notificationsEnabled = notificationsEnabled,
                reminderLeadMinutes = reminderLeadMinutes
            ).onSuccess {
                _snackbarMessage.value = "Preferences saved."
                onComplete()
            }.onFailure {
                _snackbarMessage.value = it.localizedMessage ?: "Could not save preferences."
            }
        }
    }

    fun dismissLiveBanner() {
        _liveAlertBanner.value = null
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    private fun <T> Flow<T>.toUiStateFlow(fallbackError: String): StateFlow<UiState<T>> =
        toUiStateFlowInner(fallbackError).stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    private fun <T> Flow<T>.toUiStateFlowInner(fallbackError: String): Flow<UiState<T>> =
        this.map<T, UiState<T>> { UiState.Success(it) }
            .catch { error ->
                val fsErr = unwrapFirestoreError(error)
                if (fsErr == null && error is CancellationException) throw error
                val root = fsErr ?: error
                emit(UiState.Error(root.localizedMessage ?: fallbackError))
            }

    private fun unwrapFirestoreError(error: Throwable): FirebaseFirestoreException? =
        generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()

    companion object {
        fun factory(context: Context, isGuestMode: Boolean = false): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val appCtx = context.applicationContext
                    return CampusBotViewModel(
                        appContext = appCtx,
                        repository = CampusRepository(appCtx),
                        knowledgeBase = CampusKnowledgeBase(appCtx),
                        isGuestMode = isGuestMode
                    ) as T
                }
            }
    }
}
