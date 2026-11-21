package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.model.AdminRecord
import com.example.data.model.AnnouncementSeverity
import com.example.data.model.CampusAnnouncement
import com.example.data.model.ChatMessage
import com.example.data.model.ChatSender
import com.example.data.model.DirectoryCategory
import com.example.data.model.DirectoryOffice
import com.example.data.model.HelpdeskTicket
import com.example.data.model.ScheduleItem
import com.example.data.model.ScheduleItemType
import com.example.data.model.TicketCategory
import com.example.data.model.TicketPriority
import com.example.data.model.TicketStatus
import com.example.data.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

private const val BOOTSTRAPPED_ADMIN_EMAIL = "abhipal2219@gmail.com"

class CampusRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            throw IllegalStateException("User must be signed in with Google before accessing CampusBot data.")
        }
        return uid
    }

    private fun isBootstrappedAdminLocal(): Boolean {
        val user = auth.currentUser ?: return false
        return user.email.equals(BOOTSTRAPPED_ADMIN_EMAIL, ignoreCase = true) && user.isEmailVerified
    }

    // --- Profile & Admin Role Resolution ---

    suspend fun ensureUserProfile(): Result<UserProfile> {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            return Result.failure(IllegalStateException("Not authenticated"))
        }
        val path = "users/$uid"
        return try {
            val docRef = db.collection("users").document(uid)
            val snap = docRef.get().await()
            if (snap.exists()) {
                val existing = snap.toObject(
                    UserProfile::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ) ?: UserProfile(id = uid, userId = uid)
                Result.success(existing)
            } else {
                val user = auth.currentUser
                val safeName = (user?.displayName?.takeIf { it.isNotBlank() }
                    ?: user?.email?.substringBefore("@")
                    ?: "Campus Student").take(80)
                val safeEmail = (user?.email ?: "").take(120)
                val payload = mapOf(
                    "userId" to uid,
                    "displayName" to safeName,
                    "email" to safeEmail,
                    "department" to "General Studies",
                    "notificationsEnabled" to true,
                    "reminderLeadMinutes" to 30,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
                Result.success(
                    UserProfile(
                        id = uid,
                        userId = uid,
                        displayName = safeName,
                        email = safeEmail,
                        department = "General Studies",
                        notificationsEnabled = true,
                        reminderLeadMinutes = 30
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    fun observeUserProfile(): Flow<UserProfile?> = flow {
        val uid = requireUserId()
        val path = "users/$uid"
        emitAll(
            db.collection("users").document(uid)
                .snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(
                            UserProfile::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else null
                }
                .catch { error -> handleFlowError(error, OperationType.GET, path, uid) }
        )
    }

    suspend fun updateUserProfile(
        displayName: String,
        department: String,
        notificationsEnabled: Boolean,
        reminderLeadMinutes: Int
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$uid"
        return try {
            val cleanName = displayName.trim().take(80)
            require(cleanName.isNotEmpty()) { "Display name cannot be empty." }
            val cleanDept = department.trim().take(80)
            val safeLead = reminderLeadMinutes.coerceIn(5, 1440)
            db.collection("users").document(uid).update(
                mapOf(
                    "displayName" to cleanName,
                    "department" to cleanDept,
                    "notificationsEnabled" to notificationsEnabled,
                    "reminderLeadMinutes" to safeLead,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    fun observeAdminStatus(): Flow<Boolean> = flow {
        val uid = requireUserId()
        if (isBootstrappedAdminLocal()) {
            emit(true)
            return@flow
        }
        val path = "admins/$uid"
        emitAll(
            db.collection("admins").document(uid)
                .snapshots()
                .map { snap -> snap.exists() }
                .catch { error ->
                    val firestoreError = unwrapFirestoreException(error)
                    if (firestoreError == null && error is CancellationException) throw error
                    emit(false)
                }
        )
    }

    suspend fun grantAdminAccess(targetUid: String, label: String): Result<Unit> {
        val cleanTarget = targetUid.trim()
        val path = "admins/$cleanTarget"
        return try {
            val granterUid = requireUserId()
            require(cleanTarget.matches(Regex("^[a-zA-Z0-9_\\-]{1,128}$"))) {
                "Invalid UID format. Use alphanumeric characters, hyphens, or underscores."
            }
            val payload = mapOf(
                "adminUid" to cleanTarget,
                "grantedBy" to granterUid,
                "label" to label.trim().take(80),
                "createdAt" to FieldValue.serverTimestamp()
            )
            db.collection("admins").document(cleanTarget).set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    fun observeAdminRecords(): Flow<List<AdminRecord>> = flow {
        val uid = requireUserId()
        if (!isBootstrappedAdminLocal()) {
            emit(emptyList())
            return@flow
        }
        val path = "admins"
        emitAll(
            db.collection("admins")
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        AdminRecord::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error -> handleFlowError(error, OperationType.LIST, path, uid) }
        )
    }

    suspend fun revokeAdminAccess(targetUid: String): Result<Unit> {
        val cleanTarget = targetUid.trim()
        val path = "admins/$cleanTarget"
        return try {
            requireUserId()
            db.collection("admins").document(cleanTarget).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- Class Schedule & Exam Reminders ---

    fun observeScheduleItems(): Flow<List<ScheduleItem>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/scheduleItems"
        emitAll(
            db.collection("users").document(uid).collection("scheduleItems")
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        ScheduleItem::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error -> handleFlowError(error, OperationType.LIST, path, uid) }
        )
    }

    suspend fun upsertScheduleItem(item: ScheduleItem): Result<String> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        var docPath = "users/$uid/scheduleItems"
        return try {
            val cleanCode = item.courseCode.trim().take(24)
            val cleanTitle = item.title.trim().take(100)
            require(cleanCode.isNotEmpty()) { "Course code is required." }
            require(cleanTitle.isNotEmpty()) { "Title is required." }

            val col = db.collection("users").document(uid).collection("scheduleItems")
            val isUpdate = item.id.isNotBlank()
            val docRef = if (isUpdate) col.document(item.id) else col.document()
            docPath = docRef.path

            val safeDay = item.dayOfWeek.coerceIn(1, 7)
            val safeStart = item.startTimeMinutes.coerceIn(0, 1439)
            val safeEnd = item.endTimeMinutes.coerceIn(0, 1439)
            val safeExam = if (item.parsedType == ScheduleItemType.EXAM) {
                item.examEpochMillis.coerceIn(0L, 4102444800000L)
            } else 0L
            val safeLead = item.reminderLeadMinutes.coerceIn(5, 1440)

            if (isUpdate) {
                docRef.update(
                    mapOf(
                        "itemType" to item.parsedType.name,
                        "courseCode" to cleanCode,
                        "title" to cleanTitle,
                        "dayOfWeek" to safeDay,
                        "startTimeMinutes" to safeStart,
                        "endTimeMinutes" to safeEnd,
                        "examEpochMillis" to safeExam,
                        "location" to item.location.trim().take(100),
                        "instructor" to item.instructor.trim().take(80),
                        "reminderEnabled" to item.reminderEnabled,
                        "reminderLeadMinutes" to safeLead,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "itemType" to item.parsedType.name,
                        "courseCode" to cleanCode,
                        "title" to cleanTitle,
                        "dayOfWeek" to safeDay,
                        "startTimeMinutes" to safeStart,
                        "endTimeMinutes" to safeEnd,
                        "examEpochMillis" to safeExam,
                        "location" to item.location.trim().take(100),
                        "instructor" to item.instructor.trim().take(80),
                        "reminderEnabled" to item.reminderEnabled,
                        "reminderLeadMinutes" to safeLead,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun toggleScheduleReminder(itemId: String, enabled: Boolean): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$uid/scheduleItems/$itemId"
        return try {
            require(itemId.isNotBlank()) { "Schedule item ID is missing." }
            db.collection("users").document(uid).collection("scheduleItems").document(itemId)
                .update(
                    mapOf(
                        "reminderEnabled" to enabled,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteScheduleItem(itemId: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$uid/scheduleItems/$itemId"
        return try {
            require(itemId.isNotBlank()) { "Schedule item ID is missing." }
            db.collection("users").document(uid).collection("scheduleItems").document(itemId)
                .delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- AI Chat History ---

    fun observeChatMessages(): Flow<List<ChatMessage>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/chatMessages"
        emitAll(
            db.collection("users").document(uid).collection("chatMessages")
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        ChatMessage::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.createdAt?.toDate()?.time ?: Long.MAX_VALUE }
                }
                .catch { error -> handleFlowError(error, OperationType.LIST, path, uid) }
        )
    }

    suspend fun addChatMessage(sender: ChatSender, content: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        var path = "users/$uid/chatMessages"
        return try {
            val clean = content.trim().take(8000)
            require(clean.isNotEmpty()) { "Message cannot be empty." }
            val docRef = db.collection("users").document(uid).collection("chatMessages").document()
            path = docRef.path
            docRef.set(
                mapOf(
                    "userId" to uid,
                    "sender" to sender.name,
                    "content" to clean,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun clearChatHistory(): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$uid/chatMessages"
        return try {
            val snap = db.collection("users").document(uid).collection("chatMessages").get().await()
            if (snap.isEmpty) return Result.success(Unit)
            val batch = db.batch()
            snap.documents.forEach { batch.delete(it.reference) }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- Campus Directory ---

    fun observeDirectory(): Flow<List<DirectoryOffice>> = flow {
        val uid = requireUserId()
        val path = "directory"
        emitAll(
            db.collection("directory")
                .whereEqualTo("visibility", "campus")
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        DirectoryOffice::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.name.lowercase() }
                }
                .catch { error -> handleFlowError(error, OperationType.LIST, path, uid) }
        )
    }

    suspend fun upsertDirectoryOffice(office: DirectoryOffice): Result<Unit> {
        var path = "directory"
        return try {
            requireUserId()
            val cleanName = office.name.trim().take(100)
            val cleanBuilding = office.building.trim().take(80)
            val cleanHours = office.hours.trim().take(100)
            require(cleanName.length >= 2) { "Office name must be at least 2 characters." }
            require(cleanBuilding.isNotEmpty()) { "Building name is required." }
            require(cleanHours.isNotEmpty()) { "Operating hours are required." }

            val col = db.collection("directory")
            val isUpdate = office.id.isNotBlank()
            val docRef = if (isUpdate) col.document(office.id) else col.document()
            path = docRef.path

            if (isUpdate) {
                docRef.update(
                    mapOf(
                        "name" to cleanName,
                        "category" to office.parsedCategory.name,
                        "building" to cleanBuilding,
                        "room" to office.room.trim().take(40),
                        "hours" to cleanHours,
                        "phone" to office.phone.trim().take(32),
                        "email" to office.email.trim().take(100),
                        "description" to office.description.trim().take(400),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "visibility" to "campus",
                        "name" to cleanName,
                        "category" to office.parsedCategory.name,
                        "building" to cleanBuilding,
                        "room" to office.room.trim().take(40),
                        "hours" to cleanHours,
                        "phone" to office.phone.trim().take(32),
                        "email" to office.email.trim().take(100),
                        "description" to office.description.trim().take(400),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteDirectoryOffice(officeId: String): Result<Unit> {
        val path = "directory/$officeId"
        return try {
            requireUserId()
            db.collection("directory").document(officeId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun seedStarterDirectoryIfEmpty(): Result<Unit> {
        val path = "directory"
        return try {
            requireUserId()
            val existing = db.collection("directory")
                .whereEqualTo("visibility", "campus")
                .limit(1)
                .get()
                .await()
            if (!existing.isEmpty) return Result.success(Unit)

            val defaults = listOf(
                DirectoryOffice(
                    name = "Campus IT Helpdesk",
                    category = DirectoryCategory.IT_SUPPORT.name,
                    building = "Innovation Commons",
                    room = "Suite 104",
                    hours = "Mon–Fri 08:00 – 20:00",
                    phone = "+15550101104",
                    email = "helpdesk@campus.edu",
                    description = "Campus Wi-Fi, LMS access, student email, and lab workstation troubleshooting."
                ),
                DirectoryOffice(
                    name = "Office of the Registrar",
                    category = DirectoryCategory.ACADEMIC.name,
                    building = "Founders Hall",
                    room = "Room 210",
                    hours = "Mon–Fri 09:00 – 17:00",
                    phone = "+15550102210",
                    email = "registrar@campus.edu",
                    description = "Course enrollment, exam schedule conflicts, transcripts, and degree audits."
                ),
                DirectoryOffice(
                    name = "Main Library Research Desk",
                    category = DirectoryCategory.ACADEMIC.name,
                    building = "Central Library",
                    room = "Level 2 Atrium",
                    hours = "Daily 07:30 – 23:00",
                    phone = "+15550103200",
                    email = "library@campus.edu",
                    description = "Study room bookings, research databases, printing kiosks, and exam reserves."
                ),
                DirectoryOffice(
                    name = "Campus Safety & Dispatch",
                    category = DirectoryCategory.SAFETY.name,
                    building = "North Gate Annex",
                    room = "Ground Floor",
                    hours = "24/7 Operations",
                    phone = "+15550109911",
                    email = "safety@campus.edu",
                    description = "24-hour emergency response, evening campus escort, and lost ID card deactivation."
                ),
                DirectoryOffice(
                    name = "Facilities & Maintenance",
                    category = DirectoryCategory.FACILITIES.name,
                    building = "Operations Center",
                    room = "Room 115",
                    hours = "Mon–Sat 07:00 – 19:00",
                    phone = "+15550104115",
                    email = "facilities@campus.edu",
                    description = "Classroom AV, lighting, HVAC repairs, and accessible room requests."
                )
            )
            defaults.forEach { upsertDirectoryOffice(it) }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    // --- Helpdesk Tickets ---

    fun observeMyTickets(): Flow<List<HelpdeskTicket>> = flow {
        val uid = requireUserId()
        val path = "tickets"
        emitAll(
            db.collection("tickets")
                .whereEqualTo("userId", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        HelpdeskTicket::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
                }
                .catch { error -> handleFlowError(error, OperationType.LIST, path, uid) }
        )
    }

    fun observeAllTicketsAdmin(): Flow<List<HelpdeskTicket>> = flow {
        val uid = requireUserId()
        val path = "tickets"
        emitAll(
            db.collection("tickets")
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        HelpdeskTicket::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
                }
                .catch { error -> handleFlowError(error, OperationType.LIST, path, uid) }
        )
    }

    suspend fun createTicket(
        authorName: String,
        category: TicketCategory,
        priority: TicketPriority,
        subject: String,
        description: String,
        location: String
    ): Result<Unit> {
        var path = "tickets"
        return try {
            val uid = requireUserId()
            val cleanSubject = subject.trim().take(120)
            val cleanDesc = description.trim().take(1500)
            require(cleanSubject.length >= 3) { "Subject must be at least 3 characters." }
            require(cleanDesc.length >= 5) { "Description must be at least 5 characters." }

            val docRef = db.collection("tickets").document()
            path = docRef.path
            docRef.set(
                mapOf(
                    "userId" to uid,
                    "authorName" to authorName.trim().ifBlank { "Student" }.take(80),
                    "category" to category.name,
                    "priority" to priority.name,
                    "subject" to cleanSubject,
                    "description" to cleanDesc,
                    "location" to location.trim().take(100),
                    "status" to TicketStatus.OPEN.name,
                    "adminResponse" to "",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun closeOwnTicket(ticketId: String): Result<Unit> {
        val path = "tickets/$ticketId"
        return try {
            requireUserId()
            require(ticketId.isNotBlank()) { "Ticket ID missing." }
            db.collection("tickets").document(ticketId).update(
                mapOf(
                    "status" to TicketStatus.CLOSED.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateTicketAsAdmin(
        ticketId: String,
        status: TicketStatus,
        priority: TicketPriority,
        adminResponse: String
    ): Result<Unit> {
        val path = "tickets/$ticketId"
        return try {
            requireUserId()
            require(ticketId.isNotBlank()) { "Ticket ID missing." }
            db.collection("tickets").document(ticketId).update(
                mapOf(
                    "status" to status.name,
                    "priority" to priority.name,
                    "adminResponse" to adminResponse.trim().take(1500),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- Live Campus Announcements ---

    fun observeAnnouncements(): Flow<List<CampusAnnouncement>> = flow {
        val uid = requireUserId()
        val path = "announcements"
        emitAll(
            db.collection("announcements")
                .whereEqualTo("visibility", "campus")
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        CampusAnnouncement::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
                }
                .catch { error -> handleFlowError(error, OperationType.LIST, path, uid) }
        )
    }

    suspend fun broadcastAnnouncement(
        severity: AnnouncementSeverity,
        title: String,
        body: String,
        department: String,
        authorName: String
    ): Result<Unit> {
        var path = "announcements"
        return try {
            val uid = requireUserId()
            val cleanTitle = title.trim().take(120)
            val cleanBody = body.trim().take(1500)
            val cleanDept = department.trim().ifBlank { "Campus Admin" }.take(60)
            require(cleanTitle.length >= 3) { "Headline must be at least 3 characters." }
            require(cleanBody.length >= 5) { "Message body must be at least 5 characters." }

            val docRef = db.collection("announcements").document()
            path = docRef.path
            docRef.set(
                mapOf(
                    "visibility" to "campus",
                    "severity" to severity.name,
                    "title" to cleanTitle,
                    "body" to cleanBody,
                    "department" to cleanDept,
                    "authorId" to uid,
                    "authorName" to authorName.trim().ifBlank { "Campus Admin" }.take(80),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteAnnouncement(announcementId: String): Result<Unit> {
        val path = "announcements/$announcementId"
        return try {
            requireUserId()
            db.collection("announcements").document(announcementId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    private fun unwrapFirestoreException(error: Throwable): FirebaseFirestoreException? {
        return generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
    }

    private fun handleFlowError(
        error: Throwable,
        op: OperationType,
        path: String,
        uid: String
    ): Nothing {
        val firestoreError = unwrapFirestoreException(error)
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, op, path)
        }
        throw error
    }
}
