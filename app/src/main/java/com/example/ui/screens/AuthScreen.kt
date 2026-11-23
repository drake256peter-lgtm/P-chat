package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.auth.PendingAuthPrefs
import com.example.auth.attemptAutoSignIn
import com.example.auth.launchGoogleAccountAuth
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ErrorCrimson
import com.example.ui.theme.HotPink
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.PChatTheme
import com.example.ui.theme.PureWhite

private data class LoginBackdropSlices(
    val heroTop: ImageBitmap?,
    val globeBottom: ImageBitmap?
)

@Composable
fun AuthScreen(
    isDarkTheme: Boolean = true,
    externalMessage: String? = null,
    onConsumeExternalMessage: () -> Unit = {},
    onToggleTheme: () -> Unit = {},
    onAuthSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val colors = PChatTheme.colors

    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    var showPhoneDialog by remember { mutableStateOf(false) }
    var showEmailDialog by remember { mutableStateOf(false) }
    var showSignUpDialog by remember { mutableStateOf(false) }
    var showReturningLoginDialog by remember { mutableStateOf(false) }
    var showDiagnosticsDialog by remember { mutableStateOf(false) }
    var modalAsSignUp by remember { mutableStateOf(false) }

    val knownAccounts = remember(context, showReturningLoginDialog) {
        PendingAuthPrefs.getKnownAccounts(context)
    }
    val lastMethod = remember(context) { PendingAuthPrefs.getLastMethod(context) }

    LaunchedEffect(externalMessage) {
        if (!externalMessage.isNullOrBlank()) {
            errorMsg = externalMessage
            onConsumeExternalMessage()
        }
    }

    val backdropSlices = remember(context) {
        try {
            val full = BitmapFactory.decodeResource(context.resources, R.drawable.img_pchat_login_backdrop)
            if (full != null && full.height > 200) {
                val heroH = (full.height * 0.56f).toInt().coerceIn(100, full.height)
                val globeTop = (full.height * 0.915f).toInt().coerceIn(0, full.height - 20)
                val topBmp = Bitmap.createBitmap(full, 0, 0, full.width, heroH).asImageBitmap()
                val bottomBmp = Bitmap.createBitmap(full, 0, globeTop, full.width, full.height - globeTop).asImageBitmap()
                LoginBackdropSlices(topBmp, bottomBmp)
            } else {
                LoginBackdropSlices(null, null)
            }
        } catch (_: Exception) {
            LoginBackdropSlices(null, null)
        }
    }

    fun triggerGoogleSignIn() {
        errorMsg = null
        isLoading = true
        launchGoogleAccountAuth(
            context = context,
            credentialManager = credentialManager,
            scope = scope,
            onSuccess = {
                isLoading = false
                onAuthSuccess()
            },
            onError = { err ->
                isLoading = false
                errorMsg = err
            },
            onCancelled = {
                isLoading = false
            }
        )
    }

    LaunchedEffect(Unit) {
        if (!PendingAuthPrefs.consumeSuppressAutoSignInOnce()) {
            attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = onAuthSuccess,
                onUnauthenticated = {},
                scope = scope
            )
        }
    }

    val cosmicBg = if (isDarkTheme) Color(0xFF04010D) else colors.background

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(cosmicBg)
    ) {
        val heroHeight = (maxHeight * 0.44f).coerceIn(220.dp, 380.dp)
        val buttonHorizontalPadding = if (maxWidth < 360.dp) 18.dp else 28.dp

        // Bottom glowing globe slice matching reference screenshot
        if (backdropSlices.globeBottom != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(92.dp)
                    .align(Alignment.BottomCenter)
            ) {
                Image(
                    bitmap = backdropSlices.globeBottom,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alpha = if (isDarkTheme) 0.96f else 0.30f
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(listOf(cosmicBg, Color.Transparent))
                        )
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Hero Banner Slice (Mascot, Crown, Speech Bubbles, "P-CHAT", "YOUR SMART AI CHAT PARTNER")
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(heroHeight)
            ) {
                if (backdropSlices.heroTop != null) {
                    Image(
                        bitmap = backdropSlices.heroTop,
                        contentDescription = "P-CHAT Chat Connect Share Smart AI Chat Partner",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.TopCenter,
                        alpha = if (isDarkTheme) 1f else 0.90f
                    )
                    // Smooth fade into background so real login buttons sit cleanly below
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(74.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.Transparent,
                                        cosmicBg.copy(alpha = 0.86f),
                                        cosmicBg
                                    )
                                )
                            )
                    )
                }

                // Compact top-left theme switch pill (kept left so "Chat • Connect • Share" top-right remains untouched)
                Surface(
                    color = if (isDarkTheme) Color(0xFF130B2E).copy(alpha = 0.80f) else PureWhite.copy(alpha = 0.90f),
                    shape = CircleShape,
                    modifier = Modifier
                        .testTag("auth_theme_toggle")
                        .align(Alignment.TopStart)
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .clip(CircleShape)
                        .border(
                            1.dp,
                            Brush.horizontalGradient(listOf(NeonPurple, ElectricBlue)),
                            CircleShape
                        )
                        .clickable(onClick = onToggleTheme)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = if (isDarkTheme) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                            contentDescription = "Switch theme",
                            tint = if (isDarkTheme) Color(0xFFD8B4FE) else colors.accentBlue,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (isDarkTheme) "White" else "Black",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isDarkTheme) PureWhite else colors.textPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Compact Auth Status & Diagnostics pill at bottom-right of hero banner
                Surface(
                    color = if (isDarkTheme) Color(0xFF130B2E).copy(alpha = 0.80f) else PureWhite.copy(alpha = 0.90f),
                    shape = CircleShape,
                    modifier = Modifier
                        .testTag("auth_diagnostics_button")
                        .align(Alignment.BottomEnd)
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .clip(CircleShape)
                        .border(
                            1.dp,
                            Brush.horizontalGradient(listOf(ElectricBlue, NeonPurple)),
                            CircleShape
                        )
                        .clickable { showDiagnosticsDialog = true }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Shield,
                            contentDescription = "Auth Diagnostics",
                            tint = colors.accentBlue,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Auth Status",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isDarkTheme) PureWhite else colors.textPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Auth Action Pills Stack (Matching the glowing purple glass pills in reference screenshot)
            Column(
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .padding(horizontal = buttonHorizontalPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (knownAccounts.isNotEmpty()) {
                    val primaryKnown = knownAccounts.first()
                    Surface(
                        color = if (isDarkTheme) Color(0xFF1A0F3C).copy(alpha = 0.82f) else colors.card,
                        shape = CircleShape,
                        modifier = Modifier
                            .testTag("returning_user_badge")
                            .clip(CircleShape)
                            .border(1.dp, ElectricBlue.copy(alpha = 0.65f), CircleShape)
                            .clickable { showReturningLoginDialog = true }
                    ) {
                        Text(
                            text = "Welcome back, ${primaryKnown.displayName} • Last: $lastMethod",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isDarkTheme) Color(0xFF8BE9FD) else colors.accentBlue,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }

                if (errorMsg != null) {
                    Surface(
                        color = ErrorCrimson.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, ErrorCrimson.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                    ) {
                        Text(
                            text = errorMsg!!,
                            color = ErrorCrimson,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                // 1. Continue with Google
                NeonAuthPillButton(
                    label = "Continue with Google",
                    isLoading = isLoading,
                    showChevron = true,
                    isDarkTheme = isDarkTheme,
                    modifier = Modifier.testTag("google_sign_in_button"),
                    onClick = { triggerGoogleSignIn() },
                    leadingIcon = { GoogleColorGIcon(sizeDp = 24) }
                )

                // 2. Continue with Phone
                NeonAuthPillButton(
                    label = "Continue with Phone",
                    isLoading = false,
                    showChevron = true,
                    isDarkTheme = isDarkTheme,
                    modifier = Modifier.testTag("phone_sign_in_button"),
                    onClick = {
                        modalAsSignUp = false
                        showPhoneDialog = true
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.PhoneAndroid,
                            contentDescription = null,
                            tint = if (isDarkTheme) PureWhite else colors.textPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                )

                // 3. Continue with Email
                NeonAuthPillButton(
                    label = "Continue with Email",
                    isLoading = false,
                    showChevron = true,
                    isDarkTheme = isDarkTheme,
                    modifier = Modifier.testTag("email_sign_in_button"),
                    onClick = {
                        modalAsSignUp = false
                        showEmailDialog = true
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.MailOutline,
                            contentDescription = null,
                            tint = if (isDarkTheme) PureWhite else colors.textPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                )

                // OR Divider
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        color = if (isDarkTheme) Color(0xFF3E2A6E) else colors.border
                    )
                    Text(
                        text = "  OR  ",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isDarkTheme) Color(0xFFD6CFF0) else colors.textSecondary,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp
                    )
                    HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        color = if (isDarkTheme) Color(0xFF3E2A6E) else colors.border
                    )
                }

                // 4. Sign Up Pill
                NeonAuthPillButton(
                    label = "Sign Up",
                    isLoading = false,
                    showChevron = false,
                    isDarkTheme = isDarkTheme,
                    modifier = Modifier.testTag("sign_up_button"),
                    onClick = { showSignUpDialog = true },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.PersonAdd,
                            contentDescription = null,
                            tint = if (isDarkTheme) PureWhite else NeonPurple,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                )

                Spacer(modifier = Modifier.height(2.dp))

                // 5. Already have an account? Log In
                Box(
                    modifier = Modifier
                        .testTag("already_have_account_login")
                        .minimumInteractiveComponentSize()
                        .clip(CircleShape)
                        .clickable { showReturningLoginDialog = true }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val promptText = buildAnnotatedString {
                        withStyle(
                            SpanStyle(
                                color = if (isDarkTheme) Color(0xFFE5E0FA) else colors.textSecondary,
                                fontWeight = FontWeight.Normal
                            )
                        ) {
                            append("Already have an account?  ")
                        }
                        withStyle(
                            SpanStyle(
                                color = if (isDarkTheme) Color(0xFFB55CFF) else NeonPurple,
                                fontWeight = FontWeight.Bold
                            )
                        ) {
                            append("Log In")
                        }
                    }
                    Text(
                        text = promptText,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(22.dp))
            }
        }
    }

    if (showPhoneDialog) {
        PhoneAuthDialog(
            initialSignUpMode = modalAsSignUp,
            onDismiss = { showPhoneDialog = false },
            onSuccess = {
                showPhoneDialog = false
                onAuthSuccess()
            },
            onSwitchToGoogle = { triggerGoogleSignIn() }
        )
    }

    if (showEmailDialog) {
        EmailAuthDialog(
            initialSignUpMode = modalAsSignUp,
            onDismiss = { showEmailDialog = false },
            onSuccess = {
                showEmailDialog = false
                onAuthSuccess()
            },
            onSwitchToGoogle = { triggerGoogleSignIn() }
        )
    }

    if (showSignUpDialog) {
        SignUpDialog(
            onDismiss = { showSignUpDialog = false },
            onContinueGoogle = { triggerGoogleSignIn() },
            onContinuePhone = {
                modalAsSignUp = true
                showPhoneDialog = true
            },
            onContinueEmail = {
                modalAsSignUp = true
                showEmailDialog = true
            }
        )
    }

    if (showReturningLoginDialog) {
        ReturningLoginDialog(
            onDismiss = { showReturningLoginDialog = false },
            onContinueGoogle = { triggerGoogleSignIn() },
            onContinuePhone = {
                modalAsSignUp = false
                showPhoneDialog = true
            },
            onContinueEmail = {
                modalAsSignUp = false
                showEmailDialog = true
            },
            onOpenDiagnostics = {
                showReturningLoginDialog = false
                showDiagnosticsDialog = true
            }
        )
    }

    if (showDiagnosticsDialog) {
        AuthDiagnosticsDialog(
            viewModel = null,
            onDismiss = { showDiagnosticsDialog = false }
        )
    }
}

@Composable
private fun NeonAuthPillButton(
    label: String,
    isLoading: Boolean,
    showChevron: Boolean,
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    leadingIcon: @Composable () -> Unit
) {
    val colors = PChatTheme.colors
    val pillBrush = Brush.horizontalGradient(
        listOf(
            Color(0xFF7B2CBF),
            Color(0xFFC77DFF),
            Color(0xFF9D4EDD),
            HotPink.copy(alpha = 0.85f)
        )
    )
    val innerFill = if (isDarkTheme) {
        Brush.verticalGradient(
            listOf(
                Color(0xFF140933).copy(alpha = 0.90f),
                Color(0xFF0A041C).copy(alpha = 0.95f)
            )
        )
    } else {
        Brush.verticalGradient(
            listOf(
                PureWhite.copy(alpha = 0.98f),
                Color(0xFFF3ECFF).copy(alpha = 0.96f)
            )
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .shadow(
                elevation = if (isDarkTheme) 12.dp else 4.dp,
                shape = CircleShape,
                ambientColor = Color(0xFFA855F7),
                spotColor = Color(0xFFA855F7)
            )
            .clip(CircleShape)
            .background(innerFill)
            .border(1.8.dp, pillBrush, CircleShape)
            .clickable(enabled = !isLoading, onClick = onClick)
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = if (isDarkTheme) PureWhite else NeonPurple,
                strokeWidth = 2.4.dp
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.width(34.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    leadingIcon()
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isDarkTheme) PureWhite else colors.textPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier.width(34.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    if (showChevron) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = if (isDarkTheme) PureWhite.copy(alpha = 0.90f) else colors.textSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GoogleColorGIcon(sizeDp: Int = 24) {
    Canvas(modifier = Modifier.size(sizeDp.dp)) {
        val strokeW = size.minDimension * 0.21f
        val inset = strokeW / 2f
        val arcSize = Size(size.width - strokeW, size.height - strokeW)
        val topLeft = Offset(inset, inset)
        val stroke = Stroke(width = strokeW, cap = StrokeCap.Butt)

        // Red top-left arc
        drawArc(
            color = Color(0xFFEA4335),
            startAngle = 205f,
            sweepAngle = 115f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = stroke
        )
        // Yellow left arc
        drawArc(
            color = Color(0xFFFBBC05),
            startAngle = 135f,
            sweepAngle = 72f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = stroke
        )
        // Green bottom arc
        drawArc(
            color = Color(0xFF34A853),
            startAngle = 40f,
            sweepAngle = 98f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = stroke
        )
        // Blue right arc
        drawArc(
            color = Color(0xFF4285F4),
            startAngle = -5f,
            sweepAngle = 50f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = stroke
        )
        // Horizontal bar of the G
        val centerY = size.height / 2f
        drawLine(
            color = Color(0xFF4285F4),
            start = Offset(size.width * 0.52f, centerY),
            end = Offset(size.width - inset * 0.5f, centerY),
            strokeWidth = strokeW,
            cap = StrokeCap.Butt
        )
    }
}
