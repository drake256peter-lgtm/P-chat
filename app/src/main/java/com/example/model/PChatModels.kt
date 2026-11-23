package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val username: String = "",
    val displayName: String = "",
    val fullName: String = "",
    val bio: String = "",
    val statusText: String = "",
    val country: String = "Global",
    val countryFlag: String = "🌍",
    val languages: String = "English",
    val preferredLanguage: String = "English",
    val verified: Boolean = false,
    val avatarColorHex: String = "#00D4FF",
    val photoBase64: String = "",
    val authProvider: String = "google",
    val profileCompleted: Boolean = false,
    @get:PropertyName("isOnline") @PropertyName("isOnline") val isOnline: Boolean = true,
    @get:PropertyName("isLive") @PropertyName("isLive") val isLive: Boolean = false,
    @get:PropertyName("isDiscoverable") @PropertyName("isDiscoverable") val isDiscoverable: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class PrivateAccountSettings(
    @DocumentId val id: String = "",
    val userId: String = "",
    val fullName: String = "",
    val email: String = "",
    val phoneNumber: String = "",
    val country: String = "Global",
    val countryFlag: String = "🌍",
    val preferredLanguage: String = "English",
    val dateOfBirth: String = "",
    val authProvider: String = "google",
    val emailVerified: Boolean = false,
    val phoneVerified: Boolean = false,
    val profileCompleted: Boolean = false,
    val showOnlineStatus: Boolean = true,
    val readReceiptsEnabled: Boolean = true,
    val allowCallsFrom: String = "friends",
    val notifyMessages: Boolean = true,
    val notifyCalls: Boolean = true,
    val themeMode: String = "black",
    val activeCameraFilterId: String = "none",
    val discoverLanguage: String = "",
    val discoverCountry: String = "",
    val discoverOnlineOnly: Boolean = false,
    val discoverVerifiedOnly: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class WalletSummary(
    @DocumentId val id: String = "",
    val userId: String = "",
    val balanceCents: Long = 0L,
    val coins: Long = 0L,
    val currency: String = "USD",
    val localCurrency: String = "USD",
    val localBalanceMinor: Long = 0L,
    val localCountry: String = "Global",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class WalletTransaction(
    @DocumentId val id: String = "",
    val userId: String = "",
    val txType: String = "deposit",
    val amountCents: Long = 0L,
    val coinDelta: Long = 0L,
    val title: String = "",
    val counterpartyId: String = "",
    val counterpartyName: String = "",
    val note: String = "",
    val baseAmountCents: Long = 0L,
    val feeCents: Long = 0L,
    val currency: String = "USD",
    val localCurrency: String = "USD",
    val localAmountMinor: Long = 0L,
    val exchangeRate: Double = 1.0,
    val recipientCountry: String = "",
    val paymentProvider: String = "",
    val settlementRef: String = "",
    val receiptId: String = "",
    val settlementVerified: Boolean = false,
    val status: String = "completed",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class WalletTransfer(
    @DocumentId val id: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val recipientId: String = "",
    val recipientName: String = "",
    val participantUids: List<String> = emptyList(),
    val amountCents: Long = 0L,
    val coins: Long = 0L,
    val note: String = "",
    val senderCurrency: String = "USD",
    val recipientCountry: String = "",
    val recipientCurrency: String = "USD",
    val exchangeRate: Double = 1.0,
    val recipientLocalAmountMinor: Long = 0L,
    val feeCents: Long = 0L,
    val totalDebitedCents: Long = 0L,
    val paymentProvider: String = "",
    val settlementRef: String = "",
    val receiptId: String = "",
    val settlementVerified: Boolean = false,
    val status: String = "sent",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Friendship(
    @DocumentId val id: String = "",
    val userIds: List<String> = emptyList(),
    val requesterId: String = "",
    val recipientId: String = "",
    val requesterName: String = "",
    val recipientName: String = "",
    val status: String = "pending",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Conversation(
    @DocumentId val id: String = "",
    val title: String = "",
    @get:PropertyName("isGroup") @PropertyName("isGroup") val isGroup: Boolean = false,
    val memberUids: List<String> = emptyList(),
    val memberNames: String = "",
    val createdBy: String = "",
    val lastMessageText: String = "",
    val lastMessageSenderId: String = "",
    val lastMessageType: String = "text",
    val pinnedByUids: List<String> = emptyList(),
    val unreadByUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val conversationId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val text: String = "",
    val messageType: String = "text",
    val mediaMeta: String = "",
    val imageBase64: String = "",
    val reactions: List<String> = emptyList(),
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Moment(
    @DocumentId val id: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val authorUsername: String = "",
    val caption: String = "",
    val mediaType: String = "status",
    val filterTag: String = "",
    val imageBase64: String = "",
    @get:PropertyName("isStory") @PropertyName("isStory") val isStory: Boolean = false,
    val visibility: String = "public",
    val likedByUids: List<String> = emptyList(),
    val commentCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class MomentComment(
    @DocumentId val id: String = "",
    val momentId: String = "",
    val momentAuthorId: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val text: String = "",
    val visibility: String = "public",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CallSession(
    @DocumentId val id: String = "",
    val conversationId: String = "",
    val callerId: String = "",
    val callerName: String = "",
    val title: String = "",
    val participantUids: List<String> = emptyList(),
    val callType: String = "video",
    val status: String = "ringing",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class MalakMessage(
    @DocumentId val id: String = "",
    val userId: String = "",
    val role: String = "user",
    val text: String = "",
    val mode: String = "chat",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
