package com.example.ui.screens

import android.content.Intent
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apartment
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SupportAgent
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.DirectoryCategory
import com.example.data.model.DirectoryOffice
import com.example.data.model.HelpdeskTicket
import com.example.data.model.ScheduleFormatter
import com.example.data.model.TicketCategory
import com.example.data.model.TicketPriority
import com.example.data.model.TicketStatus
import com.example.data.model.UiState
import com.example.ui.components.MonochromeBadge
import com.example.ui.components.MonochromeCard
import com.example.ui.components.MonochromeEmptyState
import com.example.ui.components.MonochromeErrorCard
import com.example.ui.components.MonochromeLoadingState
import com.example.ui.components.SegmentedMonochromeTabs

@Composable
fun CampusScreen(
    initialSegment: Int = 0,
    directoryState: UiState<List<DirectoryOffice>>,
    ticketsState: UiState<List<HelpdeskTicket>>,
    onCreateTicket: (TicketCategory, TicketPriority, String, String, String, () -> Unit) -> Unit,
    onCloseOwnTicket: (String) -> Unit
) {
    var selectedSegment by rememberSaveable(initialSegment) { mutableIntStateOf(initialSegment) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var showNewTicketSheet by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                SegmentedMonochromeTabs(
                    options = listOf("Directory", "My Tickets"),
                    selectedIndex = selectedSegment,
                    onSelected = { selectedSegment = it },
                    tagPrefix = "campus_segment"
                )
            }

            if (selectedSegment == 0) {
                DirectoryPane(
                    directoryState = directoryState,
                    searchQuery = searchQuery,
                    onSearchChange = { searchQuery = it },
                    selectedCategory = selectedCategory,
                    onCategoryChange = { selectedCategory = it }
                )
            } else {
                MyTicketsPane(
                    ticketsState = ticketsState,
                    onOpenNewTicket = { showNewTicketSheet = true },
                    onCloseTicket = onCloseOwnTicket
                )
            }
        }

        if (selectedSegment == 1) {
            FloatingActionButton(
                onClick = { showNewTicketSheet = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .testTag("new_ticket_fab")
            ) {
                Icon(imageVector = Icons.Rounded.Add, contentDescription = "Submit helpdesk ticket")
            }
        }
    }

    if (showNewTicketSheet) {
        NewTicketBottomSheet(
            onDismiss = { showNewTicketSheet = false },
            onSubmit = { cat, prio, subject, desc, loc ->
                onCreateTicket(cat, prio, subject, desc, loc) {
                    showNewTicketSheet = false
                }
            }
        )
    }
}

@Composable
private fun DirectoryPane(
    directoryState: UiState<List<DirectoryOffice>>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    selectedCategory: String?,
    onCategoryChange: (String?) -> Unit
) {
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            leadingIcon = {
                Icon(imageVector = Icons.Outlined.Search, contentDescription = null)
            },
            placeholder = { Text("Search offices, buildings, services...") },
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .testTag("directory_search_input")
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { onCategoryChange(null) },
                    label = { Text("All") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
            items(DirectoryCategory.entries) { cat ->
                FilterChip(
                    selected = selectedCategory == cat.name,
                    onClick = { onCategoryChange(cat.name) },
                    label = { Text(cat.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        }

        when (directoryState) {
            is UiState.Loading -> MonochromeLoadingState("Loading campus directory...")
            is UiState.Error -> Box(modifier = Modifier.padding(16.dp)) {
                MonochromeErrorCard(message = directoryState.message)
            }
            is UiState.Success -> {
                val filtered = remember(directoryState.data, searchQuery, selectedCategory) {
                    val q = searchQuery.trim().lowercase()
                    directoryState.data.filter { office ->
                        val catMatch = selectedCategory == null || office.category == selectedCategory
                        val queryMatch = q.isEmpty() ||
                            office.name.lowercase().contains(q) ||
                            office.building.lowercase().contains(q) ||
                            office.description.lowercase().contains(q)
                        catMatch && queryMatch
                    }
                }

                if (filtered.isEmpty()) {
                    Box(modifier = Modifier.padding(16.dp)) {
                        MonochromeEmptyState(
                            icon = Icons.Outlined.Apartment,
                            title = "No campus offices found",
                            subtitle = if (directoryState.data.isEmpty()) {
                                "Campus Admins can publish offices in the Admin Console."
                            } else {
                                "Try clearing your search or switching directory categories."
                            }
                        )
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filtered, key = { it.id }) { office ->
                            MonochromeCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("directory_card_${office.id}")
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
                                        Text(
                                            text = office.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            modifier = Modifier.weight(1f)
                                        )
                                        MonochromeBadge(
                                            text = office.parsedCategory.label,
                                            urgent = office.parsedCategory == DirectoryCategory.SAFETY
                                        )
                                    }

                                    Text(
                                        text = buildString {
                                            append(office.building)
                                            if (office.room.isNotBlank()) append(" · ${office.room}")
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Schedule,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Text(
                                            text = office.hours,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    if (office.description.isNotBlank()) {
                                        Text(
                                            text = office.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    if (office.phone.isNotBlank() || office.email.isNotBlank()) {
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            if (office.phone.isNotBlank()) {
                                                OutlinedButton(
                                                    onClick = {
                                                        val dial = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${office.phone}"))
                                                        runCatching { context.startActivity(dial) }
                                                    },
                                                    shape = MaterialTheme.shapes.small,
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Outlined.Call,
                                                        contentDescription = "Call ${office.name}",
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("Call", style = MaterialTheme.typography.labelLarge)
                                                }
                                            }
                                            if (office.email.isNotBlank()) {
                                                OutlinedButton(
                                                    onClick = {
                                                        val mail = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${office.email}"))
                                                        runCatching { context.startActivity(mail) }
                                                    },
                                                    shape = MaterialTheme.shapes.small,
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Outlined.Email,
                                                        contentDescription = "Email ${office.name}",
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("Email", style = MaterialTheme.typography.labelLarge)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MyTicketsPane(
    ticketsState: UiState<List<HelpdeskTicket>>,
    onOpenNewTicket: () -> Unit,
    onCloseTicket: (String) -> Unit
) {
    when (ticketsState) {
        is UiState.Loading -> MonochromeLoadingState("Loading your helpdesk tickets...")
        is UiState.Error -> Box(modifier = Modifier.padding(16.dp)) {
            MonochromeErrorCard(message = ticketsState.message)
        }
        is UiState.Success -> {
            val tickets = ticketsState.data
            if (tickets.isEmpty()) {
                Box(modifier = Modifier.padding(16.dp)) {
                    MonochromeEmptyState(
                        icon = Icons.Outlined.SupportAgent,
                        title = "No helpdesk tickets yet",
                        subtitle = "Report IT outages, room maintenance issues, or ID access requests and track live admin responses here.",
                        actionLabel = "Submit Ticket",
                        onAction = onOpenNewTicket,
                        actionTag = "empty_submit_ticket_button"
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 92.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(tickets, key = { it.id }) { ticket ->
                        MonochromeCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("ticket_card_${ticket.id}")
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
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        MonochromeBadge(
                                            text = ticket.parsedStatus.label,
                                            inverted = ticket.parsedStatus == TicketStatus.OPEN ||
                                                ticket.parsedStatus == TicketStatus.IN_PROGRESS
                                        )
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

                                Text(
                                    text = ticket.subject,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = ticket.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(
                                        text = ticket.parsedCategory.label,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (ticket.location.isNotBlank()) {
                                        Text(
                                            text = "· ${ticket.location}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                if (ticket.adminResponse.isNotBlank()) {
                                    Surface(
                                        shape = MaterialTheme.shapes.small,
                                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = "CAMPUS ADMIN RESPONSE",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = ticket.adminResponse,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }

                                if (ticket.parsedStatus != TicketStatus.CLOSED) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        TextButton(
                                            onClick = { onCloseTicket(ticket.id) },
                                            modifier = Modifier.testTag("close_ticket_${ticket.id}")
                                        ) {
                                            Text("Mark Closed")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewTicketBottomSheet(
    onDismiss: () -> Unit,
    onSubmit: (TicketCategory, TicketPriority, String, String, String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var category by remember { mutableStateOf(TicketCategory.IT_SUPPORT) }
    var priority by remember { mutableStateOf(TicketPriority.MEDIUM) }
    var subject by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
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
            Text("Submit Helpdesk Ticket", style = MaterialTheme.typography.headlineSmall)

            Text("Category", style = MaterialTheme.typography.labelMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(TicketCategory.entries) { cat ->
                    FilterChip(
                        selected = category == cat,
                        onClick = { category = cat },
                        label = { Text(cat.label) }
                    )
                }
            }

            Text("Priority", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TicketPriority.entries.forEach { p ->
                    FilterChip(
                        selected = priority == p,
                        onClick = { priority = p },
                        label = { Text(p.label) }
                    )
                }
            }

            OutlinedTextField(
                value = subject,
                onValueChange = { subject = it.take(120) },
                label = { Text("Subject") },
                placeholder = { Text("Eduroam Wi-Fi outage in Hall B") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ticket_subject_input")
            )

            OutlinedTextField(
                value = location,
                onValueChange = { location = it.take(100) },
                label = { Text("Building / Room (optional)") },
                placeholder = { Text("Hall B · Room 210") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = description,
                onValueChange = { description = it.take(1500) },
                label = { Text("Description") },
                placeholder = { Text("Describe the issue clearly so campus support can resolve it quickly.") },
                minLines = 3,
                maxLines = 5,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ticket_description_input")
            )

            if (error != null) {
                Text(
                    text = error!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Button(
                onClick = {
                    if (subject.trim().length < 3) {
                        error = "Subject must be at least 3 characters."
                        return@Button
                    }
                    if (description.trim().length < 5) {
                        error = "Description must be at least 5 characters."
                        return@Button
                    }
                    onSubmit(category, priority, subject, description, location)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("submit_ticket_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text("Submit Helpdesk Ticket")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
