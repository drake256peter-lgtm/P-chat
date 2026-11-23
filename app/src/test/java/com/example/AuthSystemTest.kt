package com.example

import com.example.auth.AuthActionResult
import com.example.auth.AuthBackendGateway
import com.example.auth.AuthGatewaySession
import com.example.auth.AuthLogRedactor
import com.example.auth.AuthValidation
import com.example.auth.EmailLinkStateTracker
import com.example.auth.INTERNATIONAL_DIAL_COUNTRIES
import com.example.auth.OtpSecurityTracker
import com.example.auth.PChatAuthOrchestrator
import com.example.auth.PREFERRED_LANGUAGES
import com.example.model.AuthProviderType
import com.example.model.SignUpDraft
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM unit tests covering P-CHAT authentication flows:
 * Google login/registration, Phone OTP & lockout/expiry, Passwordless Email link & resend cooldown/expiry,
 * returning user recognition, duplicate username prevention, PII diagnostic redaction, and secure logout.
 */
class AuthSystemTest {

    private class FakeAuthGateway : AuthBackendGateway {
        var activeSession: AuthGatewaySession? = null
        val reservedUsernames = mutableMapOf("sophia_official" to "uid_sophia")
        val completedProfiles = mutableMapOf<String, SignUpDraft>()
        val signedOutLog = mutableListOf<Pair<String, String>>()

        var nextGoogleResult: AuthActionResult = AuthActionResult.Authenticated(
            uid = "uid_google_new",
            provider = AuthProviderType.GOOGLE,
            isNewUser = true,
            emailVerified = true,
            phoneVerified = false
        )
        var nextOtpResult: AuthActionResult = AuthActionResult.Authenticated(
            uid = "uid_phone_1",
            provider = AuthProviderType.PHONE,
            isNewUser = false,
            emailVerified = false,
            phoneVerified = true
        )
        var nextEmailSendResult: AuthActionResult = AuthActionResult.ChallengeSent(
            provider = AuthProviderType.EMAIL_LINK,
            referenceToken = "email_ref_1",
            message = "Sent"
        )
        var nextEmailVerifyResult: AuthActionResult = AuthActionResult.Authenticated(
            uid = "uid_email_1",
            provider = AuthProviderType.EMAIL_LINK,
            isNewUser = true,
            emailVerified = true,
            phoneVerified = false
        )

        override fun currentSession(): AuthGatewaySession? = activeSession

        override suspend fun signInWithGoogle(draft: SignUpDraft?): AuthActionResult {
            if (nextGoogleResult is AuthActionResult.Authenticated) {
                val r = nextGoogleResult as AuthActionResult.Authenticated
                activeSession = AuthGatewaySession(
                    uid = r.uid,
                    provider = r.provider,
                    displayName = draft?.fullName?.ifBlank { "Google User" } ?: "Google User",
                    email = draft?.email ?: "user@gmail.com",
                    phoneNumber = null,
                    emailVerified = true
                )
            }
            return nextGoogleResult
        }

        override suspend fun requestPhoneOtp(phoneE164: String): AuthActionResult {
            return AuthActionResult.ChallengeSent(AuthProviderType.PHONE, "ver_id_1", "OTP Sent")
        }

        override suspend fun verifyPhoneOtp(
            phoneE164: String,
            verificationId: String,
            otpCode: String,
            draft: SignUpDraft?
        ): AuthActionResult {
            if (nextOtpResult is AuthActionResult.Authenticated) {
                val r = nextOtpResult as AuthActionResult.Authenticated
                activeSession = AuthGatewaySession(
                    uid = r.uid,
                    provider = r.provider,
                    displayName = draft?.fullName?.ifBlank { "Phone User" } ?: "Phone User",
                    email = null,
                    phoneNumber = phoneE164,
                    emailVerified = false
                )
            }
            return nextOtpResult
        }

        override suspend fun sendEmailLink(email: String): AuthActionResult = nextEmailSendResult

        override suspend fun signInWithEmailLink(
            email: String,
            linkUrl: String,
            draft: SignUpDraft?
        ): AuthActionResult {
            if (nextEmailVerifyResult is AuthActionResult.Authenticated) {
                val r = nextEmailVerifyResult as AuthActionResult.Authenticated
                activeSession = AuthGatewaySession(
                    uid = r.uid,
                    provider = r.provider,
                    displayName = draft?.fullName?.ifBlank { "Email User" } ?: "Email User",
                    email = email,
                    phoneNumber = null,
                    emailVerified = true
                )
            }
            return nextEmailVerifyResult
        }

        override suspend fun checkUsernameAvailable(handle: String): Result<Boolean> {
            return Result.success(!reservedUsernames.containsKey(handle))
        }

        override suspend fun reserveProfileAndUsername(uid: String, draft: SignUpDraft): Result<Unit> {
            val owner = reservedUsernames[draft.username]
            if (owner != null && owner != uid) {
                return Result.failure(IllegalStateException("Taken"))
            }
            reservedUsernames[draft.username] = uid
            completedProfiles[uid] = draft
            return Result.success(Unit)
        }

        override suspend fun signOut(uid: String, sessionId: String): Result<Unit> {
            signedOutLog.add(uid to sessionId)
            activeSession = null
            return Result.success(Unit)
        }
    }

    @Test
    fun `google registration and returning login update remembered provider and diagnostics`() = runBlocking {
        val gateway = FakeAuthGateway()
        val diagEvents = mutableListOf<String>()
        val orchestrator = PChatAuthOrchestrator(
            gateway = gateway,
            onDiagnostic = { cat, _, prov, outcome, _, _ ->
                diagEvents.add("$cat:$prov:$outcome")
            }
        )

        val regRes = orchestrator.authenticateWithGoogle()
        assertTrue(regRes is AuthActionResult.Authenticated)
        assertTrue((regRes as AuthActionResult.Authenticated).isNewUser)
        assertEquals(AuthProviderType.GOOGLE, orchestrator.rememberedLastProvider)
        assertTrue(diagEvents.any { it == "registration:google:success" })

        gateway.nextGoogleResult = AuthActionResult.Authenticated(
            uid = "uid_google_new",
            provider = AuthProviderType.GOOGLE,
            isNewUser = false,
            emailVerified = true,
            phoneVerified = false
        )
        val loginRes = orchestrator.authenticateWithGoogle()
        assertFalse((loginRes as AuthActionResult.Authenticated).isNewUser)
        assertTrue(diagEvents.any { it == "login_success:google:success" })
    }

    @Test
    fun `phone registration and login handle international E164 normalization`() = runBlocking {
        val gateway = FakeAuthGateway()
        var clock = 1_000_000L
        val orchestrator = PChatAuthOrchestrator(gateway = gateway, clockMs = { clock })

        val normalizedUg = AuthValidation.normalizePhoneE164("+256", "0700 123 456")
        assertEquals("+256700123456", normalizedUg)
        assertTrue(AuthValidation.validatePhoneE164(normalizedUg).isValid)

        val challenge = orchestrator.requestPhoneCode("+256", "0700123456")
        assertTrue(challenge is AuthActionResult.ChallengeSent)

        clock += 5_000L
        val verified = orchestrator.submitPhoneOtp(normalizedUg, "ver_id_1", "123456")
        assertTrue(verified is AuthActionResult.Authenticated)
        assertEquals(AuthProviderType.PHONE, orchestrator.rememberedLastProvider)
    }

    @Test
    fun `otp enforces 5-minute expiry and 5-attempt brute-force lockout`() = runBlocking {
        val gateway = FakeAuthGateway()
        var clock = 10_000L
        val tracker = OtpSecurityTracker(otpExpiryMs = 300_000L, maxFailedAttempts = 5, lockoutDurationMs = 120_000L)
        val orchestrator = PChatAuthOrchestrator(gateway = gateway, otpTracker = tracker, clockMs = { clock })

        orchestrator.requestPhoneCode("+1", "4155551234")
        gateway.nextOtpResult = AuthActionResult.Failure("ERROR_INVALID_VERIFICATION_CODE", "Wrong OTP")

        repeat(4) {
            clock += 1_000L
            val r = orchestrator.submitPhoneOtp("+14155551234", "ver_id_1", "000000")
            assertEquals("ERROR_INVALID_VERIFICATION_CODE", (r as AuthActionResult.Failure).errorCode)
            assertFalse(tracker.isLockedOut(clock))
        }

        // 5th wrong attempt triggers 120s lockout
        clock += 1_000L
        val fifth = orchestrator.submitPhoneOtp("+14155551234", "ver_id_1", "000000")
        assertEquals("OTP_LOCKED_OUT", (fifth as AuthActionResult.Failure).errorCode)
        assertTrue(tracker.isLockedOut(clock))

        // During lockout, even valid OTP attempt is blocked
        clock += 30_000L
        val blocked = orchestrator.submitPhoneOtp("+14155551234", "ver_id_1", "123456")
        assertEquals("OTP_LOCKED_OUT", (blocked as AuthActionResult.Failure).errorCode)

        // After 120s lockout expires and after 5m OTP expiry window, OTP reports expired cleanly
        clock += 310_000L
        val expired = orchestrator.submitPhoneOtp("+14155551234", "ver_id_1", "123456")
        assertEquals("OTP_EXPIRED", (expired as AuthActionResult.Failure).errorCode)
    }

    @Test
    fun `passwordless email enforces resend cooldown and link expiry`() = runBlocking {
        val gateway = FakeAuthGateway()
        var clock = 50_000L
        val emailTracker = EmailLinkStateTracker(linkExpiryMs = 15 * 60_000L, resendCooldownMs = 60_000L)
        val orchestrator = PChatAuthOrchestrator(gateway = gateway, emailTracker = emailTracker, clockMs = { clock })

        val badEmail = orchestrator.sendPasswordlessEmail("not-an-email")
        assertEquals("INVALID_EMAIL", (badEmail as AuthActionResult.Failure).errorCode)

        val sent = orchestrator.sendPasswordlessEmail("alex@pchat.app")
        assertTrue(sent is AuthActionResult.ChallengeSent)

        // Immediate resend blocked by 60s cooldown
        clock += 10_000L
        val cooldownBlocked = orchestrator.sendPasswordlessEmail("alex@pchat.app")
        assertEquals("RESEND_COOLDOWN", (cooldownBlocked as AuthActionResult.Failure).errorCode)

        // Verify valid link within window succeeds
        val ok = orchestrator.verifyEmailSignInLink("alex@pchat.app", "https://gen-lang-client-0321953317.firebaseapp.com/__/auth/action?oobCode=xyz")
        assertTrue(ok is AuthActionResult.Authenticated)
        assertEquals(AuthProviderType.EMAIL_LINK, orchestrator.rememberedLastProvider)

        // Expired link after 15 minutes fails gracefully
        clock += 16 * 60_000L
        val expired = orchestrator.verifyEmailSignInLink("alex@pchat.app", "https://gen-lang-client-0321953317.firebaseapp.com/__/auth/action?oobCode=xyz")
        assertEquals("EXPIRED_ACTION_CODE", (expired as AuthActionResult.Failure).errorCode)
    }

    @Test
    fun `onboarding prevents duplicate usernames, validates DOB age 13+, and reserves unique handle`() = runBlocking {
        val gateway = FakeAuthGateway()
        val orchestrator = PChatAuthOrchestrator(gateway = gateway)

        val underageDraft = SignUpDraft(
            fullName = "Junior User",
            username = "junior_ok",
            email = "junior@example.com",
            phoneE164 = "+256700999888",
            country = "Uganda",
            preferredLanguage = "English",
            dateOfBirth = "2019-03-01"
        )
        val underageRes = orchestrator.completeSignUpProfile("uid_new", underageDraft)
        assertTrue(underageRes.isFailure)

        val duplicateDraft = underageDraft.copy(
            username = "@Sophia Official!", // normalizes to sophia_official -> taken by uid_sophia
            dateOfBirth = "1999-08-21"
        )
        val dupRes = orchestrator.completeSignUpProfile("uid_new", duplicateDraft)
        assertTrue(dupRes.isFailure)
        assertTrue(dupRes.exceptionOrNull()?.message?.contains("already taken") == true)

        val cleanDraft = duplicateDraft.copy(username = "alex_kampala")
        val okRes = orchestrator.completeSignUpProfile("uid_new", cleanDraft)
        assertTrue(okRes.isSuccess)
        assertEquals("uid_new", gateway.reservedUsernames["alex_kampala"])
        assertEquals("alex_kampala", gateway.completedProfiles["uid_new"]?.username)
    }

    @Test
    fun `diagnostic log redactor strips OTPs, JWTs, passwords, tokens, and masks PII`() {
        val raw = "Failed auth email=sophia.kirabo@example.com phone=+256700123456 otp=849201 password=MySecret99 idToken=eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiIxMjMifQ.sig verificationId=abc123token"
        val redacted = AuthLogRedactor.redactSensitiveText(raw)

        assertFalse(redacted.contains("sophia.kirabo@example.com"))
        assertFalse(redacted.contains("+256700123456"))
        assertFalse(redacted.contains("849201"))
        assertFalse(redacted.contains("MySecret99"))
        assertFalse(redacted.contains("eyJhbGciOiJSUzI1NiJ9"))
        assertFalse(redacted.contains("abc123token"))
        assertTrue(redacted.contains("so***@example.com"))
        assertTrue(redacted.contains("+256***456"))
        assertTrue(redacted.contains("[REDACTED]"))
    }

    @Test
    fun `session persistence restore, returning recognition, and secure logout`() = runBlocking {
        val gateway = FakeAuthGateway()
        val orchestrator = PChatAuthOrchestrator(gateway = gateway)

        assertNull(orchestrator.restorePersistedSession())
        orchestrator.authenticateWithGoogle()
        val restored = orchestrator.restorePersistedSession()
        assertNotNull(restored)
        assertEquals("uid_google_new", restored?.uid)

        val outRes = orchestrator.logout("uid_google_new", "sess_123")
        assertTrue(outRes.isSuccess)
        assertNull(orchestrator.restorePersistedSession())
        assertEquals(listOf("uid_google_new" to "sess_123"), gateway.signedOutLog)
    }

    @Test
    fun `international dial country search and language presets cover global markets`() {
        assertTrue(INTERNATIONAL_DIAL_COUNTRIES.size >= 45)
        assertTrue(PREFERRED_LANGUAGES.containsAll(listOf("English", "Luganda", "Swahili", "French", "Arabic")))
        assertTrue(AuthValidation.searchDialCountries("+256").any { it.name == "Uganda" })
        assertTrue(AuthValidation.searchDialCountries("kenya").any { it.dialCode == "+254" })
    }

    @Test
    fun `network and Firebase connection failures propagate cleanly and log redacted errors`() = runBlocking {
        val gateway = FakeAuthGateway()
        val diagEvents = mutableListOf<Triple<String, String, String>>()
        val orchestrator = PChatAuthOrchestrator(
            gateway = gateway,
            onDiagnostic = { cat, _, _, outcome, _, code ->
                diagEvents.add(Triple(cat, outcome, code))
            }
        )

        gateway.nextGoogleResult = AuthActionResult.Failure(
            errorCode = "ERROR_NETWORK_REQUEST_FAILED",
            userMessage = "No internet connection while contacting Firebase Authentication."
        )
        val googleNetFail = orchestrator.authenticateWithGoogle()
        assertTrue(googleNetFail is AuthActionResult.Failure)
        assertEquals("ERROR_NETWORK_REQUEST_FAILED", (googleNetFail as AuthActionResult.Failure).errorCode)

        gateway.nextEmailSendResult = AuthActionResult.Failure(
            errorCode = "FIREBASE_UNAVAILABLE",
            userMessage = "Firebase Auth backend unreachable."
        )
        val emailFbFail = orchestrator.sendPasswordlessEmail("alex@pchat.app")
        assertTrue(emailFbFail is AuthActionResult.Failure)
        assertEquals("FIREBASE_UNAVAILABLE", (emailFbFail as AuthActionResult.Failure).errorCode)

        assertTrue(diagEvents.any { it.second == "failure" && it.third == "ERROR_NETWORK_REQUEST_FAILED" })
        assertTrue(diagEvents.any { it.second == "failure" && it.third == "FIREBASE_UNAVAILABLE" })
    }
}
