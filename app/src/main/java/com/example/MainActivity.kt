package com.example

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.auth.tryHandleIncomingEmailLinkIntent
import com.example.firebase.friendlyFirestoreErrorMessage
import com.example.firebase.configureAppCheck
import com.example.model.AppThemeMode
import com.example.model.UiState
import com.example.ui.components.IncomingCallBanner
import com.example.ui.components.PChatBottomBar
import com.example.ui.components.PChatRoutes
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.CallScreen
import com.example.ui.screens.ChatDetailScreen
import com.example.ui.screens.ChatHomeScreen
import com.example.ui.screens.ConnectScreen
import com.example.ui.screens.MalakAiScreen
import com.example.ui.screens.MomentsScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.PlusCameraScreen
import com.example.ui.screens.ProfileMeScreen
import com.example.ui.screens.WalletScreen
import com.example.ui.theme.PChatTheme
import com.example.ui.viewmodel.PChatViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var emailLinkBannerState by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure App Check with launch intent before Compose UI, ViewModels, or Firebase calls
        configureAppCheck(this, intent)

        handleAuthEmailLink(intent)

        enableEdgeToEdge()
        setContent {
            PChatRoot(
                externalBanner = emailLinkBannerState,
                onConsumeExternalBanner = { emailLinkBannerState = null }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthEmailLink(intent)
    }

    private fun handleAuthEmailLink(incomingIntent: Intent?) {
        lifecycleScope.launch {
            val result = tryHandleIncomingEmailLinkIntent(this@MainActivity, incomingIntent)
            if (result != null) {
                emailLinkBannerState = result.getOrElse {
                    it.localizedMessage ?: "Email link sign-in could not be completed."
                }
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun PChatRoot(
    externalBanner: String? = null,
    onConsumeExternalBanner: () -> Unit = {}
) {
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }
    var isDarkTheme by remember { mutableStateOf(true) }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivity()?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !isDarkTheme
                    isAppearanceLightNavigationBars = !isDarkTheme
                }
            }
        }
    }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose {
            Firebase.auth.removeAuthStateListener(listener)
        }
    }

    PChatTheme(darkTheme = isDarkTheme) {
        val user = currentUser
        if (user == null) {
            AuthScreen(
                isDarkTheme = isDarkTheme,
                externalMessage = externalBanner,
                onConsumeExternalMessage = onConsumeExternalBanner,
                onToggleTheme = { isDarkTheme = !isDarkTheme },
                onAuthSuccess = { currentUser = Firebase.auth.currentUser }
            )
        } else {
            key(user.uid) {
                PChatMainShell(
                    currentUserId = user.uid,
                    displayName = user.displayName,
                    email = user.email,
                    externalBanner = externalBanner,
                    onConsumeExternalBanner = onConsumeExternalBanner,
                    onThemeChanged = { isDarkTheme = it }
                )
            }
        }
    }
}

@Composable
private fun PChatMainShell(
    currentUserId: String,
    displayName: String?,
    email: String?,
    externalBanner: String? = null,
    onConsumeExternalBanner: () -> Unit = {},
    onThemeChanged: (Boolean) -> Unit
) {
    val viewModel: PChatViewModel = viewModel(
        key = currentUserId,
        factory = PChatViewModel.provideFactory(currentUserId, displayName, email)
    )

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: PChatRoutes.CHAT

    val myProfileState by viewModel.myProfile.collectAsStateWithLifecycle()
    val privateSettingsState by viewModel.privateSettings.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val conversationsState by viewModel.conversations.collectAsStateWithLifecycle()
    val friendshipsState by viewModel.friendships.collectAsStateWithLifecycle()
    val callsState by viewModel.calls.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()

    LaunchedEffect(themeMode) {
        onThemeChanged(themeMode == AppThemeMode.BLACK)
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(statusBanner, externalBanner) {
        val ext = externalBanner
        if (!ext.isNullOrBlank()) {
            onConsumeExternalBanner()
            snackbarHostState.showSnackbar(ext)
        }
        val msg = statusBanner
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearBanner()
        }
    }

    val profile = (myProfileState as? UiState.Success)?.data
    val colors = PChatTheme.colors
    val context = LocalContext.current
    val shellScope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    if (myProfileState is UiState.Loading || (myProfileState is UiState.Success && profile == null)) {
        LaunchedEffect(myProfileState) {
            if (myProfileState is UiState.Success && profile == null) {
                viewModel.retryInitialization()
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(24.dp)
            ) {
                CircularProgressIndicator(color = colors.accentBlue)
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Syncing your P-CHAT account...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary
                )
            }
        }
        return
    }

    if (myProfileState is UiState.Error) {
        val errText = friendlyFirestoreErrorMessage(
            IllegalStateException((myProfileState as UiState.Error).message),
            "Could not reach P-CHAT cloud services."
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Account Sync Paused",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.textPrimary
                )
                Text(
                    text = errText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { viewModel.retryInitialization() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.accentBlue,
                            contentColor = colors.primaryButtonContent
                        )
                    ) {
                        Text("Retry Sync")
                    }
                    OutlinedButton(
                        onClick = {
                            viewModel.signOutSecurely(context, credentialManager, shellScope, onSignedOut = {})
                        }
                    ) {
                        Text("Sign Out", color = colors.textPrimary)
                    }
                }
            }
        }
        return
    }

    if (profile != null && !profile.profileCompleted) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background),
            containerColor = colors.background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { pad ->
            Box(modifier = Modifier.fillMaxSize().padding(pad)) {
                OnboardingScreen(viewModel = viewModel)
            }
        }
        return
    }

    val unreadCount = (conversationsState as? UiState.Success)?.data
        ?.count { currentUserId in it.unreadByUids } ?: 0
    val pendingAlertCount = (friendshipsState as? UiState.Success)?.data
        ?.count { it.recipientId == currentUserId && it.status == "pending" } ?: 0

    val incomingCall = (callsState as? UiState.Success)?.data
        ?.firstOrNull { it.status == "ringing" && it.callerId != currentUserId }

    var malakInitialTab by remember { mutableIntStateOf(0) }
    var malakEditBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var walletRecipientUserId by remember { mutableStateOf<String?>(null) }

    val showBottomBar = currentRoute in setOf(
        PChatRoutes.CHAT,
        PChatRoutes.CONNECT,
        PChatRoutes.MOMENTS,
        PChatRoutes.ME
    )

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
        containerColor = colors.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (showBottomBar) {
                PChatBottomBar(
                    currentRoute = currentRoute,
                    unreadCount = unreadCount,
                    pendingAlertCount = pendingAlertCount,
                    onNavigate = { route ->
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (incomingCall != null && !currentRoute.startsWith("call/")) {
                Box(modifier = Modifier.statusBarsPadding()) {
                    IncomingCallBanner(
                        call = incomingCall,
                        onAccept = {
                            viewModel.updateCallStatus(incomingCall.id, "active")
                            navController.navigate(PChatRoutes.call(incomingCall.id))
                        },
                        onDecline = {
                            viewModel.updateCallStatus(incomingCall.id, "declined")
                        }
                    )
                }
            }

            NavHost(
                navController = navController,
                startDestination = PChatRoutes.CHAT,
                modifier = Modifier.weight(1f)
            ) {
                composable(PChatRoutes.CHAT) {
                    Box(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                        ChatHomeScreen(
                            viewModel = viewModel,
                            onOpenConversation = { convId ->
                                navController.navigate(PChatRoutes.chatDetail(convId))
                            },
                            onOpenMalakAi = {
                                malakInitialTab = 0
                                malakEditBitmap = null
                                navController.navigate(PChatRoutes.MALAK)
                            },
                            onOpenConnect = {
                                navController.navigate(PChatRoutes.CONNECT)
                            },
                            onOpenWallet = {
                                walletRecipientUserId = null
                                navController.navigate(PChatRoutes.WALLET)
                            }
                        )
                    }
                }

                composable(PChatRoutes.CONNECT) {
                    Box(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                        ConnectScreen(
                            viewModel = viewModel,
                            onOpenConversation = { convId ->
                                navController.navigate(PChatRoutes.chatDetail(convId))
                            },
                            onOpenWallet = { recipientId ->
                                walletRecipientUserId = recipientId
                                navController.navigate(PChatRoutes.WALLET)
                            }
                        )
                    }
                }

                composable(PChatRoutes.PLUS) {
                    PlusCameraScreen(
                        viewModel = viewModel,
                        onClose = { navController.popBackStack() },
                        onPostedMoment = {
                            navController.navigate(PChatRoutes.MOMENTS) {
                                popUpTo(PChatRoutes.PLUS) { inclusive = true }
                            }
                        },
                        onEditInAiStudio = { bmp ->
                            malakInitialTab = 2
                            malakEditBitmap = bmp
                            navController.navigate(PChatRoutes.MALAK) {
                                popUpTo(PChatRoutes.PLUS) { inclusive = true }
                            }
                        }
                    )
                }

                composable(PChatRoutes.MOMENTS) {
                    Box(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                        MomentsScreen(
                            viewModel = viewModel,
                            onOpenCamera = { navController.navigate(PChatRoutes.PLUS) }
                        )
                    }
                }

                composable(PChatRoutes.ME) {
                    Box(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                        ProfileMeScreen(
                            viewModel = viewModel,
                            onOpenConnect = { navController.navigate(PChatRoutes.CONNECT) },
                            onOpenWallet = {
                                walletRecipientUserId = null
                                navController.navigate(PChatRoutes.WALLET)
                            },
                            onSignedOut = {}
                        )
                    }
                }

                composable(PChatRoutes.WALLET) {
                    WalletScreen(
                        viewModel = viewModel,
                        initialRecipientUserId = walletRecipientUserId,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(PChatRoutes.MALAK) {
                    MalakAiScreen(
                        viewModel = viewModel,
                        initialTab = malakInitialTab,
                        initialEditBitmap = malakEditBitmap,
                        onBack = { navController.popBackStack() },
                        onNavigateToMoments = {
                            navController.navigate(PChatRoutes.MOMENTS) {
                                popUpTo(PChatRoutes.MALAK) { inclusive = true }
                            }
                        }
                    )
                }

                composable(PChatRoutes.CHAT_DETAIL) { entry ->
                    val convId = entry.arguments?.getString("conversationId").orEmpty()
                    ChatDetailScreen(
                        conversationId = convId,
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() },
                        onCallStarted = { session ->
                            navController.navigate(PChatRoutes.call(session.id))
                        },
                        onOpenWalletForRecipient = { recipientId ->
                            walletRecipientUserId = recipientId
                            navController.navigate(PChatRoutes.WALLET)
                        }
                    )
                }

                composable(PChatRoutes.CALL) { entry ->
                    val callId = entry.arguments?.getString("callId").orEmpty()
                    CallScreen(
                        callId = callId,
                        viewModel = viewModel,
                        onEndCall = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}
