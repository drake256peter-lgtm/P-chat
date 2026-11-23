package com.example.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.auth.AuthDiagnosticsManager
import com.example.auth.AuthLogRedactor
import com.example.auth.AuthValidation
import com.example.auth.PendingAuthPrefs
import com.example.auth.detectCurrentDeviceModel
import com.example.auth.inferProviderTypeFromFirebaseUser
import com.example.auth.refreshCurrentUserVerificationStatus
import com.example.auth.sendFirebaseVerificationEmail
import com.example.auth.signOut
import com.example.data.payment.CurrencyConversionEngine
import com.example.data.payment.FxRatesSnapshot
import com.example.data.payment.FxTransferQuote
import com.example.data.repository.MalakAiRepository
import com.example.data.repository.MalakMode
import com.example.data.repository.PChatRepository
import com.example.firebase.friendlyFirestoreErrorMessage
import com.example.model.AppThemeMode
import com.example.model.AuthProviderType
import com.example.model.CallSession
import com.example.model.ChatMessage
import com.example.model.Conversation
import com.example.model.DeviceRegistrationDoc
import com.example.model.DiscoverFilterState
import com.example.model.FilterPreset
import com.example.model.FilterSortOrder
import com.example.model.FirebaseHealthStatus
import com.example.model.Friendship
import com.example.model.KnownAccountSummary
import com.example.model.LoginHistoryDoc
import com.example.model.MalakMessage
import com.example.model.Moment
import com.example.model.MomentComment
import com.example.model.PrivateAccountSettings
import com.example.model.SecurityLogDoc
import com.example.model.ServerFilterApiResponse
import com.example.model.ServerFilterItem
import com.example.model.SignUpDraft
import com.example.model.UiState
import com.example.model.UserProfile
import com.example.model.UserSessionDoc
import com.example.model.UserSettingsDoc
import com.example.model.WalletSummary
import com.example.model.WalletTransaction
import com.example.model.WalletTransfer
import com.example.util.SnapFilter
import com.google.firebase.Firebase
import com.google.firebase.ai.type.LiveSession
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "PChatViewModel"

enum class ChatFilter(val label: String) {
    ALL("All"),
    UNREAD("Unread"),
    GROUPS("Groups")
}

sealed interface MalakStreamState {
    data object Idle : MalakStreamState
    data object Loading : MalakStreamState
    data class Streaming(val text: String) : MalakStreamState
    data class Error(val message: String) : MalakStreamState
}

sealed interface ImageStudioState {
    data object Idle : ImageStudioState
    data object Loading : ImageStudioState
    data class Success(val bitmap: Bitmap, val prompt: String) : ImageStudioState
    data class Error(val message: String) : ImageStudioState
}

sealed interface LiveVoiceState {
    data object Disconnected : LiveVoiceState
    data object Connecting : LiveVoiceState
    data class Active(val isMuted: Boolean = false) : LiveVoiceState
    data class Error(val message: String) : LiveVoiceState
}

sealed interface UsernameAvailabilityState {
    data object Idle : UsernameAvailabilityState
    data class Checking(val handle: String) : UsernameAvailabilityState
    data class Available(val handle: String) : UsernameAvailabilityState
    data class Taken(val handle: String) : UsernameAvailabilityState
    data class Invalid(val reason: String) : UsernameAvailabilityState
}

class PChatViewModel(
    private val repo: PChatRepository,
    private val aiRepo: MalakAiRepository,
    val currentUserId: String,
    private val initialDisplayName: String?,
    private val initialEmail: String?,
    private val initialUsername: String? = null,
    private val initialFullName: String? = null,
    private val initialLanguage: String? = null,
    private val initialProvider: AuthProviderType = AuthProviderType.GOOGLE,
    private val initialPhoneNumber: String? = null,
    private val initialEmailVerified: Boolean = false,
    private val initialPhoneVerified: Boolean = false,
    private val initialDeviceId: String = "dev_android",
    private val initialSessionId: String = "",
    private val initialDeviceModel: String = "Android",
    private val initialOsVersion: String = "Android",
    private val appContext: Context? = null
) : ViewModel() {

    private fun <T> Flow<T>.toUiStateFlow(errorLabel: String): StateFlow<UiState<T>> =
        map<T, UiState<T>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, errorLabel, rootError)
                emit(UiState.Error(rootError.message ?: errorLabel))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = UiState.Loading
            )

    val myProfile: StateFlow<UiState<UserProfile?>> =
        repo.observeMyProfile(currentUserId).toUiStateFlow("Failed to load profile")

    val privateSettings: StateFlow<UiState<PrivateAccountSettings?>> =
        repo.observePrivateSettings(currentUserId).toUiStateFlow("Failed to load settings")

    val discoverableUsers: StateFlow<UiState<List<UserProfile>>> =
        repo.observeDiscoverableUsers(currentUserId).toUiStateFlow("Failed to load directory")

    val friendships: StateFlow<UiState<List<Friendship>>> =
        repo.observeFriendships(currentUserId).toUiStateFlow("Failed to load friends")

    val conversations: StateFlow<UiState<List<Conversation>>> =
        repo.observeConversations(currentUserId).toUiStateFlow("Failed to load chats")

    val moments: StateFlow<UiState<List<Moment>>> =
        repo.observePublicMoments(currentUserId).toUiStateFlow("Failed to load moments")

    val calls: StateFlow<UiState<List<CallSession>>> =
        repo.observeCalls(currentUserId).toUiStateFlow("Failed to load calls")

    val malakHistory: StateFlow<UiState<List<MalakMessage>>> =
        repo.observeMalakHistory(currentUserId).toUiStateFlow("Failed to load MALAK history")

    val walletSummary: StateFlow<UiState<WalletSummary?>> =
        repo.observeWalletSummary(currentUserId).toUiStateFlow("Failed to load wallet balance")

    val walletTransactions: StateFlow<UiState<List<WalletTransaction>>> =
        repo.observeWalletTransactions(currentUserId).toUiStateFlow("Failed to load transactions")

    val walletTransfers: StateFlow<UiState<List<WalletTransfer>>> =
        repo.observeWalletTransfers(currentUserId).toUiStateFlow("Failed to load transfers")

    val serverFilters: StateFlow<UiState<List<ServerFilterItem>>> =
        repo.observeServerFilters(currentUserId).toUiStateFlow("Failed to load server filter catalog")

    val filterPresets: StateFlow<UiState<List<FilterPreset>>> =
        repo.observeFilterPresets(currentUserId).toUiStateFlow("Failed to load saved presets")

    val userSettings: StateFlow<UiState<UserSettingsDoc?>> =
        repo.observeUserSettings(currentUserId).toUiStateFlow("Failed to load user settings")

    val userSessions: StateFlow<UiState<List<UserSessionDoc>>> =
        repo.observeUserSessions(currentUserId).toUiStateFlow("Failed to load active sessions")

    val loginHistory: StateFlow<UiState<List<LoginHistoryDoc>>> =
        repo.observeLoginHistory(currentUserId).toUiStateFlow("Failed to load login history")

    val securityLogs: StateFlow<UiState<List<SecurityLogDoc>>> =
        repo.observeSecurityLogs(currentUserId).toUiStateFlow("Failed to load security logs")

    val deviceRegistrations: StateFlow<UiState<List<DeviceRegistrationDoc>>> =
        repo.observeDeviceRegistrations(currentUserId).toUiStateFlow("Failed to load devices")

    private val _usernameAvailability = MutableStateFlow<UsernameAvailabilityState>(UsernameAvailabilityState.Idle)
    val usernameAvailability: StateFlow<UsernameAvailabilityState> = _usernameAvailability.asStateFlow()

    private val _isOnboardingSaving = MutableStateFlow(false)
    val isOnboardingSaving: StateFlow<Boolean> = _isOnboardingSaving.asStateFlow()

    private val _firebaseHealthStatus = MutableStateFlow<FirebaseHealthStatus?>(null)
    val firebaseHealthStatus: StateFlow<FirebaseHealthStatus?> = _firebaseHealthStatus.asStateFlow()

    private val _themeMode = MutableStateFlow(AppThemeMode.BLACK)
    val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    private val _discoverFilters = MutableStateFlow(DiscoverFilterState())
    val discoverFilters: StateFlow<DiscoverFilterState> = _discoverFilters.asStateFlow()

    private val _serverApiResponse = MutableStateFlow<ServerFilterApiResponse?>(null)
    val serverApiResponse: StateFlow<ServerFilterApiResponse?> = _serverApiResponse.asStateFlow()

    private val _isFilterApiSyncing = MutableStateFlow(false)
    val isFilterApiSyncing: StateFlow<Boolean> = _isFilterApiSyncing.asStateFlow()

    val activeCameraFilter: SnapFilter
        get() = SnapFilter.fromId(_discoverFilters.value.cameraFilterId)

    private val _fxRates = MutableStateFlow(CurrencyConversionEngine.defaultSnapshot())
    val fxRates: StateFlow<FxRatesSnapshot> = _fxRates.asStateFlow()

    private val _isFxRefreshing = MutableStateFlow(false)
    val isFxRefreshing: StateFlow<Boolean> = _isFxRefreshing.asStateFlow()

    private val _isSettlementInProgress = MutableStateFlow(false)
    val isSettlementInProgress: StateFlow<Boolean> = _isSettlementInProgress.asStateFlow()

    private val _activeReceipt = MutableStateFlow<WalletTransaction?>(null)
    val activeReceipt: StateFlow<WalletTransaction?> = _activeReceipt.asStateFlow()

    private val _localMomentVideos = MutableStateFlow<Map<String, String>>(emptyMap())
    val localMomentVideos: StateFlow<Map<String, String>> = _localMomentVideos.asStateFlow()

    private var pendingThemeOrFilterWrites = 0

    private val _chatFilter = MutableStateFlow(ChatFilter.ALL)
    val chatFilter: StateFlow<ChatFilter> = _chatFilter.asStateFlow()

    private val _chatSearchQuery = MutableStateFlow("")
    val chatSearchQuery: StateFlow<String> = _chatSearchQuery.asStateFlow()

    private val _malakMode = MutableStateFlow(MalakMode.CHAT)
    val malakMode: StateFlow<MalakMode> = _malakMode.asStateFlow()

    private val _malakStreamState = MutableStateFlow<MalakStreamState>(MalakStreamState.Idle)
    val malakStreamState: StateFlow<MalakStreamState> = _malakStreamState.asStateFlow()

    private val _imageStudioState = MutableStateFlow<ImageStudioState>(ImageStudioState.Idle)
    val imageStudioState: StateFlow<ImageStudioState> = _imageStudioState.asStateFlow()

    private val _liveVoiceState = MutableStateFlow<LiveVoiceState>(LiveVoiceState.Disconnected)
    val liveVoiceState: StateFlow<LiveVoiceState> = _liveVoiceState.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    @OptIn(PublicPreviewAPI::class)
    private var liveSession: LiveSession? = null

    init {
        initializeAccountSession()
        viewModelScope.launch {
            privateSettings.collect { state ->
                val settings = (state as? UiState.Success)?.data ?: return@collect
                if (pendingThemeOrFilterWrites == 0) {
                    _themeMode.value = AppThemeMode.fromId(settings.themeMode)
                    _discoverFilters.value = DiscoverFilterState(
                        language = settings.discoverLanguage,
                        country = settings.discoverCountry,
                        onlineOnly = settings.discoverOnlineOnly,
                        verifiedOnly = settings.discoverVerifiedOnly,
                        cameraFilterId = settings.activeCameraFilterId.ifBlank { SnapFilter.NONE.id },
                        sortBy = _discoverFilters.value.sortBy
                    )
                }
            }
        }
        viewModelScope.launch {
            myProfile.collect { state ->
                val profile = (state as? UiState.Success)?.data ?: return@collect
                val ctx = appContext ?: return@collect
                val masked = when {
                    !initialEmail.isNullOrBlank() -> AuthLogRedactor.maskEmail(initialEmail)
                    !initialPhoneNumber.isNullOrBlank() -> AuthLogRedactor.maskPhone(initialPhoneNumber)
                    else -> "@${profile.username}"
                }
                PendingAuthPrefs.rememberAccountSummary(
                    ctx,
                    KnownAccountSummary(
                        uid = profile.userId.ifBlank { currentUserId },
                        displayName = profile.displayName.ifBlank { "P-CHAT Explorer" },
                        username = profile.username,
                        maskedIdentifier = masked,
                        provider = AuthProviderType.fromId(profile.authProvider.ifBlank { initialProvider.id })
                    )
                )
            }
        }
    }

    fun retryInitialization() {
        initializeAccountSession()
    }

    private fun initializeAccountSession() {
        viewModelScope.launch {
            repo.ensureUserInitialized(
                uid = currentUserId,
                displayName = initialDisplayName,
                email = initialEmail,
                preferredUsername = initialUsername,
                preferredLanguage = initialLanguage,
                fullName = initialFullName,
                authProvider = initialProvider.id,
                phoneNumber = initialPhoneNumber,
                emailVerified = initialEmailVerified,
                phoneVerified = initialPhoneVerified
            ).onSuccess {
                repo.syncServerFilterCatalog(currentUserId)
                val verificationLabel = when {
                    initialEmailVerified && initialPhoneVerified -> "fully_verified"
                    initialEmailVerified -> "email_verified"
                    initialPhoneVerified -> "phone_verified"
                    else -> "pending_verification"
                }
                repo.syncAuthSessionOnLogin(
                    uid = currentUserId,
                    sessionId = initialSessionId.ifBlank { "sess_${currentUserId.take(10)}" },
                    deviceId = initialDeviceId,
                    deviceModel = initialDeviceModel,
                    osVersion = initialOsVersion,
                    provider = initialProvider,
                    verificationStatus = verificationLabel,
                    emailVerified = initialEmailVerified,
                    phoneVerified = initialPhoneVerified,
                    pendingDiagnostics = AuthDiagnosticsManager.drainPendingForFirestore()
                )
            }.onFailure { e ->
                Log.w(TAG, "Profile initialization notice: ${e.message}", e)
                _statusBanner.value = friendlyFirestoreErrorMessage(e, "Could not sync P-CHAT profile.")
            }
        }
    }

    private suspend fun flushPendingDiagnostics() {
        val pending = AuthDiagnosticsManager.drainPendingForFirestore()
        if (pending.isNotEmpty()) {
            repo.flushSecurityDiagnostics(currentUserId, initialProvider.id, pending)
        }
    }

    fun clearBanner() {
        _statusBanner.value = null
    }

    fun setChatFilter(filter: ChatFilter) {
        _chatFilter.value = filter
    }

    fun setChatSearchQuery(query: String) {
        _chatSearchQuery.value = query
    }

    fun setMalakMode(mode: MalakMode) {
        _malakMode.value = mode
    }

    fun myDisplayName(): String {
        val state = myProfile.value
        return (state as? UiState.Success)?.data?.displayName?.ifBlank { null }
            ?: initialDisplayName
            ?: "P-CHAT Explorer"
    }

    fun myUsername(): String {
        val state = myProfile.value
        return (state as? UiState.Success)?.data?.username?.ifBlank { null } ?: "pchat_user"
    }

    fun observeMessages(conversationId: String): Flow<List<ChatMessage>> =
        repo.observeMessages(conversationId, currentUserId)

    fun observeMomentComments(momentId: String): Flow<List<MomentComment>> =
        repo.observeMomentComments(momentId, currentUserId)

    fun sendMessage(
        conversation: Conversation,
        text: String,
        messageType: String = "text",
        mediaMeta: String = "",
        imageBase64: String = ""
    ) {
        viewModelScope.launch {
            repo.sendMessage(conversation, myDisplayName(), text, messageType, mediaMeta, imageBase64)
                .onFailure { _statusBanner.value = it.localizedMessage ?: "Failed to send message" }
        }
    }

    fun toggleMessageReaction(conversationId: String, message: ChatMessage, emoji: String) {
        viewModelScope.launch {
            repo.toggleMessageReaction(conversationId, message, emoji)
                .onFailure { _statusBanner.value = it.localizedMessage }
        }
    }

    fun togglePinConversation(conversation: Conversation) {
        viewModelScope.launch {
            repo.togglePinConversation(conversation)
                .onFailure { _statusBanner.value = it.localizedMessage }
        }
    }

    fun markConversationRead(conversation: Conversation) {
        viewModelScope.launch {
            repo.markConversationRead(conversation)
        }
    }

    fun openDirectChat(otherUser: UserProfile, onOpened: (String) -> Unit) {
        viewModelScope.launch {
            repo.openOrCreateDirectConversation(myDisplayName(), otherUser)
                .onSuccess { onOpened(it) }
                .onFailure { _statusBanner.value = it.localizedMessage ?: "Could not open chat" }
        }
    }

    fun createGroupChat(title: String, members: List<UserProfile>, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            repo.createGroupConversation(title, myDisplayName(), members)
                .onSuccess { onCreated(it) }
                .onFailure { _statusBanner.value = it.localizedMessage ?: "Could not create group" }
        }
    }

    fun sendFriendRequest(recipient: UserProfile) {
        viewModelScope.launch {
            repo.sendFriendRequest(myDisplayName(), recipient.userId, recipient.displayName)
                .onSuccess { _statusBanner.value = "Friend request sent to ${recipient.displayName}" }
                .onFailure { _statusBanner.value = it.localizedMessage ?: "Failed to send request" }
        }
    }

    fun respondToFriendRequest(friendshipId: String, accept: Boolean) {
        viewModelScope.launch {
            repo.respondToFriendRequest(friendshipId, accept)
                .onSuccess {
                    _statusBanner.value = if (accept) "Friend request accepted!" else "Request declined"
                }
                .onFailure { _statusBanner.value = it.localizedMessage }
        }
    }

    fun removeFriendship(friendshipId: String) {
        viewModelScope.launch {
            repo.removeFriendship(friendshipId)
                .onFailure { _statusBanner.value = it.localizedMessage }
        }
    }

    fun createMoment(
        caption: String,
        mediaType: String,
        filterTag: String = "",
        imageBase64: String = "",
        isStory: Boolean = false,
        localVideoPath: String = "",
        onDone: () -> Unit = {}
    ) {
        viewModelScope.launch {
            repo.createMoment(
                authorName = myDisplayName(),
                authorUsername = myUsername(),
                caption = caption,
                mediaType = mediaType,
                filterTag = filterTag,
                imageBase64 = imageBase64,
                isStory = isStory
            ).onSuccess { momentId ->
                if (localVideoPath.isNotBlank()) {
                    _localMomentVideos.value = _localMomentVideos.value + (momentId to localVideoPath)
                }
                _statusBanner.value = if (isStory) "Story posted!" else "Moment shared!"
                onDone()
            }.onFailure {
                _statusBanner.value = it.localizedMessage ?: "Failed to post moment"
            }
        }
    }

    fun toggleLikeMoment(moment: Moment) {
        viewModelScope.launch {
            repo.toggleLikeMoment(moment)
                .onFailure { _statusBanner.value = it.localizedMessage }
        }
    }

    fun addMomentComment(moment: Moment, text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            repo.addMomentComment(moment, myDisplayName(), text)
                .onFailure { _statusBanner.value = it.localizedMessage }
        }
    }

    fun startCall(conversation: Conversation, callType: String, onCallStarted: (CallSession) -> Unit) {
        viewModelScope.launch {
            repo.startCall(conversation, myDisplayName(), callType)
                .onSuccess { session ->
                    repo.sendMessage(
                        conversation = conversation,
                        senderName = myDisplayName(),
                        text = "${callType.replace('_', ' ').replaceFirstChar { it.uppercase() }} call started",
                        messageType = "call"
                    )
                    onCallStarted(session)
                }
                .onFailure { _statusBanner.value = it.localizedMessage ?: "Failed to start call" }
        }
    }

    fun updateCallStatus(callId: String, status: String) {
        viewModelScope.launch {
            repo.updateCallStatus(callId, status)
        }
    }

    fun updateProfile(
        displayName: String,
        username: String,
        bio: String,
        statusText: String,
        country: String = "Global",
        countryFlag: String = "🌍",
        languages: String = "English",
        avatarColorHex: String,
        isOnline: Boolean,
        isLive: Boolean,
        isDiscoverable: Boolean,
        fullName: String? = null,
        preferredLanguage: String? = null,
        photoBase64: String? = null
    ) {
        viewModelScope.launch {
            repo.updateMyProfile(
                displayName = displayName,
                username = username,
                bio = bio,
                statusText = statusText,
                country = country,
                countryFlag = countryFlag,
                languages = languages,
                avatarColorHex = avatarColorHex,
                isOnline = isOnline,
                isLive = isLive,
                isDiscoverable = isDiscoverable,
                fullName = fullName,
                preferredLanguage = preferredLanguage,
                photoBase64 = photoBase64
            ).onSuccess {
                _statusBanner.value = "Profile updated"
            }.onFailure {
                _statusBanner.value = it.localizedMessage ?: "Failed to update profile"
            }
        }
    }

    // region Authentication, Onboarding, Session Management & Diagnostics

    fun checkUsernameAvailability(rawHandle: String) {
        val validation = AuthValidation.validateUsername(rawHandle)
        if (!validation.isValid) {
            _usernameAvailability.value = UsernameAvailabilityState.Invalid(
                validation.errorMessage ?: "Invalid username"
            )
            return
        }
        val cleanHandle = validation.normalizedValue
        _usernameAvailability.value = UsernameAvailabilityState.Checking(cleanHandle)
        viewModelScope.launch {
            repo.isUsernameAvailable(cleanHandle, currentUserId)
                .onSuccess { available ->
                    _usernameAvailability.value = if (available) {
                        UsernameAvailabilityState.Available(cleanHandle)
                    } else {
                        UsernameAvailabilityState.Taken(cleanHandle)
                    }
                }
                .onFailure {
                    _usernameAvailability.value = UsernameAvailabilityState.Invalid(
                        it.localizedMessage ?: "Could not verify username"
                    )
                }
        }
    }

    fun completeOnboarding(
        draft: SignUpDraft,
        onCompleted: () -> Unit = {}
    ) {
        if (_isOnboardingSaving.value) return
        _isOnboardingSaving.value = true
        viewModelScope.launch {
            try {
                val user = Firebase.auth.currentUser
                val emailVerified = user?.isEmailVerified == true
                val phoneVerified = !user?.phoneNumber.isNullOrBlank()
                repo.completeOnboardingAndReserveUsername(
                    uid = currentUserId,
                    draft = draft,
                    authProvider = initialProvider,
                    emailVerified = emailVerified,
                    phoneVerified = phoneVerified
                ).onSuccess {
                    appContext?.let { PendingAuthPrefs.clearSignUpDraft(it) }
                    AuthDiagnosticsManager.record(
                        category = "registration",
                        severity = "info",
                        provider = initialProvider.id,
                        outcome = "success",
                        summary = "Completed registration onboarding for @${AuthValidation.sanitizeUsernameHandle(draft.username)}",
                        statusCode = "ONBOARDED"
                    )
                    flushPendingDiagnostics()
                    _statusBanner.value = "Welcome to P-CHAT, ${draft.fullName.trim()}!"
                    onCompleted()
                }.onFailure { err ->
                    _statusBanner.value = friendlyFirestoreErrorMessage(err, "Could not complete registration")
                }
            } finally {
                _isOnboardingSaving.value = false
            }
        }
    }

    fun sendVerificationEmail() {
        viewModelScope.launch {
            sendFirebaseVerificationEmail()
                .onSuccess {
                    flushPendingDiagnostics()
                    _statusBanner.value = "Verification email sent. Check your inbox and tap Check Verification."
                }
                .onFailure {
                    flushPendingDiagnostics()
                    _statusBanner.value = it.localizedMessage ?: "Could not send verification email"
                }
        }
    }

    fun refreshEmailVerification() {
        viewModelScope.launch {
            refreshCurrentUserVerificationStatus()
                .onSuccess { verified ->
                    val phoneVerified = !Firebase.auth.currentUser?.phoneNumber.isNullOrBlank()
                    repo.updatePrivateVerificationFlags(currentUserId, verified, phoneVerified)
                    flushPendingDiagnostics()
                    _statusBanner.value = if (verified) {
                        "Email address verified!"
                    } else {
                        "Email not verified yet — check inbox or spam folder."
                    }
                }
                .onFailure {
                    _statusBanner.value = it.localizedMessage ?: "Could not check verification status"
                }
        }
    }

    fun revokeSession(sessionId: String) {
        viewModelScope.launch {
            repo.revokeSession(sessionId)
                .onSuccess { _statusBanner.value = "Session revoked" }
                .onFailure { _statusBanner.value = it.localizedMessage ?: "Could not revoke session" }
        }
    }

    fun runFirebaseHealthCheck(context: Context) {
        viewModelScope.launch {
            val result = repo.testFirestoreHealthPing(currentUserId)
            result.onSuccess { latency ->
                _firebaseHealthStatus.value = AuthDiagnosticsManager.inspectFirebaseHealth(
                    context = context,
                    firestoreReachable = true,
                    firestoreLatencyMs = latency,
                    firestoreMessage = "Cloud Firestore connected (${latency}ms)"
                )
            }.onFailure { err ->
                _firebaseHealthStatus.value = AuthDiagnosticsManager.inspectFirebaseHealth(
                    context = context,
                    firestoreReachable = false,
                    firestoreLatencyMs = null,
                    firestoreMessage = err.localizedMessage ?: "Firestore unreachable"
                )
            }
        }
    }

    fun signOutSecurely(
        context: Context,
        credentialManager: CredentialManager,
        scope: CoroutineScope,
        onSignedOut: () -> Unit
    ) {
        val activeSessionId = PendingAuthPrefs.getActiveSessionId(context)
        PendingAuthPrefs.setSuppressAutoSignInOnce(true)
        AuthDiagnosticsManager.record(
            category = "logout",
            severity = "info",
            provider = initialProvider.id,
            outcome = "success",
            summary = "User initiated secure logout",
            statusCode = "LOGOUT"
        )
        signOut(
            credentialManager = credentialManager,
            scope = scope,
            onBeforeFirebaseSignOut = {
                flushPendingDiagnostics()
                repo.revokeSessionOnLogout(currentUserId, activeSessionId, initialProvider.id)
                PendingAuthPrefs.clearActiveSessionId(context)
            },
            onSignOutComplete = onSignedOut
        )
    }

    // endregion

    // region Wallet Actions

    fun depositToWallet(amountCents: Long, methodLabel: String) {
        viewModelScope.launch {
            repo.depositToWallet(amountCents, methodLabel)
                .onSuccess {
                    val dollars = String.format(java.util.Locale.US, "$%.2f", amountCents / 100.0)
                    _statusBanner.value = "Added $dollars to your P-CHAT Wallet"
                }
                .onFailure {
                    _statusBanner.value = it.localizedMessage ?: "Top up failed"
                }
        }
    }

    fun withdrawFromWallet(amountCents: Long, destinationLabel: String) {
        viewModelScope.launch {
            repo.withdrawFromWallet(amountCents, destinationLabel)
                .onSuccess {
                    val dollars = String.format(java.util.Locale.US, "$%.2f", amountCents / 100.0)
                    _statusBanner.value = "Withdrew $dollars to $destinationLabel"
                }
                .onFailure {
                    _statusBanner.value = it.localizedMessage ?: "Withdrawal failed"
                }
        }
    }

    fun convertUsdToCoins(usdCents: Long) {
        viewModelScope.launch {
            repo.convertUsdToCoins(usdCents)
                .onSuccess {
                    _statusBanner.value = "Converted USD into $usdCents P-Coins!"
                }
                .onFailure {
                    _statusBanner.value = it.localizedMessage ?: "Conversion failed"
                }
        }
    }

    fun claimCoinReward(coinsAmount: Long, rewardTitle: String) {
        viewModelScope.launch {
            repo.claimCoinReward(coinsAmount, rewardTitle)
                .onSuccess {
                    _statusBanner.value = "+$coinsAmount P-Coins added to your Wallet!"
                }
                .onFailure {
                    _statusBanner.value = it.localizedMessage ?: "Could not claim P-Coins"
                }
        }
    }

    fun sendWalletTransfer(
        recipient: UserProfile,
        amountCents: Long,
        coins: Long,
        note: String,
        alsoNotifyChat: Boolean = true
    ) {
        viewModelScope.launch {
            repo.sendWalletTransfer(myDisplayName(), recipient, amountCents, coins, note)
                .onSuccess {
                    val summaryParts = buildList {
                        if (amountCents > 0L) add(String.format(java.util.Locale.US, "$%.2f", amountCents / 100.0))
                        if (coins > 0L) add("$coins P-Coins")
                    }.joinToString(" + ")
                    _statusBanner.value = "Sent $summaryParts to ${recipient.displayName}"
                    if (alsoNotifyChat) {
                        repo.openOrCreateDirectConversation(myDisplayName(), recipient)
                            .onSuccess { convId ->
                                val conv = (conversations.value as? UiState.Success)?.data
                                    ?.firstOrNull { it.id == convId }
                                    ?: Conversation(
                                        id = convId,
                                        title = recipient.displayName,
                                        memberUids = listOf(currentUserId, recipient.userId).sorted()
                                    )
                                repo.sendMessage(
                                    conversation = conv,
                                    senderName = myDisplayName(),
                                    text = "Sent $summaryParts${if (note.isNotBlank()) " — \"$note\"" else ""}",
                                    messageType = "gift",
                                    mediaMeta = summaryParts
                                )
                            }
                    }
                }
                .onFailure {
                    _statusBanner.value = friendlyFirestoreErrorMessage(it, "Transfer failed")
                }
        }
    }

    fun refreshFxRates(force: Boolean = false) {
        if (_isFxRefreshing.value) return
        viewModelScope.launch {
            _isFxRefreshing.value = true
            try {
                _fxRates.value = CurrencyConversionEngine.getLiveOrCachedRates(forceRefresh = force)
            } finally {
                _isFxRefreshing.value = false
            }
        }
    }

    fun openReceipt(transaction: WalletTransaction) {
        _activeReceipt.value = transaction
    }

    fun dismissReceipt() {
        _activeReceipt.value = null
    }

    fun sendInternationalTransfer(
        recipient: UserProfile,
        quote: FxTransferQuote,
        note: String,
        alsoNotifyChat: Boolean = true,
        onComplete: () -> Unit = {}
    ) {
        if (_isSettlementInProgress.value) return
        viewModelScope.launch {
            _isSettlementInProgress.value = true
            try {
                repo.sendInternationalTransfer(myDisplayName(), recipient, quote, note)
                    .onSuccess { res ->
                        val localFormatted = CurrencyConversionEngine.formatLocalAmount(
                            quote.recipientLocalAmountMinor,
                            quote.recipientCurrency
                        )
                        val paidFormatted = String.format(
                            java.util.Locale.US,
                            "$%.2f",
                            quote.totalDebitedCents / 100.0
                        )
                        _statusBanner.value =
                            "Settled via ${quote.provider.displayName} • ${recipient.displayName} receives $localFormatted"
                        _activeReceipt.value = res.senderReceipt
                        onComplete()

                        if (alsoNotifyChat) {
                            repo.openOrCreateDirectConversation(myDisplayName(), recipient)
                                .onSuccess { convId ->
                                    val conv = (conversations.value as? UiState.Success)?.data
                                        ?.firstOrNull { it.id == convId }
                                        ?: Conversation(
                                            id = convId,
                                            title = recipient.displayName,
                                            memberUids = listOf(currentUserId, recipient.userId).sorted()
                                        )
                                    repo.sendMessage(
                                        conversation = conv,
                                        senderName = myDisplayName(),
                                        text = "Sent $localFormatted ($paidFormatted total incl. fees) via ${quote.provider.displayName} • Ref ${res.settlementRef}${if (note.isNotBlank()) " — \"$note\"" else ""}",
                                        messageType = "gift",
                                        mediaMeta = localFormatted
                                    )
                                }
                        }
                    }
                    .onFailure {
                        _statusBanner.value = friendlyFirestoreErrorMessage(it, "Settlement verification failed")
                    }
            } finally {
                _isSettlementInProgress.value = false
            }
        }
    }

    fun claimTransfer(transfer: WalletTransfer) {
        viewModelScope.launch {
            repo.claimIncomingTransfer(transfer)
                .onSuccess { receipt ->
                    if (receipt != null && receipt.localCurrency.isNotBlank() && receipt.localAmountMinor > 0L) {
                        val info = CurrencyConversionEngine.currencyByCode(receipt.localCurrency)
                        val localFormatted = CurrencyConversionEngine.formatLocalAmount(receipt.localAmountMinor, info)
                        _statusBanner.value = "Received $localFormatted from ${transfer.senderName}!"
                        _activeReceipt.value = receipt
                    } else {
                        _statusBanner.value = "Claimed transfer from ${transfer.senderName}!"
                        if (receipt != null) _activeReceipt.value = receipt
                    }
                }
                .onFailure {
                    _statusBanner.value = it.localizedMessage ?: "Could not claim transfer"
                }
        }
    }

    // endregion

    fun updatePrivateSettings(
        showOnlineStatus: Boolean,
        readReceiptsEnabled: Boolean,
        allowCallsFrom: String,
        notifyMessages: Boolean,
        notifyCalls: Boolean
    ) {
        viewModelScope.launch {
            repo.updatePrivateSettings(
                showOnlineStatus = showOnlineStatus,
                readReceiptsEnabled = readReceiptsEnabled,
                allowCallsFrom = allowCallsFrom,
                notifyMessages = notifyMessages,
                notifyCalls = notifyCalls
            ).onFailure {
                _statusBanner.value = it.localizedMessage ?: "Failed to update privacy settings"
            }
        }
    }

    // region Theme & Server Filter API

    fun setThemeMode(mode: AppThemeMode) {
        if (_themeMode.value == mode) return
        _themeMode.value = mode
        pendingThemeOrFilterWrites += 1
        viewModelScope.launch {
            try {
                repo.updateThemeAndFilters(currentUserId, mode, _discoverFilters.value)
                    .onFailure {
                        _statusBanner.value = it.localizedMessage ?: "Failed to sync theme to server"
                    }
            } finally {
                pendingThemeOrFilterWrites = (pendingThemeOrFilterWrites - 1).coerceAtLeast(0)
            }
        }
    }

    fun toggleThemeMode() {
        val next = if (_themeMode.value == AppThemeMode.BLACK) AppThemeMode.WHITE else AppThemeMode.BLACK
        setThemeMode(next)
    }

    fun updateDiscoverFilters(
        newState: DiscoverFilterState,
        runServerQuery: Boolean = true,
        searchQuery: String = ""
    ) {
        _discoverFilters.value = newState
        pendingThemeOrFilterWrites += 1
        viewModelScope.launch {
            try {
                repo.updateThemeAndFilters(currentUserId, _themeMode.value, newState)
                if (runServerQuery) {
                    syncAndQueryServerFiltersInternal(searchQuery, newState)
                }
            } finally {
                pendingThemeOrFilterWrites = (pendingThemeOrFilterWrites - 1).coerceAtLeast(0)
            }
        }
    }

    fun resetDiscoverFilters(searchQuery: String = "") {
        updateDiscoverFilters(
            newState = DiscoverFilterState(cameraFilterId = SnapFilter.NONE.id),
            runServerQuery = true,
            searchQuery = searchQuery
        )
    }

    fun selectServerCameraFilter(filterId: String) {
        val cleanId = filterId.ifBlank { SnapFilter.NONE.id }
        if (cleanId == "verified_online") {
            val next = _discoverFilters.value.copy(
                onlineOnly = true,
                verifiedOnly = true,
                sortBy = FilterSortOrder.VERIFIED_FIRST
            )
            updateDiscoverFilters(next, runServerQuery = true)
            viewModelScope.launch { repo.recordFilterUsage(cleanId) }
            return
        }
        val next = _discoverFilters.value.copy(cameraFilterId = cleanId)
        _discoverFilters.value = next
        pendingThemeOrFilterWrites += 1
        viewModelScope.launch {
            try {
                repo.updateThemeAndFilters(currentUserId, _themeMode.value, next)
                repo.recordFilterUsage(cleanId)
            } finally {
                pendingThemeOrFilterWrites = (pendingThemeOrFilterWrites - 1).coerceAtLeast(0)
            }
        }
    }

    fun syncAndQueryServerFilters(searchQuery: String = "") {
        viewModelScope.launch {
            syncAndQueryServerFiltersInternal(searchQuery, _discoverFilters.value)
        }
    }

    private suspend fun syncAndQueryServerFiltersInternal(
        searchQuery: String,
        filterState: DiscoverFilterState
    ) {
        _isFilterApiSyncing.value = true
        try {
            repo.syncServerFilterCatalog(currentUserId)
            repo.queryFilteredDirectory(currentUserId, searchQuery, filterState)
                .onSuccess { response ->
                    _serverApiResponse.value = response
                }
                .onFailure {
                    _statusBanner.value = it.localizedMessage ?: "Server filter query failed"
                }
        } finally {
            _isFilterApiSyncing.value = false
        }
    }

    fun saveCurrentAsFilterPreset(name: String) {
        viewModelScope.launch {
            repo.saveFilterPreset(
                uid = currentUserId,
                name = name,
                themeMode = _themeMode.value,
                state = _discoverFilters.value
            ).onSuccess {
                _statusBanner.value = "Saved preset to P-CHAT Server"
            }.onFailure {
                _statusBanner.value = it.localizedMessage ?: "Could not save preset"
            }
        }
    }

    fun applyFilterPreset(preset: FilterPreset, searchQuery: String = "") {
        val nextTheme = AppThemeMode.fromId(preset.themeMode)
        val nextFilters = DiscoverFilterState(
            language = preset.languageFilter,
            country = preset.countryFilter,
            onlineOnly = preset.onlineOnly,
            verifiedOnly = preset.verifiedOnly,
            cameraFilterId = preset.cameraFilterId.ifBlank { SnapFilter.NONE.id },
            sortBy = FilterSortOrder.fromId(preset.sortBy)
        )
        _themeMode.value = nextTheme
        _discoverFilters.value = nextFilters
        pendingThemeOrFilterWrites += 1
        viewModelScope.launch {
            try {
                repo.updateThemeAndFilters(currentUserId, nextTheme, nextFilters)
                if (nextFilters.cameraFilterId != SnapFilter.NONE.id) {
                    repo.recordFilterUsage(nextFilters.cameraFilterId)
                }
                syncAndQueryServerFiltersInternal(searchQuery, nextFilters)
                _statusBanner.value = "Applied \"${preset.name}\" (${nextTheme.label})"
            } finally {
                pendingThemeOrFilterWrites = (pendingThemeOrFilterWrites - 1).coerceAtLeast(0)
            }
        }
    }

    fun deleteFilterPreset(presetId: String) {
        viewModelScope.launch {
            repo.deleteFilterPreset(currentUserId, presetId)
                .onFailure { _statusBanner.value = it.localizedMessage }
        }
    }

    // endregion

    // region MALAK AI

    fun askMalak(prompt: String, attachedBitmap: Bitmap? = null) {
        val trimmed = prompt.trim()
        if (trimmed.isEmpty() && attachedBitmap == null) return
        val mode = _malakMode.value
        val existingMessages = (malakHistory.value as? UiState.Success)?.data.orEmpty()
        val historyPairs = existingMessages.map { it.role to it.text }

        viewModelScope.launch {
            val userDisplay = if (attachedBitmap != null && trimmed.isEmpty()) {
                "Analyze this image"
            } else {
                trimmed
            }
            repo.saveMalakMessage("user", userDisplay, mode.id)
            _malakStreamState.value = MalakStreamState.Loading
            val accumulated = StringBuilder()
            try {
                aiRepo.streamMalakResponse(userDisplay, mode, historyPairs, attachedBitmap)
                    .collect { chunk ->
                        accumulated.append(chunk)
                        _malakStreamState.value = MalakStreamState.Streaming(accumulated.toString())
                    }
                val finalAnswer = accumulated.toString().trim()
                if (finalAnswer.isNotEmpty()) {
                    repo.saveMalakMessage("model", finalAnswer, mode.id)
                    _malakStreamState.value = MalakStreamState.Idle
                } else {
                    _malakStreamState.value = MalakStreamState.Error("MALAK AI returned an empty response.")
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "MALAK AI timed out", e)
                _malakStreamState.value = MalakStreamState.Error("MALAK AI timed out after 60s. Please retry.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "MALAK AI error", e)
                _malakStreamState.value = MalakStreamState.Error(e.localizedMessage ?: "MALAK AI failed to respond.")
            }
        }
    }

    fun clearMalakHistory() {
        viewModelScope.launch {
            repo.clearMalakHistory()
                .onSuccess { _statusBanner.value = "MALAK AI conversation cleared" }
                .onFailure { _statusBanner.value = it.localizedMessage }
        }
    }

    fun generateOrEditImage(prompt: String, sourceBitmap: Bitmap?) {
        val trimmed = prompt.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            _imageStudioState.value = ImageStudioState.Loading
            try {
                val outputBitmap = if (sourceBitmap != null) {
                    aiRepo.editImage(sourceBitmap, trimmed)
                } else {
                    aiRepo.generateImage(trimmed)
                }
                _imageStudioState.value = ImageStudioState.Success(outputBitmap, trimmed)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Image studio failed", e)
                _imageStudioState.value = ImageStudioState.Error(
                    e.localizedMessage ?: "Image generation failed."
                )
            }
        }
    }

    fun resetImageStudio() {
        _imageStudioState.value = ImageStudioState.Idle
    }

    @OptIn(PublicPreviewAPI::class)
    fun startMalakLiveVoice() {
        if (_liveVoiceState.value is LiveVoiceState.Active || _liveVoiceState.value is LiveVoiceState.Connecting) {
            return
        }
        viewModelScope.launch {
            _liveVoiceState.value = LiveVoiceState.Connecting
            try {
                val session = aiRepo.connectLiveSession()
                liveSession = session
                session.startAudioConversation()
                _liveVoiceState.value = LiveVoiceState.Active(isMuted = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start Live API voice conversation", e)
                cleanupMalakLiveVoiceInternal()
                _liveVoiceState.value = LiveVoiceState.Error(
                    e.localizedMessage ?: "Could not start MALAK Live voice session."
                )
            }
        }
    }

    @OptIn(PublicPreviewAPI::class)
    private suspend fun cleanupMalakLiveVoiceInternal() {
        try {
            liveSession?.stopAudioConversation()
            liveSession?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing live session", e)
        } finally {
            liveSession = null
        }
    }

    @OptIn(PublicPreviewAPI::class)
    fun stopMalakLiveVoice() {
        viewModelScope.launch {
            cleanupMalakLiveVoiceInternal()
            _liveVoiceState.value = LiveVoiceState.Disconnected
        }
    }

    // endregion

    override fun onCleared() {
        super.onCleared()
        try {
            @OptIn(PublicPreviewAPI::class)
            liveSession?.stopAudioConversation()
        } catch (_: Exception) {
        }
    }

    companion object {
        fun provideFactory(
            currentUserId: String,
            displayName: String?,
            email: String?
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val firebaseUser = runCatching { Firebase.auth.currentUser }.getOrNull()
                val draft = PendingAuthPrefs.getSignUpDraft(app)
                val provider = inferProviderTypeFromFirebaseUser()
                val pendingHandle = PendingAuthPrefs.consumePendingUsername(app)
                    ?: draft?.username?.takeIf { it.isNotBlank() }
                PChatViewModel(
                    repo = PChatRepository(app),
                    aiRepo = MalakAiRepository(),
                    currentUserId = currentUserId,
                    initialDisplayName = displayName ?: draft?.fullName?.takeIf { it.isNotBlank() },
                    initialEmail = email ?: draft?.email?.takeIf { it.isNotBlank() },
                    initialUsername = pendingHandle,
                    initialFullName = draft?.fullName?.takeIf { it.isNotBlank() } ?: displayName,
                    initialLanguage = draft?.preferredLanguage?.takeIf { it.isNotBlank() },
                    initialProvider = provider,
                    initialPhoneNumber = firebaseUser?.phoneNumber ?: draft?.phoneE164?.takeIf { it.isNotBlank() },
                    initialEmailVerified = firebaseUser?.isEmailVerified == true,
                    initialPhoneVerified = !firebaseUser?.phoneNumber.isNullOrBlank(),
                    initialDeviceId = PendingAuthPrefs.getOrCreateDeviceId(app),
                    initialSessionId = PendingAuthPrefs.getActiveSessionId(app),
                    initialDeviceModel = detectCurrentDeviceModel(),
                    initialOsVersion = "Android ${Build.VERSION.RELEASE ?: ""}".trim(),
                    appContext = app.applicationContext
                )
            }
        }
    }
}
