package com.example.firebase

import android.util.Log
import com.example.auth.AuthLogRedactor
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject

enum class OperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write"),
}

fun handleFirestoreError(
    exception: Throwable,
    operationType: OperationType,
    path: String?
): String {
    val causes = generateSequence(exception) { it.cause }
    val rootError = causes.firstOrNull { it is FirebaseFirestoreException }
        ?: causes.firstOrNull { it !is CancellationException }
        ?: exception

    val currentUser = try {
        FirebaseAuth.getInstance().currentUser
    } catch (_: Exception) {
        null
    }

    val providerInfoList = currentUser?.providerData?.map { provider ->
        JSONObject().apply {
            put("providerId", provider.providerId)
            put("email", provider.email?.let { AuthLogRedactor.maskEmail(it) })
        }
    } ?: emptyList()

    val authInfoJson = JSONObject().apply {
        put("userId", currentUser?.uid)
        put("email", currentUser?.email?.let { AuthLogRedactor.maskEmail(it) })
        put("emailVerified", currentUser?.isEmailVerified)
        put("tenantId", currentUser?.tenantId)
        put("providerInfo", JSONArray(providerInfoList))
    }

    val errorInfoJson = JSONObject().apply {
        put("error", AuthLogRedactor.redactSensitiveText(rootError.message ?: rootError.toString()))
        put("operationType", operationType.value)
        put("path", path)
        put("authInfo", authInfoJson)
    }

    val jsonString = errorInfoJson.toString()
    Log.e("FirestoreError", "Firestore Error: $jsonString")
    return jsonString
}

fun friendlyFirestoreErrorMessage(error: Throwable, fallback: String): String {
    val root = generateSequence(error) { it.cause }
        .firstOrNull { it is FirebaseFirestoreException } as? FirebaseFirestoreException
    if (root != null) {
        return when (root.code) {
            FirebaseFirestoreException.Code.UNAVAILABLE,
            FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ->
                "You appear offline or on a slow connection. Check your network and retry."
            FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                "Permission denied for this operation. Please refresh your session or sign in again."
            FirebaseFirestoreException.Code.ABORTED ->
                "Concurrent update detected. Please try again."
            else -> root.localizedMessage ?: fallback
        }
    }
    val msg = error.localizedMessage.orEmpty()
    if (msg.contains("offline", ignoreCase = true) ||
        msg.contains("Unable to resolve host", ignoreCase = true) ||
        msg.contains("Failed to get document because the client is offline", ignoreCase = true)
    ) {
        return "You appear offline. Reconnect to the internet to complete this action."
    }
    return msg.ifBlank { fallback }
}
