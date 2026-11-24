package com.example.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.School
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.example.data.model.AcademicCatalog
import com.example.data.model.UserProfile
import com.example.ui.components.MonochromeBadge
import com.example.ui.components.MonochromeCard
import com.example.ui.components.MonochromeErrorCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentOnboardingScreen(
    profile: UserProfile?,
    isGuestMode: Boolean,
    isSaving: Boolean,
    serverError: String?,
    onClearError: () -> Unit,
    onCompleteOnboarding: (
        program: String,
        department: String,
        semester: Int,
        section: String,
        rollNumber: String,
        enrollmentNumber: String,
        autoImportBundledSchedule: Boolean,
        scheduleFileUri: Uri?
    ) -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current

    val initialProgram = remember(profile?.program) {
        val existing = profile?.program.orEmpty()
        AcademicCatalog.programs.firstOrNull { it.name.equals(existing, ignoreCase = true) }?.name
            ?: AcademicCatalog.programs.first().name
    }
    var selectedProgram by rememberSaveable(initialProgram) { mutableStateOf(initialProgram) }

    val availableDepartments = remember(selectedProgram) {
        AcademicCatalog.departmentsFor(selectedProgram)
    }
    var selectedDepartment by rememberSaveable(initialProgram, profile?.department) {
        val existingDept = profile?.department.orEmpty()
        val match = AcademicCatalog.departmentsFor(initialProgram)
            .firstOrNull { it.equals(existingDept, ignoreCase = true) }
            ?: AcademicCatalog.departmentsFor(initialProgram).first()
        mutableStateOf(match)
    }
    var departmentDropdownExpanded by remember { mutableStateOf(false) }

    val availableSemesters = remember(selectedProgram) {
        AcademicCatalog.semestersFor(selectedProgram)
    }
    var selectedSemester by rememberSaveable(profile?.semester) {
        mutableIntStateOf((profile?.semester ?: 0).takeIf { it > 0 } ?: 1)
    }
    var section by rememberSaveable(profile?.section) {
        mutableStateOf(profile?.section.orEmpty())
    }
    var rollNumber by rememberSaveable(profile?.rollNumber) {
        mutableStateOf(profile?.rollNumber.orEmpty())
    }
    var enrollmentNumber by rememberSaveable(profile?.enrollmentNumber) {
        mutableStateOf(profile?.enrollmentNumber.orEmpty())
    }

    var autoImportBundled by rememberSaveable { mutableStateOf(true) }
    var attachedScheduleUri by remember { mutableStateOf<Uri?>(null) }
    var attachedFileName by remember { mutableStateOf<String?>(null) }
    var validationError by remember { mutableStateOf<String?>(null) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            attachedScheduleUri = uri
            attachedFileName = resolveDisplayName(context, uri) ?: "Selected timetable image"
        }
    }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            attachedScheduleUri = uri
            attachedFileName = resolveDisplayName(context, uri) ?: "Selected schedule file"
        }
    }

    val derivedYear = AcademicCatalog.yearFromSemester(selectedSemester)
    val matchesBundledIot2B = AcademicCatalog.matchesBundledIot2B(
        department = selectedDepartment,
        semester = selectedSemester,
        section = section
    )

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MonochromeBadge(
                        text = if (isGuestMode) "GUEST · ACADEMIC SETUP" else "STUDENT SETUP",
                        inverted = true
                    )
                    TextButton(
                        onClick = onSignOut,
                        enabled = !isSaving,
                        modifier = Modifier.testTag("onboarding_sign_out_button")
                    ) {
                        Text(if (isGuestMode) "Back to Sign In" else "Switch Account")
                    }
                }

                // Welcome banner
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val greetingName = profile?.displayName
                        ?.takeIf { it.isNotBlank() && !it.equals("Guest Student", ignoreCase = true) }
                        ?.substringBefore(" ")
                    Text(
                        text = if (greetingName != null) {
                            "Welcome, $greetingName"
                        } else {
                            "Complete Your Academic Profile"
                        },
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Select your department, semester, and section once to personalize CampusBot for your batch and directly import your class & exam schedule.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                val activeError = validationError ?: serverError
                if (!activeError.isNullOrBlank()) {
                    MonochromeErrorCard(message = activeError)
                }

                // Program & Department Card
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
                                imageVector = Icons.Outlined.School,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "PROGRAM & DEPARTMENT",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Text(
                            text = "Program",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(AcademicCatalog.programs, key = { it.name }) { prog ->
                                FilterChip(
                                    selected = selectedProgram == prog.name,
                                    onClick = {
                                        selectedProgram = prog.name
                                        val nextDepts = prog.departments
                                        if (selectedDepartment !in nextDepts) {
                                            selectedDepartment = nextDepts.first()
                                        }
                                        if (selectedSemester > prog.maxSemesters) {
                                            selectedSemester = prog.maxSemesters
                                        }
                                        validationError = null
                                        onClearError()
                                    },
                                    label = { Text(prog.name) },
                                    modifier = Modifier.testTag("onboarding_program_${prog.name}")
                                )
                            }
                        }

                        // Department dropdown
                        ExposedDropdownMenuBox(
                            expanded = departmentDropdownExpanded,
                            onExpandedChange = { departmentDropdownExpanded = !departmentDropdownExpanded }
                        ) {
                            OutlinedTextField(
                                value = selectedDepartment,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Department ($selectedProgram)") },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(
                                        expanded = departmentDropdownExpanded
                                    )
                                },
                                modifier = Modifier
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                    .fillMaxWidth()
                                    .testTag("onboarding_department_dropdown")
                            )
                            ExposedDropdownMenu(
                                expanded = departmentDropdownExpanded,
                                onDismissRequest = { departmentDropdownExpanded = false }
                            ) {
                                availableDepartments.forEach { dept ->
                                    DropdownMenuItem(
                                        text = { Text(dept) },
                                        onClick = {
                                            selectedDepartment = dept
                                            departmentDropdownExpanded = false
                                            validationError = null
                                            onClearError()
                                        },
                                        modifier = Modifier.testTag("onboarding_dept_option_$dept")
                                    )
                                }
                            }
                        }
                    }
                }

                // Semester, Section & Student IDs Card
                MonochromeCard(containerColor = MaterialTheme.colorScheme.surface) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "SEMESTER & SECTION",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            MonochromeBadge(text = "YEAR $derivedYear · SEM $selectedSemester")
                        }

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(availableSemesters) { sem ->
                                FilterChip(
                                    selected = selectedSemester == sem,
                                    onClick = {
                                        selectedSemester = sem
                                        validationError = null
                                        onClearError()
                                    },
                                    label = { Text("Sem $sem") },
                                    modifier = Modifier.testTag("onboarding_semester_$sem")
                                )
                            }
                        }

                        OutlinedTextField(
                            value = section,
                            onValueChange = {
                                section = it.uppercase().take(24)
                                validationError = null
                                onClearError()
                            },
                            label = { Text("Section (e.g. A, B, C)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Characters
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("onboarding_section_input")
                        )

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(AcademicCatalog.quickSections) { sec ->
                                FilterChip(
                                    selected = section.equals(sec, ignoreCase = true),
                                    onClick = {
                                        section = sec
                                        validationError = null
                                        onClearError()
                                    },
                                    label = { Text(sec) }
                                )
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        OutlinedTextField(
                            value = rollNumber,
                            onValueChange = {
                                rollNumber = it.take(40)
                                validationError = null
                                onClearError()
                            },
                            label = { Text("Class Roll Number") },
                            placeholder = { Text("e.g. 12023002018045 or 45") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("onboarding_roll_input")
                        )

                        OutlinedTextField(
                            value = enrollmentNumber,
                            onValueChange = {
                                enrollmentNumber = it.take(60)
                                validationError = null
                                onClearError()
                            },
                            label = { Text("Enrollment Number") },
                            placeholder = { Text("e.g. 120230020180045") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("onboarding_enrollment_input")
                        )
                    }
                }

                // Direct Class & Exam Schedule Import + Scoped RAG Card
                MonochromeCard(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
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
                                imageVector = Icons.Outlined.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "DIRECT SCHEDULE IMPORT",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Text(
                            text = "CampusBot will personalize your timetable, exams, and campus assistant for $selectedDepartment · Year $derivedYear (Sem $selectedSemester)${if (section.isNotBlank()) " · Section ${AcademicCatalog.normalizeSection(section)}" else ""}.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (matchesBundledIot2B) {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.CalendarMonth,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text(
                                                text = "Import Your Official Class Schedule",
                                                style = MaterialTheme.typography.titleSmall
                                            )
                                            Text(
                                                text = "Schedule data is available for $selectedDepartment · Year $derivedYear · Sec ${AcademicCatalog.normalizeSection(section)}.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Switch(
                                        checked = autoImportBundled,
                                        onCheckedChange = { autoImportBundled = it },
                                        modifier = Modifier.testTag("onboarding_auto_import_switch")
                                    )
                                }
                            }
                        }

                        Text(
                            text = "Optional: Directly attach your class or exam schedule (Image / PDF) to auto-extract now:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )

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
                                enabled = !isSaving,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("onboarding_pick_image_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Image,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Timetable Image")
                            }

                            OutlinedButton(
                                onClick = {
                                    pdfPickerLauncher.launch(arrayOf("application/pdf", "image/*"))
                                },
                                enabled = !isSaving,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("onboarding_pick_pdf_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.PictureAsPdf,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Schedule PDF")
                            }
                        }

                        if (attachedScheduleUri != null) {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.AttachFile,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Column {
                                            Text(
                                                text = attachedFileName ?: "Schedule attached",
                                                style = MaterialTheme.typography.titleSmall
                                            )
                                            Text(
                                                text = "Will auto-extract classes & exams when you continue.",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = {
                                            attachedScheduleUri = null
                                            attachedFileName = null
                                        },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Close,
                                            contentDescription = "Remove attached schedule"
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Primary Submit Button
                Button(
                    onClick = {
                        val cleanSection = AcademicCatalog.normalizeSection(section)
                        val cleanRoll = rollNumber.trim()
                        val cleanEnroll = enrollmentNumber.trim()
                        when {
                            selectedProgram.isBlank() || selectedDepartment.isBlank() -> {
                                validationError = "Please select your program and department."
                            }
                            selectedSemester !in 1..10 -> {
                                validationError = "Please choose your current semester."
                            }
                            cleanSection.isEmpty() -> {
                                validationError = "Please enter or pick your section (for example, A, B, or C)."
                            }
                            cleanRoll.isEmpty() -> {
                                validationError = "Please enter your class roll number."
                            }
                            cleanEnroll.isEmpty() -> {
                                validationError = "Please enter your enrollment number."
                            }
                            else -> {
                                validationError = null
                                onClearError()
                                onCompleteOnboarding(
                                    selectedProgram,
                                    selectedDepartment,
                                    selectedSemester,
                                    cleanSection,
                                    cleanRoll,
                                    cleanEnroll,
                                    matchesBundledIot2B && autoImportBundled,
                                    attachedScheduleUri
                                )
                            }
                        }
                    },
                    enabled = !isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("complete_onboarding_button")
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Saving Academic Profile...")
                    } else {
                        Text("Save & Unlock CampusBot")
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

private fun resolveDisplayName(context: android.content.Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0) cursor.getString(idx) else null
        } else null
    }
}.getOrNull()
