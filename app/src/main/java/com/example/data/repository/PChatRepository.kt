package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.auth.AuthLogRedactor
import com.example.auth.AuthValidation
import com.example.data.api.FirestoreServerFilterApi
import com.example.data.api.PChatServerFilterApi
import com.example.data.payment.CurrencyConversionEngine
import com.example.data.payment.FxTransferQuote
import com.example.data.payment.PaymentProviderGateway
import com.example.data.payment.SettlementVerificationResult
import com.example.firebase.OperationType
import com.example.firebase.handleFirestoreError
import com.example.model.AppThemeMode
import com.example.model.AuthProviderType
import com.example.model.CallSession
import com.example.model.ChatMessage
import com.example.model.Conversation
import com.example.model.DeviceRegistrationDoc
import com.example.model.DiscoverFilterState
import com.example.model.FilterPreset
import com.example.model.Friendship
import com.example.model.LocalAuthDiagnosticEntry
import com.example.model.LoginHistoryDoc
import com.example.model.MalakMessage
import com.example.model.Moment
import com.example.model.MomentComment
import com.example.model.PrivateAccountSettings
import com.example.model.SecurityLogDoc
import com.example.model.ServerFilterApiResponse
import com.example.model.ServerFilterItem
import com.example.model.SignUpDraft
import com.example.model.UserProfile
import com.example.model.UserSessionDoc
import com.example.model.UserSettingsDoc
import com.example.model.WalletSummary
import com.example.model.WalletTransaction
import com.example.model.WalletTransfer
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class PChatRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        auth = Firebase.auth
    )

    val filterApi: PChatServerFilterApi = FirestoreServerFilterApi(db, auth)

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    private fun <T> Flow<T>.logFirestoreErrors(
        uid: String,
        op: OperationType,
        path: String
    ): Flow<T> = catch { error ->
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, op, path)
        }
        throw error
    }

    // region User Profile & Private Account Settings

    suspend fun ensureUserInitialized(
        uid: String,
        displayName: String?,
        email: String?,
        preferredUsername: String? = null,
        preferredLanguage: String? = null,
        fullName: String? = null,
        authProvider: String = AuthProviderType.GOOGLE.id,
        phoneNumber: String? = null,
        emailVerified: Boolean = false,
        phoneVerified: Boolean = false
    ): Result<Unit> = runCatching {
        val cleanName = (fullName?.takeIf { it.isNotBlank() } ?: displayName?.takeIf { it.isNotBlank() } ?: "P-CHAT Explorer")
            .take(60)
        val cleanLanguage = preferredLanguage?.takeIf { it.isNotBlank() }?.take(40) ?: "English"
        val cleanCustomHandle = preferredUsername
            ?.let { AuthValidation.sanitizeUsernameHandle(it) }
            ?.takeIf { it.length >= 3 }
        val baseHandle = (cleanCustomHandle ?: email?.substringBefore("@") ?: cleanName)
            .let { AuthValidation.sanitizeUsernameHandle(it) }
            .ifBlank { "user" }
            .take(20)
        val suffix = uid.takeLast(4).lowercase().replace(Regex("[^a-z0-9]"), "0").padEnd(4, '0')
        val desiredHandle = if (cleanCustomHandle != null && cleanCustomHandle.length >= 4) {
            cleanCustomHandle.take(30)
        } else {
            "${baseHandle}_$suffix".take(30)
        }
        val safeFallbackHandle = ("pchat_${uid.takeLast(8).lowercase().replace(Regex("[^a-z0-9]"), "x")}").take(30)

        val userRef = db.collection("users").document(uid)
        val existingUser = userRef.get().await()
        if (!existingUser.exists()) {
            // Check if desired handle is available before reserving; fallback if taken
            val candidateRef = db.collection("usernames").document(desiredHandle)
            val candidateSnap = candidateRef.get().await()
            val finalHandle = if (!candidateSnap.exists() || candidateSnap.getString("userId") == uid) {
                desiredHandle
            } else {
                safeFallbackHandle
            }
            val finalHandleRef = db.collection("usernames").document(finalHandle)
            if (!finalHandleSnapExistsOrMine(finalHandleRef, uid)) {
                // ignore if collision even on fallback
            } else {
                finalHandleRef.set(
                    mapOf(
                        "username" to finalHandle,
                        "userId" to uid,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            val profilePayload = mapOf(
                "userId" to uid,
                "username" to finalHandle,
                "displayName" to cleanName,
                "fullName" to cleanName,
                "bio" to "Be Closer. Share More. Feel Happy.",
                "statusText" to "Online on P-CHAT",
                "country" to "Global",
                "countryFlag" to "🌍",
                "languages" to cleanLanguage,
                "preferredLanguage" to cleanLanguage,
                "verified" to true,
                "avatarColorHex" to "#00D4FF",
                "photoBase64" to "",
                "authProvider" to authProvider,
                "profileCompleted" to false,
                "isOnline" to true,
                "isLive" to true,
                "isDiscoverable" to true,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            userRef.set(profilePayload).await()
        } else {
            val updates = buildMap<String, Any> {
                put("isOnline", true)
                // Preserve returning accounts created prior to profileCompleted migration
                if (!existingUser.contains("profileCompleted") && !existingUser.getString("username").isNullOrBlank()) {
                    put("profileCompleted", true)
                }
                put("updatedAt", FieldValue.serverTimestamp())
            }
            userRef.update(updates).await()
        }

        val privateRef = userRef.collection("private").document("account")
        val existingPrivate = privateRef.get().await()
        if (!existingPrivate.exists()) {
            val legacyCompleted = existingUser.exists() && !existingUser.contains("profileCompleted") && !existingUser.getString("username").isNullOrBlank()
            val privatePayload = buildMap<String, Any> {
                put("userId", uid)
                put("fullName", cleanName)
                if (!email.isNullOrBlank()) put("email", email.take(160))
                if (!phoneNumber.isNullOrBlank()) put("phoneNumber", phoneNumber.take(24))
                put("country", "Global")
                put("countryFlag", "🌍")
                put("preferredLanguage", cleanLanguage)
                put("authProvider", authProvider)
                put("emailVerified", emailVerified)
                put("phoneVerified", phoneVerified)
                put("profileCompleted", legacyCompleted)
                put("showOnlineStatus", true)
                put("readReceiptsEnabled", true)
                put("allowCallsFrom", "friends")
                put("notifyMessages", true)
                put("notifyCalls", true)
                put("themeMode", AppThemeMode.BLACK.id)
                put("activeCameraFilterId", "none")
                put("discoverLanguage", "")
                put("discoverCountry", "")
                put("discoverOnlineOnly", false)
                put("discoverVerifiedOnly", false)
                put("createdAt", FieldValue.serverTimestamp())
                put("updatedAt", FieldValue.serverTimestamp())
            }
            privateRef.set(privatePayload).await()
        } else if (!existingPrivate.contains("profileCompleted") && !existingUser.getString("username").isNullOrBlank()) {
            privateRef.update(
                mapOf(
                    "profileCompleted" to true,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }

        // Ensure synchronized userSettings document exists
        val settingsRef = db.collection("userSettings").document(uid)
        val existingSettings = settingsRef.get().await()
        if (!existingSettings.exists()) {
            val isCompleted = (existingUser.exists() && existingUser.getBoolean("profileCompleted") == true) ||
                (existingUser.exists() && !existingUser.contains("profileCompleted") && !existingUser.getString("username").isNullOrBlank())
            settingsRef.set(
                mapOf(
                    "userId" to uid,
                    "preferredLanguage" to cleanLanguage,
                    "country" to (existingUser.getString("country") ?: "Global"),
                    "countryFlag" to (existingUser.getString("countryFlag") ?: "🌍"),
                    "themeMode" to (existingPrivate.getString("themeMode") ?: AppThemeMode.BLACK.id),
                    "activeCameraFilterId" to (existingPrivate.getString("activeCameraFilterId") ?: "none"),
                    "lastAuthMethod" to authProvider,
                    "emailVerified" to emailVerified,
                    "phoneVerified" to phoneVerified,
                    "profileCompleted" to isCompleted,
                    "accountStatus" to if (isCompleted) "active" else "onboarding",
                    "notifyMessages" to true,
                    "notifyCalls" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }

        // Initialize P-CHAT Wallet summary with starter welcome bonus if absent
        val walletRef = userRef.collection("wallet").document("summary")
        val existingWallet = walletRef.get().await()
        if (!existingWallet.exists()) {
            walletRef.set(
                mapOf(
                    "userId" to uid,
                    "balanceCents" to 2500L,
                    "coins" to 250L,
                    "currency" to "USD",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            userRef.collection("walletTransactions").document().set(
                mapOf(
                    "userId" to uid,
                    "txType" to "deposit",
                    "amountCents" to 2500L,
                    "coinDelta" to 250L,
                    "title" to "Welcome P-CHAT Wallet Bonus",
                    "note" to "Starter USD balance + 250 P-Coins",
                    "status" to "completed",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }

        // Create a welcome self-notes / P-CHAT Lounge conversation if user has none yet
        val existingConvs = db.collection("conversations")
            .whereArrayContains("memberUids", uid)
            .limit(1)
            .get()
            .await()
        if (existingConvs.isEmpty) {
            val convRef = db.collection("conversations").document()
            val welcomeConv = mapOf(
                "title" to "P-CHAT Lounge",
                "isGroup" to true,
                "memberUids" to listOf(uid),
                "memberNames" to cleanName,
                "createdBy" to uid,
                "lastMessageText" to "Welcome to P-CHAT! Start chatting or try MALAK AI.",
                "lastMessageSenderId" to uid,
                "lastMessageType" to "text",
                "pinnedByUids" to listOf(uid),
                "unreadByUids" to emptyList<String>(),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            convRef.set(welcomeConv).await()
            val msgRef = convRef.collection("messages").document()
            msgRef.set(
                mapOf(
                    "conversationId" to convRef.id,
                    "senderId" to uid,
                    "senderName" to "P-CHAT",
                    "text" to "Welcome to P-CHAT — Chat • Connect • Explore. Pin threads, send voice notes, capture filtered snaps, or talk live with MALAK AI.",
                    "messageType" to "text",
                    "reactions" to listOf("✨:$uid"),
                    "memberUids" to listOf(uid),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.WRITE, "users/$uid")
    }

    private suspend fun finalHandleSnapExistsOrMine(
        handleRef: com.google.firebase.firestore.DocumentReference,
        uid: String
    ): Boolean {
        val snap = handleRef.get().await()
        return !snap.exists() || snap.getString("userId") == uid
    }

    suspend fun isUsernameAvailable(rawHandle: String, currentUid: String): Result<Boolean> = runCatching {
        val validation = AuthValidation.validateUsername(rawHandle)
        require(validation.isValid) { validation.errorMessage ?: "Invalid username" }
        val snap = db.collection("usernames").document(validation.normalizedValue).get().await()
        !snap.exists() || snap.getString("userId") == currentUid
    }

    suspend fun completeOnboardingAndReserveUsername(
        uid: String,
        draft: SignUpDraft,
        authProvider: AuthProviderType,
        emailVerified: Boolean,
        phoneVerified: Boolean
    ): Result<Unit> = runCatching {
        requireUserId()
        val nameCheck = AuthValidation.validateFullName(draft.fullName)
        require(nameCheck.isValid) { nameCheck.errorMessage ?: "Enter your full name." }
        val handleCheck = AuthValidation.validateUsername(draft.username)
        require(handleCheck.isValid) { handleCheck.errorMessage ?: "Enter a valid @username." }
        val emailCheck = AuthValidation.validateEmail(draft.email)
        require(emailCheck.isValid) { emailCheck.errorMessage ?: "Enter a valid email address." }
        val phoneCheck = AuthValidation.validatePhoneE164(draft.phoneE164)
        require(phoneCheck.isValid) { phoneCheck.errorMessage ?: "Enter a valid phone number." }
        val dobCheck = AuthValidation.validateDateOfBirth(draft.dateOfBirth)
        require(dobCheck.isValid) { dobCheck.errorMessage ?: "Enter date of birth (YYYY-MM-DD)." }

        val cleanName = nameCheck.normalizedValue
        val cleanHandle = handleCheck.normalizedValue
        val cleanEmail = emailCheck.normalizedValue
        val cleanPhone = phoneCheck.normalizedValue
        val cleanDob = dobCheck.normalizedValue
        val cleanCountry = draft.country.trim().ifBlank { "Global" }.take(60)
        val cleanFlag = draft.countryFlag.trim().ifBlank { "🌍" }.take(16)
        val cleanLang = draft.preferredLanguage.trim().ifBlank { "English" }.take(40)
        val cleanAvatar = draft.avatarBase64.take(240000)

        val userRef = db.collection("users").document(uid)
        val privateRef = userRef.collection("private").document("account")
        val settingsRef = db.collection("userSettings").document(uid)
        val newHandleRef = db.collection("usernames").document(cleanHandle)

        db.runTransaction { tx ->
            val userSnap = tx.get(userRef)
            val privateSnap = tx.get(privateRef)
            val settingsSnap = tx.get(settingsRef)
            val newHandleSnap = tx.get(newHandleRef)

            if (newHandleSnap.exists() && newHandleSnap.getString("userId") != uid) {
                throw IllegalStateException("Username @$cleanHandle is already taken. Choose another handle.")
            }

            val oldHandle = userSnap.getString("username")?.lowercase()
            val oldHandleRef = if (!oldHandle.isNullOrBlank() && oldHandle != cleanHandle &&
                AuthValidation.validateUsername(oldHandle).isValid
            ) {
                db.collection("usernames").document(oldHandle)
            } else {
                null
            }
            val oldHandleSnap = oldHandleRef?.let { tx.get(it) }

            // Perform all writes after reads
            if (oldHandleRef != null && oldHandleSnap != null && oldHandleSnap.exists() && oldHandleSnap.getString("userId") == uid) {
                tx.delete(oldHandleRef)
            }
            if (!newHandleSnap.exists()) {
                tx.set(
                    newHandleRef,
                    mapOf(
                        "username" to cleanHandle,
                        "userId" to uid,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            } else {
                tx.update(newHandleRef, mapOf("updatedAt" to FieldValue.serverTimestamp()))
            }

            val existingAvatar = userSnap.getString("photoBase64").orEmpty()
            val finalAvatar = cleanAvatar.ifBlank { existingAvatar }
            if (!userSnap.exists()) {
                tx.set(
                    userRef,
                    mapOf(
                        "userId" to uid,
                        "username" to cleanHandle,
                        "displayName" to cleanName,
                        "fullName" to cleanName,
                        "bio" to "Be Closer. Share More. Feel Happy.",
                        "statusText" to "Online on P-CHAT",
                        "country" to cleanCountry,
                        "countryFlag" to cleanFlag,
                        "languages" to cleanLang,
                        "preferredLanguage" to cleanLang,
                        "verified" to true,
                        "avatarColorHex" to "#00D4FF",
                        "photoBase64" to finalAvatar,
                        "authProvider" to authProvider.id,
                        "profileCompleted" to true,
                        "isOnline" to true,
                        "isLive" to true,
                        "isDiscoverable" to true,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            } else {
                tx.update(
                    userRef,
                    mapOf(
                        "username" to cleanHandle,
                        "displayName" to cleanName,
                        "fullName" to cleanName,
                        "country" to cleanCountry,
                        "countryFlag" to cleanFlag,
                        "languages" to cleanLang,
                        "preferredLanguage" to cleanLang,
                        "photoBase64" to finalAvatar,
                        "authProvider" to authProvider.id,
                        "profileCompleted" to true,
                        "isOnline" to true,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            if (!privateSnap.exists()) {
                tx.set(
                    privateRef,
                    mapOf(
                        "userId" to uid,
                        "fullName" to cleanName,
                        "email" to cleanEmail,
                        "phoneNumber" to cleanPhone,
                        "country" to cleanCountry,
                        "countryFlag" to cleanFlag,
                        "preferredLanguage" to cleanLang,
                        "dateOfBirth" to cleanDob,
                        "authProvider" to authProvider.id,
                        "emailVerified" to emailVerified,
                        "phoneVerified" to phoneVerified,
                        "profileCompleted" to true,
                        "showOnlineStatus" to true,
                        "readReceiptsEnabled" to true,
                        "allowCallsFrom" to "friends",
                        "notifyMessages" to true,
                        "notifyCalls" to true,
                        "themeMode" to AppThemeMode.BLACK.id,
                        "activeCameraFilterId" to "none",
                        "discoverLanguage" to "",
                        "discoverCountry" to "",
                        "discoverOnlineOnly" to false,
                        "discoverVerifiedOnly" to false,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            } else {
                tx.update(
                    privateRef,
                    mapOf(
                        "fullName" to cleanName,
                        "email" to cleanEmail,
                        "phoneNumber" to cleanPhone,
                        "country" to cleanCountry,
                        "countryFlag" to cleanFlag,
                        "preferredLanguage" to cleanLang,
                        "dateOfBirth" to cleanDob,
                        "authProvider" to authProvider.id,
                        "emailVerified" to emailVerified,
                        "phoneVerified" to phoneVerified,
                        "profileCompleted" to true,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            if (!settingsSnap.exists()) {
                tx.set(
                    settingsRef,
                    mapOf(
                        "userId" to uid,
                        "preferredLanguage" to cleanLang,
                        "country" to cleanCountry,
                        "countryFlag" to cleanFlag,
                        "themeMode" to AppThemeMode.BLACK.id,
                        "activeCameraFilterId" to "none",
                        "lastAuthMethod" to authProvider.id,
                        "emailVerified" to emailVerified,
                        "phoneVerified" to phoneVerified,
                        "profileCompleted" to true,
                        "accountStatus" to "active",
                        "notifyMessages" to true,
                        "notifyCalls" to true,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            } else {
                tx.update(
                    settingsRef,
                    mapOf(
                        "preferredLanguage" to cleanLang,
                        "country" to cleanCountry,
                        "countryFlag" to cleanFlag,
                        "lastAuthMethod" to authProvider.id,
                        "emailVerified" to emailVerified,
                        "phoneVerified" to phoneVerified,
                        "profileCompleted" to true,
                        "accountStatus" to "active",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            Unit
        }.await()

        // Record registration event in loginHistory
        val entryId = "reg_${UUID.randomUUID().toString().replace("-", "").take(18)}"
        db.collection("loginHistory").document(entryId).set(
            mapOf(
                "entryId" to entryId,
                "userId" to uid,
                "provider" to authProvider.id,
                "eventType" to "registration_complete",
                "outcome" to "success",
                "statusCode" to "ONBOARDED",
                "message" to "Profile & unique handle reserved ($cleanCountry / $cleanLang)",
                "verificationStatus" to if (emailVerified || phoneVerified) "verified" else "pending_verification",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.WRITE, "users/$uid")
    }

    suspend fun syncAuthSessionOnLogin(
        uid: String,
        sessionId: String,
        deviceId: String,
        deviceModel: String,
        osVersion: String,
        provider: AuthProviderType,
        verificationStatus: String,
        emailVerified: Boolean,
        phoneVerified: Boolean,
        pendingDiagnostics: List<LocalAuthDiagnosticEntry>
    ): Result<Unit> = runCatching {
        val safeSessionId = sessionId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(120).ifBlank {
            "sess_${uid.take(8)}_${System.currentTimeMillis()}"
        }
        val safeDeviceId = deviceId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(64).ifBlank { "dev_android" }
        val safeDeviceDocId = "${uid}_${safeDeviceId}".replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(120)

        val sessionRef = db.collection("userSessions").document(safeSessionId)
        val existingSess = sessionRef.get().await()
        if (!existingSess.exists()) {
            sessionRef.set(
                mapOf(
                    "sessionId" to safeSessionId,
                    "userId" to uid,
                    "deviceId" to safeDeviceId,
                    "provider" to provider.id,
                    "status" to "active",
                    "platform" to "android",
                    "appVersion" to "1.0",
                    "lastActiveAt" to FieldValue.serverTimestamp(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } else {
            sessionRef.update(
                mapOf(
                    "status" to "active",
                    "appVersion" to "1.0",
                    "lastActiveAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }

        val deviceRef = db.collection("deviceRegistrations").document(safeDeviceDocId)
        val existingDevice = deviceRef.get().await()
        if (!existingDevice.exists()) {
            deviceRef.set(
                mapOf(
                    "deviceDocId" to safeDeviceDocId,
                    "userId" to uid,
                    "deviceId" to safeDeviceId,
                    "deviceModel" to deviceModel.take(60).ifBlank { "Android Device" },
                    "osVersion" to osVersion.take(32),
                    "appVersion" to "1.0",
                    "platform" to "android",
                    "isTrusted" to true,
                    "lastSeenAt" to FieldValue.serverTimestamp(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } else {
            deviceRef.update(
                mapOf(
                    "deviceModel" to deviceModel.take(60).ifBlank { "Android Device" },
                    "osVersion" to osVersion.take(32),
                    "appVersion" to "1.0",
                    "isTrusted" to true,
                    "lastSeenAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }

        val entryId = "login_${UUID.randomUUID().toString().replace("-", "").take(18)}"
        db.collection("loginHistory").document(entryId).set(
            mapOf(
                "entryId" to entryId,
                "userId" to uid,
                "provider" to provider.id,
                "eventType" to "login_success",
                "outcome" to "success",
                "statusCode" to "OK",
                "message" to AuthLogRedactor.redactSensitiveText("Session authenticated via ${provider.label}").take(240),
                "deviceId" to safeDeviceId,
                "verificationStatus" to verificationStatus.take(32),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()

        // Keep userSettings & private verification flags synchronized
        val settingsRef = db.collection("userSettings").document(uid)
        val settingsSnap = settingsRef.get().await()
        if (settingsSnap.exists()) {
            settingsRef.update(
                mapOf(
                    "lastAuthMethod" to provider.id,
                    "emailVerified" to emailVerified,
                    "phoneVerified" to phoneVerified,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }

        val privateRef = db.collection("users").document(uid).collection("private").document("account")
        val privateSnap = privateRef.get().await()
        if (privateSnap.exists()) {
            privateRef.update(
                mapOf(
                    "authProvider" to provider.id,
                    "emailVerified" to emailVerified,
                    "phoneVerified" to phoneVerified,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }

        flushSecurityDiagnostics(uid, provider.id, pendingDiagnostics)
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.WRITE, "userSessions")
    }

    suspend fun flushSecurityDiagnostics(
        uid: String,
        defaultProvider: String,
        entries: List<LocalAuthDiagnosticEntry>
    ): Result<Unit> = runCatching {
        if (entries.isEmpty()) return@runCatching
        entries.takeLast(20).forEach { entry ->
            val safeLogId = entry.id.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(120)
            val severity = if (entry.severity in setOf("info", "warn", "critical")) entry.severity else "info"
            val redactedSummary = AuthLogRedactor.redactSensitiveText(entry.summary).take(240)
            db.collection("securityLogs").document(safeLogId).set(
                mapOf(
                    "logId" to safeLogId,
                    "userId" to uid,
                    "category" to entry.category.take(40).ifBlank { "auth_event" },
                    "severity" to severity,
                    "provider" to entry.provider.take(32).ifBlank { defaultProvider },
                    "summary" to redactedSummary,
                    "redacted" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
        Unit
    }

    fun observeUserSettings(uid: String): Flow<UserSettingsDoc?> = flow {
        val docRef = db.collection("userSettings").document(uid)
        docRef.snapshots()
            .map { snap ->
                snap.toObject(UserSettingsDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .logFirestoreErrors(uid, OperationType.GET, docRef.path)
            .collect { emit(it) }
    }

    fun observeUserSessions(uid: String): Flow<List<UserSessionDoc>> = flow {
        db.collection("userSessions")
            .whereEqualTo("userId", uid)
            .snapshots()
            .map { snap ->
                snap.toObjects(UserSessionDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.updatedAt?.seconds ?: 0L }
            }
            .logFirestoreErrors(uid, OperationType.LIST, "userSessions")
            .collect { emit(it) }
    }

    fun observeLoginHistory(uid: String): Flow<List<LoginHistoryDoc>> = flow {
        db.collection("loginHistory")
            .whereEqualTo("userId", uid)
            .snapshots()
            .map { snap ->
                snap.toObjects(LoginHistoryDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
                    .take(40)
            }
            .logFirestoreErrors(uid, OperationType.LIST, "loginHistory")
            .collect { emit(it) }
    }

    fun observeSecurityLogs(uid: String): Flow<List<SecurityLogDoc>> = flow {
        db.collection("securityLogs")
            .whereEqualTo("userId", uid)
            .snapshots()
            .map { snap ->
                snap.toObjects(SecurityLogDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
                    .take(50)
            }
            .logFirestoreErrors(uid, OperationType.LIST, "securityLogs")
            .collect { emit(it) }
    }

    fun observeDeviceRegistrations(uid: String): Flow<List<DeviceRegistrationDoc>> = flow {
        db.collection("deviceRegistrations")
            .whereEqualTo("userId", uid)
            .snapshots()
            .map { snap ->
                snap.toObjects(DeviceRegistrationDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.lastSeenAt?.seconds ?: 0L }
            }
            .logFirestoreErrors(uid, OperationType.LIST, "deviceRegistrations")
            .collect { emit(it) }
    }

    suspend fun revokeSession(sessionId: String): Result<Unit> = runCatching {
        requireUserId()
        val safeSessionId = sessionId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(120)
        db.collection("userSessions").document(safeSessionId)
            .update(
                mapOf(
                    "status" to "revoked",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "userSessions/$sessionId")
    }

    suspend fun revokeSessionOnLogout(
        uid: String,
        sessionId: String?,
        provider: String
    ): Result<Unit> = runCatching {
        if (!sessionId.isNullOrBlank()) {
            val safeSessionId = sessionId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(120)
            val sessRef = db.collection("userSessions").document(safeSessionId)
            val snap = sessRef.get().await()
            if (snap.exists() && snap.getString("userId") == uid) {
                sessRef.update(
                    mapOf(
                        "status" to "signed_out",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        }
        val userRef = db.collection("users").document(uid)
        val userSnap = userRef.get().await()
        if (userSnap.exists()) {
            userRef.update(
                mapOf(
                    "isOnline" to false,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
        val entryId = "logout_${UUID.randomUUID().toString().replace("-", "").take(18)}"
        db.collection("loginHistory").document(entryId).set(
            mapOf(
                "entryId" to entryId,
                "userId" to uid,
                "provider" to provider.take(32).ifBlank { "google" },
                "eventType" to "logout",
                "outcome" to "success",
                "statusCode" to "LOGOUT",
                "message" to "Signed out securely",
                "verificationStatus" to "verified",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "userSessions")
    }

    suspend fun updatePrivateVerificationFlags(
        uid: String,
        emailVerified: Boolean,
        phoneVerified: Boolean
    ): Result<Unit> = runCatching {
        requireUserId()
        val privateRef = db.collection("users").document(uid).collection("private").document("account")
        privateRef.update(
            mapOf(
                "emailVerified" to emailVerified,
                "phoneVerified" to phoneVerified,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        val settingsRef = db.collection("userSettings").document(uid)
        if (settingsRef.get().await().exists()) {
            settingsRef.update(
                mapOf(
                    "emailVerified" to emailVerified,
                    "phoneVerified" to phoneVerified,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
        Unit
    }

    suspend fun testFirestoreHealthPing(uid: String): Result<Long> = runCatching {
        val start = System.currentTimeMillis()
        db.collection("users").document(uid).get().await()
        (System.currentTimeMillis() - start).coerceAtLeast(1L)
    }

    fun observeMyProfile(uid: String): Flow<UserProfile?> = flow {
        val docRef = db.collection("users").document(uid)
        docRef.snapshots()
            .map { snap ->
                snap.toObject(
                    UserProfile::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .logFirestoreErrors(uid, OperationType.GET, docRef.path)
            .collect { emit(it) }
    }

    fun observePrivateSettings(uid: String): Flow<PrivateAccountSettings?> = flow {
        val docRef = db.collection("users").document(uid).collection("private").document("account")
        docRef.snapshots()
            .map { snap ->
                snap.toObject(
                    PrivateAccountSettings::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .logFirestoreErrors(uid, OperationType.GET, docRef.path)
            .collect { emit(it) }
    }

    suspend fun updateMyProfile(
        displayName: String,
        username: String,
        bio: String,
        statusText: String,
        country: String,
        countryFlag: String,
        languages: String,
        avatarColorHex: String,
        isOnline: Boolean,
        isLive: Boolean,
        isDiscoverable: Boolean,
        fullName: String? = null,
        preferredLanguage: String? = null,
        photoBase64: String? = null
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val handleCheck = AuthValidation.validateUsername(username)
        require(handleCheck.isValid) { handleCheck.errorMessage ?: "Enter a valid @username." }
        val cleanHandle = handleCheck.normalizedValue
        val docRef = db.collection("users").document(uid)
        val privateRef = docRef.collection("private").document("account")
        val settingsRef = db.collection("userSettings").document(uid)
        val newHandleRef = db.collection("usernames").document(cleanHandle)

        db.runTransaction { tx ->
            val userSnap = tx.get(docRef)
            val privateSnap = tx.get(privateRef)
            val settingsSnap = tx.get(settingsRef)
            val newHandleSnap = tx.get(newHandleRef)
            if (newHandleSnap.exists() && newHandleSnap.getString("userId") != uid) {
                throw IllegalStateException("Username @$cleanHandle is already taken.")
            }
            val oldHandle = userSnap.getString("username")?.lowercase()
            val oldHandleRef = if (!oldHandle.isNullOrBlank() && oldHandle != cleanHandle &&
                AuthValidation.validateUsername(oldHandle).isValid
            ) {
                db.collection("usernames").document(oldHandle)
            } else {
                null
            }
            val oldHandleSnap = oldHandleRef?.let { tx.get(it) }

            if (oldHandleRef != null && oldHandleSnap != null && oldHandleSnap.exists() && oldHandleSnap.getString("userId") == uid) {
                tx.delete(oldHandleRef)
            }
            if (!newHandleSnap.exists()) {
                tx.set(
                    newHandleRef,
                    mapOf(
                        "username" to cleanHandle,
                        "userId" to uid,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            } else {
                tx.update(newHandleRef, mapOf("updatedAt" to FieldValue.serverTimestamp()))
            }

            val cleanCountry = country.trim().take(60).ifBlank { "Global" }
            val cleanFlag = countryFlag.trim().take(16).ifBlank { "🌍" }
            val updates = buildMap<String, Any> {
                put("displayName", displayName.trim().take(60).ifBlank { "P-CHAT User" })
                put("username", cleanHandle)
                put("bio", bio.trim().take(240))
                put("statusText", statusText.trim().take(100))
                put("country", cleanCountry)
                put("countryFlag", cleanFlag)
                put("languages", languages.trim().take(120).ifBlank { "English" })
                put("avatarColorHex", avatarColorHex.take(16))
                put("isOnline", isOnline)
                put("isLive", isLive)
                put("isDiscoverable", isDiscoverable)
                if (!fullName.isNullOrBlank()) put("fullName", fullName.trim().take(60))
                if (!preferredLanguage.isNullOrBlank()) put("preferredLanguage", preferredLanguage.trim().take(40))
                if (photoBase64 != null) put("photoBase64", photoBase64.take(240000))
                put("updatedAt", FieldValue.serverTimestamp())
            }
            tx.update(docRef, updates)

            if (privateSnap.exists()) {
                val privateUpdates = buildMap<String, Any> {
                    put("country", cleanCountry)
                    put("countryFlag", cleanFlag)
                    if (!fullName.isNullOrBlank()) put("fullName", fullName.trim().take(60))
                    if (!preferredLanguage.isNullOrBlank()) put("preferredLanguage", preferredLanguage.trim().take(40))
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                tx.update(privateRef, privateUpdates)
            }

            if (settingsSnap.exists()) {
                val settingsUpdates = buildMap<String, Any> {
                    put("country", cleanCountry)
                    put("countryFlag", cleanFlag)
                    if (!preferredLanguage.isNullOrBlank()) put("preferredLanguage", preferredLanguage.trim().take(40))
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                tx.update(settingsRef, settingsUpdates)
            }
            Unit
        }.await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "users/${auth.currentUser?.uid}")
    }

    suspend fun updatePrivateSettings(
        showOnlineStatus: Boolean,
        readReceiptsEnabled: Boolean,
        allowCallsFrom: String,
        notifyMessages: Boolean,
        notifyCalls: Boolean
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("private").document("account")
        docRef.update(
            mapOf(
                "showOnlineStatus" to showOnlineStatus,
                "readReceiptsEnabled" to readReceiptsEnabled,
                "allowCallsFrom" to allowCallsFrom,
                "notifyMessages" to notifyMessages,
                "notifyCalls" to notifyCalls,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        val settingsRef = db.collection("userSettings").document(uid)
        if (settingsRef.get().await().exists()) {
            settingsRef.update(
                mapOf(
                    "notifyMessages" to notifyMessages,
                    "notifyCalls" to notifyCalls,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "users/${auth.currentUser?.uid}/private/account")
    }

    // endregion

    // region Connect & Friendships

    fun observeDiscoverableUsers(uid: String): Flow<List<UserProfile>> = flow {
        db.collection("users")
            .whereEqualTo("isDiscoverable", true)
            .snapshots()
            .map { snap ->
                snap.toObjects(
                    UserProfile::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .logFirestoreErrors(uid, OperationType.LIST, "users")
            .collect { emit(it) }
    }

    fun observeFriendships(uid: String): Flow<List<Friendship>> = flow {
        db.collection("friendships")
            .whereArrayContains("userIds", uid)
            .snapshots()
            .map { snap ->
                snap.toObjects(
                    Friendship::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .logFirestoreErrors(uid, OperationType.LIST, "friendships")
            .collect { emit(it) }
    }

    suspend fun sendFriendRequest(
        requesterName: String,
        recipientId: String,
        recipientName: String
    ): Result<String> = runCatching {
        val uid = requireUserId()
        require(uid != recipientId) { "Cannot send friend request to yourself." }
        val pair = listOf(uid, recipientId).sorted()
        val friendshipId = "${pair[0]}_${pair[1]}"
        val docRef = db.collection("friendships").document(friendshipId)
        val existing = docRef.get().await()
        if (existing.exists()) {
            val status = existing.getString("status")
            if (status == "declined") {
                docRef.update(
                    mapOf(
                        "status" to "pending",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            return@runCatching friendshipId
        }
        val payload = mapOf(
            "userIds" to pair,
            "requesterId" to uid,
            "recipientId" to recipientId,
            "requesterName" to requesterName.take(60).ifBlank { "P-CHAT User" },
            "recipientName" to recipientName.take(60).ifBlank { "Friend" },
            "status" to "pending",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        docRef.set(payload).await()
        friendshipId
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.CREATE, "friendships")
    }

    suspend fun respondToFriendRequest(friendshipId: String, accept: Boolean): Result<Unit> = runCatching {
        requireUserId()
        val docRef = db.collection("friendships").document(friendshipId)
        docRef.update(
            mapOf(
                "status" to if (accept) "accepted" else "declined",
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "friendships/$friendshipId")
    }

    suspend fun removeFriendship(friendshipId: String): Result<Unit> = runCatching {
        requireUserId()
        db.collection("friendships").document(friendshipId).delete().await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.DELETE, "friendships/$friendshipId")
    }

    // endregion

    // region Conversations & Messaging

    fun observeConversations(uid: String): Flow<List<Conversation>> = flow {
        db.collection("conversations")
            .whereArrayContains("memberUids", uid)
            .snapshots()
            .map { snap ->
                snap.toObjects(
                    Conversation::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.updatedAt?.seconds ?: 0L }
            }
            .logFirestoreErrors(uid, OperationType.LIST, "conversations")
            .collect { emit(it) }
    }

    suspend fun openOrCreateDirectConversation(
        myName: String,
        otherUser: UserProfile
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val pair = listOf(uid, otherUser.userId).sorted()
        val convId = "dm_${pair[0]}_${pair[1]}".take(120)
        val docRef = db.collection("conversations").document(convId)
        val snap = docRef.get().await()
        if (!snap.exists()) {
            val payload = mapOf(
                "title" to otherUser.displayName.ifBlank { otherUser.username },
                "isGroup" to false,
                "memberUids" to pair,
                "memberNames" to "$myName, ${otherUser.displayName}",
                "createdBy" to uid,
                "lastMessageText" to "Say hello on P-CHAT!",
                "lastMessageSenderId" to uid,
                "lastMessageType" to "text",
                "pinnedByUids" to emptyList<String>(),
                "unreadByUids" to emptyList<String>(),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
        }
        convId
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.CREATE, "conversations")
    }

    suspend fun createGroupConversation(
        title: String,
        myName: String,
        members: List<UserProfile>
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val memberUids = (listOf(uid) + members.map { it.userId }).distinct().take(20)
        val memberNames = (listOf(myName) + members.map { it.displayName }).distinct().joinToString(", ").take(300)
        val docRef = db.collection("conversations").document()
        val payload = mapOf(
            "title" to title.trim().take(80).ifBlank { "Neon Squad" },
            "isGroup" to true,
            "memberUids" to memberUids,
            "memberNames" to memberNames,
            "createdBy" to uid,
            "lastMessageText" to "$myName created the group",
            "lastMessageSenderId" to uid,
            "lastMessageType" to "text",
            "pinnedByUids" to emptyList<String>(),
            "unreadByUids" to memberUids.filter { it != uid },
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        docRef.set(payload).await()
        docRef.id
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.CREATE, "conversations")
    }

    suspend fun togglePinConversation(conversation: Conversation): Result<Unit> = runCatching {
        val uid = requireUserId()
        val updatedPins = if (uid in conversation.pinnedByUids) {
            conversation.pinnedByUids - uid
        } else {
            (conversation.pinnedByUids + uid).distinct().take(20)
        }
        db.collection("conversations").document(conversation.id)
            .update(
                mapOf(
                    "pinnedByUids" to updatedPins,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "conversations/${conversation.id}")
    }

    suspend fun markConversationRead(conversation: Conversation): Result<Unit> = runCatching {
        val uid = requireUserId()
        if (uid !in conversation.unreadByUids) return@runCatching
        val updatedUnread = conversation.unreadByUids - uid
        db.collection("conversations").document(conversation.id)
            .update(
                mapOf(
                    "unreadByUids" to updatedUnread,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "conversations/${conversation.id}")
    }

    fun observeMessages(conversationId: String, uid: String): Flow<List<ChatMessage>> = flow {
        val path = "conversations/$conversationId/messages"
        db.collection("conversations")
            .document(conversationId)
            .collection("messages")
            .whereArrayContains("memberUids", uid)
            .snapshots()
            .map { snap ->
                snap.toObjects(
                    ChatMessage::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedBy { it.createdAt?.seconds ?: Long.MAX_VALUE }
            }
            .logFirestoreErrors(uid, OperationType.LIST, path)
            .collect { emit(it) }
    }

    suspend fun sendMessage(
        conversation: Conversation,
        senderName: String,
        text: String,
        messageType: String = "text",
        mediaMeta: String = "",
        imageBase64: String = ""
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val convRef = db.collection("conversations").document(conversation.id)
        val msgRef = convRef.collection("messages").document()
        val safeMembers = if (uid in conversation.memberUids) conversation.memberUids else (conversation.memberUids + uid).distinct().take(20)

        val msgPayload = buildMap<String, Any> {
            put("conversationId", conversation.id)
            put("senderId", uid)
            put("senderName", senderName.take(60).ifBlank { "Me" })
            put("text", text.take(4000))
            put("messageType", messageType)
            if (mediaMeta.isNotBlank()) put("mediaMeta", mediaMeta.take(200))
            if (imageBase64.isNotBlank()) put("imageBase64", imageBase64.take(240000))
            put("reactions", emptyList<String>())
            put("memberUids", safeMembers)
            put("createdAt", FieldValue.serverTimestamp())
            put("updatedAt", FieldValue.serverTimestamp())
        }
        msgRef.set(msgPayload).await()

        val preview = when (messageType) {
            "voice" -> "🎤 Voice note ${mediaMeta.ifBlank { "" }}".trim()
            "image" -> "📸 Photo ${text.take(60)}".trim()
            "video" -> "🎬 Video ${text.take(60)}".trim()
            "file" -> "📎 File: ${mediaMeta.ifBlank { text }}".take(120)
            "call" -> "📞 $text"
            "gift" -> "🎁 ${mediaMeta.ifBlank { text }}".take(120)
            else -> text
        }.take(500)

        convRef.update(
            mapOf(
                "lastMessageText" to preview,
                "lastMessageSenderId" to uid,
                "lastMessageType" to messageType,
                "unreadByUids" to safeMembers.filter { it != uid }.take(20),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        msgRef.id
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.CREATE, "conversations/${conversation.id}/messages")
    }

    suspend fun toggleMessageReaction(
        conversationId: String,
        message: ChatMessage,
        emoji: String
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val token = "$emoji:$uid"
        val updated = if (token in message.reactions) {
            message.reactions - token
        } else {
            (message.reactions + token).take(30)
        }
        db.collection("conversations")
            .document(conversationId)
            .collection("messages")
            .document(message.id)
            .update(
                mapOf(
                    "reactions" to updated,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "conversations/$conversationId/messages/${message.id}")
    }

    // endregion

    // region Moments & Stories

    fun observePublicMoments(uid: String): Flow<List<Moment>> = flow {
        db.collection("moments")
            .whereEqualTo("visibility", "public")
            .snapshots()
            .map { snap ->
                snap.toObjects(
                    Moment::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .logFirestoreErrors(uid, OperationType.LIST, "moments")
            .collect { emit(it) }
    }

    suspend fun createMoment(
        authorName: String,
        authorUsername: String,
        caption: String,
        mediaType: String,
        filterTag: String = "",
        imageBase64: String = "",
        isStory: Boolean = false
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("moments").document()
        val payload = buildMap<String, Any> {
            put("authorId", uid)
            put("authorName", authorName.take(60).ifBlank { "P-CHAT User" })
            put("authorUsername", authorUsername.take(30))
            put("caption", caption.trim().take(1000))
            put("mediaType", mediaType)
            if (filterTag.isNotBlank()) put("filterTag", filterTag.take(40))
            if (imageBase64.isNotBlank()) put("imageBase64", imageBase64.take(240000))
            put("isStory", isStory)
            put("visibility", "public")
            put("likedByUids", emptyList<String>())
            put("commentCount", 0L)
            put("createdAt", FieldValue.serverTimestamp())
            put("updatedAt", FieldValue.serverTimestamp())
        }
        docRef.set(payload).await()
        docRef.id
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.CREATE, "moments")
    }

    suspend fun toggleLikeMoment(moment: Moment): Result<Unit> = runCatching {
        val uid = requireUserId()
        val updated = if (uid in moment.likedByUids) {
            moment.likedByUids - uid
        } else {
            (moment.likedByUids + uid).distinct().take(50)
        }
        db.collection("moments").document(moment.id)
            .update(
                mapOf(
                    "likedByUids" to updated,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "moments/${moment.id}")
    }

    fun observeMomentComments(momentId: String, uid: String): Flow<List<MomentComment>> = flow {
        val path = "moments/$momentId/comments"
        db.collection("moments")
            .document(momentId)
            .collection("comments")
            .whereEqualTo("visibility", "public")
            .snapshots()
            .map { snap ->
                snap.toObjects(
                    MomentComment::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedBy { it.createdAt?.seconds ?: 0L }
            }
            .logFirestoreErrors(uid, OperationType.LIST, path)
            .collect { emit(it) }
    }

    suspend fun addMomentComment(
        moment: Moment,
        authorName: String,
        text: String
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val momentRef = db.collection("moments").document(moment.id)
        val commentRef = momentRef.collection("comments").document()
        val payload = mapOf(
            "momentId" to moment.id,
            "momentAuthorId" to moment.authorId,
            "authorId" to uid,
            "authorName" to authorName.take(60).ifBlank { "P-CHAT User" },
            "text" to text.trim().take(500),
            "visibility" to "public",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        commentRef.set(payload).await()
        momentRef.update(
            mapOf(
                "commentCount" to FieldValue.increment(1L),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        commentRef.id
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.CREATE, "moments/${moment.id}/comments")
    }

    // endregion

    // region Calls

    fun observeCalls(uid: String): Flow<List<CallSession>> = flow {
        db.collection("calls")
            .whereArrayContains("participantUids", uid)
            .snapshots()
            .map { snap ->
                snap.toObjects(
                    CallSession::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .logFirestoreErrors(uid, OperationType.LIST, "calls")
            .collect { emit(it) }
    }

    suspend fun startCall(
        conversation: Conversation,
        callerName: String,
        callType: String
    ): Result<CallSession> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("calls").document()
        val participants = (conversation.memberUids + uid).distinct().take(20)
        val payload = mapOf(
            "conversationId" to conversation.id,
            "callerId" to uid,
            "callerName" to callerName.take(60).ifBlank { "Me" },
            "title" to conversation.title.take(80),
            "participantUids" to participants,
            "callType" to callType,
            "status" to "ringing",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        docRef.set(payload).await()
        CallSession(
            id = docRef.id,
            conversationId = conversation.id,
            callerId = uid,
            callerName = callerName,
            title = conversation.title,
            participantUids = participants,
            callType = callType,
            status = "ringing"
        )
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.CREATE, "calls")
    }

    suspend fun updateCallStatus(callId: String, status: String): Result<Unit> = runCatching {
        requireUserId()
        db.collection("calls").document(callId)
            .update(
                mapOf(
                    "status" to status,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "calls/$callId")
    }

    // endregion

    // region MALAK AI Chat History

    fun observeMalakHistory(uid: String): Flow<List<MalakMessage>> = flow {
        val path = "users/$uid/malakChats"
        db.collection("users")
            .document(uid)
            .collection("malakChats")
            .snapshots()
            .map { snap ->
                snap.toObjects(
                    MalakMessage::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedBy { it.createdAt?.seconds ?: Long.MAX_VALUE }
            }
            .logFirestoreErrors(uid, OperationType.LIST, path)
            .collect { emit(it) }
    }

    suspend fun saveMalakMessage(
        role: String,
        text: String,
        mode: String
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("malakChats").document()
        docRef.set(
            mapOf(
                "userId" to uid,
                "role" to role,
                "text" to text.take(12000),
                "mode" to mode.take(32),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        docRef.id
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.CREATE, "users/${auth.currentUser?.uid}/malakChats")
    }

    suspend fun clearMalakHistory(): Result<Unit> = runCatching {
        val uid = requireUserId()
        val col = db.collection("users").document(uid).collection("malakChats")
        val docs = col.get().await().documents
        docs.forEach { it.reference.delete().await() }
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.DELETE, "users/${auth.currentUser?.uid}/malakChats")
    }

    // endregion

    // region P-CHAT Wallet & Transfers

    fun observeWalletSummary(uid: String): Flow<WalletSummary?> = flow {
        val docRef = db.collection("users").document(uid).collection("wallet").document("summary")
        docRef.snapshots()
            .map { snap ->
                snap.toObject(
                    WalletSummary::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .logFirestoreErrors(uid, OperationType.GET, docRef.path)
            .collect { emit(it) }
    }

    fun observeWalletTransactions(uid: String): Flow<List<WalletTransaction>> = flow {
        val path = "users/$uid/walletTransactions"
        db.collection("users")
            .document(uid)
            .collection("walletTransactions")
            .snapshots()
            .map { snap ->
                snap.toObjects(
                    WalletTransaction::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.createdAt?.seconds ?: Long.MAX_VALUE }
            }
            .logFirestoreErrors(uid, OperationType.LIST, path)
            .collect { emit(it) }
    }

    fun observeWalletTransfers(uid: String): Flow<List<WalletTransfer>> = flow {
        db.collection("walletTransfers")
            .whereArrayContains("participantUids", uid)
            .snapshots()
            .map { snap ->
                snap.toObjects(
                    WalletTransfer::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.createdAt?.seconds ?: Long.MAX_VALUE }
            }
            .logFirestoreErrors(uid, OperationType.LIST, "walletTransfers")
            .collect { emit(it) }
    }

    suspend fun depositToWallet(amountCents: Long, methodLabel: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(amountCents in 100L..10000000L) { "Top-up must be between $1.00 and $100,000.00" }
        val walletRef = db.collection("users").document(uid).collection("wallet").document("summary")
        val txRef = db.collection("users").document(uid).collection("walletTransactions").document()

        db.runTransaction { tx ->
            val snap = tx.get(walletRef)
            val currentCents = if (snap.exists()) (snap.getLong("balanceCents") ?: 0L) else 0L
            val currentCoins = if (snap.exists()) (snap.getLong("coins") ?: 0L) else 0L
            val newCents = (currentCents + amountCents).coerceAtMost(100000000L)
            if (!snap.exists()) {
                tx.set(
                    walletRef,
                    mapOf(
                        "userId" to uid,
                        "balanceCents" to newCents,
                        "coins" to currentCoins,
                        "currency" to "USD",
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            } else {
                tx.update(
                    walletRef,
                    mapOf(
                        "balanceCents" to newCents,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            tx.set(
                txRef,
                mapOf(
                    "userId" to uid,
                    "txType" to "deposit",
                    "amountCents" to amountCents,
                    "coinDelta" to 0L,
                    "title" to "Top Up via ${methodLabel.take(40).ifBlank { "Card" }}",
                    "note" to "Added funds to P-CHAT Wallet",
                    "status" to "completed",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            Unit
        }.await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.WRITE, "users/${auth.currentUser?.uid}/wallet/summary")
    }

    suspend fun withdrawFromWallet(amountCents: Long, destinationLabel: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(amountCents >= 100L) { "Minimum withdrawal is $1.00" }
        val walletRef = db.collection("users").document(uid).collection("wallet").document("summary")
        val txRef = db.collection("users").document(uid).collection("walletTransactions").document()

        db.runTransaction { tx ->
            val snap = tx.get(walletRef)
            val currentCents = snap.getLong("balanceCents") ?: 0L
            require(currentCents >= amountCents) { "Insufficient USD balance" }
            tx.update(
                walletRef,
                mapOf(
                    "balanceCents" to (currentCents - amountCents),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            tx.set(
                txRef,
                mapOf(
                    "userId" to uid,
                    "txType" to "withdraw",
                    "amountCents" to -amountCents,
                    "coinDelta" to 0L,
                    "title" to "Withdraw to ${destinationLabel.take(40).ifBlank { "Bank Account" }}",
                    "note" to "Payout processed",
                    "status" to "completed",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            Unit
        }.await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.WRITE, "users/${auth.currentUser?.uid}/wallet/summary")
    }

    suspend fun convertUsdToCoins(usdCents: Long): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(usdCents >= 100L) { "Minimum conversion is $1.00" }
        val coinsGained = usdCents // $1.00 (100 cents) = 100 P-Coins
        val walletRef = db.collection("users").document(uid).collection("wallet").document("summary")
        val txRef = db.collection("users").document(uid).collection("walletTransactions").document()

        db.runTransaction { tx ->
            val snap = tx.get(walletRef)
            val currentCents = snap.getLong("balanceCents") ?: 0L
            val currentCoins = snap.getLong("coins") ?: 0L
            require(currentCents >= usdCents) { "Insufficient USD balance to convert" }
            tx.update(
                walletRef,
                mapOf(
                    "balanceCents" to (currentCents - usdCents),
                    "coins" to (currentCoins + coinsGained).coerceAtMost(100000000L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            tx.set(
                txRef,
                mapOf(
                    "userId" to uid,
                    "txType" to "convert",
                    "amountCents" to -usdCents,
                    "coinDelta" to coinsGained,
                    "title" to "Converted to $coinsGained P-Coins",
                    "note" to "Instant USD → P-Coins swap",
                    "status" to "completed",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            Unit
        }.await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.WRITE, "users/${auth.currentUser?.uid}/wallet/summary")
    }

    suspend fun claimCoinReward(coinsAmount: Long, rewardTitle: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(coinsAmount in 1L..10_000L) { "Invalid coin reward amount" }
        val walletRef = db.collection("users").document(uid).collection("wallet").document("summary")
        val txRef = db.collection("users").document(uid).collection("walletTransactions").document()

        db.runTransaction { tx ->
            val snap = tx.get(walletRef)
            val currentCents = if (snap.exists()) (snap.getLong("balanceCents") ?: 0L) else 0L
            val currentCoins = if (snap.exists()) (snap.getLong("coins") ?: 0L) else 0L
            val newCoins = (currentCoins + coinsAmount).coerceAtMost(100_000_000L)
            if (!snap.exists()) {
                tx.set(
                    walletRef,
                    mapOf(
                        "userId" to uid,
                        "balanceCents" to currentCents,
                        "coins" to newCoins,
                        "currency" to "USD",
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            } else {
                tx.update(
                    walletRef,
                    mapOf(
                        "coins" to newCoins,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            tx.set(
                txRef,
                mapOf(
                    "userId" to uid,
                    "txType" to "gift",
                    "amountCents" to 0L,
                    "coinDelta" to coinsAmount,
                    "title" to rewardTitle.take(80).ifBlank { "P-CHAT Coins Reward" },
                    "note" to "Credited $coinsAmount P-Coins",
                    "status" to "completed",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            Unit
        }.await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.WRITE, "users/${auth.currentUser?.uid}/wallet/summary")
    }

    suspend fun sendWalletTransfer(
        senderName: String,
        recipient: UserProfile,
        amountCents: Long,
        coins: Long,
        note: String
    ): Result<String> = runCatching {
        val uid = requireUserId()
        require(uid != recipient.userId) { "Cannot transfer to yourself." }
        require(amountCents >= 0L && coins >= 0L && (amountCents > 0L || coins > 0L)) {
            "Enter a valid USD amount or P-Coins."
        }
        val walletRef = db.collection("users").document(uid).collection("wallet").document("summary")
        val txRef = db.collection("users").document(uid).collection("walletTransactions").document()
        val transferRef = db.collection("walletTransfers").document()
        val pair = listOf(uid, recipient.userId).sorted()

        db.runTransaction { tx ->
            val snap = tx.get(walletRef)
            val currentCents = snap.getLong("balanceCents") ?: 0L
            val currentCoins = snap.getLong("coins") ?: 0L
            require(currentCents >= amountCents) { "Insufficient USD balance" }
            require(currentCoins >= coins) { "Insufficient P-Coins" }

            tx.update(
                walletRef,
                mapOf(
                    "balanceCents" to (currentCents - amountCents),
                    "coins" to (currentCoins - coins),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            tx.set(
                txRef,
                mapOf(
                    "userId" to uid,
                    "txType" to if (coins > 0L && amountCents == 0L) "gift" else "transfer_out",
                    "amountCents" to -amountCents,
                    "coinDelta" to -coins,
                    "title" to "Sent to ${recipient.displayName.take(40)}",
                    "counterpartyId" to recipient.userId,
                    "counterpartyName" to recipient.displayName.take(60),
                    "note" to note.trim().take(200).ifBlank { "P-CHAT Wallet Transfer" },
                    "status" to "completed",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            tx.set(
                transferRef,
                buildMap<String, Any> {
                    put("senderId", uid)
                    put("senderName", senderName.take(60).ifBlank { "P-CHAT Friend" })
                    put("recipientId", recipient.userId)
                    put("recipientName", recipient.displayName.take(60).ifBlank { "Friend" })
                    put("participantUids", pair)
                    put("amountCents", amountCents)
                    put("coins", coins)
                    if (note.isNotBlank()) put("note", note.trim().take(200))
                    put("status", "sent")
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
            )
            Unit
        }.await()
        transferRef.id
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.WRITE, "walletTransfers")
    }

    data class InternationalTransferReceiptResult(
        val transferId: String,
        val senderReceipt: WalletTransaction,
        val settlementRef: String,
        val receiptId: String
    )

    suspend fun sendInternationalTransfer(
        senderName: String,
        recipient: UserProfile,
        quote: FxTransferQuote,
        note: String
    ): Result<InternationalTransferReceiptResult> = runCatching {
        val uid = requireUserId()
        require(uid != recipient.userId) { "Cannot transfer to yourself." }

        val walletRef = db.collection("users").document(uid).collection("wallet").document("summary")
        val preSnap = walletRef.get().await()
        val availableUsdCents = if (preSnap.exists()) (preSnap.getLong("balanceCents") ?: 0L) else 0L

        // Step 1: Process through approved payment provider & verify settlement BEFORE touching balances
        val settlement = when (
            val res = PaymentProviderGateway.processAndVerifySettlement(
                senderUid = uid,
                recipientUid = recipient.userId,
                quote = quote,
                senderAvailableUsdCents = availableUsdCents
            )
        ) {
            is SettlementVerificationResult.Verified -> res
            is SettlementVerificationResult.Rejected -> throw IllegalStateException(res.reason)
        }

        // Step 2: Settlement verified -> atomically deduct sender balance, write transfer ticket, and generate sender receipt
        val txRef = db.collection("users").document(uid).collection("walletTransactions").document()
        val transferRef = db.collection("walletTransfers").document()
        val pair = listOf(uid, recipient.userId).sorted()
        val cleanNote = note.trim().take(200).ifBlank {
            "Intl Transfer (${quote.recipientCountry} • ${quote.recipientCurrency.currencyCode})"
        }
        val cleanRecipientName = recipient.displayName.take(60).ifBlank { "Friend" }
        val txTitle = "Sent ${quote.recipientCurrency.currencyCode} to ${recipient.displayName.take(28)}"

        db.runTransaction { tx ->
            val snap = tx.get(walletRef)
            val currentCents = snap.getLong("balanceCents") ?: 0L
            require(currentCents >= quote.totalDebitedCents) {
                "Insufficient USD balance to cover amount + fees."
            }

            tx.update(
                walletRef,
                mapOf(
                    "balanceCents" to (currentCents - quote.totalDebitedCents),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            tx.set(
                txRef,
                mapOf(
                    "userId" to uid,
                    "txType" to "transfer_out",
                    "amountCents" to -quote.totalDebitedCents,
                    "coinDelta" to 0L,
                    "title" to txTitle,
                    "counterpartyId" to recipient.userId,
                    "counterpartyName" to cleanRecipientName,
                    "note" to cleanNote,
                    "baseAmountCents" to quote.sendUsdCents,
                    "feeCents" to quote.totalFeeCents,
                    "currency" to "USD",
                    "localCurrency" to quote.recipientCurrency.currencyCode,
                    "localAmountMinor" to quote.recipientLocalAmountMinor,
                    "exchangeRate" to quote.exchangeRate,
                    "recipientCountry" to quote.recipientCountry.take(60),
                    "paymentProvider" to settlement.providerName.take(60),
                    "settlementRef" to settlement.settlementRef,
                    "receiptId" to settlement.receiptId,
                    "settlementVerified" to true,
                    "status" to "completed",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            tx.set(
                transferRef,
                mapOf(
                    "senderId" to uid,
                    "senderName" to senderName.take(60).ifBlank { "P-CHAT Friend" },
                    "recipientId" to recipient.userId,
                    "recipientName" to cleanRecipientName,
                    "participantUids" to pair,
                    "amountCents" to quote.sendUsdCents,
                    "coins" to 0L,
                    "note" to cleanNote,
                    "senderCurrency" to "USD",
                    "recipientCountry" to quote.recipientCountry.take(60),
                    "recipientCurrency" to quote.recipientCurrency.currencyCode,
                    "exchangeRate" to quote.exchangeRate,
                    "recipientLocalAmountMinor" to quote.recipientLocalAmountMinor,
                    "feeCents" to quote.totalFeeCents,
                    "totalDebitedCents" to quote.totalDebitedCents,
                    "paymentProvider" to settlement.providerName.take(60),
                    "settlementRef" to settlement.settlementRef,
                    "receiptId" to settlement.receiptId,
                    "settlementVerified" to true,
                    "status" to "sent",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            Unit
        }.await()

        val receipt = WalletTransaction(
            id = txRef.id,
            userId = uid,
            txType = "transfer_out",
            amountCents = -quote.totalDebitedCents,
            coinDelta = 0L,
            title = txTitle,
            counterpartyId = recipient.userId,
            counterpartyName = cleanRecipientName,
            note = cleanNote,
            baseAmountCents = quote.sendUsdCents,
            feeCents = quote.totalFeeCents,
            currency = "USD",
            localCurrency = quote.recipientCurrency.currencyCode,
            localAmountMinor = quote.recipientLocalAmountMinor,
            exchangeRate = quote.exchangeRate,
            recipientCountry = quote.recipientCountry,
            paymentProvider = settlement.providerName,
            settlementRef = settlement.settlementRef,
            receiptId = settlement.receiptId,
            settlementVerified = true,
            status = "completed"
        )

        InternationalTransferReceiptResult(
            transferId = transferRef.id,
            senderReceipt = receipt,
            settlementRef = settlement.settlementRef,
            receiptId = settlement.receiptId
        )
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.WRITE, "walletTransfers")
    }

    suspend fun claimIncomingTransfer(transfer: WalletTransfer): Result<WalletTransaction?> = runCatching {
        val uid = requireUserId()
        require(transfer.recipientId == uid) { "Only the recipient can claim this transfer." }
        if (transfer.status != "sent") return@runCatching null

        // Verify settlement metadata if this is an international / provider-settled transfer
        val isFxTransfer = transfer.settlementVerified ||
            (transfer.recipientCurrency.isNotBlank() && transfer.recipientCurrency != "USD")
        if (isFxTransfer) {
            require(transfer.settlementVerified && transfer.settlementRef.isNotBlank() && transfer.receiptId.isNotBlank()) {
                "Transfer settlement is not yet verified by payment provider."
            }
        }

        val transferRef = db.collection("walletTransfers").document(transfer.id)
        val walletRef = db.collection("users").document(uid).collection("wallet").document("summary")
        val txRef = db.collection("users").document(uid).collection("walletTransactions").document()

        val targetCurrency = transfer.recipientCurrency.ifBlank { "USD" }
        val targetLocalDelta = if (transfer.recipientLocalAmountMinor > 0L) {
            transfer.recipientLocalAmountMinor
        } else {
            transfer.amountCents
        }
        val targetCountry = transfer.recipientCountry.ifBlank {
            CurrencyConversionEngine.currencyByCode(targetCurrency).countryName
        }
        val txTitle = if (transfer.amountCents > 0L && targetLocalDelta > 0L) {
            "Received $targetCurrency from ${transfer.senderName.take(28)}"
        } else {
            "Received from ${transfer.senderName.take(40)}"
        }
        val txNote = transfer.note.ifBlank { "Claimed in $targetCurrency" }.take(200)

        var alreadyClaimed = false

        db.runTransaction { tx ->
            val transferSnap = tx.get(transferRef)
            if (transferSnap.getString("status") != "sent") {
                alreadyClaimed = true
                return@runTransaction
            }
            val walletSnap = tx.get(walletRef)
            val currentCents = if (walletSnap.exists()) (walletSnap.getLong("balanceCents") ?: 0L) else 0L
            val currentCoins = if (walletSnap.exists()) (walletSnap.getLong("coins") ?: 0L) else 0L
            val existingLocalCurrency = if (walletSnap.exists()) (walletSnap.getString("localCurrency") ?: "USD") else "USD"
            val existingLocalMinor = if (walletSnap.exists()) (walletSnap.getLong("localBalanceMinor") ?: 0L) else 0L

            val updatedLocalMinor = if (existingLocalCurrency == targetCurrency || existingLocalMinor == 0L) {
                (existingLocalMinor + targetLocalDelta).coerceAtMost(100_000_000_000L)
            } else {
                // Keep accumulated local balance cleanly converted into recipient's claimed local currency
                targetLocalDelta.coerceAtMost(100_000_000_000L)
            }

            tx.update(
                transferRef,
                mapOf(
                    "status" to "claimed",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            if (!walletSnap.exists()) {
                tx.set(
                    walletRef,
                    mapOf(
                        "userId" to uid,
                        "balanceCents" to (currentCents + transfer.amountCents).coerceAtMost(100000000L),
                        "coins" to (currentCoins + transfer.coins).coerceAtMost(100000000L),
                        "currency" to "USD",
                        "localCurrency" to targetCurrency,
                        "localBalanceMinor" to updatedLocalMinor,
                        "localCountry" to targetCountry.take(60),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            } else {
                tx.update(
                    walletRef,
                    mapOf(
                        "balanceCents" to (currentCents + transfer.amountCents).coerceAtMost(100000000L),
                        "coins" to (currentCoins + transfer.coins).coerceAtMost(100000000L),
                        "localCurrency" to targetCurrency,
                        "localBalanceMinor" to updatedLocalMinor,
                        "localCountry" to targetCountry.take(60),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            val receiptMap = buildMap<String, Any> {
                put("userId", uid)
                put("txType", "transfer_in")
                put("amountCents", transfer.amountCents)
                put("coinDelta", transfer.coins)
                put("title", txTitle)
                put("counterpartyId", transfer.senderId)
                put("counterpartyName", transfer.senderName.take(60))
                put("note", txNote)
                put("baseAmountCents", transfer.amountCents)
                put("feeCents", transfer.feeCents)
                put("currency", transfer.senderCurrency.ifBlank { "USD" })
                put("localCurrency", targetCurrency)
                put("localAmountMinor", targetLocalDelta)
                put("exchangeRate", if (transfer.exchangeRate > 0.0) transfer.exchangeRate else 1.0)
                put("recipientCountry", targetCountry.take(60))
                if (transfer.paymentProvider.isNotBlank()) put("paymentProvider", transfer.paymentProvider.take(60))
                if (transfer.settlementRef.isNotBlank()) put("settlementRef", transfer.settlementRef.take(64))
                if (transfer.receiptId.isNotBlank()) put("receiptId", transfer.receiptId.take(64))
                put("settlementVerified", transfer.settlementVerified)
                put("status", "completed")
                put("createdAt", FieldValue.serverTimestamp())
                put("updatedAt", FieldValue.serverTimestamp())
            }
            tx.set(txRef, receiptMap)
            Unit
        }.await()

        if (alreadyClaimed) null else WalletTransaction(
            id = txRef.id,
            userId = uid,
            txType = "transfer_in",
            amountCents = transfer.amountCents,
            coinDelta = transfer.coins,
            title = txTitle,
            counterpartyId = transfer.senderId,
            counterpartyName = transfer.senderName,
            note = txNote,
            baseAmountCents = transfer.amountCents,
            feeCents = transfer.feeCents,
            currency = transfer.senderCurrency.ifBlank { "USD" },
            localCurrency = targetCurrency,
            localAmountMinor = targetLocalDelta,
            exchangeRate = if (transfer.exchangeRate > 0.0) transfer.exchangeRate else 1.0,
            recipientCountry = targetCountry,
            paymentProvider = transfer.paymentProvider,
            settlementRef = transfer.settlementRef,
            receiptId = transfer.receiptId,
            settlementVerified = transfer.settlementVerified,
            status = "completed"
        )
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.WRITE, "walletTransfers/${transfer.id}")
    }

    // endregion

    // region Theme & Server Filter API

    fun observeServerFilters(uid: String): Flow<List<ServerFilterItem>> =
        filterApi.observeServerFilters(uid)

    fun observeFilterPresets(uid: String): Flow<List<FilterPreset>> =
        filterApi.observeFilterPresets(uid)

    suspend fun syncServerFilterCatalog(uid: String): Result<List<ServerFilterItem>> =
        filterApi.syncServerFilterCatalog(uid)

    suspend fun recordFilterUsage(filterId: String): Result<Unit> =
        filterApi.recordFilterUsage(filterId)

    suspend fun saveFilterPreset(
        uid: String,
        name: String,
        themeMode: AppThemeMode,
        state: DiscoverFilterState
    ): Result<String> = filterApi.saveFilterPreset(uid, name, themeMode, state)

    suspend fun deleteFilterPreset(uid: String, presetId: String): Result<Unit> =
        filterApi.deleteFilterPreset(uid, presetId)

    suspend fun updateThemeAndFilters(
        uid: String,
        themeMode: AppThemeMode,
        state: DiscoverFilterState
    ): Result<Unit> = filterApi.updateThemeAndFilters(uid, themeMode, state).also { result ->
        if (result.isSuccess) {
            runCatching {
                val settingsRef = db.collection("userSettings").document(uid)
                if (settingsRef.get().await().exists()) {
                    settingsRef.update(
                        mapOf(
                            "themeMode" to themeMode.id,
                            "activeCameraFilterId" to state.cameraFilterId.take(40),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }
        }
    }

    suspend fun queryFilteredDirectory(
        uid: String,
        searchQuery: String,
        state: DiscoverFilterState
    ): Result<ServerFilterApiResponse> = filterApi.queryFilteredDirectory(uid, searchQuery, state)

    // endregion

    companion object {
        fun deterministicDirectId(uidA: String, uidB: String): String {
            val pair = listOf(uidA, uidB).sorted()
            return "dm_${pair[0]}_${pair[1]}"
        }

        fun randomDocId(): String = UUID.randomUUID().toString()
    }
}
