package com.example.ui.screens

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Apartment
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.SupportAgent
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.AdminRecord
import com.example.data.model.AnnouncementSeverity
import com.example.data.model.DirectoryCategory
import com.example.data.model.DirectoryOffice
import com.example.data.model.HelpdeskTicket
import com.example.data.model.PublishedSchedule
import com.example.data.model.PublishedScheduleKind
import com.example.data.model.ScheduleFormatter
import com.example.data.model.ScheduleItem
import com.example.data.model.TicketPriority
import com.example.data.model.TicketStatus
import com.example.data.model.UiState
import com.example.ui.components.MonochromeBadge
import com.example.ui.components.MonochromeCard
import com.example.ui.components.MonochromeEmptyState
import com.example.ui.components.MonochromeErrorCard
import com.example.ui.components.MonochromeLoadingState

@Composable
fun AdminScreen(
    allTicketsState: UiState<List<HelpdeskTicket>>,
    directoryState: UiState<List<DirectoryOffice>>,
    publishedSchedulesState: UiState<List<PublishedSchedule>>,
    adminRecords: List<AdminRecord>,
    adminDraftEntries: List<ScheduleItem>,
    isExtractingDraft: Boolean,
    draftError: String?,
    isPublishingSchedule: Boolean,
    onBroadcastAlert: (AnnouncementSeverity, String, String, String, () -> Unit) -> Unit,
    onExtractDraftFromUri: (Uri) -> Unit,
    onAddDraftEntry: (ScheduleItem) -> Unit,
    onRemoveDraftEntry: (Int) -> Unit,
    onClearDraft: () -> Unit,
    onDismissDraftError: () -> Unit,
    onPublishSchedule: (
        program: String,
        department: String,
        academicYear: Int,
        semester: Int,
        section: String,
        kind: PublishedScheduleKind,
        title: String,
        onComplete: () -> Unit
    ) -> Unit,
    onDeletePublishedSchedule: (String) -> Unit,
    onTriageTicket: (String, TicketStatus, TicketPriority, String, () -> Unit) -> Unit,
    onSaveDirectoryOffice: (DirectoryOffice, () -> Unit) -> Unit,
    onDeleteDirectoryOffice: (String) -> Unit,
    onGrantAdmin: (String, String, () -> Unit) -> Unit,
    onRevokeAdmin: (String) -> Unit
) {
    val adminTabs = remember { listOf("Broadcast", "Schedules", "Tickets", "Directory", "Roles") }
    var selectedSection by rememberSaveable { mutableIntStateOf(0) } // 0=Broadcast, 1=Schedules, 2=Tickets, 3=Directory, 4=Roles

    Column(modifier = Modifier.fillMaxSize()) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(adminTabs.indices.toList()) { idx ->
                val selected = selectedSection == idx
                Surface(
                    onClick = { selectedSection = idx },
                    shape = MaterialTheme.shapes.small,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(
                        1.dp,
                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier.testTag("admin_section_$idx")
                ) {
                    Text(
                        text = adminTabs[idx],
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
            }
        }

        when (selectedSection) {
            0 -> AdminBroadcastPane(onBroadcastAlert = onBroadcastAlert)
            1 -> AdminSchedulesPane(
                publishedSchedulesState = publishedSchedulesState,
                draftEntries = adminDraftEntries,
                isExtractingDraft = isExtractingDraft,
                draftError = draftError,
                isPublishing = isPublishingSchedule,
                onExtractDraftFromUri = onExtractDraftFromUri,
                onAddDraftEntry = onAddDraftEntry,
                onRemoveDraftEntry = onRemoveDraftEntry,
                onClearDraft = onClearDraft,
                onDismissDraftError = onDismissDraftError,
                onPublishSchedule = onPublishSchedule,
                onDeletePublishedSchedule = onDeletePublishedSchedule
            )
            2 -> AdminTicketsPane(
                allTicketsState = allTicketsState,
                onTriageTicket = onTriageTicket
            )
            3 -> AdminDirectoryPane(
                directoryState = directoryState,
                onSaveOffice = onSaveDirectoryOffice,
                onDeleteOffice = onDeleteDirectoryOffice
            )
            else -> AdminAccessPane(
                adminRecords = adminRecords,
                onGrantAdmin = onGrantAdmin,
                onRevokeAdmin = onRevokeAdmin
            )
        }
    }
}

@Composable
private fun AdminBroadcastPane(
    onBroadcastAlert: (AnnouncementSeverity, String, String, String, () -> Unit) -> Unit
) {
    var severity by remember { mutableStateOf(AnnouncementSeverity.URGENT) }
    var department by remember { mutableStateOf("Campus Operations") }
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        MonochromeCard(containerColor = MaterialTheme.colorScheme.surface) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Campaign,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Real-Time Campus Broadcast",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
                Text(
                    text = "Published notices immediately synchronize to all connected CampusBot clients and trigger native Android push notifications.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AnnouncementSeverity.entries.forEach { sev ->
                        FilterChip(
                            selected = severity == sev,
                            onClick = { severity = sev },
                            label = {
                                Text(if (sev == AnnouncementSeverity.URGENT) "URGENT ALERT" else "GENERAL NOTICE")
                            },
                            modifier = Modifier.testTag("severity_chip_${sev.name}")
                        )
                    }
                }

                OutlinedTextField(
                    value = department,
                    onValueChange = { department = it.take(60) },
                    label = { Text("Issuing Department") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(120) },
                    label = { Text("Alert Headline") },
                    placeholder = { Text("Severe weather shuttle reroute") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("broadcast_title_input")
                )

                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it.take(1500) },
                    label = { Text("Message Details") },
                    placeholder = { Text("Provide concise instructions, locations, and effective hours.") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("broadcast_body_input")
                )

                if (validationError != null) {
                    Text(
                        text = validationError!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Button(
                    onClick = {
                        if (title.trim().length < 3) {
                            validationError = "Headline must be at least 3 characters."
                            return@Button
                        }
                        if (body.trim().length < 5) {
                            validationError = "Message details must be at least 5 characters."
                            return@Button
                        }
                        validationError = null
                        onBroadcastAlert(severity, title, body, department) {
                            title = ""
                            body = ""
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("publish_broadcast_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (severity == AnnouncementSeverity.URGENT) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        contentColor = if (severity == AnnouncementSeverity.URGENT) {
                            MaterialTheme.colorScheme.onError
                        } else {
                            MaterialTheme.colorScheme.onPrimary
                        }
                    )
                ) {
                    Text(
                        text = if (severity == AnnouncementSeverity.URGENT) {
                            "Broadcast Urgent Push Alert"
                        } else {
                            "Publish Campus Notice"
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdminTicketsPane(
    allTicketsState: UiState<List<HelpdeskTicket>>,
    onTriageTicket: (String, TicketStatus, TicketPriority, String, () -> Unit) -> Unit
) {
    var statusFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedTicket by remember { mutableStateOf<HelpdeskTicket?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item {
                FilterChip(
                    selected = statusFilter == null,
                    onClick = { statusFilter = null },
                    label = { Text("All") }
                )
            }
            items(TicketStatus.entries) { st ->
                FilterChip(
                    selected = statusFilter == st.name,
                    onClick = { statusFilter = st.name },
                    label = { Text(st.label) }
                )
            }
        }

        when (allTicketsState) {
            is UiState.Loading -> MonochromeLoadingState("Loading campus ticket queue...")
            is UiState.Error -> Box(modifier = Modifier.padding(16.dp)) {
                MonochromeErrorCard(message = allTicketsState.message)
            }
            is UiState.Success -> {
                val filtered = remember(allTicketsState.data, statusFilter) {
                    if (statusFilter == null) allTicketsState.data
                    else allTicketsState.data.filter { it.status == statusFilter }
                }
                if (filtered.isEmpty()) {
                    Box(modifier = Modifier.padding(16.dp)) {
                        MonochromeEmptyState(
                            icon = Icons.Outlined.SupportAgent,
                            title = "Ticket queue clear",
                            subtitle = "No student helpdesk tickets match the selected status."
                        )
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filtered, key = { it.id }) { ticket ->
                            MonochromeCard(
                                onClick = { selectedTicket = ticket },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("admin_ticket_${ticket.id}")
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            MonochromeBadge(text = ticket.parsedStatus.label, inverted = true)
                                            MonochromeBadge(
                                                text = ticket.parsedPriority.label,
                                                urgent = ticket.parsedPriority == TicketPriority.HIGH
                                            )
                                        }
                                        Text(
                                            text = ScheduleFormatter.formatTimestamp(ticket.createdAt),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(ticket.subject, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        text = "${ticket.authorName} · ${ticket.parsedCategory.label}" +
                                            if (ticket.location.isNotBlank()) " · ${ticket.location}" else "",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = ticket.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    selectedTicket?.let { ticket ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var status by remember(ticket.id) { mutableStateOf(ticket.parsedStatus) }
        var priority by remember(ticket.id) { mutableStateOf(ticket.parsedPriority) }
        var response by remember(ticket.id) { mutableStateOf(ticket.adminResponse) }

        ModalBottomSheet(
            onDismissRequest = { selectedTicket = null },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Triage Helpdesk Ticket", style = MaterialTheme.typography.headlineSmall)
                Text(ticket.subject, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = ticket.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Text("Status", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(TicketStatus.entries) { st ->
                        FilterChip(
                            selected = status == st,
                            onClick = { status = st },
                            label = { Text(st.label) }
                        )
                    }
                }

                Text("Priority", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TicketPriority.entries.forEach { pr ->
                        FilterChip(
                            selected = priority == pr,
                            onClick = { priority = pr },
                            label = { Text(pr.label) }
                        )
                    }
                }

                OutlinedTextField(
                    value = response,
                    onValueChange = { response = it.take(1500) },
                    label = { Text("Official Admin Response") },
                    placeholder = { Text("Share resolution details or next steps with the student.") },
                    minLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_ticket_response_input")
                )

                Button(
                    onClick = {
                        onTriageTicket(ticket.id, status, priority, response) {
                            selectedTicket = null
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("save_ticket_triage_button")
                ) {
                    Text("Save Triage Update")
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdminDirectoryPane(
    directoryState: UiState<List<DirectoryOffice>>,
    onSaveOffice: (DirectoryOffice, () -> Unit) -> Unit,
    onDeleteOffice: (String) -> Unit
) {
    var editingOffice by remember { mutableStateOf<DirectoryOffice?>(null) }
    var showEditor by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            OutlinedButton(
                onClick = {
                    editingOffice = DirectoryOffice()
                    showEditor = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("admin_add_office_button")
            ) {
                Text("+ Add Campus Office")
            }
        }

        when (directoryState) {
            is UiState.Loading -> item { MonochromeLoadingState("Loading directory entries...") }
            is UiState.Error -> item { MonochromeErrorCard(message = directoryState.message) }
            is UiState.Success -> {
                if (directoryState.data.isEmpty()) {
                    item {
                        MonochromeEmptyState(
                            icon = Icons.Outlined.Apartment,
                            title = "Directory is empty",
                            subtitle = "Add official campus offices so students and CampusBot AI can look up hours and locations."
                        )
                    }
                } else {
                    items(directoryState.data, key = { it.id }) { office ->
                        MonochromeCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(office.name, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        text = "${office.building} ${office.room} · ${office.hours}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row {
                                    IconButton(
                                        onClick = {
                                            editingOffice = office
                                            showEditor = true
                                        },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(Icons.Outlined.Edit, contentDescription = "Edit office")
                                    }
                                    IconButton(
                                        onClick = { onDeleteOffice(office.id) },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete office")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditor) {
        val initial = editingOffice ?: DirectoryOffice()
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var name by remember { mutableStateOf(initial.name) }
        var category by remember { mutableStateOf(initial.parsedCategory) }
        var building by remember { mutableStateOf(initial.building) }
        var room by remember { mutableStateOf(initial.room) }
        var hours by remember { mutableStateOf(initial.hours.ifBlank { "Mon–Fri 09:00 – 17:00" }) }
        var phone by remember { mutableStateOf(initial.phone) }
        var email by remember { mutableStateOf(initial.email) }
        var description by remember { mutableStateOf(initial.description) }
        var err by remember { mutableStateOf<String?>(null) }

        ModalBottomSheet(
            onDismissRequest = { showEditor = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = if (initial.id.isBlank()) "Add Campus Office" else "Edit Campus Office",
                    style = MaterialTheme.typography.headlineSmall
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(100) },
                    label = { Text("Office Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(DirectoryCategory.entries) { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat.label) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = building,
                        onValueChange = { building = it.take(80) },
                        label = { Text("Building") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = room,
                        onValueChange = { room = it.take(40) },
                        label = { Text("Room / Suite") },
                        singleLine = true,
                        modifier = Modifier.weight(0.7f)
                    )
                }
                OutlinedTextField(
                    value = hours,
                    onValueChange = { hours = it.take(100) },
                    label = { Text("Operating Hours") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it.take(32) },
                        label = { Text("Phone") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it.take(100) },
                        label = { Text("Email") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it.take(400) },
                    label = { Text("Services Summary") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                if (err != null) {
                    Text(err!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                Button(
                    onClick = {
                        if (name.trim().length < 2 || building.isBlank() || hours.isBlank()) {
                            err = "Office name, building, and hours are required."
                            return@Button
                        }
                        onSaveOffice(
                            initial.copy(
                                name = name.trim(),
                                category = category.name,
                                building = building.trim(),
                                room = room.trim(),
                                hours = hours.trim(),
                                phone = phone.trim(),
                                email = email.trim(),
                                description = description.trim()
                            )
                        ) { showEditor = false }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Text("Save Office")
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun AdminAccessPane(
    adminRecords: List<AdminRecord>,
    onGrantAdmin: (String, String, () -> Unit) -> Unit,
    onRevokeAdmin: (String) -> Unit
) {
    var targetUid by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            MonochromeCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Outlined.AdminPanelSettings, contentDescription = null)
                        Text("Grant Campus Admin Role", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        text = "Enter a verified user's Firebase UID (visible in their Account sheet) to grant admin privileges enforced server-side in Firestore rules.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = targetUid,
                        onValueChange = { targetUid = it.take(128) },
                        label = { Text("User UID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it.take(80) },
                        label = { Text("Title / Label (e.g. IT Coordinator)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (error != null) {
                        Text(error!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    Button(
                        onClick = {
                            if (targetUid.trim().length < 4) {
                                error = "Enter a valid Firebase UID."
                                return@Button
                            }
                            error = null
                            onGrantAdmin(targetUid, label) {
                                targetUid = ""
                                label = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Grant Admin Access")
                    }
                }
            }
        }

        if (adminRecords.isNotEmpty()) {
            item {
                Text(
                    text = "DELEGATED CAMPUS ADMINS",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(adminRecords, key = { it.id }) { record ->
                MonochromeCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = record.label.ifBlank { "Campus Admin" },
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = record.adminUid,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = { onRevokeAdmin(record.adminUid) },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Outlined.DeleteOutline, contentDescription = "Revoke admin")
                        }
                    }
                }
            }
        }
    }
}
