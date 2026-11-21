package com.example.viewmodel

import android.content.Context
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
import com.example.data.model.ScheduleItem
import com.example.data.model.TicketCategory
import com.example.data.model.TicketPriority
import com.example.data.model.TicketStatus
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.repository.CampusAiRepository
import com.example.data.repository.CampusRepository
import com.example.notifications.CampusNotificationManager
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

private const val VM_TAG = "CampusBotViewModel"

@OptIn(ExperimentalCoroutinesApi::class)
class CampusBotViewModel(
    private val appContext: Context,
    private val repository: CampusRepository,
    private val aiRepository: CampusAiRepository = CampusAiRepository()
) : ViewModel() {

    val userProfile: StateFlow<UserProfile?> = repository.observeUserProfile()
        .catch { error ->
            val fsErr = unwrapFirestoreError(error)
            if (fsErr == null && error is CancellationException) throw error
            Log.e(VM_TAG, "Profile stream error", error)
            emit(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val isAdmin: StateFlow<Boolean> = repository.observeAdminStatus()
        .catch { error ->
            val fsErr = unwrapFirestoreError(error)
            if (fsErr == null && error is CancellationException) throw error
            emit(false)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), false)

    val adminRecords: StateFlow<List<AdminRecord>> = isAdmin
        .flatMapLatest { admin ->
            if (admin) repository.observeAdminRecords() else flowOf(emptyList())
        }
        .catch { error ->
            val fsErr = unwrapFirestoreError(error)
            if (fsErr == null && error is CancellationException) throw error
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val scheduleState: StateFlow<UiState<List<ScheduleItem>>> =
        repository.observeScheduleItems().toUiStateFlow("Failed to load schedule")

    val directoryState: StateFlow<UiState<List<DirectoryOffice>>> =
        repository.observeDirectory().toUiStateFlow("Failed to load campus directory")

    val myTicketsState: StateFlow<UiState<List<HelpdeskTicket>>> =
        repository.observeMyTickets().toUiStateFlow("Failed to load helpdesk tickets")

    val allTicketsState: StateFlow<UiState<List<HelpdeskTicket>>> = isAdmin
        .flatMapLatest { admin ->
            if (admin) {
                repository.observeAllTicketsAdmin().toUiStateFlowInner("Failed to load ticket queue")
            } else {
                flowOf(UiState.Success(emptyList()))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val announcementsState: StateFlow<UiState<List<CampusAnnouncement>>> =
        repository.observeAnnouncements().toUiStateFlow("Failed to load campus alerts")

    val chatMessagesState: StateFlow<UiState<List<ChatMessage>>> =
        repository.observeChatMessages().toUiStateFlow("Failed to load chat history")

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

    private val seenAnnouncementIds = mutableSetOf<String>()
    private var announcementsBaselineLoaded = false
    private var directorySeedAttempted = false

    init {
        CampusNotificationManager.ensureChannels(appContext)
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

        // Keep local Android reminders synchronized with cloud schedule items
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

            val saveUserResult = repository.addChatMessage(ChatSender.USER, prompt)
            if (saveUserResult.isFailure) {
                _aiLoading.value = false
                _aiStreamingText.value = null
                _aiError.value = saveUserResult.exceptionOrNull()?.localizedMessage
                    ?: "Could not send message."
                return@launch
            }

            val history = (chatMessagesState.value as? UiState.Success)?.data.orEmpty()
            val schedule = (scheduleState.value as? UiState.Success)?.data.orEmpty()
            val alerts = (announcementsState.value as? UiState.Success)?.data.orEmpty()
            val offices = (directoryState.value as? UiState.Success)?.data.orEmpty()

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
                    repository.addChatMessage(ChatSender.ASSISTANT, finalReply)
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

    // --- Helpdesk Ticket Actions ---

    fun submitTicket(
        category: TicketCategory,
        priority: TicketPriority,
        subject: String,
        description: String,
        location: String,
        onComplete: () -> Unit = {}
    ) {
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
        fun factory(context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val appCtx = context.applicationContext
                    return CampusBotViewModel(
                        appContext = appCtx,
                        repository = CampusRepository(appCtx)
                    ) as T
                }
            }
    }
}
