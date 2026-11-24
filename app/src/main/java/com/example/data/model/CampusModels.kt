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
import org.json.JSONArray
import org.json.JSONObject

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

enum class PublishedScheduleKind(val label: String) {
    CLASS("Class Timetable"),
    EXAM("Exam Routine"),
    BOTH("Class & Exam");

    companion object {
        fun fromRaw(raw: String): PublishedScheduleKind =
            entries.firstOrNull { it.name == raw } ?: CLASS
    }
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val program: String = "",
    val department: String = "",
    val semester: Int = 0,
    val section: String = "",
    val rollNumber: String = "",
    val enrollmentNumber: String = "",
    val profileCompleted: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val reminderLeadMinutes: Int = 30,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val academicYear: Int
        get() = AcademicCatalog.yearFromSemester(semester)

    val needsAcademicOnboarding: Boolean
        get() = !profileCompleted ||
            program.isBlank() ||
            department.isBlank() ||
            semester <= 0 ||
            section.isBlank() ||
            rollNumber.isBlank() ||
            enrollmentNumber.isBlank()

    fun toAcademicScope(): StudentAcademicScope? {
        if (department.isBlank() || semester <= 0 || section.isBlank()) return null
        return StudentAcademicScope(
            program = program,
            department = department,
            semester = semester,
            section = section,
            rollNumber = rollNumber,
            enrollmentNumber = enrollmentNumber
        )
    }
}

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

@Keep
@IgnoreExtraProperties
data class PublishedSchedule(
    @DocumentId val id: String = "",
    val visibility: String = "campus",
    val program: String = "",
    val department: String = "",
    val academicYear: Int = 1,
    val semester: Int = 0,
    val section: String = "",
    val scheduleKind: String = PublishedScheduleKind.CLASS.name,
    val title: String = "",
    val entriesJson: String = "[]",
    val entryCount: Int = 0,
    val publishedByUid: String = "",
    val publishedByName: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val parsedKind: PublishedScheduleKind get() = PublishedScheduleKind.fromRaw(scheduleKind)

    val batchLabel: String
        get() {
            val parts = buildList {
                if (program.isNotBlank()) add(program)
                if (department.isNotBlank()) add(department)
                add("Year $academicYear")
                if (semester > 0) add("Sem $semester")
                if (section.isNotBlank()) add("Sec ${AcademicCatalog.normalizeSection(section)}")
            }
            return parts.joinToString(" · ")
        }

    fun matchesScope(scope: StudentAcademicScope?): Boolean {
        if (scope == null || !scope.isConfigured) return false
        val deptMatch = department.trim().equals(scope.department.trim(), ignoreCase = true)
        val programMatch = program.isBlank() || scope.program.isBlank() ||
            program.trim().equals(scope.program.trim(), ignoreCase = true)
        val yearMatch = academicYear == scope.academicYear
        val semMatch = semester <= 0 || semester == scope.semester
        val secMatch = AcademicCatalog.normalizeSection(section) == AcademicCatalog.normalizeSection(scope.section)
        return deptMatch && programMatch && yearMatch && semMatch && secMatch
    }
}

object PublishedScheduleCodec {
    fun encode(items: List<ScheduleItem>): String {
        val arr = JSONArray()
        items.take(150).forEach { item ->
            val obj = JSONObject()
            obj.put("itemType", item.parsedType.name)
            obj.put("courseCode", item.courseCode.trim().ifBlank { "CLS" }.take(24))
            obj.put("title", item.title.trim().ifBlank { "Session" }.take(100))
            obj.put("dayOfWeek", item.dayOfWeek.coerceIn(1, 7))
            obj.put("startTimeMinutes", item.startTimeMinutes.coerceIn(0, 1439))
            obj.put("endTimeMinutes", item.endTimeMinutes.coerceIn(0, 1439))
            obj.put("examEpochMillis", item.examEpochMillis.coerceAtLeast(0L))
            obj.put("location", item.location.trim().take(100))
            obj.put("instructor", item.instructor.trim().take(80))
            arr.put(obj)
        }
        return arr.toString()
    }

    fun decode(entriesJson: String, defaultLeadMinutes: Int = 30): List<ScheduleItem> {
        if (entriesJson.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(entriesJson)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val rawType = obj.optString("itemType", ScheduleItemType.CLASS.name)
                    val title = obj.optString("title", "").trim().take(100)
                    if (title.isBlank()) continue
                    val startMin = obj.optInt("startTimeMinutes", 540).coerceIn(0, 1439)
                    val endMin = obj.optInt("endTimeMinutes", (startMin + 60).coerceAtMost(1439)).coerceIn(0, 1439)
                    add(
                        ScheduleItem(
                            itemType = ScheduleItemType.fromRaw(rawType).name,
                            courseCode = obj.optString("courseCode", "CLS").trim().ifBlank { "CLS" }.take(24),
                            title = title,
                            dayOfWeek = obj.optInt("dayOfWeek", 1).coerceIn(1, 7),
                            startTimeMinutes = startMin,
                            endTimeMinutes = endMin,
                            examEpochMillis = obj.optLong("examEpochMillis", 0L).coerceAtLeast(0L),
                            location = obj.optString("location", "").trim().take(100),
                            instructor = obj.optString("instructor", "").trim().take(80),
                            reminderEnabled = true,
                            reminderLeadMinutes = defaultLeadMinutes.coerceIn(5, 1440)
                        )
                    )
                }
            }
        }.getOrElse { emptyList() }
    }
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
