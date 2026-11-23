package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.auth.AuthValidation
import com.example.auth.DialCountry
import com.example.auth.INTERNATIONAL_DIAL_COUNTRIES
import com.example.auth.PREFERRED_LANGUAGES
import com.example.auth.PendingAuthPrefs
import com.example.auth.inferProviderTypeFromFirebaseUser
import com.example.model.SignUpDraft
import com.example.model.UiState
import com.example.ui.components.PChatBrandEmblem
import com.example.ui.theme.AmberSpark
import com.example.ui.theme.ErrorCrimson
import com.example.ui.theme.HotPink
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.PChatBrandGradient
import com.example.ui.theme.PChatTheme
import com.example.ui.viewmodel.PChatViewModel
import com.example.ui.viewmodel.UsernameAvailabilityState
import com.example.util.MediaUtils
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(
    viewModel: PChatViewModel,
    onSignedOut: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val colors = PChatTheme.colors

    val profileState by viewModel.myProfile.collectAsStateWithLifecycle()
    val settingsState by viewModel.privateSettings.collectAsStateWithLifecycle()
    val usernameAvailability by viewModel.usernameAvailability.collectAsStateWithLifecycle()
    val isSaving by viewModel.isOnboardingSaving.collectAsStateWithLifecycle()

    val existingProfile = (profileState as? UiState.Success)?.data
    val existingSettings = (settingsState as? UiState.Success)?.data
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()
    val firebaseUser = remember(statusBanner, existingSettings?.emailVerified) { Firebase.auth.currentUser }
    val savedDraft = remember(context) { PendingAuthPrefs.getSignUpDraft(context) }

    var fullName by remember(existingProfile?.userId) {
        mutableStateOf(
            savedDraft?.fullName?.ifBlank { null }
                ?: existingProfile?.fullName?.ifBlank { null }
                ?: existingProfile?.displayName?.ifBlank { null }
                ?: firebaseUser?.displayName.orEmpty()
        )
    }
    var username by remember(existingProfile?.userId) {
        mutableStateOf(
            savedDraft?.username?.ifBlank { null }
                ?: existingProfile?.username?.ifBlank { null }
                ?: AuthValidation.sanitizeUsernameHandle(fullName)
        )
    }
    var email by remember(existingSettings?.userId) {
        mutableStateOf(
            savedDraft?.email?.ifBlank { null }
                ?: existingSettings?.email?.ifBlank { null }
                ?: firebaseUser?.email.orEmpty()
        )
    }

    val initialCountry = remember(existingProfile?.country, savedDraft?.country) {
        val candidateName = savedDraft?.country?.takeIf { it.isNotBlank() && it != "Global" }
            ?: existingProfile?.country?.takeIf { it.isNotBlank() && it != "Global" }
            ?: "United States"
        INTERNATIONAL_DIAL_COUNTRIES.firstOrNull { it.name.equals(candidateName, ignoreCase = true) }
            ?: INTERNATIONAL_DIAL_COUNTRIES.first()
    }

    var selectedDialCountry by remember(initialCountry) { mutableStateOf(initialCountry) }
    var selectedCountry by remember(initialCountry) { mutableStateOf(initialCountry) }
    var localPhoneInput by remember(existingSettings?.phoneNumber) {
        val rawPhone = savedDraft?.phoneE164?.ifBlank { null }
            ?: existingSettings?.phoneNumber?.ifBlank { null }
            ?: firebaseUser?.phoneNumber.orEmpty()
        mutableStateOf(rawPhone)
    }
    var preferredLanguage by remember(existingProfile?.preferredLanguage) {
        mutableStateOf(
            savedDraft?.preferredLanguage?.ifBlank { null }
                ?: existingProfile?.preferredLanguage?.ifBlank { null }
                ?: "English"
        )
    }
    var dateOfBirth by remember(existingSettings?.dateOfBirth) {
        mutableStateOf(
            savedDraft?.dateOfBirth?.ifBlank { null }
                ?: existingSettings?.dateOfBirth.orEmpty()
        )
    }
    var avatarBase64 by remember(existingProfile?.photoBase64) {
        mutableStateOf(
            savedDraft?.avatarBase64?.ifBlank { null }
                ?: existingProfile?.photoBase64.orEmpty()
        )
    }

    var showDialPicker by remember { mutableStateOf(false) }
    var showCountryPicker by remember { mutableStateOf(false) }
    var dialSearch by remember { mutableStateOf("") }
    var countrySearch by remember { mutableStateOf("") }
    var showDiagnostics by remember { mutableStateOf(false) }

    val avatarPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val bmp = MediaUtils.uriToBitmap(context, uri, 420)
            if (bmp != null) {
                avatarBase64 = MediaUtils.bitmapToBase64(bmp, 420, 72)
            } else {
                Toast.makeText(context, "Could not decode selected photo", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Debounced unique username check against Firestore /usernames/{handle}
    LaunchedEffect(username) {
        delay(300L)
        if (username.isNotBlank()) {
            viewModel.checkUsernameAvailability(username)
        }
    }

    val fullNameCheck = AuthValidation.validateFullName(fullName)
    val usernameCheck = AuthValidation.validateUsername(username)
    val emailCheck = AuthValidation.validateEmail(email)
    val normalizedPhone = AuthValidation.normalizePhoneE164(selectedDialCountry.dialCode, localPhoneInput)
    val phoneCheck = AuthValidation.validatePhoneE164(normalizedPhone)
    val dobCheck = AuthValidation.validateDateOfBirth(dateOfBirth)

    val usernameReady = usernameCheck.isValid &&
        usernameAvailability !is UsernameAvailabilityState.Taken &&
        usernameAvailability !is UsernameAvailabilityState.Invalid &&
        usernameAvailability !is UsernameAvailabilityState.Checking

    val canComplete = !isSaving &&
        fullNameCheck.isValid &&
        usernameReady &&
        emailCheck.isValid &&
        phoneCheck.isValid &&
        dobCheck.isValid

    val avatarBitmap = remember(avatarBase64) {
        if (avatarBase64.isNotBlank()) MediaUtils.base64ToBitmap(avatarBase64)?.asImageBitmap() else null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier.widthIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PChatBrandEmblem(size = 40.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Complete Your Profile",
                                style = MaterialTheme.typography.titleLarge,
                                color = colors.textPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "1-step P-CHAT account setup",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.accentBlue
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { showDiagnostics = true }) {
                            Text("Diagnostics", color = colors.accentBlue)
                        }
                        TextButton(
                            onClick = {
                                viewModel.signOutSecurely(context, credentialManager, scope, onSignedOut)
                            }
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = ErrorCrimson, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Sign Out", color = ErrorCrimson)
                        }
                    }
                }

                // 1. Profile Picture Picker (Android Photo Picker — zero permissions)
                Surface(
                    color = colors.card,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, colors.border, RoundedCornerShape(20.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .testTag("onboarding_avatar_picker")
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(colors.elevated)
                                .border(2.dp, PChatBrandGradient, CircleShape)
                                .clickable {
                                    avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (avatarBitmap != null) {
                                Image(
                                    bitmap = avatarBitmap,
                                    contentDescription = "Selected profile picture",
                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(Icons.Filled.AddAPhoto, contentDescription = "Pick avatar photo", tint = NeonPurple, modifier = Modifier.size(28.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Profile Picture", style = MaterialTheme.typography.titleSmall, color = colors.textPrimary, fontWeight = FontWeight.Bold)
                            Text(
                                text = if (avatarBitmap != null) "Photo ready for P-CHAT & Connect" else "Optional: choose a photo from your gallery",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedButton(
                                onClick = {
                                    avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, NeonPurple)
                            ) {
                                Text(if (avatarBitmap != null) "Change Photo" else "Choose Photo", color = colors.textPrimary)
                            }
                        }
                    }
                }

                // 2. Full Name & 3. Unique Username
                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it.take(60) },
                    label = { Text("Full Name *") },
                    placeholder = { Text("e.g. Sophia Kirabo") },
                    isError = fullName.isNotEmpty() && !fullNameCheck.isValid,
                    supportingText = if (fullName.isNotEmpty() && !fullNameCheck.isValid) {
                        { Text(fullNameCheck.errorMessage.orEmpty(), color = ErrorCrimson) }
                    } else null,
                    singleLine = true,
                    modifier = Modifier
                        .testTag("onboarding_fullname_input")
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = onboardingTextFieldColors()
                )

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = AuthValidation.sanitizeUsernameHandle(it) },
                    label = { Text("Unique Username *") },
                    prefix = { Text("@", color = colors.accentBlue, fontWeight = FontWeight.Bold) },
                    placeholder = { Text("sophia_pchat") },
                    singleLine = true,
                    trailingIcon = {
                        when (usernameAvailability) {
                            is UsernameAvailabilityState.Checking ->
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = NeonPurple)
                            is UsernameAvailabilityState.Available ->
                                Icon(Icons.Filled.CheckCircle, contentDescription = "Available", tint = colors.accentEmerald)
                            is UsernameAvailabilityState.Taken, is UsernameAvailabilityState.Invalid ->
                                Icon(Icons.Filled.ErrorOutline, contentDescription = "Unavailable", tint = ErrorCrimson)
                            UsernameAvailabilityState.Idle -> Unit
                        }
                    },
                    supportingText = {
                        when (val st = usernameAvailability) {
                            is UsernameAvailabilityState.Checking ->
                                Text("Checking @${st.handle} availability...", color = colors.textSecondary)
                            is UsernameAvailabilityState.Available ->
                                Text("@${st.handle} is available", color = colors.accentEmerald)
                            is UsernameAvailabilityState.Taken ->
                                Text("@${st.handle} is already taken. Choose another handle.", color = ErrorCrimson)
                            is UsernameAvailabilityState.Invalid ->
                                Text(st.reason, color = ErrorCrimson)
                            UsernameAvailabilityState.Idle ->
                                if (!usernameCheck.isValid && username.isNotEmpty()) {
                                    Text(usernameCheck.errorMessage.orEmpty(), color = ErrorCrimson)
                                }
                        }
                    },
                    modifier = Modifier
                        .testTag("onboarding_username_input")
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = onboardingTextFieldColors()
                )

                // 4. Email & Verification Status
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it.take(160) },
                    label = { Text("Email Address *") },
                    placeholder = { Text("you@example.com") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    isError = email.isNotEmpty() && !emailCheck.isValid,
                    supportingText = if (email.isNotEmpty() && !emailCheck.isValid) {
                        { Text(emailCheck.errorMessage.orEmpty(), color = ErrorCrimson) }
                    } else null,
                    modifier = Modifier
                        .testTag("onboarding_email_input")
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = onboardingTextFieldColors()
                )

                if (firebaseUser?.email != null && !firebaseUser.isEmailVerified) {
                    Surface(
                        color = AmberSpark.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, AmberSpark.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.MarkEmailRead, contentDescription = null, tint = AmberSpark, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Email not verified yet", style = MaterialTheme.typography.labelMedium, color = colors.textPrimary, fontWeight = FontWeight.Bold)
                                Text("Send a verification link or tap Refresh after verifying.", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                            }
                            TextButton(onClick = { viewModel.sendVerificationEmail() }) {
                                Text("Verify", color = colors.accentBlue)
                            }
                            TextButton(onClick = { viewModel.refreshEmailVerification() }) {
                                Icon(Icons.Filled.Refresh, contentDescription = "Refresh verification", tint = colors.accentEmerald, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                // 5. Phone Number + International Code
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box {
                        Surface(
                            color = colors.card,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .height(56.dp)
                                .border(1.dp, colors.border, RoundedCornerShape(14.dp))
                                .clickable { showDialPicker = true }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            ) {
                                Text("${selectedDialCountry.flag} ${selectedDialCountry.dialCode}", color = colors.textPrimary, fontWeight = FontWeight.Bold)
                            }
                        }
                        SearchableDialDropdown(
                            expanded = showDialPicker,
                            searchQuery = dialSearch,
                            onSearchChange = { dialSearch = it },
                            onSelect = { item ->
                                selectedDialCountry = item
                                showDialPicker = false
                            },
                            onDismiss = { showDialPicker = false }
                        )
                    }

                    OutlinedTextField(
                        value = localPhoneInput,
                        onValueChange = { localPhoneInput = it.filter { ch -> ch.isDigit() || ch == '+' || ch == ' ' || ch == '-' } },
                        label = { Text("Phone Number *") },
                        placeholder = { Text("700 123 456") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        isError = localPhoneInput.isNotEmpty() && !phoneCheck.isValid,
                        supportingText = if (localPhoneInput.isNotEmpty() && !phoneCheck.isValid) {
                            { Text(phoneCheck.errorMessage.orEmpty(), color = ErrorCrimson) }
                        } else null,
                        modifier = Modifier
                            .testTag("onboarding_phone_input")
                            .weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = onboardingTextFieldColors()
                    )
                }

                // 6. Country
                Box {
                    Surface(
                        color = colors.card,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .testTag("onboarding_country_picker")
                            .fillMaxWidth()
                            .border(1.dp, colors.border, RoundedCornerShape(14.dp))
                            .clickable { showCountryPicker = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 15.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Public, contentDescription = null, tint = colors.accentBlue, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Country *", style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
                                Text(
                                    text = "${selectedCountry.flag} ${selectedCountry.name}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = colors.textPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Text("Change", color = colors.accentBlue, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    SearchableDialDropdown(
                        expanded = showCountryPicker,
                        searchQuery = countrySearch,
                        onSearchChange = { countrySearch = it },
                        onSelect = { item ->
                            selectedCountry = item
                            selectedDialCountry = item
                            showCountryPicker = false
                        },
                        onDismiss = { showCountryPicker = false }
                    )
                }

                // 7. Preferred Language
                Text("Preferred Language *", style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PREFERRED_LANGUAGES.forEach { lang ->
                        val selected = preferredLanguage == lang
                        FilterChip(
                            selected = selected,
                            onClick = { preferredLanguage = lang },
                            label = { Text(lang) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NeonPurple,
                                selectedLabelColor = colors.onGradientText
                            )
                        )
                    }
                }

                // 8. Date of Birth
                OutlinedTextField(
                    value = dateOfBirth,
                    onValueChange = { dateOfBirth = it.filter { ch -> ch.isDigit() || ch == '-' || ch == '/' }.take(10) },
                    label = { Text("Date of Birth (YYYY-MM-DD) *") },
                    placeholder = { Text("1999-05-14") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = dateOfBirth.isNotEmpty() && !dobCheck.isValid,
                    supportingText = if (dateOfBirth.isNotEmpty() && !dobCheck.isValid) {
                        { Text(dobCheck.errorMessage.orEmpty(), color = ErrorCrimson) }
                    } else null,
                    modifier = Modifier
                        .testTag("onboarding_dob_input")
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = onboardingTextFieldColors()
                )

                Button(
                    onClick = {
                        viewModel.completeOnboarding(
                            SignUpDraft(
                                fullName = fullNameCheck.normalizedValue,
                                username = usernameCheck.normalizedValue,
                                email = emailCheck.normalizedValue,
                                phoneE164 = phoneCheck.normalizedValue,
                                country = selectedCountry.name,
                                countryFlag = selectedCountry.flag,
                                preferredLanguage = preferredLanguage,
                                dateOfBirth = dobCheck.normalizedValue,
                                avatarBase64 = avatarBase64,
                                preferredProvider = inferProviderTypeFromFirebaseUser()
                            )
                        )
                    },
                    enabled = canComplete,
                    modifier = Modifier
                        .testTag("onboarding_complete_button")
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonPurple,
                        contentColor = colors.onGradientText
                    )
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), color = colors.onGradientText, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.VerifiedUser, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Complete Registration & Enter P-CHAT", fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))
            }
        }
    }

    if (showDiagnostics) {
        AuthDiagnosticsDialog(
            viewModel = viewModel,
            onDismiss = { showDiagnostics = false }
        )
    }
}

@Composable
private fun SearchableDialDropdown(
    expanded: Boolean,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onSelect: (DialCountry) -> Unit,
    onDismiss: () -> Unit
) {
    val matches = remember(searchQuery) { AuthValidation.searchDialCountries(searchQuery) }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(min = 260.dp, max = 330.dp)
    ) {
        Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                placeholder = { Text("Search country or +code") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        matches.take(18).forEach { country ->
            DropdownMenuItem(
                text = { Text("${country.flag} ${country.name} (${country.dialCode})") },
                onClick = { onSelect(country) }
            )
        }
    }
}

@Composable
private fun onboardingTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = PChatTheme.colors.card,
    unfocusedContainerColor = PChatTheme.colors.card,
    focusedBorderColor = NeonPurple,
    unfocusedBorderColor = PChatTheme.colors.border,
    focusedTextColor = PChatTheme.colors.textPrimary,
    unfocusedTextColor = PChatTheme.colors.textPrimary
)
