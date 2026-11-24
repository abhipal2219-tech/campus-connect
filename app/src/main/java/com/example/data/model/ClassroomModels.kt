package com.example.data.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ClassroomAssignment(
    val id: String,
    val courseId: String,
    val courseName: String,
    val courseSection: String = "",
    val title: String,
    val description: String = "",
    val dueEpochMillis: Long? = null,
    val submissionState: String = "NEW",
    val isLate: Boolean = false,
    val maxPoints: Double? = null,
    val alternateLink: String = ""
) {
    val isOverdue: Boolean
        get() = isLate || (dueEpochMillis != null && dueEpochMillis < System.currentTimeMillis())

    val isDueSoon: Boolean
        get() {
            val due = dueEpochMillis ?: return false
            val now = System.currentTimeMillis()
            return due in now..(now + 48 * 60 * 60 * 1000L)
        }

    val statusLabel: String
        get() = when {
            isOverdue -> "Missing"
            submissionState == "RECLAIMED_BY_STUDENT" -> "Reclaimed"
            submissionState == "CREATED" -> "In Progress"
            else -> "Assigned"
        }

    fun formattedDueLabel(): String {
        val due = dueEpochMillis ?: return "No due date"
        val fmt = SimpleDateFormat("EEE, MMM d · h:mm a", Locale.getDefault())
        return fmt.format(Date(due))
    }

    fun dueBadgeLabel(): String {
        val due = dueEpochMillis ?: return "NO DUE DATE"
        val diff = due - System.currentTimeMillis()
        if (diff <= 0L) {
            val pastHours = (-diff) / 3_600_000L
            return when {
                pastHours < 24L -> "OVERDUE ${pastHours.coerceAtLeast(1)}H"
                else -> "OVERDUE ${pastHours / 24L}D"
            }
        }
        val hours = diff / 3_600_000L
        val mins = (diff % 3_600_000L) / 60_000L
        return when {
            hours < 1L -> "DUE IN ${mins.coerceAtLeast(1)}M"
            hours < 24L -> "DUE IN ${hours}H"
            else -> "DUE IN ${hours / 24L}D"
        }
    }
}

data class ClassroomSyncResult(
    val assignments: List<ClassroomAssignment>,
    val activeCourseCount: Int,
    val syncedAtMillis: Long = System.currentTimeMillis()
)

sealed interface ClassroomUiState {
    data object Disconnected : ClassroomUiState
    data object Loading : ClassroomUiState
    data class Connected(
        val assignments: List<ClassroomAssignment>,
        val activeCourseCount: Int,
        val lastSyncedMillis: Long
    ) : ClassroomUiState
    data class Error(
        val message: String,
        val needsReconnect: Boolean = false
    ) : ClassroomUiState
}
