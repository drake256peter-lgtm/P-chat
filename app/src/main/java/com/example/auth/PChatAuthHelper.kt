package com.example.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.credentials.CredentialManager
import com.example.model.AuthProviderType
import com.example.model.KnownAccountSummary
import com.example.model.SignUpDraft
import com.google.firebase.Firebase
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.ActionCodeSettings
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

private const val AUTH_TAG = "PChatAuth"
private const val PREFS_NAME = "pchat_auth_prefs"
private const val KEY_PENDING_EMAIL = "pending_email"
private const val KEY_PENDING_DISPLAY_NAME = "pending_display_name"
private const val KEY_PENDING_USERNAME = "pending_username"
private const val KEY_SIGNUP_DRAFT_JSON = "signup_draft_json"
private const val KEY_LAST_METHOD = "last_auth_method"
private const val KEY_LAST_PROVIDER_ID = "last_provider_id"
private const val KEY_KNOWN_ACCOUNTS = "known_accounts_json"
private const val KEY_DEVICE_ID = "device_installation_id"
private const val KEY_ACTIVE_SESSION_ID = "active_session_id"
private const val KEY_EMAIL_LINK_SENT_AT = "email_link_sent_at"

tailrec fun Context.findAuthActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findAuthActivity()
    else -> null
}

object PendingAuthPrefs {
    private val otpTracker = OtpSecurityTracker()
    private val emailTracker = EmailLinkStateTracker()
    @Volatile
    private var suppressAutoSignInOnce: Boolean = false

    fun setSuppressAutoSignInOnce(suppress: Boolean = true) {
        suppressAutoSignInOnce = suppress
    }

    fun consumeSuppressAutoSignInOnce(): Boolean {
        val current = suppressAutoSignInOnce
        suppressAutoSignInOnce = false
        return current
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun savePendingEmail(context: Context, email: String) {
        val now = System.currentTimeMillis()
        emailTracker.markSent(now)
        prefs(context).edit()
            .putString(KEY_PENDING_EMAIL, email.trim())
            .putLong(KEY_EMAIL_LINK_SENT_AT, now)
            .apply()
    }

    fun getPendingEmail(context: Context): String? =
        prefs(context).getString(KEY_PENDING_EMAIL, null)?.takeIf { it.isNotBlank() }

    fun getEmailLinkResendRemainingSec(context: Context): Int {
        val persistedSentAt = prefs(context).getLong(KEY_EMAIL_LINK_SENT_AT, 0L)
        if (persistedSentAt > 0L && emailTracker.sentAtMs == 0L) {
            emailTracker.markSent(persistedSentAt)
        }
        return emailTracker.remainingCooldownSeconds(System.currentTimeMillis())
    }

    fun isPendingEmailLinkExpired(context: Context): Boolean {
        val persistedSentAt = prefs(context).getLong(KEY_EMAIL_LINK_SENT_AT, 0L)
        if (persistedSentAt <= 0L) return false
        return (System.currentTimeMillis() - persistedSentAt) > emailTracker.linkExpiryMs
    }

    fun clearPendingEmail(context: Context) {
        prefs(context).edit()
            .remove(KEY_PENDING_EMAIL)
            .remove(KEY_EMAIL_LINK_SENT_AT)
            .apply()
    }

    fun saveSignUpDraft(context: Context, displayName: String, username: String) {
        val current = getSignUpDraft(context) ?: SignUpDraft()
        saveFullSignUpDraft(
            context,
            current.copy(
                fullName = displayName.trim().take(60),
                username = AuthValidation.sanitizeUsernameHandle(username)
            )
        )
    }

    fun saveFullSignUpDraft(context: Context, draft: SignUpDraft) {
        val json = JSONObject().apply {
            put("fullName", draft.fullName.trim().take(60))
            put("username", AuthValidation.sanitizeUsernameHandle(draft.username))
            put("email", draft.email.trim().take(160))
            put("phoneE164", draft.phoneE164.trim().take(20))
            put("country", draft.country.trim().take(60))
            put("countryFlag", draft.countryFlag.trim().take(16))
            put("preferredLanguage", draft.preferredLanguage.trim().take(40))
            put("dateOfBirth", draft.dateOfBirth.trim().take(16))
            put("avatarBase64", draft.avatarBase64.take(180000))
            put("providerId", draft.preferredProvider.id)
        }
        prefs(context).edit()
            .putString(KEY_PENDING_DISPLAY_NAME, draft.fullName.trim().take(60))
            .putString(KEY_PENDING_USERNAME, AuthValidation.sanitizeUsernameHandle(draft.username))
            .putString(KEY_SIGNUP_DRAFT_JSON, json.toString())
            .apply()
    }

    fun getSignUpDraft(context: Context): SignUpDraft? {
        val raw = prefs(context).getString(KEY_SIGNUP_DRAFT_JSON, null)
        if (raw.isNullOrBlank()) {
            val name = getPendingDisplayName(context) ?: return null
            val handle = prefs(context).getString(KEY_PENDING_USERNAME, "") ?: ""
            return SignUpDraft(fullName = name, username = handle)
        }
        return runCatching {
            val obj = JSONObject(raw)
            SignUpDraft(
                fullName = obj.optString("fullName"),
                username = obj.optString("username"),
                email = obj.optString("email"),
                phoneE164 = obj.optString("phoneE164"),
                country = obj.optString("country", "United States").ifBlank { "United States" },
                countryFlag = obj.optString("countryFlag", "🇺🇸").ifBlank { "🇺🇸" },
                preferredLanguage = obj.optString("preferredLanguage", "English").ifBlank { "English" },
                dateOfBirth = obj.optString("dateOfBirth"),
                avatarBase64 = obj.optString("avatarBase64"),
                preferredProvider = AuthProviderType.fromId(obj.optString("providerId"))
            )
        }.getOrNull()
    }

    fun clearSignUpDraft(context: Context) {
        prefs(context).edit()
            .remove(KEY_SIGNUP_DRAFT_JSON)
            .remove(KEY_PENDING_DISPLAY_NAME)
            .remove(KEY_PENDING_USERNAME)
            .apply()
    }

    fun getPendingDisplayName(context: Context): String? =
        prefs(context).getString(KEY_PENDING_DISPLAY_NAME, null)?.takeIf { it.isNotBlank() }

    fun consumePendingUsername(context: Context): String? {
        val raw = prefs(context).getString(KEY_PENDING_USERNAME, null)?.takeIf { it.isNotBlank() }
        if (raw != null) {
            prefs(context).edit().remove(KEY_PENDING_USERNAME).apply()
        }
        return raw
    }

    fun clearSignUpDisplayName(context: Context) {
        prefs(context).edit().remove(KEY_PENDING_DISPLAY_NAME).apply()
    }

    fun saveLastMethod(context: Context, method: String, providerType: AuthProviderType = AuthProviderType.GOOGLE) {
        prefs(context).edit()
            .putString(KEY_LAST_METHOD, method)
            .putString(KEY_LAST_PROVIDER_ID, providerType.id)
            .apply()
    }

    fun getLastMethod(context: Context): String =
        prefs(context).getString(KEY_LAST_METHOD, "Google Account") ?: "Google Account"

    fun getLastProviderType(context: Context): AuthProviderType =
        AuthProviderType.fromId(prefs(context).getString(KEY_LAST_PROVIDER_ID, AuthProviderType.GOOGLE.id))

    fun rememberAccountSummary(context: Context, summary: KnownAccountSummary) {
        val list = getKnownAccounts(context).toMutableList()
        list.removeAll { it.uid == summary.uid }
        list.add(0, summary)
        val trimmed = list.take(5)
        val arr = JSONArray()
        trimmed.forEach { item ->
            arr.put(
                JSONObject().apply {
                    put("uid", item.uid)
                    put("displayName", item.displayName.take(60))
                    put("username", item.username.take(30))
                    put("maskedIdentifier", item.maskedIdentifier.take(80))
                    put("provider", item.provider.id)
                }
            )
        }
        prefs(context).edit().putString(KEY_KNOWN_ACCOUNTS, arr.toString()).apply()
    }

    fun getKnownAccounts(context: Context): List<KnownAccountSummary> {
        val raw = prefs(context).getString(KEY_KNOWN_ACCOUNTS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val uid = obj.optString("uid")
                    if (uid.isNotBlank()) {
                        add(
                            KnownAccountSummary(
                                uid = uid,
                                displayName = obj.optString("displayName").ifBlank { "P-CHAT User" },
                                username = obj.optString("username"),
                                maskedIdentifier = obj.optString("maskedIdentifier"),
                                provider = AuthProviderType.fromId(obj.optString("provider"))
                            )
                        )
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    fun removeKnownAccount(context: Context, uid: String) {
        val remaining = getKnownAccounts(context).filterNot { it.uid == uid }
        val arr = JSONArray()
        remaining.forEach { item ->
            arr.put(
                JSONObject().apply {
                    put("uid", item.uid)
                    put("displayName", item.displayName)
                    put("username", item.username)
                    put("maskedIdentifier", item.maskedIdentifier)
                    put("provider", item.provider.id)
                }
            )
        }
        prefs(context).edit().putString(KEY_KNOWN_ACCOUNTS, arr.toString()).apply()
    }

    fun getOrCreateDeviceId(context: Context): String {
        val existing = prefs(context).getString(KEY_DEVICE_ID, null)
        if (!existing.isNullOrBlank()) return existing
        val created = "dev_${UUID.randomUUID().toString().replace("-", "").take(20)}"
        prefs(context).edit().putString(KEY_DEVICE_ID, created).apply()
        return created
    }

    fun startNewSessionId(context: Context): String {
        val sessionId = "sess_${UUID.randomUUID().toString().replace("-", "").take(22)}"
        prefs(context).edit().putString(KEY_ACTIVE_SESSION_ID, sessionId).apply()
        return sessionId
    }

    fun getActiveSessionId(context: Context): String {
        val existing = prefs(context).getString(KEY_ACTIVE_SESSION_ID, null)
        return if (!existing.isNullOrBlank()) existing else startNewSessionId(context)
    }

    fun clearActiveSessionId(context: Context) {
        prefs(context).edit().remove(KEY_ACTIVE_SESSION_ID).apply()
    }

    fun otpSecurityTracker(): OtpSecurityTracker = otpTracker
}

fun sanitizeUsernameHandle(raw: String): String = AuthValidation.sanitizeUsernameHandle(raw)

fun isValidEmailAddress(email: String): Boolean = AuthValidation.isValidEmailAddress(email)

fun normalizePhoneE164(countryCode: String, localNumber: String): String =
    AuthValidation.normalizePhoneE164(countryCode, localNumber)

fun detectCurrentDeviceModel(): String {
    val man = Build.MANUFACTURER.orEmpty().trim()
    val model = Build.MODEL.orEmpty().trim()
    return "$man $model".trim().ifBlank { "Android Device" }.take(60)
}

fun inferProviderTypeFromFirebaseUser(): AuthProviderType {
    val user = Firebase.auth.currentUser ?: return AuthProviderType.GOOGLE
    val providerIds = user.providerData.map { it.providerId }
    return when {
        "google.com" in providerIds -> AuthProviderType.GOOGLE
        "phone" in providerIds -> AuthProviderType.PHONE
        "password" in providerIds || "emailLink" in providerIds -> AuthProviderType.EMAIL_LINK
        !user.phoneNumber.isNullOrBlank() && user.email.isNullOrBlank() -> AuthProviderType.PHONE
        else -> AuthProviderType.GOOGLE
    }
}

suspend fun applyPendingDisplayNameIfSet(context: Context) {
    val user = Firebase.auth.currentUser ?: return
    val pendingName = PendingAuthPrefs.getPendingDisplayName(context) ?: return
    try {
        val req = UserProfileChangeRequest.Builder()
            .setDisplayName(pendingName)
            .build()
        user.updateProfile(req).await()
        PendingAuthPrefs.clearSignUpDisplayName(context)
        Log.d(AUTH_TAG, "Applied pending sign-up displayName for uid=${user.uid}")
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(AUTH_TAG, "Could not update Firebase profile displayName: ${e.message}")
    }
}

suspend fun sendFirebaseVerificationEmail(): Result<Unit> {
    val user = Firebase.auth.currentUser
        ?: return Result.failure(IllegalStateException("Sign in first to verify your email."))
    return try {
        user.sendEmailVerification().await()
        AuthDiagnosticsManager.record(
            category = "verification_email_sent",
            severity = "info",
            provider = AuthProviderType.EMAIL_LINK.id,
            outcome = "success",
            summary = "Sent Firebase email verification for uid=${user.uid}",
            statusCode = "SENT"
        )
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        AuthDiagnosticsManager.record(
            category = "verification_email_failed",
            severity = "warn",
            provider = AuthProviderType.EMAIL_LINK.id,
            outcome = "failure",
            summary = e.localizedMessage ?: "Failed to send verification email",
            statusCode = (e as? FirebaseAuthException)?.errorCode ?: "ERROR"
        )
        Result.failure(e)
    }
}

suspend fun refreshCurrentUserVerificationStatus(): Result<Boolean> {
    val user = Firebase.auth.currentUser
        ?: return Result.failure(IllegalStateException("No authenticated session."))
    return try {
        user.reload().await()
        val verified = user.isEmailVerified
        AuthDiagnosticsManager.record(
            category = "verification_checked",
            severity = "info",
            provider = inferProviderTypeFromFirebaseUser().id,
            outcome = "success",
            summary = "Reloaded verification status: emailVerified=$verified",
            statusCode = if (verified) "VERIFIED" else "UNVERIFIED"
        )
        Result.success(verified)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
}

fun startPhoneNumberVerification(
    context: Context,
    phoneE164: String,
    scope: CoroutineScope,
    resendToken: PhoneAuthProvider.ForceResendingToken? = null,
    onCodeSent: (verificationId: String, token: PhoneAuthProvider.ForceResendingToken?) -> Unit,
    onAutoVerified: () -> Unit,
    onError: (message: String, needsConsoleSetup: Boolean) -> Unit
) {
    val tracker = PendingAuthPrefs.otpSecurityTracker()
    val now = System.currentTimeMillis()
    if (tracker.isLockedOut(now)) {
        val rem = tracker.remainingLockoutSeconds(now)
        val msg = "Too many incorrect OTP attempts. Try again in ${rem}s."
        AuthDiagnosticsManager.record("otp_lockout", "warn", AuthProviderType.PHONE.id, "failure", msg, "LOCKOUT")
        onError(msg, false)
        return
    }

    val activity = context.findAuthActivity()
    if (activity == null) {
        onError("Phone verification requires an active screen window.", false)
        return
    }
    val validation = AuthValidation.validatePhoneE164(phoneE164)
    if (!validation.isValid) {
        onError(validation.errorMessage ?: "Enter a valid phone number including country code.", false)
        return
    }
    val cleanPhone = validation.normalizedValue
    AuthDiagnosticsManager.record(
        category = "auth_attempt",
        severity = "info",
        provider = AuthProviderType.PHONE.id,
        outcome = "pending",
        summary = "Requesting SMS OTP for ${AuthLogRedactor.maskPhone(cleanPhone)}",
        statusCode = "REQUEST"
    )

    val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
        override fun onVerificationCompleted(credential: PhoneAuthCredential) {
            scope.launch {
                try {
                    val res = Firebase.auth.signInWithCredential(credential).await()
                    val isNew = res.additionalUserInfo?.isNewUser == true
                    applyPendingDisplayNameIfSet(context)
                    PendingAuthPrefs.saveLastMethod(context, AuthProviderType.PHONE.label, AuthProviderType.PHONE)
                    tracker.reset()
                    AuthDiagnosticsManager.record(
                        category = if (isNew) "registration" else "login_success",
                        severity = "info",
                        provider = AuthProviderType.PHONE.id,
                        outcome = "success",
                        summary = "Phone auto-verification completed for uid=${res.user?.uid}",
                        statusCode = "OK"
                    )
                    onAutoVerified()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val code = (e as? FirebaseAuthException)?.errorCode ?: "AUTO_VERIFY_ERR"
                    AuthDiagnosticsManager.record("login_failed", "warn", AuthProviderType.PHONE.id, "failure", e.localizedMessage ?: "Failed", code)
                    onError(e.localizedMessage ?: "Failed to complete phone sign-in.", false)
                }
            }
        }

        override fun onVerificationFailed(e: FirebaseException) {
            val code = (e as? FirebaseAuthException)?.errorCode.orEmpty()
            val msg = e.localizedMessage.orEmpty()
            Log.w(AUTH_TAG, "Phone verification failed code=$code")
            val consoleNeeded = code == "ERROR_OPERATION_NOT_ALLOWED" ||
                code == "ERROR_APP_NOT_AUTHORIZED" ||
                msg.contains("not enabled", ignoreCase = true) ||
                msg.contains("BILLING_NOT_ENABLED", ignoreCase = true) ||
                msg.contains("reCAPTCHA", ignoreCase = true) ||
                msg.contains("Play Integrity", ignoreCase = true)

            val friendly = when {
                e is FirebaseNetworkException ->
                    "No internet connection. Check your network and try again."
                code == "ERROR_OPERATION_NOT_ALLOWED" || msg.contains("not enabled", ignoreCase = true) ->
                    "Phone SMS sign-in is not yet enabled in Firebase Console (Authentication → Sign-in method → Phone). Enable Phone Auth there or use Continue with Google now."
                msg.contains("BILLING_NOT_ENABLED", ignoreCase = true) ->
                    "Firebase SMS delivery requires Firebase Blaze plan quota or adding test phone numbers in Firebase Console."
                code == "ERROR_INVALID_PHONE_NUMBER" ->
                    "Invalid phone number format. Include country dial code (e.g. +1 or +256)."
                code == "ERROR_TOO_MANY_REQUESTS" || code == "ERROR_QUOTA_EXCEEDED" ->
                    "Too many SMS attempts or project SMS quota reached. Please wait and try again."
                consoleNeeded ->
                    "App verification (Play Integrity / SHA-256 / reCAPTCHA) or Phone Auth toggle needs setup in Firebase Console. Use Google Sign-In in the meantime."
                else -> e.localizedMessage ?: "Phone verification could not start."
            }
            AuthDiagnosticsManager.record(
                category = if (consoleNeeded) "config_error" else "login_failed",
                severity = "warn",
                provider = AuthProviderType.PHONE.id,
                outcome = "failure",
                summary = friendly,
                statusCode = code.ifBlank { "PHONE_FAIL" }
            )
            onError(friendly, consoleNeeded)
        }

        override fun onCodeSent(
            verificationId: String,
            token: PhoneAuthProvider.ForceResendingToken
        ) {
            tracker.markCodeDispatched(System.currentTimeMillis())
            AuthDiagnosticsManager.record(
                category = "otp_dispatched",
                severity = "info",
                provider = AuthProviderType.PHONE.id,
                outcome = "pending",
                summary = "SMS OTP sent to ${AuthLogRedactor.maskPhone(cleanPhone)}",
                statusCode = "SENT"
            )
            onCodeSent(verificationId, token)
        }
    }

    val builder = PhoneAuthOptions.newBuilder(Firebase.auth)
        .setPhoneNumber(cleanPhone)
        .setTimeout(60L, TimeUnit.SECONDS)
        .setActivity(activity)
        .setCallbacks(callbacks)
    if (resendToken != null) {
        builder.setForceResendingToken(resendToken)
    }

    try {
        PhoneAuthProvider.verifyPhoneNumber(builder.build())
    } catch (e: Exception) {
        AuthDiagnosticsManager.record("config_error", "critical", AuthProviderType.PHONE.id, "failure", e.localizedMessage ?: "Sync error", "EXCEPTION")
        onError(e.localizedMessage ?: "Unable to start phone verification.", false)
    }
}

suspend fun verifyPhoneOtpCode(
    context: Context,
    verificationId: String,
    otpCode: String
): Result<Unit> {
    val tracker = PendingAuthPrefs.otpSecurityTracker()
    val now = System.currentTimeMillis()
    if (tracker.isLockedOut(now)) {
        val rem = tracker.remainingLockoutSeconds(now)
        val msg = "OTP verification locked for ${rem}s after multiple failed attempts."
        AuthDiagnosticsManager.record("otp_lockout", "critical", AuthProviderType.PHONE.id, "failure", msg, "LOCKED")
        return Result.failure(IllegalStateException(msg))
    }
    if (tracker.isExpired(now)) {
        val msg = "This verification code has expired (5 min limit). Request a new SMS code."
        AuthDiagnosticsManager.record("otp_expired", "warn", AuthProviderType.PHONE.id, "failure", msg, "EXPIRED")
        return Result.failure(IllegalStateException(msg))
    }

    return try {
        val validation = AuthValidation.validateOtpCode(otpCode)
        if (!validation.isValid) {
            return Result.failure(IllegalArgumentException(validation.errorMessage ?: "Enter the 6-digit SMS code."))
        }
        val credential = PhoneAuthProvider.getCredential(verificationId, validation.normalizedValue)
        val authRes = Firebase.auth.signInWithCredential(credential).await()
        val isNew = authRes.additionalUserInfo?.isNewUser == true
        applyPendingDisplayNameIfSet(context)
        PendingAuthPrefs.saveLastMethod(context, AuthProviderType.PHONE.label, AuthProviderType.PHONE)
        tracker.reset()
        AuthDiagnosticsManager.record(
            category = if (isNew) "registration" else "login_success",
            severity = "info",
            provider = AuthProviderType.PHONE.id,
            outcome = "success",
            summary = "Phone OTP verified cleanly for uid=${authRes.user?.uid}",
            statusCode = "OK"
        )
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        val code = (e as? FirebaseAuthException)?.errorCode.orEmpty()
        val lockedNow = tracker.recordFailedAttempt(System.currentTimeMillis())
        val friendly = when {
            lockedNow -> "Too many incorrect OTP attempts. Please wait 2 minutes before retrying."
            code == "ERROR_SESSION_EXPIRED" -> "Verification code expired. Please resend a fresh SMS code."
            code == "ERROR_INVALID_VERIFICATION_CODE" -> "Incorrect 6-digit SMS code (${tracker.maxFailedAttempts - tracker.failedAttempts} attempts left)."
            e is FirebaseNetworkException -> "Network error while verifying code."
            else -> e.localizedMessage ?: "Invalid verification code. Please check and retry."
        }
        AuthDiagnosticsManager.record(
            category = if (lockedNow) "otp_lockout" else "otp_failure",
            severity = if (lockedNow) "critical" else "warn",
            provider = AuthProviderType.PHONE.id,
            outcome = "failure",
            summary = friendly,
            statusCode = code.ifBlank { "OTP_FAIL" }
        )
        Result.failure(Exception(friendly))
    }
}

suspend fun sendPasswordlessEmailLink(
    context: Context,
    email: String
): Result<Unit> {
    val validation = AuthValidation.validateEmail(email)
    if (!validation.isValid) {
        AuthDiagnosticsManager.record("auth_attempt", "warn", AuthProviderType.EMAIL_LINK.id, "failure", "Invalid email format", "INVALID_EMAIL")
        return Result.failure(IllegalArgumentException(validation.errorMessage ?: "Enter a valid email address."))
    }
    val cleanEmail = validation.normalizedValue
    return try {
        val actionCodeSettings = ActionCodeSettings.newBuilder()
            .setUrl("https://gen-lang-client-0321953317.firebaseapp.com/__/auth/action")
            .setHandleCodeInApp(true)
            .setAndroidPackageName(context.packageName, true, null)
            .build()

        Firebase.auth.sendSignInLinkToEmail(cleanEmail, actionCodeSettings).await()
        PendingAuthPrefs.savePendingEmail(context, cleanEmail)
        AuthDiagnosticsManager.record(
            category = "email_link_sent",
            severity = "info",
            provider = AuthProviderType.EMAIL_LINK.id,
            outcome = "pending",
            summary = "Passwordless sign-in link dispatched to ${AuthLogRedactor.maskEmail(cleanEmail)}",
            statusCode = "SENT"
        )
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        val code = (e as? FirebaseAuthException)?.errorCode.orEmpty()
        Log.w(AUTH_TAG, "sendSignInLinkToEmail failed code=$code")
        val consoleMissing = code == "ERROR_OPERATION_NOT_ALLOWED" ||
            e.message?.contains("not allowed", ignoreCase = true) == true
        val friendly = when {
            e is FirebaseNetworkException ->
                "Unable to reach Firebase Auth. Check your internet connection."
            consoleMissing ->
                "Email Link (Passwordless) sign-in is not yet enabled in Firebase Console (Authentication → Sign-in method → Email/Password → Email link). You can also use 'Continue with Google' right now."
            code == "ERROR_UNAUTHORIZED_DOMAIN" ->
                "App redirect domain needs authorization in Firebase Console → Authentication → Settings → Authorized domains."
            else -> e.localizedMessage ?: "Could not send sign-in link."
        }
        AuthDiagnosticsManager.record(
            category = if (consoleMissing || code == "ERROR_UNAUTHORIZED_DOMAIN") "config_error" else "login_failed",
            severity = "warn",
            provider = AuthProviderType.EMAIL_LINK.id,
            outcome = "failure",
            summary = friendly,
            statusCode = code.ifBlank { "EMAIL_SEND_FAIL" }
        )
        Result.failure(Exception(friendly))
    }
}

suspend fun completeEmailLinkSignIn(
    context: Context,
    email: String,
    emailLinkUrl: String
): Result<Unit> {
    val emailCheck = AuthValidation.validateEmail(email)
    val cleanEmail = emailCheck.normalizedValue
    val cleanLink = emailLinkUrl.trim()
    if (!emailCheck.isValid) {
        return Result.failure(IllegalArgumentException("Enter the email address you used to request the link."))
    }
    if (!Firebase.auth.isSignInWithEmailLink(cleanLink)) {
        AuthDiagnosticsManager.record("login_failed", "warn", AuthProviderType.EMAIL_LINK.id, "failure", "Rejected invalid email link URL format", "INVALID_LINK")
        return Result.failure(IllegalArgumentException("That URL is not a valid P-CHAT sign-in link."))
    }
    return try {
        val res = Firebase.auth.signInWithEmailLink(cleanEmail, cleanLink).await()
        val isNew = res.additionalUserInfo?.isNewUser == true
        PendingAuthPrefs.clearPendingEmail(context)
        applyPendingDisplayNameIfSet(context)
        PendingAuthPrefs.saveLastMethod(context, AuthProviderType.EMAIL_LINK.label, AuthProviderType.EMAIL_LINK)
        AuthDiagnosticsManager.record(
            category = if (isNew) "registration" else "login_success",
            severity = "info",
            provider = AuthProviderType.EMAIL_LINK.id,
            outcome = "success",
            summary = "Passwordless email link sign-in succeeded for uid=${res.user?.uid}",
            statusCode = "OK"
        )
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        val code = (e as? FirebaseAuthException)?.errorCode.orEmpty()
        val friendly = when (code) {
            "ERROR_EXPIRED_ACTION_CODE" -> "This sign-in link has expired. Request a new Email Link and open it within 15 minutes."
            "ERROR_INVALID_ACTION_CODE" -> "This sign-in link has already been used or is invalid. Request a fresh link."
            else -> if (e is FirebaseNetworkException) "Network connection required to verify your email link."
            else e.localizedMessage ?: "Failed to verify email link."
        }
        AuthDiagnosticsManager.record(
            category = if (code.contains("EXPIRED")) "link_expired" else "login_failed",
            severity = "warn",
            provider = AuthProviderType.EMAIL_LINK.id,
            outcome = "failure",
            summary = friendly,
            statusCode = code.ifBlank { "EMAIL_LINK_FAIL" }
        )
        Result.failure(Exception(friendly))
    }
}

suspend fun tryHandleIncomingEmailLinkIntent(context: Context, intent: Intent?): Result<String>? {
    val link = intent?.data?.toString()?.takeIf { it.isNotBlank() } ?: return null
    if (!Firebase.auth.isSignInWithEmailLink(link)) return null
    val savedEmail = PendingAuthPrefs.getPendingEmail(context)
        ?: return Result.failure(
            IllegalStateException("Open P-CHAT's 'Continue with Email' dialog, re-enter your email, and paste the link to complete verification.")
        )
    return completeEmailLinkSignIn(context, savedEmail, link)
        .map { "Signed in via Email Link (${AuthLogRedactor.maskEmail(savedEmail)})" }
}

fun launchGoogleAccountAuth(
    context: Context,
    credentialManager: CredentialManager,
    scope: CoroutineScope,
    onSuccess: () -> Unit,
    onError: (String) -> Unit,
    onCancelled: () -> Unit
) {
    AuthDiagnosticsManager.record(
        category = "auth_attempt",
        severity = "info",
        provider = AuthProviderType.GOOGLE.id,
        outcome = "pending",
        summary = "Launching Google Credential Manager account picker",
        statusCode = "START"
    )
    val activity = context.findAuthActivity() ?: context
    onGoogleSignInClicked(
        context = activity,
        credentialManager = credentialManager,
        onAuthSuccess = {
            scope.launch {
                applyPendingDisplayNameIfSet(context)
                PendingAuthPrefs.saveLastMethod(context, AuthProviderType.GOOGLE.label, AuthProviderType.GOOGLE)
                AuthDiagnosticsManager.record(
                    category = "login_success",
                    severity = "info",
                    provider = AuthProviderType.GOOGLE.id,
                    outcome = "success",
                    summary = "Google authentication succeeded for uid=${Firebase.auth.currentUser?.uid}",
                    statusCode = "OK"
                )
                onSuccess()
            }
        },
        onAuthError = { err ->
            AuthDiagnosticsManager.record(
                category = "login_failed",
                severity = "warn",
                provider = AuthProviderType.GOOGLE.id,
                outcome = "failure",
                summary = err,
                statusCode = "GOOGLE_FAIL"
            )
            onError(err)
        },
        scope = scope,
        onAuthCancelled = {
            AuthDiagnosticsManager.record(
                category = "auth_attempt",
                severity = "info",
                provider = AuthProviderType.GOOGLE.id,
                outcome = "cancelled",
                summary = "User dismissed Google account selector",
                statusCode = "CANCELLED"
            )
            onCancelled()
        }
    )
}
