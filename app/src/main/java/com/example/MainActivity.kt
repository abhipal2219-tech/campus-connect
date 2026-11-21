package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Domain
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Domain
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.firebase.configureAppCheck
import com.example.data.firebase.signOutUser
import com.example.data.model.UiState
import com.example.ui.components.LiveAlertBanner
import com.example.ui.components.MonochromeBadge
import com.example.ui.screens.AccountPreferencesSheet
import com.example.ui.screens.AdminScreen
import com.example.ui.screens.AlertsScreen
import com.example.ui.screens.AssistantScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.CampusScreen
import com.example.ui.screens.ScheduleScreen
import com.example.ui.theme.CampusBotTheme
import com.example.viewmodel.CampusBotViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth

enum class CampusTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    ASSISTANT("assistant", "Assistant", Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome),
    SCHEDULE("schedule", "Schedule", Icons.Filled.CalendarMonth, Icons.Outlined.CalendarMonth),
    CAMPUS("campus", "Campus", Icons.Filled.Domain, Icons.Outlined.Domain),
    ALERTS("alerts", "Alerts", Icons.Filled.Notifications, Icons.Outlined.Notifications),
    ADMIN("admin", "Admin", Icons.Filled.AdminPanelSettings, Icons.Outlined.AdminPanelSettings)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure Firebase App Check with launch intent BEFORE Compose UI or Firebase calls
        configureAppCheck(this, intent)

        enableEdgeToEdge()

        setContent {
            CampusBotTheme {
                AppContent()
            }
        }
    }
}

@Composable
private fun AppContent() {
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    if (currentUser == null) {
        AuthScreen(
            onAuthSuccess = {
                currentUser = Firebase.auth.currentUser
            }
        )
    } else {
        val uid = currentUser!!.uid
        key(uid) {
            CampusBotShell(currentUserId = uid)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CampusBotShell(currentUserId: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val vm: CampusBotViewModel = viewModel(
        key = currentUserId,
        factory = CampusBotViewModel.factory(context)
    )

    val userProfile by vm.userProfile.collectAsStateWithLifecycle()
    val isAdmin by vm.isAdmin.collectAsStateWithLifecycle()
    val adminRecords by vm.adminRecords.collectAsStateWithLifecycle()
    val scheduleState by vm.scheduleState.collectAsStateWithLifecycle()
    val directoryState by vm.directoryState.collectAsStateWithLifecycle()
    val myTicketsState by vm.myTicketsState.collectAsStateWithLifecycle()
    val allTicketsState by vm.allTicketsState.collectAsStateWithLifecycle()
    val announcementsState by vm.announcementsState.collectAsStateWithLifecycle()
    val chatMessagesState by vm.chatMessagesState.collectAsStateWithLifecycle()
    val aiLoading by vm.aiLoading.collectAsStateWithLifecycle()
    val aiStreamingText by vm.aiStreamingText.collectAsStateWithLifecycle()
    val aiError by vm.aiError.collectAsStateWithLifecycle()
    val liveAlertBanner by vm.liveAlertBanner.collectAsStateWithLifecycle()
    val snackbarMessage by vm.snackbarMessage.collectAsStateWithLifecycle()

    val tabs = remember(isAdmin) {
        if (isAdmin) CampusTab.entries else CampusTab.entries.filter { it != CampusTab.ADMIN }
    }
    var selectedTab by rememberSaveable { mutableStateOf(CampusTab.ASSISTANT) }
    var campusInitialSegment by rememberSaveable { mutableIntStateOf(0) }
    var showAccountSheet by remember { mutableStateOf(false) }

    if (selectedTab == CampusTab.ADMIN && !isAdmin) {
        selectedTab = CampusTab.ASSISTANT
    }

    BackHandler(enabled = selectedTab != CampusTab.ASSISTANT) {
        selectedTab = CampusTab.ASSISTANT
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(snackbarMessage) {
        val msg = snackbarMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            vm.clearSnackbar()
        }
    }

    val scheduleItems = (scheduleState as? UiState.Success)?.data.orEmpty()
    val announcements = (announcementsState as? UiState.Success)?.data.orEmpty()
    val directoryCount = (directoryState as? UiState.Success)?.data?.size ?: 0

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                Column {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    text = selectedTab.label,
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        },
                        actions = {
                            Surface(
                                onClick = { showAccountSheet = true },
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier
                                    .padding(end = 12.dp)
                                    .testTag("account_profile_button")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    MonochromeBadge(
                                        text = if (isAdmin) "ADMIN" else "STUDENT",
                                        inverted = isAdmin
                                    )
                                    Icon(
                                        imageVector = Icons.Outlined.AccountCircle,
                                        contentDescription = "Account and notification preferences",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background
                        )
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            },
            bottomBar = {
                if (!useRail) {
                    Column {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            windowInsets = WindowInsets.navigationBars,
                            tonalElevation = 0.dp
                        ) {
                            tabs.forEach { tab ->
                                val selected = tab == selectedTab
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { selectedTab = tab },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = tab.label
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = tab.label,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                        selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                        indicatorColor = MaterialTheme.colorScheme.primary,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    modifier = Modifier.testTag("nav_${tab.route}")
                                )
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        tabs.forEach { tab ->
                            val selected = tab == selectedTab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { selectedTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = {
                                    Text(
                                        text = tab.label,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                    indicatorColor = MaterialTheme.colorScheme.primary,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.testTag("rail_${tab.route}")
                            )
                        }
                    }
                    VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 840.dp)
                            .fillMaxSize()
                    ) {
                        AnimatedVisibility(
                            visible = liveAlertBanner != null,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            liveAlertBanner?.let { banner ->
                                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                    LiveAlertBanner(
                                        announcement = banner,
                                        onView = {
                                            vm.dismissLiveBanner()
                                            selectedTab = CampusTab.ALERTS
                                        },
                                        onDismiss = { vm.dismissLiveBanner() }
                                    )
                                }
                            }
                        }

                        Box(modifier = Modifier.weight(1f)) {
                            when (selectedTab) {
                                CampusTab.ASSISTANT -> AssistantScreen(
                                    chatState = chatMessagesState,
                                    scheduleItems = scheduleItems,
                                    announcements = announcements,
                                    directoryCount = directoryCount,
                                    isAiLoading = aiLoading,
                                    streamingText = aiStreamingText,
                                    aiError = aiError,
                                    onSendPrompt = vm::sendAiPrompt,
                                    onClearChat = vm::clearChatHistory,
                                    onDismissError = vm::clearAiError,
                                    onNavigateToTickets = {
                                        campusInitialSegment = 1
                                        selectedTab = CampusTab.CAMPUS
                                    }
                                )

                                CampusTab.SCHEDULE -> ScheduleScreen(
                                    scheduleState = scheduleState,
                                    defaultLeadMinutes = userProfile?.reminderLeadMinutes ?: 30,
                                    onSaveItem = vm::saveScheduleItem,
                                    onToggleReminder = vm::toggleScheduleReminder,
                                    onDeleteItem = vm::deleteScheduleItem,
                                    onPreviewReminder = vm::sendPreviewReminder
                                )

                                CampusTab.CAMPUS -> CampusScreen(
                                    initialSegment = campusInitialSegment,
                                    directoryState = directoryState,
                                    ticketsState = myTicketsState,
                                    onCreateTicket = vm::submitTicket,
                                    onCloseOwnTicket = vm::closeOwnTicket
                                )

                                CampusTab.ALERTS -> AlertsScreen(
                                    announcementsState = announcementsState,
                                    notificationsEnabled = userProfile?.notificationsEnabled ?: true,
                                    isAdmin = isAdmin,
                                    onOpenPreferences = { showAccountSheet = true },
                                    onDeleteAnnouncement = vm::deleteAnnouncement
                                )

                                CampusTab.ADMIN -> AdminScreen(
                                    allTicketsState = allTicketsState,
                                    directoryState = directoryState,
                                    adminRecords = adminRecords,
                                    onBroadcastAlert = vm::broadcastAnnouncement,
                                    onTriageTicket = vm::triageTicketAsAdmin,
                                    onSaveDirectoryOffice = vm::saveDirectoryOffice,
                                    onDeleteDirectoryOffice = vm::deleteDirectoryOffice,
                                    onGrantAdmin = vm::grantAdminAccess,
                                    onRevokeAdmin = vm::revokeAdminAccess
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAccountSheet) {
        AccountPreferencesSheet(
            uid = currentUserId,
            profile = userProfile,
            isAdmin = isAdmin,
            onDismiss = { showAccountSheet = false },
            onSavePreferences = vm::updateProfilePreferences,
            onSignOut = {
                signOutUser(
                    credentialManager = credentialManager,
                    scope = scope
                )
            }
        )
    }
}
