package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.auth.AuthValidation
import com.example.auth.DialCountry
import com.example.auth.INTERNATIONAL_DIAL_COUNTRIES
import com.example.auth.PREFERRED_LANGUAGES
import com.example.auth.PendingAuthPrefs
import com.example.auth.completeEmailLinkSignIn
import com.example.auth.isValidEmailAddress
import com.example.auth.normalizePhoneE164
import com.example.auth.sanitizeUsernameHandle
import com.example.auth.sendPasswordlessEmailLink
import com.example.auth.startPhoneNumberVerification
import com.example.auth.verifyPhoneOtpCode
import com.example.model.AuthProviderType
import com.example.model.SignUpDraft
import com.example.ui.theme.AmberSpark
import com.example.ui.theme.ErrorCrimson
import com.example.ui.theme.HotPink
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.PChatBrandGradient
import com.example.ui.theme.PChatTheme
import com.example.util.MediaUtils
import com.google.firebase.auth.PhoneAuthProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PhoneAuthDialog(
    initialSignUpMode: Boolean,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit,
    onSwitchToGoogle: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = PChatTheme.colors

    var selectedCountry by remember { mutableStateOf(INTERNATIONAL_DIAL_COUNTRIES.first()) }
    var showCountryMenu by remember { mutableStateOf(false) }
    var countrySearch by remember { mutableStateOf("") }
    var localNumber by remember { mutableStateOf("") }
    var verificationId by remember { mutableStateOf<String?>(null) }
    var resendToken by remember { mutableStateOf<PhoneAuthProvider.ForceResendingToken?>(null) }
    var otpCode by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var needsConsoleSetup by remember { mutableStateOf(false) }
    var resendSeconds by remember { mutableIntStateOf(0) }
    var otpExpirySec by remember { mutableIntStateOf(0) }
    var lockoutSec by remember { mutableIntStateOf(0) }

    val tracker = remember { PendingAuthPrefs.otpSecurityTracker() }

    LaunchedEffect(verificationId, errorMsg) {
        while (verificationId != null || lockoutSec > 0) {
            val now = System.currentTimeMillis()
            otpExpirySec = if (verificationId != null) tracker.remainingExpirySeconds(now) else 0
            lockoutSec = tracker.remainingLockoutSeconds(now)
            if (resendSeconds > 0) resendSeconds -= 1
            delay(1000L)
        }
    }

    LaunchedEffect(resendSeconds) {
        if (resendSeconds > 0 && verificationId == null && lockoutSec == 0) {
            delay(1000L)
            resendSeconds -= 1
        }
    }

    fun sendCode(token: PhoneAuthProvider.ForceResendingToken? = null) {
        errorMsg = null
        needsConsoleSetup = false
        val fullPhone = normalizePhoneE164(selectedCountry.dialCode, localNumber)
        isLoading = true
        startPhoneNumberVerification(
            context = context,
            phoneE164 = fullPhone,
            scope = scope,
            resendToken = token,
            onCodeSent = { verId, rToken ->
                isLoading = false
                verificationId = verId
                resendToken = rToken
                resendSeconds = 60
                otpExpirySec = tracker.remainingExpirySeconds(System.currentTimeMillis())
            },
            onAutoVerified = {
                isLoading = false
                onSuccess()
            },
            onError = { msg, consoleNeeded ->
                isLoading = false
                errorMsg = msg
                needsConsoleSetup = consoleNeeded
                lockoutSec = tracker.remainingLockoutSeconds(System.currentTimeMillis())
            }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = colors.surface,
            shape = RoundedCornerShape(26.dp),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .border(1.5.dp, PChatBrandGradient, RoundedCornerShape(26.dp))
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.PhoneAndroid,
                        contentDescription = null,
                        tint = NeonPurple,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (initialSignUpMode) "Sign Up with Phone" else "Continue with Phone",
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = colors.textSecondary)
                    }
                }

                Text(
                    text = if (verificationId == null) {
                        "Enter your phone number to receive a 6-digit SMS verification code via Firebase Phone Auth."
                    } else {
                        "Enter the 6-digit SMS code sent to ${normalizePhoneE164(selectedCountry.dialCode, localNumber)}."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary
                )

                if (lockoutSec > 0) {
                    Surface(
                        color = ErrorCrimson.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, ErrorCrimson.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                    ) {
                        Text(
                            text = "Verification locked for ${lockoutSec}s due to repeated incorrect OTP attempts.",
                            color = ErrorCrimson,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                if (verificationId == null) {
                    val phoneE164 = normalizePhoneE164(selectedCountry.dialCode, localNumber)
                    val phoneCheck = AuthValidation.validatePhoneE164(phoneE164)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box {
                            Surface(
                                color = colors.card,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .testTag("phone_country_picker")
                                    .height(56.dp)
                                    .border(1.dp, colors.border, RoundedCornerShape(14.dp))
                                    .clickable { showCountryMenu = true }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 12.dp)
                                ) {
                                    Text("${selectedCountry.flag} ${selectedCountry.dialCode}", color = colors.textPrimary, fontWeight = FontWeight.Bold)
                                }
                            }
                            SearchableDialMenu(
                                expanded = showCountryMenu,
                                query = countrySearch,
                                onQueryChange = { countrySearch = it },
                                onSelect = { country ->
                                    selectedCountry = country
                                    showCountryMenu = false
                                    countrySearch = ""
                                },
                                onDismiss = { showCountryMenu = false }
                            )
                        }

                        OutlinedTextField(
                            value = localNumber,
                            onValueChange = { localNumber = it.filter { ch -> ch.isDigit() || ch == '+' || ch == ' ' || ch == '-' } },
                            label = { Text("Phone Number") },
                            placeholder = { Text("700 123 456") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            modifier = Modifier
                                .testTag("phone_number_input")
                                .weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            colors = authTextFieldColors()
                        )
                    }

                    Button(
                        onClick = { sendCode() },
                        enabled = !isLoading && lockoutSec == 0 && phoneCheck.isValid,
                        modifier = Modifier
                            .testTag("phone_send_code_button")
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonPurple,
                            contentColor = colors.onGradientText
                        )
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = colors.onGradientText, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.Sms, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Send Verification Code", fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    Text(
                        text = if (otpExpirySec > 0) {
                            "Code expires in ${otpExpirySec / 60}m ${otpExpirySec % 60}s"
                        } else {
                            "Code expired — request a new SMS verification code"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (otpExpirySec > 0) colors.accentBlue else ErrorCrimson
                    )

                    OutlinedTextField(
                        value = otpCode,
                        onValueChange = { otpCode = it.filter { ch -> ch.isDigit() }.take(6) },
                        label = { Text("6-Digit SMS Code") },
                        placeholder = { Text("123456") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier
                            .testTag("phone_otp_input")
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = authTextFieldColors()
                    )

                    Button(
                        onClick = {
                            val verId = verificationId ?: return@Button
                            errorMsg = null
                            isLoading = true
                            scope.launch {
                                val res = verifyPhoneOtpCode(context, verId, otpCode)
                                isLoading = false
                                res.onSuccess { onSuccess() }
                                    .onFailure {
                                        errorMsg = it.localizedMessage
                                        lockoutSec = tracker.remainingLockoutSeconds(System.currentTimeMillis())
                                    }
                            }
                        },
                        enabled = !isLoading && lockoutSec == 0 && otpExpirySec > 0 && otpCode.length == 6,
                        modifier = Modifier
                            .testTag("phone_verify_code_button")
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.accentEmerald,
                            contentColor = colors.primaryButtonContent
                        )
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = colors.primaryButtonContent, strokeWidth = 2.dp)
                        } else {
                            Text("Verify & Continue", fontWeight = FontWeight.Bold)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { verificationId = null; otpCode = "" }) {
                            Text("Change number", color = colors.accentBlue)
                        }
                        TextButton(
                            enabled = resendSeconds == 0 && !isLoading && lockoutSec == 0,
                            onClick = { sendCode(resendToken) }
                        ) {
                            Text(
                                text = if (resendSeconds > 0) "Resend in ${resendSeconds}s" else "Resend SMS",
                                color = if (resendSeconds > 0) colors.textMuted else colors.accentBlue
                            )
                        }
                    }
                }

                if (errorMsg != null) {
                    AuthErrorCard(
                        message = errorMsg!!,
                        showGoogleSwitch = needsConsoleSetup,
                        onSwitchToGoogle = {
                            onDismiss()
                            onSwitchToGoogle()
                        }
                    )
                }

                Surface(
                    color = colors.card,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, colors.border, RoundedCornerShape(14.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(Icons.Filled.Info, contentDescription = null, tint = AmberSpark, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Note: Phone SMS requires enabling 'Phone' in Firebase Console → Authentication → Sign-in method (and Play Integrity / SHA-256 or a test phone number).",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EmailAuthDialog(
    initialSignUpMode: Boolean,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit,
    onSwitchToGoogle: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = PChatTheme.colors

    var email by remember { mutableStateOf(PendingAuthPrefs.getPendingEmail(context).orEmpty()) }
    var pastedLink by remember { mutableStateOf("") }
    var linkSent by remember { mutableStateOf(PendingAuthPrefs.getPendingEmail(context) != null) }
    var resendRemainingSec by remember { mutableIntStateOf(PendingAuthPrefs.getEmailLinkResendRemainingSec(context)) }
    var linkExpired by remember { mutableStateOf(PendingAuthPrefs.isPendingEmailLinkExpired(context)) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(linkSent, resendRemainingSec) {
        while (linkSent) {
            resendRemainingSec = PendingAuthPrefs.getEmailLinkResendRemainingSec(context)
            linkExpired = PendingAuthPrefs.isPendingEmailLinkExpired(context)
            delay(1000L)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = colors.surface,
            shape = RoundedCornerShape(26.dp),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .border(1.5.dp, PChatBrandGradient, RoundedCornerShape(26.dp))
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Email,
                        contentDescription = null,
                        tint = colors.accentBlue,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (initialSignUpMode) "Sign Up with Email" else "Continue with Email",
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = colors.textSecondary)
                    }
                }

                Text(
                    text = "Sign in securely without a password via Firebase Email Link, or pick your Gmail / Workspace email instantly via Google Credential Manager.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary
                )

                OutlinedButton(
                    onClick = {
                        onDismiss()
                        onSwitchToGoogle()
                    },
                    modifier = Modifier
                        .testTag("email_google_fallback_button")
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Filled.AccountCircle, contentDescription = null, tint = colors.accentEmerald, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Instant Sign-In with Gmail / Workspace",
                        color = colors.textPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                HorizontalDivider(color = colors.border)

                val emailCheck = AuthValidation.validateEmail(email)
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it; errorMsg = null },
                    label = { Text("Email Address") },
                    placeholder = { Text("you@example.com") },
                    singleLine = true,
                    isError = email.isNotEmpty() && !emailCheck.isValid,
                    supportingText = if (email.isNotEmpty() && !emailCheck.isValid) {
                        { Text(emailCheck.errorMessage.orEmpty(), color = ErrorCrimson) }
                    } else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier
                        .testTag("email_address_input")
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = authTextFieldColors()
                )

                Button(
                    onClick = {
                        errorMsg = null
                        isLoading = true
                        scope.launch {
                            val res = sendPasswordlessEmailLink(context, email)
                            isLoading = false
                            res.onSuccess {
                                linkSent = true
                                resendRemainingSec = 60
                                linkExpired = false
                            }.onFailure {
                                errorMsg = it.localizedMessage
                            }
                        }
                    },
                    enabled = !isLoading && isValidEmailAddress(email) && resendRemainingSec == 0,
                    modifier = Modifier
                        .testTag("email_send_link_button")
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonPurple,
                        contentColor = colors.onGradientText
                    )
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = colors.onGradientText, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when {
                                resendRemainingSec > 0 -> "Resend available in ${resendRemainingSec}s"
                                linkSent -> "Resend Email Sign-In Link"
                                else -> "Send Email Sign-In Link"
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (linkSent) {
                    Surface(
                        color = if (linkExpired) ErrorCrimson.copy(alpha = 0.14f) else colors.accentEmerald.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                1.dp,
                                if (linkExpired) ErrorCrimson.copy(alpha = 0.5f) else colors.accentEmerald.copy(alpha = 0.45f),
                                RoundedCornerShape(14.dp)
                            )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = if (linkExpired) {
                                    "Your previous sign-in link expired (15m limit). Request a fresh link above."
                                } else {
                                    "Link dispatched! Open the link from your inbox on this device, or paste the verification link below:"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textPrimary
                            )
                            OutlinedTextField(
                                value = pastedLink,
                                onValueChange = { pastedLink = it },
                                placeholder = { Text("Paste https://... sign-in link") },
                                singleLine = true,
                                modifier = Modifier
                                    .testTag("email_link_paste_input")
                                    .fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = authTextFieldColors()
                            )
                            Button(
                                onClick = {
                                    errorMsg = null
                                    isLoading = true
                                    scope.launch {
                                        val res = completeEmailLinkSignIn(context, email, pastedLink)
                                        isLoading = false
                                        res.onSuccess { onSuccess() }
                                            .onFailure { errorMsg = it.localizedMessage }
                                    }
                                },
                                enabled = !isLoading && pastedLink.isNotBlank(),
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.accentEmerald,
                                    contentColor = colors.primaryButtonContent
                                )
                            ) {
                                Text("Verify Link & Sign In", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                if (errorMsg != null) {
                    AuthErrorCard(
                        message = errorMsg!!,
                        showGoogleSwitch = true,
                        onSwitchToGoogle = {
                            onDismiss()
                            onSwitchToGoogle()
                        }
                    )
                }

                Surface(
                    color = colors.card,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, colors.border, RoundedCornerShape(14.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(Icons.Filled.Info, contentDescription = null, tint = AmberSpark, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Note: Sending passwordless email links requires turning on 'Email/Password → Email link' in Firebase Console → Authentication → Sign-in method.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SignUpDialog(
    onDismiss: () -> Unit,
    onContinueGoogle: () -> Unit,
    onContinuePhone: () -> Unit,
    onContinueEmail: () -> Unit
) {
    val context = LocalContext.current
    val colors = PChatTheme.colors

    val existingDraft = remember(context) { PendingAuthPrefs.getSignUpDraft(context) }
    var fullName by remember { mutableStateOf(existingDraft?.fullName.orEmpty()) }
    var username by remember { mutableStateOf(existingDraft?.username.orEmpty()) }
    var customUsernameEdited by remember { mutableStateOf(existingDraft?.username?.isNotBlank() == true) }
    var email by remember { mutableStateOf(existingDraft?.email.orEmpty()) }
    var phone by remember { mutableStateOf(existingDraft?.phoneE164.orEmpty()) }
    var selectedCountry by remember {
        mutableStateOf(
            INTERNATIONAL_DIAL_COUNTRIES.firstOrNull {
                it.name.equals(existingDraft?.country, ignoreCase = true)
            } ?: INTERNATIONAL_DIAL_COUNTRIES.first()
        )
    }
    var preferredLanguage by remember {
        mutableStateOf(existingDraft?.preferredLanguage?.ifBlank { "English" } ?: "English")
    }
    var dateOfBirth by remember { mutableStateOf(existingDraft?.dateOfBirth.orEmpty()) }
    var avatarBase64 by remember { mutableStateOf(existingDraft?.avatarBase64.orEmpty()) }
    var showCountryPicker by remember { mutableStateOf(false) }
    var countrySearch by remember { mutableStateOf("") }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val bmp = MediaUtils.uriToBitmap(context, uri, 420)
            if (bmp != null) {
                avatarBase64 = MediaUtils.bitmapToBase64(bmp, 420, 72)
            } else {
                Toast.makeText(context, "Could not decode image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val avatarBmp = remember(avatarBase64) {
        if (avatarBase64.isNotBlank()) MediaUtils.base64ToBitmap(avatarBase64)?.asImageBitmap() else null
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = colors.surface,
            shape = RoundedCornerShape(26.dp),
            modifier = Modifier
                .fillMaxWidth(0.93f)
                .border(1.5.dp, PChatBrandGradient, RoundedCornerShape(26.dp))
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Person, contentDescription = null, tint = HotPink, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Create P-CHAT Account",
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = colors.textSecondary)
                    }
                }

                Text(
                    text = "Fill in your profile details below (or finish any missing fields right after authenticating) and choose a verification provider.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(58.dp)
                            .clip(CircleShape)
                            .background(colors.card)
                            .border(1.5.dp, PChatBrandGradient, CircleShape)
                            .clickable {
                                photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (avatarBmp != null) {
                            Image(
                                bitmap = avatarBmp,
                                contentDescription = "Chosen avatar",
                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(Icons.Filled.AddAPhoto, contentDescription = "Pick avatar", tint = NeonPurple)
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Profile Photo (Optional)", style = MaterialTheme.typography.labelLarge, color = colors.textPrimary)
                        Text(
                            text = if (avatarBmp != null) "Photo attached" else "Tap icon to choose from gallery",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary
                        )
                    }
                }

                OutlinedTextField(
                    value = fullName,
                    onValueChange = { next ->
                        fullName = next.take(60)
                        if (!customUsernameEdited) {
                            username = sanitizeUsernameHandle(next)
                        }
                    },
                    label = { Text("Full Name") },
                    placeholder = { Text("e.g. Sophia Kirabo") },
                    singleLine = true,
                    modifier = Modifier
                        .testTag("signup_name_input")
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = authTextFieldColors()
                )

                OutlinedTextField(
                    value = username,
                    onValueChange = { next ->
                        customUsernameEdited = true
                        username = sanitizeUsernameHandle(next)
                    },
                    label = { Text("Unique Username") },
                    prefix = { Text("@", color = colors.accentBlue, fontWeight = FontWeight.Bold) },
                    placeholder = { Text("sophia_pchat") },
                    singleLine = true,
                    modifier = Modifier
                        .testTag("signup_username_input")
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = authTextFieldColors()
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it.take(160) },
                    label = { Text("Email Address (Optional pre-fill)") },
                    placeholder = { Text("you@example.com") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = authTextFieldColors()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box {
                        Surface(
                            color = colors.card,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .height(56.dp)
                                .border(1.dp, colors.border, RoundedCornerShape(14.dp))
                                .clickable { showCountryPicker = true }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp)
                            ) {
                                Text("${selectedCountry.flag} ${selectedCountry.dialCode}", color = colors.textPrimary, fontWeight = FontWeight.Bold)
                            }
                        }
                        SearchableDialMenu(
                            expanded = showCountryPicker,
                            query = countrySearch,
                            onQueryChange = { countrySearch = it },
                            onSelect = { c ->
                                selectedCountry = c
                                showCountryPicker = false
                                countrySearch = ""
                            },
                            onDismiss = { showCountryPicker = false }
                        )
                    }
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it.filter { ch -> ch.isDigit() || ch == '+' || ch == ' ' || ch == '-' } },
                        label = { Text("Phone Number") },
                        placeholder = { Text("700 123 456") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = authTextFieldColors()
                    )
                }

                OutlinedTextField(
                    value = dateOfBirth,
                    onValueChange = { dateOfBirth = it.filter { ch -> ch.isDigit() || ch == '-' || ch == '/' }.take(10) },
                    label = { Text("Date of Birth (YYYY-MM-DD)") },
                    placeholder = { Text("2000-05-14") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = authTextFieldColors()
                )

                Text("Preferred Language", style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PREFERRED_LANGUAGES.take(6).forEach { lang ->
                        FilterChip(
                            selected = preferredLanguage == lang,
                            onClick = { preferredLanguage = lang },
                            label = { Text(lang) }
                        )
                    }
                }

                fun commitDraft(provider: AuthProviderType) {
                    val normalizedPhone = if (phone.isNotBlank()) normalizePhoneE164(selectedCountry.dialCode, phone) else ""
                    PendingAuthPrefs.saveFullSignUpDraft(
                        context,
                        SignUpDraft(
                            fullName = fullName,
                            username = username,
                            email = email,
                            phoneE164 = normalizedPhone,
                            country = selectedCountry.name,
                            countryFlag = selectedCountry.flag,
                            preferredLanguage = preferredLanguage,
                            dateOfBirth = dateOfBirth,
                            avatarBase64 = avatarBase64,
                            preferredProvider = provider
                        )
                    )
                }

                Button(
                    onClick = {
                        commitDraft(AuthProviderType.GOOGLE)
                        onDismiss()
                        onContinueGoogle()
                    },
                    modifier = Modifier
                        .testTag("signup_google_button")
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonPurple,
                        contentColor = colors.onGradientText
                    )
                ) {
                    Icon(Icons.Filled.VerifiedUser, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sign Up with Google (Instant)", fontWeight = FontWeight.Bold)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            commitDraft(AuthProviderType.PHONE)
                            onDismiss()
                            onContinuePhone()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Filled.PhoneAndroid, contentDescription = null, tint = colors.accentEmerald, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Phone", color = colors.textPrimary)
                    }
                    OutlinedButton(
                        onClick = {
                            commitDraft(AuthProviderType.EMAIL_LINK)
                            onDismiss()
                            onContinueEmail()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Filled.Email, contentDescription = null, tint = colors.accentBlue, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Email", color = colors.textPrimary)
                    }
                }
            }
        }
    }
}

@Composable
fun ReturningLoginDialog(
    onDismiss: () -> Unit,
    onContinueGoogle: () -> Unit,
    onContinuePhone: () -> Unit,
    onContinueEmail: () -> Unit,
    onOpenDiagnostics: () -> Unit = {}
) {
    val context = LocalContext.current
    val colors = PChatTheme.colors
    val lastMethod = remember { PendingAuthPrefs.getLastMethod(context) }
    var knownAccounts by remember { mutableStateOf(PendingAuthPrefs.getKnownAccounts(context)) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = colors.surface,
            shape = RoundedCornerShape(26.dp),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .border(1.5.dp, PChatBrandGradient, RoundedCornerShape(26.dp))
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Returning User & Account Switch",
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = colors.textSecondary)
                    }
                }

                Text(
                    text = "Welcome back! Last successful method: $lastMethod",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.accentBlue
                )

                if (knownAccounts.isNotEmpty()) {
                    Text(
                        text = "Saved Accounts on This Device",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textSecondary
                    )
                    knownAccounts.forEach { acc ->
                        Surface(
                            color = colors.card,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, NeonPurple.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                                .clickable {
                                    onDismiss()
                                    when (acc.provider) {
                                        AuthProviderType.GOOGLE -> onContinueGoogle()
                                        AuthProviderType.PHONE -> onContinuePhone()
                                        AuthProviderType.EMAIL_LINK -> onContinueEmail()
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${acc.displayName} (@${acc.username})",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = colors.textPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${acc.provider.label} • ${acc.maskedIdentifier}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.textSecondary
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        PendingAuthPrefs.removeKnownAccount(context, acc.uid)
                                        knownAccounts = PendingAuthPrefs.getKnownAccounts(context)
                                    }
                                ) {
                                    Icon(Icons.Filled.DeleteOutline, contentDescription = "Forget saved account", tint = colors.textMuted)
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = colors.border)
                }

                QuickMethodRow(
                    icon = Icons.Filled.AccountCircle,
                    title = "Google Account",
                    subtitle = "Ready now via Credential Manager",
                    statusReady = true,
                    accent = colors.accentEmerald,
                    onClick = {
                        onDismiss()
                        onContinueGoogle()
                    }
                )
                QuickMethodRow(
                    icon = Icons.Filled.PhoneAndroid,
                    title = "Phone Number (SMS OTP)",
                    subtitle = "Requires Phone provider enabled in Firebase Console",
                    statusReady = false,
                    accent = NeonPurple,
                    onClick = {
                        onDismiss()
                        onContinuePhone()
                    }
                )
                QuickMethodRow(
                    icon = Icons.Filled.Email,
                    title = "Email Link (Passwordless)",
                    subtitle = "Requires Email link enabled in Firebase Console",
                    statusReady = false,
                    accent = colors.accentBlue,
                    onClick = {
                        onDismiss()
                        onContinueEmail()
                    }
                )

                OutlinedButton(
                    onClick = {
                        onDismiss()
                        onOpenDiagnostics()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Filled.Shield, contentDescription = null, tint = colors.accentBlue, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Open Auth & Firebase Diagnostics", color = colors.textPrimary, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun SearchableDialMenu(
    expanded: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onSelect: (DialCountry) -> Unit,
    onDismiss: () -> Unit
) {
    val matches = remember(query) { AuthValidation.searchDialCountries(query) }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(min = 250.dp, max = 320.dp)
    ) {
        Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("Search country or +code") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        matches.take(16).forEach { country ->
            DropdownMenuItem(
                text = { Text("${country.flag} ${country.name} (${country.dialCode})") },
                onClick = { onSelect(country) }
            )
        }
    }
}

@Composable
private fun QuickMethodRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    statusReady: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    val colors = PChatTheme.colors
    Surface(
        color = colors.card,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, colors.border, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accent)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleSmall, color = colors.textPrimary, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = if (statusReady) Icons.Filled.CheckCircle else Icons.Filled.WarningAmber,
                        contentDescription = null,
                        tint = if (statusReady) colors.accentEmerald else AmberSpark,
                        modifier = Modifier.size(15.dp)
                    )
                }
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = colors.textSecondary)
        }
    }
}

@Composable
private fun AuthErrorCard(
    message: String,
    showGoogleSwitch: Boolean,
    onSwitchToGoogle: () -> Unit
) {
    val colors = PChatTheme.colors
    Surface(
        color = ErrorCrimson.copy(alpha = 0.14f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, ErrorCrimson.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(message, style = MaterialTheme.typography.bodySmall, color = ErrorCrimson)
            if (showGoogleSwitch) {
                Button(
                    onClick = onSwitchToGoogle,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accentBlue,
                        contentColor = colors.primaryButtonContent
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Continue with Google Account Instead", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun authTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = PChatTheme.colors.card,
    unfocusedContainerColor = PChatTheme.colors.card,
    focusedBorderColor = NeonPurple,
    unfocusedBorderColor = PChatTheme.colors.border,
    focusedTextColor = PChatTheme.colors.textPrimary,
    unfocusedTextColor = PChatTheme.colors.textPrimary
)
