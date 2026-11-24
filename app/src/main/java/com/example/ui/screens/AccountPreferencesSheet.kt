package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.example.data.model.AcademicCatalog
import com.example.data.model.ScheduleFormatter
import com.example.data.model.UserProfile
import com.example.ui.components.MonochromeBadge

private val DefaultLeadOptions = listOf(15, 30, 60, 180, 1440)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountPreferencesSheet(
    uid: String,
    profile: UserProfile?,
    isAdmin: Boolean,
    isGuestMode: Boolean = false,
    onDismiss: () -> Unit,
    onSavePreferences: (
        displayName: String,
        program: String,
        department: String,
        semester: Int,
        section: String,
        rollNumber: String,
        enrollmentNumber: String,
        notificationsEnabled: Boolean,
        reminderLeadMinutes: Int,
        onComplete: () -> Unit
    ) -> Unit,
    onSignOut: () -> Unit,
    onSignInWithGoogle: () -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var displayName by remember(profile?.displayName) {
        mutableStateOf(profile?.displayName.orEmpty())
    }
    var selectedProgram by remember(profile?.program) {
        val existing = profile?.program.orEmpty()
        mutableStateOf(
            AcademicCatalog.programs.firstOrNull { it.name.equals(existing, ignoreCase = true) }?.name
                ?: AcademicCatalog.programs.first().name
        )
    }
    var selectedDepartment by remember(profile?.department, selectedProgram) {
        val depts = AcademicCatalog.departmentsFor(selectedProgram)
        val existing = profile?.department.orEmpty()
        mutableStateOf(
            depts.firstOrNull { it.equals(existing, ignoreCase = true) } ?: depts.first()
        )
    }
    var departmentExpanded by remember { mutableStateOf(false) }

    var semester by remember(profile?.semester) {
        mutableIntStateOf((profile?.semester ?: 1).coerceAtLeast(1))
    }
    var section by remember(profile?.section) {
        mutableStateOf(profile?.section.orEmpty())
    }
    var rollNumber by remember(profile?.rollNumber) {
        mutableStateOf(profile?.rollNumber.orEmpty())
    }
    var enrollmentNumber by remember(profile?.enrollmentNumber) {
        mutableStateOf(profile?.enrollmentNumber.orEmpty())
    }
    var notificationsEnabled by remember(profile?.notificationsEnabled) {
        mutableStateOf(profile?.notificationsEnabled ?: true)
    }
    var leadMinutes by remember(profile?.reminderLeadMinutes) {
        mutableIntStateOf(profile?.reminderLeadMinutes ?: 30)
    }

    val availableDepartments = remember(selectedProgram) {
        AcademicCatalog.departmentsFor(selectedProgram)
    }
    val availableSemesters = remember(selectedProgram) {
        AcademicCatalog.semestersFor(selectedProgram)
    }
    val derivedYear = AcademicCatalog.yearFromSemester(semester)

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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Academic Profile & Alerts", style = MaterialTheme.typography.headlineSmall)
                MonochromeBadge(
                    text = when {
                        isGuestMode -> "GUEST"
                        isAdmin -> "CAMPUS ADMIN"
                        else -> "STUDENT"
                    },
                    inverted = isAdmin
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
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = if (isGuestMode) {
                            "Guest Mode (Local Session)"
                        } else {
                            profile?.email?.ifBlank { "Google Account" } ?: "Google Account"
                        },
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = if (isGuestMode) {
                            "Scoped RAG, campus directory, and local reminders are active. Sign in with Google to sync across devices."
                        } else {
                            "UID: $uid"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it.take(80) },
                label = { Text("Display Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

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
                            if (semester > prog.maxSemesters) {
                                semester = prog.maxSemesters
                            }
                        },
                        label = { Text(prog.name) }
                    )
                }
            }

            ExposedDropdownMenuBox(
                expanded = departmentExpanded,
                onExpandedChange = { departmentExpanded = !departmentExpanded }
            ) {
                OutlinedTextField(
                    value = selectedDepartment,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Department") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = departmentExpanded)
                    },
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth()
                        .testTag("pref_department_dropdown")
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Semester", style = MaterialTheme.typography.labelMedium)
                MonochromeBadge(text = "YEAR $derivedYear · SEM $semester")
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(availableSemesters) { sem ->
                    FilterChip(
                        selected = semester == sem,
                        onClick = { semester = sem },
                        label = { Text("Sem $sem") }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = section,
                    onValueChange = { section = it.uppercase().take(24) },
                    label = { Text("Section") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters
                    ),
                    modifier = Modifier
                        .weight(0.4f)
                        .testTag("pref_section_input")
                )
                OutlinedTextField(
                    value = rollNumber,
                    onValueChange = { rollNumber = it.take(40) },
                    label = { Text("Roll No.") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(0.6f)
                        .testTag("pref_roll_input")
                )
            }

            OutlinedTextField(
                value = enrollmentNumber,
                onValueChange = { enrollmentNumber = it.take(60) },
                label = { Text("Enrollment Number") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("pref_enrollment_input")
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Urgent Campus Push Alerts", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "Dispatch system notifications when new campus announcements arrive.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = notificationsEnabled,
                    onCheckedChange = { notificationsEnabled = it },
                    modifier = Modifier.testTag("pref_notifications_switch")
                )
            }

            Text("Default Class/Exam Reminder Lead Time", style = MaterialTheme.typography.labelMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(DefaultLeadOptions) { mins ->
                    FilterChip(
                        selected = leadMinutes == mins,
                        onClick = { leadMinutes = mins },
                        label = { Text(ScheduleFormatter.leadTimeLabel(mins)) }
                    )
                }
            }

            Button(
                onClick = {
                    if (displayName.isNotBlank() && section.isNotBlank() && rollNumber.isNotBlank() && enrollmentNumber.isNotBlank()) {
                        onSavePreferences(
                            displayName,
                            selectedProgram,
                            selectedDepartment,
                            semester,
                            section,
                            rollNumber,
                            enrollmentNumber,
                            notificationsEnabled,
                            leadMinutes
                        ) {
                            onDismiss()
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("save_preferences_button")
            ) {
                Text("Save Profile & Preferences")
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            if (isGuestMode) {
                Button(
                    onClick = {
                        onDismiss()
                        onSignInWithGoogle()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("guest_sign_in_button")
                ) {
                    Text("Sign in with Google")
                }
            } else {
                OutlinedButton(
                    onClick = {
                        onDismiss()
                        onSignOut()
                    },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("sign_out_button")
                ) {
                    Text("Sign Out")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
