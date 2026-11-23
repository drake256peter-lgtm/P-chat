package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.auth.AuthDiagnosticsManager
import com.example.auth.PendingAuthPrefs
import com.example.model.UiState
import com.example.ui.theme.AmberSpark
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ErrorCrimson
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.PChatBrandGradient
import com.example.ui.theme.PChatTheme
import com.example.ui.viewmodel.PChatViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AuthDiagnosticsDialog(
    viewModel: PChatViewModel? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val colors = PChatTheme.colors
    val localEvents by AuthDiagnosticsManager.events.collectAsStateWithLifecycle()

    val healthFromVmState = viewModel?.firebaseHealthStatus?.collectAsStateWithLifecycle()
    val localHealth = remember(context, healthFromVmState?.value) {
        healthFromVmState?.value ?: AuthDiagnosticsManager.inspectFirebaseHealth(context)
    }

    val sessionsState = viewModel?.userSessions?.collectAsStateWithLifecycle()
    val loginHistoryState = viewModel?.loginHistory?.collectAsStateWithLifecycle()
    val securityLogsState = viewModel?.securityLogs?.collectAsStateWithLifecycle()
    val devicesState = viewModel?.deviceRegistrations?.collectAsStateWithLifecycle()
    val activeSessionId = remember(context) { PendingAuthPrefs.getActiveSessionId(context) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = colors.surface,
            shape = RoundedCornerShape(26.dp),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 680.dp)
                .border(1.5.dp, PChatBrandGradient, RoundedCornerShape(26.dp))
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Shield,
                        contentDescription = null,
                        tint = colors.accentBlue,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Auth & Firebase Diagnostics",
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = colors.textSecondary)
                    }
                }

                Text(
                    text = "Live Firebase configuration health, redacted security telemetry, and active session governance. Passwords, OTP codes, tokens, and raw PII are never logged.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary
                )

                // Firebase Health Summary Card
                Surface(
                    color = colors.card,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, colors.border, RoundedCornerShape(16.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.CloudDone, contentDescription = null, tint = NeonPurple, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Firebase Project Health",
                                style = MaterialTheme.typography.titleSmall,
                                color = colors.textPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        DiagHealthRow("Project ID", localHealth.projectId, true)
                        DiagHealthRow("Firestore DB", localHealth.databaseId.ifBlank { "(default)" }, localHealth.databaseId.isNotBlank())
                        DiagHealthRow(
                            "Firebase App",
                            if (localHealth.firebaseAppInitialized) "Initialized" else "Not initialized",
                            localHealth.firebaseAppInitialized
                        )
                        DiagHealthRow(
                            "Google Credential Manager",
                            if (localHealth.hasGoogleWebClientId) "Configured (OAuth Web Client Ready)" else "Missing client ID",
                            localHealth.hasGoogleWebClientId
                        )
                        DiagHealthRow(
                            "App Check (reCAPTCHA)",
                            if (localHealth.hasAppCheckKey) "Key configured in strings" else "No App Check site key",
                            localHealth.hasAppCheckKey
                        )
                        DiagHealthRow(
                            "Network Connectivity",
                            if (localHealth.networkAvailable) "Online" else "Offline",
                            localHealth.networkAvailable
                        )
                        DiagHealthRow(
                            "Firestore Ping",
                            localHealth.firestoreStatusMessage,
                            localHealth.firestoreReachable ?: localHealth.networkAvailable
                        )

                        if (viewModel != null) {
                            Button(
                                onClick = { viewModel.runFirebaseHealthCheck(context) },
                                modifier = Modifier
                                    .testTag("diag_ping_firestore_button")
                                    .fillMaxWidth()
                                    .height(44.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.accentBlue,
                                    contentColor = colors.primaryButtonContent
                                )
                            ) {
                                Icon(Icons.Filled.NetworkCheck, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Test Firestore Connection", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Console Prerequisite Status
                Surface(
                    color = colors.card,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, colors.border, RoundedCornerShape(16.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Provider & SDK Configuration Status",
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        DiagStatusNote(
                            title = "Google Sign-In",
                            detail = "Active in app via Jetpack CredentialManager + Firebase Auth.",
                            ready = localHealth.hasGoogleWebClientId
                        )
                        DiagStatusNote(
                            title = "Phone SMS OTP",
                            detail = "Implemented in app. Requires 'Phone' enabled in Firebase Console + SHA-256 / Play Integrity + Blaze SMS quota.",
                            ready = false
                        )
                        DiagStatusNote(
                            title = "Passwordless Email Link",
                            detail = "Implemented in app. Requires 'Email link' enabled & authorized domain in Firebase Console.",
                            ready = false
                        )
                        DiagStatusNote(
                            title = "Profile Photos",
                            detail = "Firebase Storage SDK is not bundled; avatars use Photo Picker + compressed Firestore storage.",
                            ready = false
                        )
                    }
                }

                // Active Sessions & Devices (only when signed in)
                if (viewModel != null) {
                    val sessions = (sessionsState?.value as? UiState.Success)?.data.orEmpty()
                    val devices = (devicesState?.value as? UiState.Success)?.data.orEmpty()
                    val history = (loginHistoryState?.value as? UiState.Success)?.data.orEmpty()
                    val secLogs = (securityLogsState?.value as? UiState.Success)?.data.orEmpty()

                    Surface(
                        color = colors.card,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, colors.border, RoundedCornerShape(16.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Devices, contentDescription = null, tint = colors.accentEmerald, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Active Sessions (${sessions.size}) & Devices (${devices.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = colors.textPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (sessions.isEmpty()) {
                                Text("No recorded Firestore sessions yet.", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                            } else {
                                sessions.take(6).forEach { s ->
                                    val isCurrent = s.sessionId == activeSessionId
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(colors.surface)
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "${s.provider.uppercase()} • ${if (isCurrent) "This Device" else s.deviceId.take(12)}",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = colors.textPrimary,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "Status: ${s.status} • ${s.platform}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (s.status == "active") colors.accentEmerald else colors.textMuted
                                            )
                                        }
                                        if (s.status == "active" && !isCurrent) {
                                            TextButton(onClick = { viewModel.revokeSession(s.sessionId) }) {
                                                Text("Revoke", color = ErrorCrimson, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                            if (devices.isNotEmpty()) {
                                HorizontalDivider(color = colors.border)
                                devices.take(4).forEach { d ->
                                    Text(
                                        text = "📱 ${d.deviceModel} (${d.osVersion}) • Trusted=${d.isTrusted}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.textSecondary
                                    )
                                }
                            }
                        }
                    }

                    // Cloud Login History & Security Logs
                    Surface(
                        color = colors.card,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, colors.border, RoundedCornerShape(16.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.History, contentDescription = null, tint = ElectricBlue, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Cloud Login History & Security Audit",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = colors.textPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (history.isEmpty() && secLogs.isEmpty()) {
                                Text("No cloud login history synced yet.", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                            } else {
                                history.take(6).forEach { item ->
                                    Text(
                                        text = "• [${item.provider}] ${item.eventType} (${item.outcome}/${item.statusCode}) — ${item.message}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.textPrimary
                                    )
                                }
                                if (secLogs.isNotEmpty()) {
                                    HorizontalDivider(color = colors.border)
                                    secLogs.take(6).forEach { log ->
                                        Text(
                                            text = "🛡️ [${log.severity.uppercase()}] ${log.category}: ${log.summary}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (log.severity == "critical") ErrorCrimson else colors.textSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Live Local Redacted Event Buffer (works both signed-out and signed-in)
                Surface(
                    color = colors.card,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, colors.border, RoundedCornerShape(16.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Security, contentDescription = null, tint = NeonPurple, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Live Redacted Auth Stream (${localEvents.size})",
                                style = MaterialTheme.typography.titleSmall,
                                color = colors.textPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (localEvents.isEmpty()) {
                            Text(
                                text = "No auth events recorded in this session yet. Attempt Google, Phone, or Email auth to inspect live telemetry.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary
                            )
                        } else {
                            val fmt = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }
                            localEvents.take(12).forEach { ev ->
                                val timeStr = fmt.format(Date(ev.timestampMs))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(colors.surface)
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .padding(top = 4.dp)
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(
                                                when {
                                                    ev.severity == "critical" -> ErrorCrimson
                                                    ev.outcome == "failure" || ev.severity == "warn" -> AmberSpark
                                                    else -> colors.accentEmerald
                                                }
                                            )
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "$timeStr • ${ev.category} [${ev.provider}/${ev.statusCode}]",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = colors.accentBlue,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = ev.summary,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colors.textPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, colors.border)
                ) {
                    Text("Close Diagnostics", color = colors.textPrimary, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun DiagHealthRow(label: String, value: String, ok: Boolean) {
    val colors = PChatTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                color = if (ok) colors.textPrimary else AmberSpark,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = if (ok) Icons.Filled.CheckCircle else Icons.Filled.WarningAmber,
                contentDescription = null,
                tint = if (ok) colors.accentEmerald else AmberSpark,
                modifier = Modifier.size(15.dp)
            )
        }
    }
}

@Composable
private fun DiagStatusNote(title: String, detail: String, ready: Boolean) {
    val colors = PChatTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = if (ready) Icons.Filled.CheckCircle else Icons.Filled.WarningAmber,
            contentDescription = null,
            tint = if (ready) colors.accentEmerald else AmberSpark,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = colors.textPrimary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary
            )
        }
    }
}
