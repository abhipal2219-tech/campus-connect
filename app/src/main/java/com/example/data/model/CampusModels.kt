package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class ScheduleItemType(val label: String) {
    CLASS("Class"),
    EXAM("Exam");

    companion object {
        fun fromRaw(raw: String): ScheduleItemType =
            entries.firstOrNull { it.name == raw } ?: CLASS
    }
}

enum class DirectoryCategory(val label: String) {
    ACADEMIC("Academic"),
    IT_SUPPORT("IT Support"),
    STUDENT_LIFE("Student Life"),
    FACILITIES("Facilities"),
    SAFETY("Safety");

    companion object {
        fun fromRaw(raw: String): DirectoryCategory =
            entries.firstOrNull { it.name == raw } ?: ACADEMIC
    }
}

enum class TicketCategory(val label: String) {
    IT_SUPPORT("IT Support"),
    FACILITIES("Facilities"),
    ACADEMIC("Academic"),
    ID_ACCESS("ID & Access"),
    OTHER("Other");

    companion object {
        fun fromRaw(raw: String): TicketCategory =
            entries.firstOrNull { it.name == raw } ?: OTHER
    }
}

enum class TicketPriority(val label: String) {
    LOW("Low"),
    MEDIUM("Medium"),
    HIGH("High");

    companion object {
        fun fromRaw(raw: String): TicketPriority =
            entries.firstOrNull { it.name == raw } ?: MEDIUM
    }
}

enum class TicketStatus(val label: String) {
    OPEN("Open"),
    IN_PROGRESS("In Progress"),
    RESOLVED("Resolved"),
    CLOSED("Closed");

    companion object {
        fun fromRaw(raw: String): TicketStatus =
            entries.firstOrNull { it.name == raw } ?: OPEN
    }
}

enum class AnnouncementSeverity(val label: String) {
    URGENT("Urgent"),
    INFO("Info");

    companion object {
        fun fromRaw(raw: String): AnnouncementSeverity =
            entries.firstOrNull { it.name == raw } ?: INFO
    }
}

enum class ChatSender {
    USER,
    ASSISTANT;

    companion object {
        fun fromRaw(raw: String): ChatSender =
            entries.firstOrNull { it.name == raw } ?: USER
    }
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val department: String = "",
    val notificationsEnabled: Boolean = true,
    val reminderLeadMinutes: Int = 30,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AdminRecord(
    @DocumentId val id: String = "",
    val adminUid: String = "",
    val grantedBy: String = "",
    val label: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ScheduleItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val itemType: String = ScheduleItemType.CLASS.name,
    val courseCode: String = "",
    val title: String = "",
    val dayOfWeek: Int = 1,
    val startTimeMinutes: Int = 540,
    val endTimeMinutes: Int = 600,
    val examEpochMillis: Long = 0L,
    val location: String = "",
    val instructor: String = "",
    val reminderEnabled: Boolean = true,
    val reminderLeadMinutes: Int = 30,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val parsedType: ScheduleItemType get() = ScheduleItemType.fromRaw(itemType)
}

@Keep
@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val userId: String = "",
    val sender: String = ChatSender.USER.name,
    val content: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    val parsedSender: ChatSender get() = ChatSender.fromRaw(sender)
}

@Keep
@IgnoreExtraProperties
data class DirectoryOffice(
    @DocumentId val id: String = "",
    val visibility: String = "campus",
    val name: String = "",
    val category: String = DirectoryCategory.ACADEMIC.name,
    val building: String = "",
    val room: String = "",
    val hours: String = "",
    val phone: String = "",
    val email: String = "",
    val description: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val parsedCategory: DirectoryCategory get() = DirectoryCategory.fromRaw(category)
}

@Keep
@IgnoreExtraProperties
data class HelpdeskTicket(
    @DocumentId val id: String = "",
    val userId: String = "",
    val authorName: String = "",
    val category: String = TicketCategory.IT_SUPPORT.name,
    val priority: String = TicketPriority.MEDIUM.name,
    val subject: String = "",
    val description: String = "",
    val location: String = "",
    val status: String = TicketStatus.OPEN.name,
    val adminResponse: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val parsedCategory: TicketCategory get() = TicketCategory.fromRaw(category)
    val parsedPriority: TicketPriority get() = TicketPriority.fromRaw(priority)
    val parsedStatus: TicketStatus get() = TicketStatus.fromRaw(status)
}

@Keep
@IgnoreExtraProperties
data class CampusAnnouncement(
    @DocumentId val id: String = "",
    val visibility: String = "campus",
    val severity: String = AnnouncementSeverity.INFO.name,
    val title: String = "",
    val body: String = "",
    val department: String = "",
    val authorId: String = "",
    val authorName: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    val parsedSeverity: AnnouncementSeverity get() = AnnouncementSeverity.fromRaw(severity)
}

object ScheduleFormatter {
    private val dayNamesShort = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    private val dayNamesFull = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

    fun dayShort(dayOfWeek: Int): String = dayNamesShort.getOrElse(dayOfWeek - 1) { "Mon" }
    fun dayFull(dayOfWeek: Int): String = dayNamesFull.getOrElse(dayOfWeek - 1) { "Monday" }

    fun todayDayOfWeek(): Int {
        val cal = Calendar.getInstance()
        return when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            else -> 7
        }
    }

    fun formatMinutes(minutesFromMidnight: Int): String {
        val clamped = minutesFromMidnight.coerceIn(0, 1439)
        val hours = clamped / 60
        val mins = clamped % 60
        return String.format(Locale.US, "%02d:%02d", hours, mins)
    }

    fun formatExamDate(epochMillis: Long): String {
        if (epochMillis <= 0L) return "TBD"
        val fmt = SimpleDateFormat("EEE, MMM d · HH:mm", Locale.US)
        return fmt.format(Date(epochMillis))
    }

    fun formatTimestamp(ts: Timestamp?): String {
        val date = ts?.toDate() ?: return "Just now"
        val fmt = SimpleDateFormat("MMM d · HH:mm", Locale.US)
        return fmt.format(date)
    }

    fun nextOccurrenceMillis(item: ScheduleItem, nowMillis: Long = System.currentTimeMillis()): Long {
        if (item.parsedType == ScheduleItemType.EXAM) {
            return item.examEpochMillis
        }
        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val targetCalDay = when (item.dayOfWeek) {
            1 -> Calendar.MONDAY
            2 -> Calendar.TUESDAY
            3 -> Calendar.WEDNESDAY
            4 -> Calendar.THURSDAY
            5 -> Calendar.FRIDAY
            6 -> Calendar.SATURDAY
            else -> Calendar.SUNDAY
        }
        val currentCalDay = cal.get(Calendar.DAY_OF_WEEK)
        var daysAhead = (targetCalDay - currentCalDay + 7) % 7
        cal.add(Calendar.DAY_OF_YEAR, daysAhead)
        cal.set(Calendar.HOUR_OF_DAY, (item.startTimeMinutes / 60).coerceIn(0, 23))
        cal.set(Calendar.MINUTE, (item.startTimeMinutes % 60).coerceIn(0, 59))
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        if (cal.timeInMillis <= nowMillis) {
            cal.add(Calendar.DAY_OF_YEAR, 7)
        }
        return cal.timeInMillis
    }

    fun countdownLabel(targetMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
        val diff = targetMillis - nowMillis
        if (diff <= 0L) return "NOW / PAST"
        val totalMinutes = diff / 60_000L
        val days = totalMinutes / (60 * 24)
        val hours = (totalMinutes % (60 * 24)) / 60
        val mins = totalMinutes % 60
        return when {
            days > 0 -> "IN ${days}D ${hours}H"
            hours > 0 -> "IN ${hours}H ${mins}M"
            else -> "IN ${mins.coerceAtLeast(1)}M"
        }
    }

    fun leadTimeLabel(minutes: Int): String = when (minutes) {
        15 -> "15 min before"
        30 -> "30 min before"
        60 -> "1 hour before"
        180 -> "3 hours before"
        1440 -> "1 day before"
        else -> "$minutes min before"
    }
}
