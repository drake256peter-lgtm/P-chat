package com.example.auth

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.R
import com.example.model.FirebaseHealthStatus
import com.example.model.LocalAuthDiagnosticEntry
import com.google.firebase.FirebaseApp
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Thread-safe diagnostic ring buffer & Firebase configuration inspector.
 * Every recorded summary passes through [AuthLogRedactor.redactSensitiveText] so passwords,
 * OTP codes, tokens, and raw PII are never retained.
 */
object AuthDiagnosticsManager {

    private const val MAX_EVENTS = 60
    private val lock = Any()
    private val buffer = ArrayList<LocalAuthDiagnosticEntry>(MAX_EVENTS)
    private val pendingFlush = ArrayList<LocalAuthDiagnosticEntry>(MAX_EVENTS)

    private val _events = MutableStateFlow<List<LocalAuthDiagnosticEntry>>(emptyList())
    val events: StateFlow<List<LocalAuthDiagnosticEntry>> = _events.asStateFlow()

    fun record(
        category: String,
        severity: String,
        provider: String,
        outcome: String,
        summary: String,
        statusCode: String = "OK",
        nowMs: Long = System.currentTimeMillis()
    ): LocalAuthDiagnosticEntry {
        val sanitizedEntry = LocalAuthDiagnosticEntry(
            id = UUID.randomUUID().toString(),
            timestampMs = nowMs,
            category = category.take(40),
            severity = severity.lowercase().let { if (it in setOf("info", "warn", "critical")) it else "info" },
            provider = provider.take(32),
            outcome = outcome.take(24),
            summary = AuthLogRedactor.redactSensitiveText(summary),
            statusCode = statusCode.take(64)
        )
        synchronized(lock) {
            buffer.add(0, sanitizedEntry)
            if (buffer.size > MAX_EVENTS) {
                buffer.removeAt(buffer.lastIndex)
            }
            pendingFlush.add(sanitizedEntry)
            if (pendingFlush.size > MAX_EVENTS) {
                pendingFlush.removeAt(0)
            }
            _events.value = buffer.toList()
        }
        return sanitizedEntry
    }

    fun drainPendingForFirestore(): List<LocalAuthDiagnosticEntry> {
        synchronized(lock) {
            val copy = pendingFlush.toList()
            pendingFlush.clear()
            return copy
        }
    }

    fun clearAllForTest() {
        synchronized(lock) {
            buffer.clear()
            pendingFlush.clear()
            _events.value = emptyList()
        }
    }

    fun inspectFirebaseHealth(
        context: Context,
        firestoreReachable: Boolean? = null,
        firestoreLatencyMs: Long? = null,
        firestoreMessage: String? = null
    ): FirebaseHealthStatus {
        val apps = runCatching { FirebaseApp.getApps(context) }.getOrDefault(emptyList())
        val appInitialized = apps.isNotEmpty()
        val projectId = runCatching { apps.firstOrNull()?.options?.projectId }.getOrNull()
            ?: "gen-lang-client-0321953317"
        val dbId = runCatching { context.getString(R.string.firestore_database_id) }.getOrDefault("")
        val webClientId = runCatching { context.getString(R.string.default_web_client_id) }.getOrDefault("")
        val appCheckKey = runCatching { context.getString(R.string.recaptcha_site_key) }.getOrDefault("")
        val networkAvailable = isNetworkConnected(context)

        val currentUidStatus = runCatching { Firebase.auth.currentUser != null }.getOrDefault(false)
        val defaultMsg = when {
            firestoreMessage != null -> firestoreMessage
            !networkAvailable -> "Device offline — Firestore persistence queue active"
            currentUidStatus -> "Firebase Auth & Firestore active"
            else -> "Firebase initialized (ready for sign-in)"
        }

        return FirebaseHealthStatus(
            projectId = projectId,
            databaseId = dbId,
            firebaseAppInitialized = appInitialized,
            hasGoogleWebClientId = webClientId.isNotBlank(),
            hasAppCheckKey = appCheckKey.isNotBlank(),
            networkAvailable = networkAvailable,
            firestoreReachable = firestoreReachable,
            firestoreLatencyMs = firestoreLatencyMs,
            firestoreStatusMessage = defaultMsg
        )
    }

    private fun isNetworkConnected(context: Context): Boolean {
        return runCatching {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }.getOrDefault(false)
    }
}
