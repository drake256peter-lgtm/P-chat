package com.example.auth

import com.example.model.AuthProviderType
import com.example.model.SignUpDraft

sealed interface AuthActionResult {
    data class Authenticated(
        val uid: String,
        val provider: AuthProviderType,
        val isNewUser: Boolean,
        val emailVerified: Boolean,
        val phoneVerified: Boolean
    ) : AuthActionResult

    data class ChallengeSent(
        val provider: AuthProviderType,
        val referenceToken: String,
        val message: String
    ) : AuthActionResult

    data class Failure(
        val errorCode: String,
        val userMessage: String,
        val needsConsoleSetup: Boolean = false
    ) : AuthActionResult

    data object Cancelled : AuthActionResult
}

data class AuthGatewaySession(
    val uid: String,
    val provider: AuthProviderType,
    val displayName: String,
    val email: String?,
    val phoneNumber: String?,
    val emailVerified: Boolean
)

/**
 * Pure Kotlin gateway contract separating Firebase auth mechanics from testable authentication flows
 * (registration, returning login, OTP limits/expiry, email links, duplicate username prevention,
 * session restoration, and secure logout).
 */
interface AuthBackendGateway {
    fun currentSession(): AuthGatewaySession?
    suspend fun signInWithGoogle(draft: SignUpDraft? = null): AuthActionResult
    suspend fun requestPhoneOtp(phoneE164: String): AuthActionResult
    suspend fun verifyPhoneOtp(phoneE164: String, verificationId: String, otpCode: String, draft: SignUpDraft? = null): AuthActionResult
    suspend fun sendEmailLink(email: String): AuthActionResult
    suspend fun signInWithEmailLink(email: String, linkUrl: String, draft: SignUpDraft? = null): AuthActionResult
    suspend fun checkUsernameAvailable(handle: String): Result<Boolean>
    suspend fun reserveProfileAndUsername(uid: String, draft: SignUpDraft): Result<Unit>
    suspend fun signOut(uid: String, sessionId: String): Result<Unit>
}

/**
 * Orchestrates all P-CHAT sign-in, sign-up, verification, lockout/expiry, and diagnostic rules
 * identically in production and JVM unit tests.
 */
class PChatAuthOrchestrator(
    private val gateway: AuthBackendGateway,
    private val otpTracker: OtpSecurityTracker = OtpSecurityTracker(),
    private val emailTracker: EmailLinkStateTracker = EmailLinkStateTracker(),
    private val clockMs: () -> Long = { System.currentTimeMillis() },
    private val onDiagnostic: (category: String, severity: String, provider: String, outcome: String, summary: String, code: String) -> Unit = { _, _, _, _, _, _ -> }
) {
    var rememberedLastProvider: AuthProviderType? = null
        private set

    fun restorePersistedSession(): AuthGatewaySession? {
        val session = gateway.currentSession()
        if (session != null) {
            onDiagnostic(
                "session_restored",
                "info",
                session.provider.id,
                "success",
                "Restored valid persisted session for uid=${session.uid}",
                "OK"
            )
        }
        return session
    }

    suspend fun authenticateWithGoogle(draft: SignUpDraft? = null): AuthActionResult {
        onDiagnostic("auth_attempt", "info", AuthProviderType.GOOGLE.id, "pending", "Starting Google Credential Manager authentication", "START")
        val res = gateway.signInWithGoogle(draft)
        return when (res) {
            is AuthActionResult.Authenticated -> {
                rememberedLastProvider = AuthProviderType.GOOGLE
                val event = if (res.isNewUser) "registration" else "login_success"
                onDiagnostic(event, "info", AuthProviderType.GOOGLE.id, "success", "Google auth succeeded for uid=${res.uid}", "OK")
                res
            }
            is AuthActionResult.Failure -> {
                onDiagnostic("login_failed", "warn", AuthProviderType.GOOGLE.id, "failure", res.userMessage, res.errorCode)
                res
            }
            AuthActionResult.Cancelled -> {
                onDiagnostic("auth_attempt", "info", AuthProviderType.GOOGLE.id, "cancelled", "User cancelled Google prompt", "CANCELLED")
                res
            }
            is AuthActionResult.ChallengeSent -> res
        }
    }

    suspend fun requestPhoneCode(dialCode: String, localPhone: String): AuthActionResult {
        val now = clockMs()
        if (otpTracker.isLockedOut(now)) {
            val sec = otpTracker.remainingLockoutSeconds(now)
            val msg = "Too many incorrect OTP attempts. Try again in ${sec}s."
            onDiagnostic("otp_lockout", "warn", AuthProviderType.PHONE.id, "failure", msg, "LOCKOUT")
            return AuthActionResult.Failure("OTP_LOCKED_OUT", msg)
        }
        val normalized = AuthValidation.normalizePhoneE164(dialCode, localPhone)
        val validation = AuthValidation.validatePhoneE164(normalized)
        if (!validation.isValid) {
            onDiagnostic("auth_attempt", "warn", AuthProviderType.PHONE.id, "failure", validation.errorMessage ?: "Invalid phone", "INVALID_PHONE")
            return AuthActionResult.Failure("INVALID_PHONE", validation.errorMessage ?: "Invalid phone number.")
        }
        return when (val res = gateway.requestPhoneOtp(validation.normalizedValue)) {
            is AuthActionResult.ChallengeSent -> {
                otpTracker.markCodeDispatched(now)
                onDiagnostic("otp_dispatched", "info", AuthProviderType.PHONE.id, "pending", "SMS OTP challenge sent to ${AuthLogRedactor.maskPhone(validation.normalizedValue)}", "SENT")
                res
            }
            is AuthActionResult.Authenticated -> {
                rememberedLastProvider = AuthProviderType.PHONE
                otpTracker.reset()
                onDiagnostic("login_success", "info", AuthProviderType.PHONE.id, "success", "Phone auto-verified for uid=${res.uid}", "AUTO_VERIFIED")
                res
            }
            is AuthActionResult.Failure -> {
                val cat = if (res.needsConsoleSetup) "config_error" else "login_failed"
                onDiagnostic(cat, "warn", AuthProviderType.PHONE.id, "failure", res.userMessage, res.errorCode)
                res
            }
            AuthActionResult.Cancelled -> res
        }
    }

    suspend fun submitPhoneOtp(
        phoneE164: String,
        verificationId: String,
        otpCode: String,
        draft: SignUpDraft? = null
    ): AuthActionResult {
        val now = clockMs()
        if (otpTracker.isLockedOut(now)) {
            val sec = otpTracker.remainingLockoutSeconds(now)
            val msg = "OTP verification temporarily locked for ${sec}s after too many wrong codes."
            onDiagnostic("otp_lockout", "critical", AuthProviderType.PHONE.id, "failure", msg, "OTP_LOCKED_OUT")
            return AuthActionResult.Failure("OTP_LOCKED_OUT", msg)
        }
        if (otpTracker.isExpired(now)) {
            onDiagnostic("otp_expired", "warn", AuthProviderType.PHONE.id, "failure", "SMS OTP code expired after 5 minutes.", "OTP_EXPIRED")
            return AuthActionResult.Failure("OTP_EXPIRED", "This verification code has expired. Tap Resend SMS for a fresh code.")
        }
        val codeValidation = AuthValidation.validateOtpCode(otpCode)
        if (!codeValidation.isValid) {
            return AuthActionResult.Failure("INVALID_OTP_FORMAT", codeValidation.errorMessage ?: "Enter 6 digits.")
        }
        return when (val res = gateway.verifyPhoneOtp(phoneE164, verificationId, codeValidation.normalizedValue, draft)) {
            is AuthActionResult.Authenticated -> {
                rememberedLastProvider = AuthProviderType.PHONE
                otpTracker.reset()
                val event = if (res.isNewUser) "registration" else "login_success"
                onDiagnostic(event, "info", AuthProviderType.PHONE.id, "success", "Phone OTP verified for uid=${res.uid}", "OK")
                res
            }
            is AuthActionResult.Failure -> {
                val triggeredLockout = otpTracker.recordFailedAttempt(now)
                val cat = if (triggeredLockout) "otp_lockout" else "otp_failure"
                val sev = if (triggeredLockout) "critical" else "warn"
                onDiagnostic(cat, sev, AuthProviderType.PHONE.id, "failure", res.userMessage, res.errorCode)
                if (triggeredLockout) {
                    AuthActionResult.Failure(
                        "OTP_LOCKED_OUT",
                        "Too many incorrect OTP codes. Please wait 2 minutes before trying again."
                    )
                } else {
                    res
                }
            }
            else -> res
        }
    }

    suspend fun sendPasswordlessEmail(email: String): AuthActionResult {
        val validation = AuthValidation.validateEmail(email)
        if (!validation.isValid) {
            onDiagnostic("auth_attempt", "warn", AuthProviderType.EMAIL_LINK.id, "failure", "Invalid email format rejected", "INVALID_EMAIL")
            return AuthActionResult.Failure("INVALID_EMAIL", validation.errorMessage ?: "Invalid email address.")
        }
        val now = clockMs()
        val cooldown = emailTracker.remainingCooldownSeconds(now)
        if (cooldown > 0) {
            return AuthActionResult.Failure("RESEND_COOLDOWN", "Please wait ${cooldown}s before requesting another link.")
        }
        return when (val res = gateway.sendEmailLink(validation.normalizedValue)) {
            is AuthActionResult.ChallengeSent -> {
                emailTracker.markSent(now)
                onDiagnostic("email_link_sent", "info", AuthProviderType.EMAIL_LINK.id, "pending", "Sign-in link sent to ${AuthLogRedactor.maskEmail(validation.normalizedValue)}", "SENT")
                res
            }
            is AuthActionResult.Failure -> {
                val cat = if (res.needsConsoleSetup) "config_error" else "login_failed"
                onDiagnostic(cat, "warn", AuthProviderType.EMAIL_LINK.id, "failure", res.userMessage, res.errorCode)
                res
            }
            else -> res
        }
    }

    suspend fun verifyEmailSignInLink(email: String, linkUrl: String, draft: SignUpDraft? = null): AuthActionResult {
        val emailValidation = AuthValidation.validateEmail(email)
        if (!emailValidation.isValid) {
            return AuthActionResult.Failure("INVALID_EMAIL", emailValidation.errorMessage ?: "Invalid email address.")
        }
        val now = clockMs()
        if (emailTracker.sentAtMs > 0L && emailTracker.isExpired(now)) {
            onDiagnostic("link_expired", "warn", AuthProviderType.EMAIL_LINK.id, "failure", "Email sign-in link expired", "EXPIRED_ACTION_CODE")
            return AuthActionResult.Failure("EXPIRED_ACTION_CODE", "This sign-in link has expired. Request a new email link.")
        }
        if (!linkUrl.trim().startsWith("https://")) {
            return AuthActionResult.Failure("INVALID_EMAIL_LINK", "That URL is not a valid P-CHAT sign-in link.")
        }
        return when (val res = gateway.signInWithEmailLink(emailValidation.normalizedValue, linkUrl.trim(), draft)) {
            is AuthActionResult.Authenticated -> {
                rememberedLastProvider = AuthProviderType.EMAIL_LINK
                val event = if (res.isNewUser) "registration" else "login_success"
                onDiagnostic(event, "info", AuthProviderType.EMAIL_LINK.id, "success", "Email link authenticated uid=${res.uid}", "OK")
                res
            }
            is AuthActionResult.Failure -> {
                val cat = if (res.errorCode.contains("EXPIRED", ignoreCase = true)) "link_expired" else "login_failed"
                onDiagnostic(cat, "warn", AuthProviderType.EMAIL_LINK.id, "failure", res.userMessage, res.errorCode)
                res
            }
            else -> res
        }
    }

    suspend fun completeSignUpProfile(uid: String, draft: SignUpDraft): Result<Unit> {
        val nameCheck = AuthValidation.validateFullName(draft.fullName)
        if (!nameCheck.isValid) return Result.failure(IllegalArgumentException(nameCheck.errorMessage))

        val userCheck = AuthValidation.validateUsername(draft.username)
        if (!userCheck.isValid) return Result.failure(IllegalArgumentException(userCheck.errorMessage))

        if (draft.email.isNotBlank()) {
            val emailCheck = AuthValidation.validateEmail(draft.email)
            if (!emailCheck.isValid) return Result.failure(IllegalArgumentException(emailCheck.errorMessage))
        }
        if (draft.phoneE164.isNotBlank()) {
            val phoneCheck = AuthValidation.validatePhoneE164(draft.phoneE164)
            if (!phoneCheck.isValid) return Result.failure(IllegalArgumentException(phoneCheck.errorMessage))
        }
        val dobCheck = AuthValidation.validateDateOfBirth(draft.dateOfBirth)
        if (!dobCheck.isValid) return Result.failure(IllegalArgumentException(dobCheck.errorMessage))

        val available = gateway.checkUsernameAvailable(userCheck.normalizedValue)
            .getOrElse { return Result.failure(it) }
        if (!available) {
            onDiagnostic(
                "duplicate_username",
                "warn",
                draft.preferredProvider.id,
                "failure",
                "Rejected duplicate registration for handle=@${userCheck.normalizedValue}",
                "USERNAME_TAKEN"
            )
            return Result.failure(IllegalStateException("Username @${userCheck.normalizedValue} is already taken. Choose another handle."))
        }

        val cleanDraft = draft.copy(
            fullName = nameCheck.normalizedValue,
            username = userCheck.normalizedValue,
            dateOfBirth = dobCheck.normalizedValue
        )
        return gateway.reserveProfileAndUsername(uid, cleanDraft).onSuccess {
            onDiagnostic(
                "registration",
                "info",
                draft.preferredProvider.id,
                "success",
                "Completed registration & reserved @${cleanDraft.username} for uid=$uid",
                "PROFILE_COMPLETED"
            )
        }
    }

    suspend fun logout(uid: String, sessionId: String): Result<Unit> {
        return gateway.signOut(uid, sessionId).onSuccess {
            onDiagnostic("logout", "info", (rememberedLastProvider ?: AuthProviderType.GOOGLE).id, "success", "Signed out cleanly for uid=$uid", "OK")
        }
    }
}
