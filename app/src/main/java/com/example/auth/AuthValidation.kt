package com.example.auth

import java.util.Locale

data class ValidationResult(
    val isValid: Boolean,
    val normalizedValue: String = "",
    val errorMessage: String? = null
)

data class DialCountry(
    val flag: String,
    val name: String,
    val dialCode: String,
    val isoHint: String = ""
)

/**
 * Pure Kotlin international dial directory matching all 47+ countries supported in P-CHAT Wallet & Connect.
 */
val INTERNATIONAL_DIAL_COUNTRIES: List<DialCountry> = listOf(
    DialCountry("🇺🇸", "United States", "+1", "US"),
    DialCountry("🇺🇬", "Uganda", "+256", "UG"),
    DialCountry("🇰🇪", "Kenya", "+254", "KE"),
    DialCountry("🇹🇿", "Tanzania", "+255", "TZ"),
    DialCountry("🇷🇼", "Rwanda", "+250", "RW"),
    DialCountry("🇳🇬", "Nigeria", "+234", "NG"),
    DialCountry("🇿🇦", "South Africa", "+27", "ZA"),
    DialCountry("🇬🇭", "Ghana", "+233", "GH"),
    DialCountry("🇪🇹", "Ethiopia", "+251", "ET"),
    DialCountry("🇪🇬", "Egypt", "+20", "EG"),
    DialCountry("🇲🇦", "Morocco", "+212", "MA"),
    DialCountry("🇸🇳", "Senegal", "+221", "SN"),
    DialCountry("🇨🇲", "Cameroon", "+237", "CM"),
    DialCountry("🇬🇧", "United Kingdom", "+44", "GB"),
    DialCountry("🇩🇪", "Germany", "+49", "DE"),
    DialCountry("🇫🇷", "France", "+33", "FR"),
    DialCountry("🇪🇸", "Spain", "+34", "ES"),
    DialCountry("🇮🇹", "Italy", "+39", "IT"),
    DialCountry("🇳🇱", "Netherlands", "+31", "NL"),
    DialCountry("🇵🇹", "Portugal", "+351", "PT"),
    DialCountry("🇧🇪", "Belgium", "+32", "BE"),
    DialCountry("🇮🇪", "Ireland", "+353", "IE"),
    DialCountry("🇨🇭", "Switzerland", "+41", "CH"),
    DialCountry("🇵🇱", "Poland", "+48", "PL"),
    DialCountry("🇸🇪", "Sweden", "+46", "SE"),
    DialCountry("🇳🇴", "Norway", "+47", "NO"),
    DialCountry("🇩🇰", "Denmark", "+45", "DK"),
    DialCountry("🇹🇷", "Turkey", "+90", "TR"),
    DialCountry("🇷🇺", "Russia", "+7", "RU"),
    DialCountry("🇦🇪", "United Arab Emirates", "+971", "AE"),
    DialCountry("🇸🇦", "Saudi Arabia", "+966", "SA"),
    DialCountry("🇶🇦", "Qatar", "+974", "QA"),
    DialCountry("🇮🇱", "Israel", "+972", "IL"),
    DialCountry("🇮🇳", "India", "+91", "IN"),
    DialCountry("🇵🇰", "Pakistan", "+92", "PK"),
    DialCountry("🇧🇩", "Bangladesh", "+880", "BD"),
    DialCountry("🇨🇳", "China", "+86", "CN"),
    DialCountry("🇯🇵", "Japan", "+81", "JP"),
    DialCountry("🇰🇷", "South Korea", "+82", "KR"),
    DialCountry("🇸🇬", "Singapore", "+65", "SG"),
    DialCountry("🇮🇩", "Indonesia", "+62", "ID"),
    DialCountry("🇲🇾", "Malaysia", "+60", "MY"),
    DialCountry("🇹🇭", "Thailand", "+66", "TH"),
    DialCountry("🇻🇳", "Vietnam", "+84", "VN"),
    DialCountry("🇵🇭", "Philippines", "+63", "PH"),
    DialCountry("🇦🇺", "Australia", "+61", "AU"),
    DialCountry("🇳🇿", "New Zealand", "+64", "NZ"),
    DialCountry("🇨🇦", "Canada", "+1", "CA"),
    DialCountry("🇲🇽", "Mexico", "+52", "MX"),
    DialCountry("🇧🇷", "Brazil", "+55", "BR"),
    DialCountry("🇨🇴", "Colombia", "+57", "CO"),
    DialCountry("🇨🇱", "Chile", "+56", "CL"),
    DialCountry("🇦🇷", "Argentina", "+54", "AR"),
    DialCountry("🇵🇪", "Peru", "+51", "PE")
)

val PREFERRED_LANGUAGES: List<String> = listOf(
    "English",
    "Luganda",
    "Swahili",
    "French",
    "Arabic",
    "Spanish",
    "Portuguese",
    "Chinese",
    "Hindi",
    "German",
    "Russian",
    "Japanese",
    "Korean",
    "Turkish",
    "Italian"
)

object AuthValidation {

    private val EMAIL_REGEX = Regex(
        "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,24}$"
    )
    private val USERNAME_REGEX = Regex("^[a-z0-9_]{3,24}$")
    private val DOB_ISO_REGEX = Regex("^(\\d{4})-(\\d{2})-(\\d{2})$")

    fun sanitizeUsernameHandle(raw: String): String {
        return raw.lowercase(Locale.US)
            .removePrefix("@")
            .replace(Regex("[^a-z0-9_]"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')
            .take(24)
    }

    fun validateUsername(raw: String): ValidationResult {
        val clean = sanitizeUsernameHandle(raw)
        if (clean.length < 3) {
            return ValidationResult(false, clean, "Username must be at least 3 characters (letters, numbers, underscores).")
        }
        if (!USERNAME_REGEX.matches(clean)) {
            return ValidationResult(false, clean, "Username may only contain lowercase letters, numbers, and underscores.")
        }
        val reserved = setOf("admin", "support", "system", "pchat_admin", "moderator")
        if (clean in reserved) {
            return ValidationResult(false, clean, "That username is reserved by P-CHAT.")
        }
        return ValidationResult(true, clean)
    }

    fun validateFullName(raw: String): ValidationResult {
        val clean = raw.trim().replace(Regex("\\s+"), " ")
        if (clean.length < 2) {
            return ValidationResult(false, clean, "Enter your full name (at least 2 characters).")
        }
        if (clean.length > 60) {
            return ValidationResult(false, clean.take(60), "Full name must be 60 characters or fewer.")
        }
        return ValidationResult(true, clean)
    }

    fun validateEmail(raw: String): ValidationResult {
        val clean = raw.trim()
        if (clean.isEmpty()) {
            return ValidationResult(false, "", "Email address is required.")
        }
        if (clean.length > 160 || !EMAIL_REGEX.matches(clean) || clean.contains("..")) {
            return ValidationResult(false, clean, "Enter a valid email address (e.g. name@domain.com).")
        }
        return ValidationResult(true, clean)
    }

    fun isValidEmailAddress(email: String): Boolean = validateEmail(email).isValid

    fun normalizePhoneE164(countryCode: String, localNumber: String): String {
        val trimmedLocal = localNumber.trim()
        if (trimmedLocal.isBlank()) return ""
        return if (trimmedLocal.startsWith("+")) {
            "+${trimmedLocal.filter { it.isDigit() }}"
        } else {
            val digits = trimmedLocal.filter { it.isDigit() }.trimStart('0')
            val cleanCode = countryCode.filter { it.isDigit() }
            if (cleanCode.isEmpty()) "" else "+$cleanCode$digits"
        }
    }

    fun validatePhoneE164(phoneE164: String): ValidationResult {
        val clean = if (phoneE164.startsWith("+")) {
            "+${phoneE164.filter { it.isDigit() }}"
        } else {
            ""
        }
        val digitsCount = clean.count { it.isDigit() }
        if (!clean.startsWith("+") || digitsCount < 8 || digitsCount > 15) {
            return ValidationResult(
                false,
                clean,
                "Enter a valid international phone number with country code (8-15 digits)."
            )
        }
        return ValidationResult(true, clean)
    }

    fun validateOtpCode(otp: String): ValidationResult {
        val digits = otp.filter { it.isDigit() }
        if (digits.length != 6) {
            return ValidationResult(false, digits, "Enter the exact 6-digit verification code.")
        }
        return ValidationResult(true, digits)
    }

    fun validateDateOfBirth(raw: String, currentYear: Int = 2026): ValidationResult {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            return ValidationResult(false, "", "Date of birth is required (YYYY-MM-DD).")
        }
        val normalized = if (trimmed.contains("/")) {
            val parts = trimmed.split("/")
            if (parts.size == 3 && parts[2].length == 4) {
                "${parts[2]}-${parts[1].padStart(2, '0')}-${parts[0].padStart(2, '0')}"
            } else {
                trimmed
            }
        } else {
            trimmed
        }

        val match = DOB_ISO_REGEX.matchEntire(normalized)
            ?: return ValidationResult(false, normalized, "Use YYYY-MM-DD format (e.g. 2000-05-14).")
        val year = match.groupValues[1].toIntOrNull() ?: return ValidationResult(false, normalized, "Invalid birth year.")
        val month = match.groupValues[2].toIntOrNull() ?: return ValidationResult(false, normalized, "Invalid birth month.")
        val day = match.groupValues[3].toIntOrNull() ?: return ValidationResult(false, normalized, "Invalid birth day.")

        if (month !in 1..12) {
            return ValidationResult(false, normalized, "Birth month must be between 01 and 12.")
        }
        val maxDay = when (month) {
            2 -> if ((year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)) 29 else 28
            4, 6, 9, 11 -> 30
            else -> 31
        }
        if (day !in 1..maxDay) {
            return ValidationResult(false, normalized, "Invalid day $day for month $month.")
        }
        if (year < 1900 || year > currentYear) {
            return ValidationResult(false, normalized, "Enter a realistic birth year between 1900 and $currentYear.")
        }
        val approxAge = currentYear - year
        if (approxAge < 13) {
            return ValidationResult(false, normalized, "You must be at least 13 years old to register on P-CHAT.")
        }
        return ValidationResult(true, normalized)
    }

    fun searchDialCountries(query: String): List<DialCountry> {
        val q = query.trim().lowercase(Locale.US)
        if (q.isEmpty()) return INTERNATIONAL_DIAL_COUNTRIES
        return INTERNATIONAL_DIAL_COUNTRIES.filter {
            it.name.lowercase(Locale.US).contains(q) ||
                it.dialCode.contains(q) ||
                it.isoHint.lowercase(Locale.US).contains(q)
        }
    }
}

/**
 * Tracks Phone OTP expiration (5 minutes) and brute-force lockout protection (5 failed attempts -> 120s cooldown).
 */
class OtpSecurityTracker(
    val otpExpiryMs: Long = 5 * 60 * 1000L,
    val maxFailedAttempts: Int = 5,
    val lockoutDurationMs: Long = 120 * 1000L
) {
    var issuedAtMs: Long = 0L
        private set
    var failedAttempts: Int = 0
        private set
    var lockoutUntilMs: Long = 0L
        private set

    fun markCodeDispatched(nowMs: Long) {
        issuedAtMs = nowMs
        failedAttempts = 0
    }

    fun isExpired(nowMs: Long): Boolean {
        if (issuedAtMs <= 0L) return true
        return (nowMs - issuedAtMs) > otpExpiryMs
    }

    fun remainingExpirySeconds(nowMs: Long): Int {
        if (issuedAtMs <= 0L) return 0
        val rem = ((issuedAtMs + otpExpiryMs) - nowMs) / 1000L
        return rem.coerceAtLeast(0L).toInt()
    }

    fun isLockedOut(nowMs: Long): Boolean = nowMs < lockoutUntilMs

    fun remainingLockoutSeconds(nowMs: Long): Int {
        if (nowMs >= lockoutUntilMs) return 0
        return (((lockoutUntilMs - nowMs) + 999L) / 1000L).toInt()
    }

    fun recordFailedAttempt(nowMs: Long): Boolean {
        failedAttempts += 1
        if (failedAttempts >= maxFailedAttempts) {
            lockoutUntilMs = nowMs + lockoutDurationMs
            return true
        }
        return false
    }

    fun reset() {
        issuedAtMs = 0L
        failedAttempts = 0
        lockoutUntilMs = 0L
    }
}

/**
 * Tracks Passwordless Email Link expiration (15 minutes) and resend cooldown (60 seconds).
 */
class EmailLinkStateTracker(
    val linkExpiryMs: Long = 15 * 60 * 1000L,
    val resendCooldownMs: Long = 60 * 1000L
) {
    var sentAtMs: Long = 0L
        private set

    fun markSent(nowMs: Long) {
        sentAtMs = nowMs
    }

    fun isExpired(nowMs: Long): Boolean {
        if (sentAtMs <= 0L) return true
        return (nowMs - sentAtMs) > linkExpiryMs
    }

    fun remainingCooldownSeconds(nowMs: Long): Int {
        if (sentAtMs <= 0L) return 0
        val remMs = (sentAtMs + resendCooldownMs) - nowMs
        return if (remMs <= 0L) 0 else ((remMs + 999L) / 1000L).toInt()
    }
}

/**
 * Ensures diagnostic logs, LoginHistory, and SecurityLogs NEVER store passwords, OTP codes,
 * Firebase ID tokens, verification IDs, or unmasked email/phone PII.
 */
object AuthLogRedactor {

    private val OTP_PATTERN = Regex("\\b\\d{6}\\b")
    private val PHONE_PATTERN = Regex("\\+\\d{7,15}")
    private val EMAIL_PATTERN = Regex("[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,24}")
    private val BEARER_OR_JWT_PATTERN = Regex("eyJ[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]+(?:\\.[A-Za-z0-9_-]+)?")
    private val KEY_VALUE_SECRET_PATTERN = Regex(
        "(?i)(password|passcode|otp|pin|token|idToken|verificationId|oobCode|apiKey|secret)\\s*[:=]\\s*[^\\s,;]+"
    )

    fun maskEmail(email: String): String {
        val clean = email.trim()
        val atIdx = clean.indexOf('@')
        if (atIdx <= 0) return "***"
        val local = clean.substring(0, atIdx)
        val domain = clean.substring(atIdx + 1)
        val visible = local.take(2)
        return "$visible***@$domain"
    }

    fun maskPhone(phoneE164: String): String {
        val clean = phoneE164.filter { it.isDigit() || it == '+' }
        if (clean.length <= 6) return "***"
        return "${clean.take(4)}***${clean.takeLast(3)}"
    }

    fun redactSensitiveText(raw: String): String {
        if (raw.isBlank()) return ""
        var safe = raw.trim()
        safe = KEY_VALUE_SECRET_PATTERN.replace(safe) { match ->
            "${match.groupValues[1]}=[REDACTED]"
        }
        safe = BEARER_OR_JWT_PATTERN.replace(safe, "[JWT_REDACTED]")
        safe = EMAIL_PATTERN.replace(safe) { maskEmail(it.value) }
        safe = PHONE_PATTERN.replace(safe) { maskPhone(it.value) }
        safe = OTP_PATTERN.replace(safe, "[OTP_REDACTED]")
        return safe.take(240)
    }
}
