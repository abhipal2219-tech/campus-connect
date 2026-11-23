package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.AnnouncementSeverity
import com.example.data.model.CampusAnnouncement
import com.example.data.model.ScheduleFormatter
import com.example.data.model.UiState
import com.example.notifications.CampusNotificationManager
import com.example.ui.components.MonochromeBadge
import com.example.ui.components.MonochromeCard
import com.example.ui.components.MonochromeEmptyState
import com.example.ui.components.MonochromeErrorCard
import com.example.ui.components.MonochromeLoadingState

@Composable
fun AlertsScreen(
    announcementsState: UiState<List<CampusAnnouncement>>,
    notificationsEnabled: Boolean,
    isAdmin: Boolean,
    isGuestMode: Boolean = false,
    onOpenPreferences: () -> Unit,
    onDeleteAnnouncement: (String) -> Unit,
    onRequestSignIn: () -> Unit = {}
) {
    val context = LocalContext.current
    var hasSystemPermission by remember {
        mutableStateOf(CampusNotificationManager.hasNotificationPermission(context))
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasSystemPermission = granted
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Real-time push notification status & permission card
        item {
            if (!hasSystemPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                MonochromeCard(
                    containerColor = MaterialTheme.colorScheme.surface,
                    borderColor = MaterialTheme.colorScheme.primary
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.NotificationsActive,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Enable Real-Time Push Alerts",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Text(
                            text = "Allow Android notifications so urgent campus announcements and upcoming class or exam reminders appear instantly in your system tray.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            },
                            shape = MaterialTheme.shapes.small,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier.testTag("enable_push_permission_button")
                        ) {
                            Text("Allow Notifications")
                        }
                    }
                }
            } else {
                MonochromeCard(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp)
                            )
                            Column {
                                Text(
                                    text = if (notificationsEnabled) {
                                        "Live Campus Push Active"
                                    } else {
                                        "Campus Push Muted in Preferences"
                                    },
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "Listening for urgent announcements & scheduled exam alerts.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = onOpenPreferences,
                            shape = MaterialTheme.shapes.small,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("alerts_preferences_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Tune,
                                contentDescription = "Notification preferences",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Prefs", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "LIVE CAMPUS FEED",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, start = 2.dp)
            )
        }

        if (isGuestMode) {
            item {
                MonochromeEmptyState(
                    icon = Icons.Outlined.Campaign,
                    title = "Sign in for live campus alerts",
                    subtitle = "Local class and exam reminders work in Guest Mode. Sign in with Google to receive real-time urgent campus announcements from admins.",
                    actionLabel = "Sign in with Google",
                    onAction = onRequestSignIn,
                    actionTag = "alerts_guest_sign_in_button"
                )
            }
        } else {
            when (announcementsState) {
                is UiState.Loading -> item { MonochromeLoadingState("Listening for campus alerts...") }
                is UiState.Error -> item { MonochromeErrorCard(message = announcementsState.message) }
                is UiState.Success -> {
                    val alerts = announcementsState.data
                    if (alerts.isEmpty()) {
                        item {
                            MonochromeEmptyState(
                                icon = Icons.Outlined.Campaign,
                                title = "All quiet on campus",
                                subtitle = if (isAdmin) {
                                    "Publish an urgent alert or campus notice from the Admin Console to push it live to all students."
                                } else {
                                    "Urgent campus alerts and official notices will appear here in real time."
                                }
                            )
                        }
                    } else {
                        items(alerts, key = { it.id }) { alert ->
                            AnnouncementCard(
                                announcement = alert,
                                canDelete = isAdmin,
                                onDelete = { onDeleteAnnouncement(alert.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnnouncementCard(
    announcement: CampusAnnouncement,
    canDelete: Boolean,
    onDelete: () -> Unit
) {
    val isUrgent = announcement.parsedSeverity == AnnouncementSeverity.URGENT

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("announcement_card_${announcement.id}"),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = if (isUrgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            if (isUrgent) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.error)
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MonochromeBadge(
                            text = if (isUrgent) "URGENT ALERT" else "NOTICE",
                            urgent = isUrgent,
                            inverted = !isUrgent
                        )
                        Text(
                            text = announcement.department,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (canDelete) {
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("delete_alert_${announcement.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = "Delete announcement",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Text(
                    text = announcement.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = announcement.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Issued by ${announcement.authorName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = ScheduleFormatter.formatTimestamp(announcement.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
