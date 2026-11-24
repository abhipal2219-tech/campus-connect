package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.AssignmentTurnedIn
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.ClassroomAssignment
import com.example.data.model.ClassroomUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun LazyListScope.classroomAssignmentsItems(
    classroomState: ClassroomUiState,
    selectedFilter: String,
    onSelectFilter: (String) -> Unit,
    onConnectClassroom: () -> Unit,
    onRefreshClassroom: () -> Unit,
    onDisconnectClassroom: () -> Unit,
    onAddAssignmentReminder: (ClassroomAssignment) -> Unit
) {
    when (classroomState) {
        is ClassroomUiState.Disconnected -> {
            item(key = "classroom_connect_card") {
                ClassroomConnectCard(onConnect = onConnectClassroom)
            }
        }

        is ClassroomUiState.Loading -> {
            item(key = "classroom_loading") {
                MonochromeLoadingState("Syncing pending assignments from Google Classroom...")
            }
        }

        is ClassroomUiState.Error -> {
            item(key = "classroom_error") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    MonochromeErrorCard(message = classroomState.message)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = if (classroomState.needsReconnect) onConnectClassroom else onRefreshClassroom,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("classroom_retry_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (classroomState.needsReconnect) "Reconnect Classroom" else "Try Again")
                        }
                    }
                }
            }
        }

        is ClassroomUiState.Connected -> {
            val allPending = classroomState.assignments
            val overdueCount = allPending.count { it.isOverdue }
            val dueSoonCount = allPending.count { it.isDueSoon }
            val distinctCourses = allPending.map { it.courseName }.distinct().sorted()

            item(key = "classroom_summary_card") {
                ClassroomSummaryHeaderCard(
                    pendingCount = allPending.size,
                    overdueCount = overdueCount,
                    courseCount = classroomState.activeCourseCount,
                    lastSyncedMillis = classroomState.lastSyncedMillis,
                    onRefresh = onRefreshClassroom,
                    onDisconnect = onDisconnectClassroom
                )
            }

            if (allPending.isEmpty()) {
                item(key = "classroom_caught_up") {
                    MonochromeEmptyState(
                        icon = Icons.Outlined.AssignmentTurnedIn,
                        title = "All caught up",
                        subtitle = if (classroomState.activeCourseCount > 0) {
                            "Zero pending assignments across your ${classroomState.activeCourseCount} active Google Classroom courses."
                        } else {
                            "No active Google Classroom courses were found on this Google account."
                        },
                        actionLabel = "Refresh Now",
                        onAction = onRefreshClassroom,
                        actionTag = "classroom_empty_refresh_button"
                    )
                }
            } else {
                // Filter bar: All Pending, Overdue, Due Soon, and per-course chips
                item(key = "classroom_filters") {
                    val baseFilters = buildList {
                        add("ALL" to "All (${allPending.size})")
                        if (overdueCount > 0) add("OVERDUE" to "Overdue ($overdueCount)")
                        if (dueSoonCount > 0) add("DUE_SOON" to "Due 48h ($dueSoonCount)")
                        distinctCourses.forEach { course ->
                            add("COURSE:$course" to course)
                        }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(baseFilters, key = { it.first }) { (filterKey, label) ->
                            FilterChip(
                                selected = selectedFilter == filterKey,
                                onClick = { onSelectFilter(filterKey) },
                                label = {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                modifier = Modifier.testTag("classroom_filter_$filterKey")
                            )
                        }
                    }
                }

                val filtered = when {
                    selectedFilter == "OVERDUE" -> allPending.filter { it.isOverdue }
                    selectedFilter == "DUE_SOON" -> allPending.filter { it.isDueSoon }
                    selectedFilter.startsWith("COURSE:") -> {
                        val courseTarget = selectedFilter.removePrefix("COURSE:")
                        allPending.filter { it.courseName == courseTarget }
                    }
                    else -> allPending
                }

                if (filtered.isEmpty()) {
                    item(key = "classroom_filter_empty") {
                        MonochromeEmptyState(
                            icon = Icons.Outlined.Assignment,
                            title = "No assignments in this filter",
                            subtitle = "Switch back to All to view all ${allPending.size} pending assignments.",
                            actionLabel = "Show All",
                            onAction = { onSelectFilter("ALL") },
                            actionTag = "classroom_reset_filter_button"
                        )
                    }
                } else {
                    items(filtered, key = { it.id }) { assignment ->
                        ClassroomAssignmentCard(
                            assignment = assignment,
                            onAddReminder = { onAddAssignmentReminder(assignment) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ClassroomConnectCard(
    onConnect: () -> Unit
) {
    MonochromeCard(containerColor = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.School,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(10.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Google Classroom Assignments",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Live read-only sync",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = "Connect your Google Classroom account to automatically pull every unsubmitted and overdue assignment across all active courses, add push deadline reminders, and ask CampusBot what's due next.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = onConnect,
                shape = MaterialTheme.shapes.small,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("connect_classroom_button")
            ) {
                Icon(
                    imageVector = Icons.Outlined.Assignment,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Connect Google Classroom",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun ClassroomSummaryHeaderCard(
    pendingCount: Int,
    overdueCount: Int,
    courseCount: Int,
    lastSyncedMillis: Long,
    onRefresh: () -> Unit,
    onDisconnect: () -> Unit
) {
    val syncedTime = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(lastSyncedMillis))
    MonochromeCard(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Google Classroom · Connected",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "$courseCount active ${if (courseCount == 1) "course" else "courses"} · Synced at $syncedTime",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("refresh_classroom_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = "Refresh Google Classroom assignments",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = onDisconnect,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("disconnect_classroom_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.LinkOff,
                            contentDescription = "Disconnect Google Classroom",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MonochromeBadge(
                    text = "$pendingCount PENDING",
                    inverted = pendingCount > 0
                )
                if (overdueCount > 0) {
                    MonochromeBadge(
                        text = "$overdueCount OVERDUE",
                        urgent = true
                    )
                }
            }
        }
    }
}

@Composable
private fun ClassroomAssignmentCard(
    assignment: ClassroomAssignment,
    onAddReminder: () -> Unit
) {
    val context = LocalContext.current
    val urgent = assignment.isOverdue || assignment.isDueSoon

    MonochromeCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("classroom_assignment_${assignment.id}"),
        borderColor = if (assignment.isOverdue) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.outlineVariant
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    MonochromeBadge(
                        text = assignment.courseName.take(26),
                        inverted = true
                    )
                    MonochromeBadge(
                        text = assignment.dueBadgeLabel(),
                        urgent = urgent
                    )
                }
                if (assignment.maxPoints != null) {
                    val ptsStr = if (assignment.maxPoints % 1.0 == 0.0) {
                        "${assignment.maxPoints.toInt()} pts"
                    } else {
                        "${assignment.maxPoints} pts"
                    }
                    Text(
                        text = ptsStr,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = assignment.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (assignment.description.isNotBlank()) {
                Text(
                    text = assignment.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Schedule,
                    contentDescription = null,
                    tint = if (assignment.isOverdue) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = "${assignment.formattedDueLabel()} · ${assignment.statusLabel}",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (assignment.isOverdue) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onAddReminder,
                    shape = MaterialTheme.shapes.small,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("remind_assignment_${assignment.id}")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.NotificationsActive,
                        contentDescription = "Add deadline reminder",
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Reminder", style = MaterialTheme.typography.labelMedium)
                }

                if (assignment.alternateLink.isNotBlank()) {
                    TextButton(
                        onClick = {
                            runCatching {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(assignment.alternateLink))
                                context.startActivity(intent)
                            }
                        },
                        modifier = Modifier.testTag("open_classroom_${assignment.id}")
                    ) {
                        Text("Open", style = MaterialTheme.typography.labelLarge)
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                            contentDescription = "Open in Google Classroom",
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }
    }
}
