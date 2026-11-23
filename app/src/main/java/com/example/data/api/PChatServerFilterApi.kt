package com.example.data.api

import com.example.firebase.OperationType
import com.example.firebase.handleFirestoreError
import com.example.model.AppThemeMode
import com.example.model.DiscoverFilterState
import com.example.model.FilterPreset
import com.example.model.FilterSortOrder
import com.example.model.ServerFilterApiResponse
import com.example.model.ServerFilterItem
import com.example.model.UserProfile
import com.example.util.SnapFilter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/**
 * Backend Server Filter & Theme API contract backed by P-CHAT Cloud Server (Firestore).
 */
interface PChatServerFilterApi {
    fun observeServerFilters(uid: String): Flow<List<ServerFilterItem>>
    fun observeFilterPresets(uid: String): Flow<List<FilterPreset>>
    suspend fun syncServerFilterCatalog(uid: String): Result<List<ServerFilterItem>>
    suspend fun recordFilterUsage(filterId: String): Result<Unit>
    suspend fun saveFilterPreset(uid: String, name: String, themeMode: AppThemeMode, state: DiscoverFilterState): Result<String>
    suspend fun deleteFilterPreset(uid: String, presetId: String): Result<Unit>
    suspend fun updateThemeAndFilters(uid: String, themeMode: AppThemeMode, state: DiscoverFilterState): Result<Unit>
    suspend fun queryFilteredDirectory(uid: String, searchQuery: String, state: DiscoverFilterState): Result<ServerFilterApiResponse>
}

class FirestoreServerFilterApi(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth
) : PChatServerFilterApi {

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

    override fun observeServerFilters(uid: String): Flow<List<ServerFilterItem>> = flow {
        db.collection("serverFilters")
            .whereEqualTo("isEnabled", true)
            .snapshotsFlow()
            .map { snap ->
                snap.toObjects(
                    ServerFilterItem::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
                    .filterNot { SnapFilter.isLegacyCartoonFilterId(it.filterId) }
                    .sortedBy { it.sortOrder }
            }
            .logFirestoreErrors(uid, OperationType.LIST, "serverFilters")
            .collect { emit(it) }
    }

    override fun observeFilterPresets(uid: String): Flow<List<FilterPreset>> = flow {
        val path = "users/$uid/filterPresets"
        db.collection("users")
            .document(uid)
            .collection("filterPresets")
            .snapshotsFlow()
            .map { snap ->
                snap.toObjects(
                    FilterPreset::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.updatedAt?.seconds ?: 0L }
            }
            .logFirestoreErrors(uid, OperationType.LIST, path)
            .collect { emit(it) }
    }

    override suspend fun syncServerFilterCatalog(uid: String): Result<List<ServerFilterItem>> = runCatching {
        val existingSnap = db.collection("serverFilters")
            .whereEqualTo("isEnabled", true)
            .get()
            .await()

        val existingIds = existingSnap.documents.map { it.id }.toSet()
        defaultServerFilters().forEachIndexed { index, seed ->
            if (seed.filterId !in existingIds) {
                val docRef = db.collection("serverFilters").document(seed.filterId)
                docRef.set(
                    mapOf(
                        "filterId" to seed.filterId,
                        "name" to seed.name,
                        "category" to seed.category,
                        "badge" to seed.badge,
                        "description" to seed.description,
                        "usageCount" to seed.usageCount,
                        "sortOrder" to index.toLong(),
                        "isEnabled" to true,
                        "createdBy" to uid,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        }

        val refreshed = db.collection("serverFilters")
            .whereEqualTo("isEnabled", true)
            .get()
            .await()
        refreshed.toObjects(
            ServerFilterItem::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
        )
            .filterNot { SnapFilter.isLegacyCartoonFilterId(it.filterId) }
            .sortedBy { it.sortOrder }
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.LIST, "serverFilters")
    }

    override suspend fun recordFilterUsage(filterId: String): Result<Unit> = runCatching {
        if (filterId.isBlank() || filterId == SnapFilter.NONE.id) return@runCatching
        val docRef = db.collection("serverFilters").document(filterId)
        val snap = docRef.get().await()
        if (snap.exists()) {
            docRef.update(
                mapOf(
                    "usageCount" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "serverFilters/$filterId")
    }

    override suspend fun saveFilterPreset(
        uid: String,
        name: String,
        themeMode: AppThemeMode,
        state: DiscoverFilterState
    ): Result<String> = runCatching {
        val cleanName = name.trim().take(60).ifBlank { "${themeMode.label} Preset" }
        val docRef = db.collection("users").document(uid).collection("filterPresets").document()
        val payload = buildMap<String, Any> {
            put("userId", uid)
            put("name", cleanName)
            put("themeMode", themeMode.id)
            if (state.language.isNotBlank()) put("languageFilter", state.language.trim().take(40))
            if (state.country.isNotBlank()) put("countryFilter", state.country.trim().take(60))
            put("onlineOnly", state.onlineOnly)
            put("verifiedOnly", state.verifiedOnly)
            if (state.cameraFilterId.isNotBlank()) put("cameraFilterId", state.cameraFilterId.take(40))
            put("sortBy", state.sortBy.id)
            put("createdAt", FieldValue.serverTimestamp())
            put("updatedAt", FieldValue.serverTimestamp())
        }
        docRef.set(payload).await()
        docRef.id
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.CREATE, "users/$uid/filterPresets")
    }

    override suspend fun deleteFilterPreset(uid: String, presetId: String): Result<Unit> = runCatching {
        db.collection("users").document(uid)
            .collection("filterPresets").document(presetId)
            .delete()
            .await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.DELETE, "users/$uid/filterPresets/$presetId")
    }

    override suspend fun updateThemeAndFilters(
        uid: String,
        themeMode: AppThemeMode,
        state: DiscoverFilterState
    ): Result<Unit> = runCatching {
        val docRef = db.collection("users").document(uid).collection("private").document("account")
        val payload = mapOf(
            "themeMode" to themeMode.id,
            "activeCameraFilterId" to state.cameraFilterId.ifBlank { SnapFilter.NONE.id }.take(40),
            "discoverLanguage" to state.language.trim().take(40),
            "discoverCountry" to state.country.trim().take(60),
            "discoverOnlineOnly" to state.onlineOnly,
            "discoverVerifiedOnly" to state.verifiedOnly,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        docRef.update(payload).await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "users/$uid/private/account")
    }

    override suspend fun queryFilteredDirectory(
        uid: String,
        searchQuery: String,
        state: DiscoverFilterState
    ): Result<ServerFilterApiResponse> = runCatching {
        val started = System.currentTimeMillis()
        val snap = db.collection("users")
            .whereEqualTo("isDiscoverable", true)
            .get()
            .await()
        val elapsed = (System.currentTimeMillis() - started).coerceAtLeast(1L)

        val profiles = snap.toObjects(
            UserProfile::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
        ).filter { it.userId != uid }

        val filtered = applyClientAndServerFilters(profiles, searchQuery, state)
        ServerFilterApiResponse(
            matchedUsers = filtered,
            latencyMs = elapsed,
            syncedAtEpochMs = System.currentTimeMillis(),
            endpoint = "firestore://users?isDiscoverable=true&lang=${state.language.ifBlank { "all" }}&country=${state.country.ifBlank { "all" }}",
            fromServer = !snap.metadata.isFromCache
        )
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.LIST, "users")
    }

    companion object {
        fun applyClientAndServerFilters(
            users: List<UserProfile>,
            searchQuery: String,
            state: DiscoverFilterState
        ): List<UserProfile> {
            val q = searchQuery.trim()
            val lang = state.language.trim()
            val country = state.country.trim()

            return users
                .filter { user ->
                    val matchesSearch = q.isEmpty() ||
                        user.displayName.contains(q, ignoreCase = true) ||
                        user.username.contains(q, ignoreCase = true) ||
                        user.country.contains(q, ignoreCase = true) ||
                        user.languages.contains(q, ignoreCase = true) ||
                        user.bio.contains(q, ignoreCase = true)
                    val matchesLang = lang.isEmpty() || user.languages.contains(lang, ignoreCase = true)
                    val matchesCountry = country.isEmpty() || user.country.contains(country, ignoreCase = true)
                    val matchesOnline = !state.onlineOnly || user.isOnline
                    val matchesVerified = !state.verifiedOnly || user.verified
                    matchesSearch && matchesLang && matchesCountry && matchesOnline && matchesVerified
                }
                .let { list ->
                    when (state.sortBy) {
                        FilterSortOrder.ONLINE_FIRST -> list.sortedWith(
                            compareByDescending<UserProfile> { it.isOnline }
                                .thenByDescending { it.verified }
                                .thenBy { it.displayName }
                        )
                        FilterSortOrder.VERIFIED_FIRST -> list.sortedWith(
                            compareByDescending<UserProfile> { it.verified }
                                .thenByDescending { it.isOnline }
                                .thenBy { it.displayName }
                        )
                        FilterSortOrder.ALPHABETICAL -> list.sortedBy { it.displayName.lowercase() }
                        FilterSortOrder.RECOMMENDED -> list.sortedWith(
                            compareByDescending<UserProfile> { it.isLive }
                                .thenByDescending { it.isOnline }
                                .thenByDescending { it.verified }
                        )
                    }
                }
        }

        private data class SeedSpec(
            val filterId: String,
            val name: String,
            val category: String,
            val badge: String,
            val description: String,
            val usageCount: Long
        )

        private fun defaultServerFilters(): List<SeedSpec> = listOf(
            SeedSpec(
                filterId = SnapFilter.BEAUTY_GLOW.id,
                name = "Studio Soft",
                category = "color_grade",
                badge = SnapFilter.BEAUTY_GLOW.badge,
                description = "Subtle warm tone balance for live camera and video calls",
                usageCount = 128L
            ),
            SeedSpec(
                filterId = SnapFilter.GOLDEN_HOUR.id,
                name = "Warm Sunlight",
                category = "color_grade",
                badge = SnapFilter.GOLDEN_HOUR.badge,
                description = "Warm sunset amber optical color grade",
                usageCount = 96L
            ),
            SeedSpec(
                filterId = SnapFilter.NEON_NIGHT.id,
                name = "Neon Purple",
                category = "color_grade",
                badge = SnapFilter.NEON_NIGHT.badge,
                description = "Signature P-CHAT low-light violet color balance",
                usageCount = 114L
            ),
            SeedSpec(
                filterId = SnapFilter.MONO_FILM.id,
                name = "Noir Mono",
                category = "color_grade",
                badge = SnapFilter.MONO_FILM.badge,
                description = "Classic monochrome contrast color grade",
                usageCount = 67L
            ),
            SeedSpec(
                filterId = "verified_online",
                name = "Verified Online",
                category = "discovery",
                badge = "🛡️",
                description = "Server discovery filter prioritizing active verified creators",
                usageCount = 84L
            )
        )
    }
}

private fun Query.snapshotsFlow() = this.snapshots()
