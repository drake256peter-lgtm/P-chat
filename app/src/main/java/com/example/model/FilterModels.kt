package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

enum class AppThemeMode(val id: String, val label: String) {
    BLACK("black", "Black Theme"),
    WHITE("white", "White Theme");

    companion object {
        fun fromId(raw: String?): AppThemeMode =
            entries.firstOrNull { it.id == raw } ?: BLACK
    }
}

enum class FilterSortOrder(val id: String, val label: String) {
    RECOMMENDED("recommended", "Recommended"),
    ONLINE_FIRST("online_first", "Online First"),
    VERIFIED_FIRST("verified_first", "Verified First"),
    ALPHABETICAL("alphabetical", "A–Z");

    companion object {
        fun fromId(raw: String?): FilterSortOrder =
            entries.firstOrNull { it.id == raw } ?: RECOMMENDED
    }
}

@Keep
@IgnoreExtraProperties
data class FilterPreset(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val themeMode: String = AppThemeMode.BLACK.id,
    val languageFilter: String = "",
    val countryFilter: String = "",
    val onlineOnly: Boolean = false,
    val verifiedOnly: Boolean = false,
    val cameraFilterId: String = "none",
    val sortBy: String = FilterSortOrder.RECOMMENDED.id,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ServerFilterItem(
    @DocumentId val id: String = "",
    val filterId: String = "",
    val name: String = "",
    val category: String = "ar_lens",
    val badge: String = "✨",
    val description: String = "",
    val usageCount: Long = 0L,
    val sortOrder: Long = 0L,
    @get:PropertyName("isEnabled") @PropertyName("isEnabled") val isEnabled: Boolean = true,
    val createdBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class DiscoverFilterState(
    val language: String = "",
    val country: String = "",
    val onlineOnly: Boolean = false,
    val verifiedOnly: Boolean = false,
    val cameraFilterId: String = "none",
    val sortBy: FilterSortOrder = FilterSortOrder.RECOMMENDED
) {
    val activeFilterCount: Int
        get() = listOf(
            language.isNotBlank(),
            country.isNotBlank(),
            onlineOnly,
            verifiedOnly,
            cameraFilterId != "none" && cameraFilterId.isNotBlank(),
            sortBy != FilterSortOrder.RECOMMENDED
        ).count { it }
}

data class ServerFilterApiResponse(
    val matchedUsers: List<UserProfile> = emptyList(),
    val latencyMs: Long = 0L,
    val syncedAtEpochMs: Long = 0L,
    val endpoint: String = "cloud.firestore/serverFilters",
    val fromServer: Boolean = false
)
