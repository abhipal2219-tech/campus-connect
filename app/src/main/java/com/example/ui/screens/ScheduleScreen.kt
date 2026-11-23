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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.example.data.model.ScheduleFormatter
import com.example.data.model.ScheduleItem
import com.example.data.model.ScheduleItemType
import com.example.data.model.UiState
import com.example.ui.components.MonochromeBadge
import com.example.ui.components.MonochromeCard
import com.example.ui.components.MonochromeEmptyState
import com.example.ui.components.MonochromeErrorCard
import com.example.ui.components.MonochromeLoadingState
import com.example.ui.components.SegmentedMonochromeTabs
import java.util.Calendar

private val LeadTimeOptions = listOf(15, 30, 60, 180, 1440)
private val StartHourOptions = listOf(8, 9, 10, 11, 13, 14, 15, 16, 18)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    scheduleState: UiState<List<ScheduleItem>>,
    defaultLeadMinutes: Int,
    iotSlotCount: Int,
    isImportingTimetable: Boolean,
    isExtractingSchedule: Boolean,
    extractionError: String?,
    lastImportSummary: String?,
    onImportIot2B: () -> Unit,
    onImportScheduleFile: (Uri) -> Unit,
    onDismissExtractionError: () -> Unit,
    onDismissImportSummary: () -> Unit,
    onSaveItem: (ScheduleItem, () -> Unit) -> Unit,
    onToggleReminder: (ScheduleItem) -> Unit,
    onDeleteItem: (ScheduleItem) -> Unit,
    onPreviewReminder: (ScheduleItem) -> Unit
) {
    var selectedSegment by rememberSaveable { mutableIntStateOf(0) } // 0 = Classes, 1 = Exams
    var selectedDay by rememberSaveable { mutableIntStateOf(ScheduleFormatter.todayDayOfWeek()) }
    var editingItem by remember { mutableStateOf<ScheduleItem?>(null) }
    var showSheet by remember { mutableStateOf(false) }

    // Zero-permission Android Photo Picker for timetable/exam screenshots & photos
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onImportScheduleFile(uri)
        }
    }

    // Zero-permission SAF Document Picker for PDF (and image) schedule files
    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onImportScheduleFile(uri)
        }
    }

    // Automatically focus an imported day or the Exams tab right after auto-extraction completes
    LaunchedEffect(lastImportSummary, scheduleState) {
        if (lastImportSummary != null && scheduleState is UiState.Success) {
            val items = scheduleState.data
            val classes = items.filter { it.parsedType == ScheduleItemType.CLASS }
            val exams = items.filter { it.parsedType == ScheduleItemType.EXAM }
            if (classes.isEmpty() && exams.isNotEmpty()) {
                selectedSegment = 1
            } else if (classes.isNotEmpty() && classes.none { it.dayOfWeek == selectedDay }) {
                selectedSegment = 0
                selectedDay = classes.minOf { it.dayOfWeek }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (scheduleState) {
            is UiState.Loading -> MonochromeLoadingState("Syncing class schedule & exams...")
            is UiState.Error -> Box(modifier = Modifier.padding(16.dp)) {
                MonochromeErrorCard(message = scheduleState.message)
            }
            is UiState.Success -> {
                val allItems = scheduleState.data
                val now = System.currentTimeMillis()
                val nextUp = remember(allItems, now) {
                    allItems
                        .map { it to ScheduleFormatter.nextOccurrenceMillis(it, now) }
                        .filter { it.second > now }
                        .minByOrNull { it.second }
                }
                val classesForDay = remember(allItems, selectedDay) {
                    allItems
                        .filter { it.parsedType == ScheduleItemType.CLASS && it.dayOfWeek == selectedDay }
                        .sortedBy { it.startTimeMinutes }
                }
                val examsSorted = remember(allItems) {
                    allItems
                        .filter { it.parsedType == ScheduleItemType.EXAM }
                        .sortedBy { it.examEpochMillis }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 92.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Next Up Hero Countdown Card
                    item {
                        NextUpHeroCard(
                            nextUp = nextUp,
                            onPreviewReminder = onPreviewReminder
                        )
                    }

                    // Auto-Extract Schedule from PDF or Image Card
                    item {
                        ScheduleDocumentScanCard(
                            isExtracting = isExtractingSchedule,
                            extractionError = extractionError,
                            lastImportSummary = lastImportSummary,
                            onPickImage = {
                                imagePickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            onPickPdf = {
                                pdfPickerLauncher.launch(arrayOf("application/pdf", "image/*"))
                            },
                            onDismissError = onDismissExtractionError,
                            onDismissSummary = onDismissImportSummary
                        )
                    }

                    // Segmented Classes / Exams Switcher
                    item {
                        SegmentedMonochromeTabs(
                            options = listOf("Classes", "Exams & Deadlines"),
                            selectedIndex = selectedSegment,
                            onSelected = { selectedSegment = it },
                            tagPrefix = "schedule_segment"
                        )
                    }

                    if (selectedSegment == 0) {
                        // Day of week selector
                        item {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items((1..7).toList()) { day ->
                                    val count = allItems.count {
                                        it.parsedType == ScheduleItemType.CLASS && it.dayOfWeek == day
                                    }
                                    FilterChip(
                                        selected = selectedDay == day,
                                        onClick = { selectedDay = day },
                                        label = {
                                            Text(
                                                text = if (count > 0) "${ScheduleFormatter.dayShort(day)} ($count)" else ScheduleFormatter.dayShort(day),
                                                style = MaterialTheme.typography.labelMedium
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                        ),
                                        modifier = Modifier.testTag("day_chip_$day")
                                    )
                                }
                            }
                        }

                        if (iotSlotCount > 0) {
                            item {
                                MonochromeCard(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "IoT 2B Official Timetable",
                                                style = MaterialTheme.typography.titleSmall,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "Load weekly classes & push reminders from campus JSON.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        OutlinedButton(
                                            onClick = onImportIot2B,
                                            enabled = !isImportingTimetable,
                                            shape = MaterialTheme.shapes.small,
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                            modifier = Modifier.testTag("import_iot2b_timetable_button")
                                        ) {
                                            if (isImportingTimetable) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(14.dp),
                                                    strokeWidth = 2.dp
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = Icons.Outlined.Download,
                                                    contentDescription = "Import IoT 2B timetable",
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Import", style = MaterialTheme.typography.labelLarge)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (classesForDay.isEmpty()) {
                            item {
                                MonochromeEmptyState(
                                    icon = Icons.Outlined.CalendarMonth,
                                    title = "No classes on ${ScheduleFormatter.dayFull(selectedDay)}",
                                    subtitle = "Add your weekly lectures or labs to get automated reminders and AI schedule answers.",
                                    actionLabel = "Add Class",
                                    onAction = {
                                        editingItem = ScheduleItem(
                                            itemType = ScheduleItemType.CLASS.name,
                                            dayOfWeek = selectedDay,
                                            reminderLeadMinutes = defaultLeadMinutes
                                        )
                                        showSheet = true
                                    },
                                    actionTag = "empty_add_class_button"
                                )
                            }
                        } else {
                            items(classesForDay, key = { it.id }) { cls ->
                                ScheduleItemCard(
                                    item = cls,
                                    onEdit = {
                                        editingItem = cls
                                        showSheet = true
                                    },
                                    onToggleReminder = { onToggleReminder(cls) },
                                    onDelete = { onDeleteItem(cls) }
                                )
                            }
                        }
                    } else {
                        if (examsSorted.isEmpty()) {
                            item {
                                MonochromeEmptyState(
                                    icon = Icons.Outlined.School,
                                    title = "No upcoming exams tracked",
                                    subtitle = "Log midterms, finals, or project deadlines to enable countdown badges and deadline notifications.",
                                    actionLabel = "Add Exam",
                                    onAction = {
                                        val tomorrow = Calendar.getInstance().apply {
                                            add(Calendar.DAY_OF_YEAR, 3)
                                            set(Calendar.HOUR_OF_DAY, 10)
                                            set(Calendar.MINUTE, 0)
                                            set(Calendar.SECOND, 0)
                                        }.timeInMillis
                                        editingItem = ScheduleItem(
                                            itemType = ScheduleItemType.EXAM.name,
                                            examEpochMillis = tomorrow,
                                            reminderLeadMinutes = 60
                                        )
                                        showSheet = true
                                    },
                                    actionTag = "empty_add_exam_button"
                                )
                            }
                        } else {
                            items(examsSorted, key = { it.id }) { exam ->
                                ScheduleItemCard(
                                    item = exam,
                                    onEdit = {
                                        editingItem = exam
                                        showSheet = true
                                    },
                                    onToggleReminder = { onToggleReminder(exam) },
                                    onDelete = { onDeleteItem(exam) }
                                )
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = {
                val isExamTab = selectedSegment == 1
                val defaultExamMillis = Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_YEAR, 3)
                    set(Calendar.HOUR_OF_DAY, 10)
                    set(Calendar.MINUTE, 0)
                }.timeInMillis
                editingItem = ScheduleItem(
                    itemType = if (isExamTab) ScheduleItemType.EXAM.name else ScheduleItemType.CLASS.name,
                    dayOfWeek = selectedDay,
                    examEpochMillis = if (isExamTab) defaultExamMillis else 0L,
                    reminderLeadMinutes = defaultLeadMinutes
                )
                showSheet = true
            },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .testTag("add_schedule_fab")
        ) {
            Icon(imageVector = Icons.Rounded.Add, contentDescription = "Add class or exam")
        }
    }

    if (showSheet) {
        ScheduleEditorSheet(
            initial = editingItem ?: ScheduleItem(reminderLeadMinutes = defaultLeadMinutes),
            onDismiss = { showSheet = false },
            onSave = { updated ->
                onSaveItem(updated) { showSheet = false }
            }
        )
    }
}

@Composable
private fun ScheduleDocumentScanCard(
    isExtracting: Boolean,
    extractionError: String?,
    lastImportSummary: String?,
    onPickImage: () -> Unit,
    onPickPdf: () -> Unit,
    onDismissError: () -> Unit,
    onDismissSummary: () -> Unit
) {
    MonochromeCard(containerColor = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.DocumentScanner,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Auto-Import from Image or PDF",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Upload your class timetable or exam routine to extract & add every entry automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (isExtracting) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Reading document and adding classes & exams...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onPickImage,
                        shape = MaterialTheme.shapes.small,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("upload_schedule_image_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Image,
                            contentDescription = "Upload schedule image",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Upload Image", style = MaterialTheme.typography.labelLarge)
                    }

                    OutlinedButton(
                        onClick = onPickPdf,
                        shape = MaterialTheme.shapes.small,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("upload_schedule_pdf_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PictureAsPdf,
                            contentDescription = "Upload schedule PDF or file",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("PDF / Files", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            if (lastImportSummary != null) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = lastImportSummary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(
                            onClick = onDismissSummary,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "Dismiss import summary",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            if (extractionError != null) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    MonochromeErrorCard(message = extractionError)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismissError) {
                            Text("Dismiss")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NextUpHeroCard(
    nextUp: Pair<ScheduleItem, Long>?,
    onPreviewReminder: (ScheduleItem) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "NEXT ON SCHEDULE",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.72f)
                )
                if (nextUp != null) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = ScheduleFormatter.countdownLabel(nextUp.second),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            if (nextUp == null) {
                Text(
                    text = "Clear horizon",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Text(
                    text = "Add your classes or upcoming exam deadlines to activate countdowns and push reminders.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                )
            } else {
                val item = nextUp.first
                Text(
                    text = "${item.courseCode} · ${item.title}",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val timing = if (item.parsedType == ScheduleItemType.EXAM) {
                        ScheduleFormatter.formatExamDate(item.examEpochMillis)
                    } else {
                        "${ScheduleFormatter.dayShort(item.dayOfWeek)} ${ScheduleFormatter.formatMinutes(item.startTimeMinutes)}–${ScheduleFormatter.formatMinutes(item.endTimeMinutes)}"
                    }
                    Text(
                        text = buildString {
                            append(timing)
                            if (item.location.isNotBlank()) append(" · ${item.location}")
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.84f),
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = { onPreviewReminder(item) },
                        modifier = Modifier.testTag("test_schedule_notification_button")
                    ) {
                        Text(
                            text = "TEST ALERT",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduleItemCard(
    item: ScheduleItem,
    onEdit: () -> Unit,
    onToggleReminder: () -> Unit,
    onDelete: () -> Unit
) {
    val isExam = item.parsedType == ScheduleItemType.EXAM
    val now = System.currentTimeMillis()
    val urgentExam = isExam && item.examEpochMillis in now..(now + 24 * 60 * 60 * 1000L)

    MonochromeCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("schedule_card_${item.id}"),
        borderColor = if (urgentExam) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant
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
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MonochromeBadge(text = item.courseCode, inverted = true)
                    if (isExam) {
                        MonochromeBadge(
                            text = ScheduleFormatter.countdownLabel(item.examEpochMillis),
                            urgent = urgentExam
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onToggleReminder,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("toggle_reminder_${item.id}")
                    ) {
                        Icon(
                            imageVector = if (item.reminderEnabled) {
                                Icons.Outlined.NotificationsActive
                            } else {
                                Icons.Outlined.NotificationsOff
                            },
                            contentDescription = "Toggle reminder",
                            tint = if (item.reminderEnabled) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("edit_schedule_${item.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = "Edit schedule item",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("delete_schedule_${item.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = "Delete schedule item",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }

            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AccessTime,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = if (isExam) {
                            ScheduleFormatter.formatExamDate(item.examEpochMillis)
                        } else {
                            "${ScheduleFormatter.formatMinutes(item.startTimeMinutes)} – ${ScheduleFormatter.formatMinutes(item.endTimeMinutes)}"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (item.location.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = item.location,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (item.instructor.isNotBlank() || item.reminderEnabled) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = item.instructor.ifBlank { if (isExam) "Exam Session" else "Weekly Session" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (item.reminderEnabled) {
                            "Alert ${ScheduleFormatter.leadTimeLabel(item.reminderLeadMinutes)}"
                        } else {
                            "Reminder muted"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleEditorSheet(
    initial: ScheduleItem,
    onDismiss: () -> Unit,
    onSave: (ScheduleItem) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var itemType by remember { mutableStateOf(initial.parsedType) }
    var courseCode by remember { mutableStateOf(initial.courseCode) }
    var title by remember { mutableStateOf(initial.title) }
    var dayOfWeek by remember { mutableIntStateOf(initial.dayOfWeek.coerceIn(1, 7)) }
    var startHour by remember { mutableIntStateOf((initial.startTimeMinutes / 60).coerceIn(7, 21)) }
    var durationMinutes by remember {
        mutableIntStateOf((initial.endTimeMinutes - initial.startTimeMinutes).coerceIn(45, 180))
    }
    var examDaysFromNow by remember {
        val diffDays = if (initial.examEpochMillis > System.currentTimeMillis()) {
            ((initial.examEpochMillis - System.currentTimeMillis()) / 86_400_000L).toInt().coerceIn(0, 60)
        } else 3
        mutableIntStateOf(diffDays)
    }
    var location by remember { mutableStateOf(initial.location) }
    var instructor by remember { mutableStateOf(initial.instructor) }
    var reminderEnabled by remember { mutableStateOf(initial.reminderEnabled) }
    var leadMinutes by remember { mutableIntStateOf(initial.reminderLeadMinutes) }
    var validationError by remember { mutableStateOf<String?>(null) }

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
            Text(
                text = if (initial.id.isBlank()) "Add Schedule Entry" else "Edit Schedule Entry",
                style = MaterialTheme.typography.headlineSmall
            )

            SegmentedMonochromeTabs(
                options = listOf("Weekly Class", "Exam / Deadline"),
                selectedIndex = if (itemType == ScheduleItemType.CLASS) 0 else 1,
                onSelected = { idx ->
                    itemType = if (idx == 0) ScheduleItemType.CLASS else ScheduleItemType.EXAM
                },
                tagPrefix = "editor_type"
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = courseCode,
                    onValueChange = { courseCode = it.take(24) },
                    label = { Text("Course Code") },
                    placeholder = { Text("CS-301") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(0.4f)
                        .testTag("schedule_course_code_input")
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(100) },
                    label = { Text(if (itemType == ScheduleItemType.EXAM) "Exam Title" else "Class Title") },
                    placeholder = { Text(if (itemType == ScheduleItemType.EXAM) "Midterm Exam" else "Operating Systems") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(0.6f)
                        .testTag("schedule_title_input")
                )
            }

            if (itemType == ScheduleItemType.CLASS) {
                Text("Day of Week", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items((1..7).toList()) { d ->
                        FilterChip(
                            selected = dayOfWeek == d,
                            onClick = { dayOfWeek = d },
                            label = { Text(ScheduleFormatter.dayShort(d)) }
                        )
                    }
                }
            } else {
                Text("Exam Date Offset", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(listOf(0 to "Today", 1 to "Tomorrow", 2 to "In 2d", 3 to "In 3d", 5 to "In 5d", 7 to "In 1w", 14 to "In 2w")) { (days, lbl) ->
                        FilterChip(
                            selected = examDaysFromNow == days,
                            onClick = { examDaysFromNow = days },
                            label = { Text(lbl) }
                        )
                    }
                }
            }

            Text("Start Time", style = MaterialTheme.typography.labelMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(StartHourOptions) { hr ->
                    FilterChip(
                        selected = startHour == hr,
                        onClick = { startHour = hr },
                        label = { Text(ScheduleFormatter.formatMinutes(hr * 60)) }
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it.take(100) },
                    label = { Text("Building & Room") },
                    placeholder = { Text("Turing Hall 204") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = instructor,
                    onValueChange = { instructor = it.take(80) },
                    label = { Text("Instructor") },
                    placeholder = { Text("Dr. Patel") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Push Reminder Notification", style = MaterialTheme.typography.titleSmall)
                        Switch(
                            checked = reminderEnabled,
                            onCheckedChange = { reminderEnabled = it }
                        )
                    }
                    if (reminderEnabled) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(LeadTimeOptions) { mins ->
                                FilterChip(
                                    selected = leadMinutes == mins,
                                    onClick = { leadMinutes = mins },
                                    label = { Text(ScheduleFormatter.leadTimeLabel(mins)) }
                                )
                            }
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
                    if (courseCode.isBlank() || title.isBlank()) {
                        validationError = "Please enter both a course code and title."
                        return@Button
                    }
                    val startMins = startHour * 60
                    val endMins = (startMins + durationMinutes).coerceAtMost(1439)
                    val examMillis = if (itemType == ScheduleItemType.EXAM) {
                        Calendar.getInstance().apply {
                            add(Calendar.DAY_OF_YEAR, examDaysFromNow)
                            set(Calendar.HOUR_OF_DAY, startHour)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }.timeInMillis
                    } else 0L

                    onSave(
                        initial.copy(
                            itemType = itemType.name,
                            courseCode = courseCode.trim(),
                            title = title.trim(),
                            dayOfWeek = dayOfWeek,
                            startTimeMinutes = startMins,
                            endTimeMinutes = endMins,
                            examEpochMillis = examMillis,
                            location = location.trim(),
                            instructor = instructor.trim(),
                            reminderEnabled = reminderEnabled,
                            reminderLeadMinutes = leadMinutes
                        )
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("save_schedule_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(if (initial.id.isBlank()) "Save to Schedule" else "Update Entry")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
