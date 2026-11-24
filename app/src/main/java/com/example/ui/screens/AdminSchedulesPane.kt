package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.example.data.model.AcademicCatalog
import com.example.data.model.PublishedSchedule
import com.example.data.model.PublishedScheduleKind
import com.example.data.model.ScheduleFormatter
import com.example.data.model.ScheduleItem
import com.example.data.model.ScheduleItemType
import com.example.data.model.UiState
import com.example.ui.components.MonochromeBadge
import com.example.ui.components.MonochromeCard
import com.example.ui.components.MonochromeEmptyState
import com.example.ui.components.MonochromeErrorCard
import com.example.ui.components.MonochromeLoadingState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminSchedulesPane(
    publishedSchedulesState: UiState<List<PublishedSchedule>>,
    draftEntries: List<ScheduleItem>,
    isExtractingDraft: Boolean,
    draftError: String?,
    isPublishing: Boolean,
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
    onDeletePublishedSchedule: (String) -> Unit
) {
    var selectedProgram by rememberSaveable { mutableStateOf(AcademicCatalog.programs.first().name) }
    val availableDepartments = remember(selectedProgram) {
        AcademicCatalog.departmentsFor(selectedProgram)
    }
    var selectedDepartment by rememberSaveable(selectedProgram) {
        mutableStateOf(AcademicCatalog.departmentsFor(selectedProgram).first())
    }
    var departmentExpanded by remember { mutableStateOf(false) }

    val availableYears = remember(selectedProgram) { AcademicCatalog.yearsFor(selectedProgram) }
    var selectedYear by rememberSaveable { mutableIntStateOf(1) }

    val availableSemestersForYear = remember(selectedProgram, selectedYear) {
        val allSems = AcademicCatalog.semestersFor(selectedProgram)
        allSems.filter { AcademicCatalog.yearFromSemester(it) == selectedYear }
    }
    var selectedSemester by rememberSaveable { mutableIntStateOf(0) } // 0 = All semesters in selected year
    var section by rememberSaveable { mutableStateOf("A") }
    var scheduleKind by rememberSaveable { mutableStateOf(PublishedScheduleKind.CLASS) }
    var customTitle by rememberSaveable { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }
    var showManualEntrySheet by remember { mutableStateOf(false) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) onExtractDraftFromUri(uri)
    }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onExtractDraftFromUri(uri)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            MonochromeCard(containerColor = MaterialTheme.colorScheme.surface) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CalendarMonth,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Publish Batch Schedule",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                    Text(
                        text = "Upload or enter a class timetable or exam routine for any program, branch, year, and section. Matching students are notified live and can import it in one tap.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Program
                    Text("Program", style = MaterialTheme.typography.labelMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(AcademicCatalog.programs, key = { it.name }) { prog ->
                            FilterChip(
                                selected = selectedProgram == prog.name,
                                onClick = {
                                    selectedProgram = prog.name
                                    if (selectedDepartment !in prog.departments) {
                                        selectedDepartment = prog.departments.first()
                                    }
                                    val yrs = AcademicCatalog.yearsFor(prog.name)
                                    if (selectedYear !in yrs) selectedYear = yrs.first()
                                    selectedSemester = 0
                                },
                                label = { Text(prog.name) }
                            )
                        }
                    }

                    // Branch / Department dropdown
                    ExposedDropdownMenuBox(
                        expanded = departmentExpanded,
                        onExpandedChange = { departmentExpanded = !departmentExpanded }
                    ) {
                        OutlinedTextField(
                            value = selectedDepartment,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Branch / Department") },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = departmentExpanded)
                            },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                                .testTag("admin_schedule_department_dropdown")
                        )
                        ExposedDropdownMenu(
                            expanded = departmentExpanded,
                            onDismissRequest = { departmentExpanded = false }
                        ) {
                            availableDepartments.forEach { dept ->
                                DropdownMenuItem(
                                    text = { Text(dept) },
                                    onClick = {
                                        selectedDepartment = dept
                                        departmentExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Academic Year
                    Text("Academic Year", style = MaterialTheme.typography.labelMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(availableYears) { yr ->
                            FilterChip(
                                selected = selectedYear == yr,
                                onClick = {
                                    selectedYear = yr
                                    selectedSemester = 0
                                },
                                label = { Text("Year $yr") },
                                modifier = Modifier.testTag("admin_schedule_year_$yr")
                            )
                        }
                    }

                    // Optional Semester filter inside the chosen year
                    if (availableSemestersForYear.isNotEmpty()) {
                        Text("Semester (Optional)", style = MaterialTheme.typography.labelMedium)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            item {
                                FilterChip(
                                    selected = selectedSemester == 0,
                                    onClick = { selectedSemester = 0 },
                                    label = { Text("All Year $selectedYear") }
                                )
                            }
                            items(availableSemestersForYear) { sem ->
                                FilterChip(
                                    selected = selectedSemester == sem,
                                    onClick = { selectedSemester = sem },
                                    label = { Text("Sem $sem") }
                                )
                            }
                        }
                    }

                    // Section selector
                    OutlinedTextField(
                        value = section,
                        onValueChange = { section = it.uppercase().take(24) },
                        label = { Text("Section (e.g. A, B, C)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_schedule_section_input")
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(AcademicCatalog.quickSections) { sec ->
                            FilterChip(
                                selected = AcademicCatalog.normalizeSection(section) == sec,
                                onClick = { section = sec },
                                label = { Text(sec) }
                            )
                        }
                    }

                    // Schedule Type
                    Text("Schedule Type", style = MaterialTheme.typography.labelMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(PublishedScheduleKind.entries) { kind ->
                            FilterChip(
                                selected = scheduleKind == kind,
                                onClick = { scheduleKind = kind },
                                label = { Text(kind.label) },
                                modifier = Modifier.testTag("admin_schedule_kind_${kind.name}")
                            )
                        }
                    }

                    OutlinedTextField(
                        value = customTitle,
                        onValueChange = { customTitle = it.take(120) },
                        label = { Text("Headline (Optional)") },
                        placeholder = { Text("$selectedDepartment · Year $selectedYear · Sec ${AcademicCatalog.normalizeSection(section)} ${scheduleKind.label}") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // Populate Entries: Upload Image / PDF or Add Manually
                    Text("Schedule Entries (${draftEntries.size})", style = MaterialTheme.typography.titleSmall)

                    if (isExtractingDraft) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Text(
                                    text = "Extracting classes & exams from file...",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    imagePickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("admin_extract_image_button")
                            ) {
                                Icon(Icons.Outlined.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Image")
                            }
                            OutlinedButton(
                                onClick = {
                                    pdfPickerLauncher.launch(arrayOf("application/pdf", "image/*"))
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("admin_extract_pdf_button")
                            ) {
                                Icon(Icons.Outlined.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("PDF / File")
                            }
                        }
                        OutlinedButton(
                            onClick = { showManualEntrySheet = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("admin_add_manual_entry_button")
                        ) {
                            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add Class or Exam Entry Manually")
                        }
                    }

                    if (draftError != null) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            MonochromeErrorCard(message = draftError)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = onDismissDraftError) { Text("Dismiss") }
                            }
                        }
                    }

                    if (draftEntries.isNotEmpty()) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val clsCount = draftEntries.count { it.parsedType == ScheduleItemType.CLASS }
                                    val examCount = draftEntries.count { it.parsedType == ScheduleItemType.EXAM }
                                    Text(
                                        text = "DRAFT · $clsCount CLASSES · $examCount EXAMS",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    TextButton(onClick = onClearDraft) {
                                        Text("Clear")
                                    }
                                }
                                draftEntries.take(8).forEachIndexed { idx, entry ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "${entry.courseCode} · ${entry.title}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            val timing = if (entry.parsedType == ScheduleItemType.EXAM) {
                                                "EXAM · ${ScheduleFormatter.formatExamDate(entry.examEpochMillis)}"
                                            } else {
                                                "${ScheduleFormatter.dayShort(entry.dayOfWeek)} ${ScheduleFormatter.formatMinutes(entry.startTimeMinutes)}–${ScheduleFormatter.formatMinutes(entry.endTimeMinutes)}"
                                            }
                                            Text(
                                                text = buildString {
                                                    append(timing)
                                                    if (entry.location.isNotBlank()) append(" · ${entry.location}")
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        IconButton(
                                            onClick = { onRemoveDraftEntry(idx) },
                                            modifier = Modifier.size(48.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Close,
                                                contentDescription = "Remove draft entry",
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                                if (draftEntries.size > 8) {
                                    Text(
                                        text = "+ ${draftEntries.size - 8} more entries ready to publish",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    if (validationError != null) {
                        Text(
                            text = validationError!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Button(
                        onClick = {
                            val cleanSec = AcademicCatalog.normalizeSection(section)
                            when {
                                cleanSec.isEmpty() -> {
                                    validationError = "Please choose or enter a section (e.g. A, B, C)."
                                }
                                draftEntries.isEmpty() -> {
                                    validationError = "Upload a schedule image/PDF or add at least one entry first."
                                }
                                else -> {
                                    validationError = null
                                    onPublishSchedule(
                                        selectedProgram,
                                        selectedDepartment,
                                        selectedYear,
                                        selectedSemester,
                                        cleanSec,
                                        scheduleKind,
                                        customTitle
                                    ) {
                                        customTitle = ""
                                    }
                                }
                            }
                        },
                        enabled = !isPublishing && !isExtractingDraft,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("admin_publish_schedule_button")
                    ) {
                        if (isPublishing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Publishing & Notifying...")
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.NotificationsActive,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Publish Schedule & Notify Students")
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "PUBLISHED BATCH SCHEDULES",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, start = 2.dp)
            )
        }

        when (publishedSchedulesState) {
            is UiState.Loading -> item { MonochromeLoadingState("Loading published schedules...") }
            is UiState.Error -> item { MonochromeErrorCard(message = publishedSchedulesState.message) }
            is UiState.Success -> {
                if (publishedSchedulesState.data.isEmpty()) {
                    item {
                        MonochromeEmptyState(
                            icon = Icons.Outlined.CalendarMonth,
                            title = "No batch schedules published yet",
                            subtitle = "Publish a class or exam routine above and matching students will be notified to import it."
                        )
                    }
                } else {
                    items(publishedSchedulesState.data, key = { it.id }) { schedule ->
                        MonochromeCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        MonochromeBadge(text = schedule.parsedKind.label.uppercase(), inverted = true)
                                        MonochromeBadge(text = "${schedule.entryCount} ENTRIES")
                                    }
                                    Text(
                                        text = schedule.batchLabel,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Published ${ScheduleFormatter.formatTimestamp(schedule.updatedAt ?: schedule.createdAt)} · ${schedule.publishedByName}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = { onDeletePublishedSchedule(schedule.id) },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.DeleteOutline,
                                        contentDescription = "Delete published schedule"
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showManualEntrySheet) {
        val defaultType = if (scheduleKind == PublishedScheduleKind.EXAM) {
            ScheduleItemType.EXAM
        } else {
            ScheduleItemType.CLASS
        }
        ScheduleEditorSheet(
            initial = ScheduleItem(itemType = defaultType.name),
            onDismiss = { showManualEntrySheet = false },
            onSave = { entry ->
                onAddDraftEntry(entry)
                showManualEntrySheet = false
            }
        )
    }
}
