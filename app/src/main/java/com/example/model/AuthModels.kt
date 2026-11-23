package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class AuthProviderType(
    val id: String,
    val label: String
) {
    GOOGLE("google", "Google Account"),
    PHONE("phone", "Phone SMS"),
    EMAIL_LINK("email_link", "Email Link");

    companion object {
        fun fromId(raw: String?): AuthProviderType {
            return entries.firstOrNull { it.id.equals(raw, ignoreCase = true) } ?: GOOGLE
        }
    }
}

data class SignUpDraft(
    val fullName: String = "",
    val username: String = "",
    val email: String = "",
    val phoneE164: String = "",
    val country: String = "United States",
    val countryFlag: String = "🇺🇸",
    val preferredLanguage: String = "English",
    val dateOfBirth: String = "",
    val avatarBase64: String = "",
    val preferredProvider: AuthProviderType = AuthProviderType.GOOGLE
)

data class KnownAccountSummary(
    val uid: String,
    val displayName: String,
    val username: String,
    val maskedIdentifier: String,
    val provider: AuthProviderType
)

data class LocalAuthDiagnosticEntry(
    val id: String,
    val timestampMs: Long,
    val category: String,
    val severity: String,
    val provider: String,
    val outcome: String,
    val summary: String,
    val statusCode: String = ""
)

data class FirebaseHealthStatus(
    val projectId: String,
    val databaseId: String,
    val firebaseAppInitialized: Boolean,
    val hasGoogleWebClientId: Boolean,
    val hasAppCheckKey: Boolean,
    val networkAvailable: Boolean,
    val firestoreReachable: Boolean?,
    val firestoreLatencyMs: Long?,
    val firestoreStatusMessage: String
)

@Keep
@IgnoreExtraProperties
data class UsernameClaim(
    @DocumentId val id: String = "",
    val username: String = "",
    val userId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class UserSettingsDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val preferredLanguage: String = "English",
    val country: String = "Global",
    val countryFlag: String = "🌍",
    val themeMode: String = "black",
    val activeCameraFilterId: String = "none",
    val lastAuthMethod: String = "google",
    val emailVerified: Boolean = false,
    val phoneVerified: Boolean = false,
    val profileCompleted: Boolean = false,
    val accountStatus: String = "active",
    val notifyMessages: Boolean = true,
    val notifyCalls: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class UserSessionDoc(
    @DocumentId val id: String = "",
    val sessionId: String = "",
    val userId: String = "",
    val deviceId: String = "",
    val provider: String = "google",
    val status: String = "active",
    val platform: String = "android",
    val appVersion: String = "1.0",
    @ServerTimestamp val lastActiveAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class LoginHistoryDoc(
    @DocumentId val id: String = "",
    val entryId: String = "",
    val userId: String = "",
    val provider: String = "google",
    val eventType: String = "login_success",
    val outcome: String = "success",
    val statusCode: String = "OK",
    val message: String = "",
    val deviceId: String = "",
    val verificationStatus: String = "verified",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class SecurityLogDoc(
    @DocumentId val id: String = "",
    val logId: String = "",
    val userId: String = "",
    val category: String = "auth_attempt",
    val severity: String = "info",
    val provider: String = "google",
    val summary: String = "",
    val redacted: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class DeviceRegistrationDoc(
    @DocumentId val id: String = "",
    val deviceDocId: String = "",
    val userId: String = "",
    val deviceId: String = "",
    val deviceModel: String = "Android",
    val osVersion: String = "",
    val appVersion: String = "1.0",
    val platform: String = "android",
    val isTrusted: Boolean = true,
    @ServerTimestamp val lastSeenAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
