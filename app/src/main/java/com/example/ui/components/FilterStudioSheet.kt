package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.AppThemeMode
import com.example.model.DiscoverFilterState
import com.example.model.FilterSortOrder
import com.example.model.UiState
import com.example.ui.theme.DeepNavy
import com.example.ui.theme.ErrorCrimson
import com.example.ui.theme.HotPink
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.PChatBrandGradient
import com.example.ui.theme.PChatFollowGradient
import com.example.ui.theme.PChatTheme
import com.example.ui.theme.PearlWhite
import com.example.ui.theme.PureWhite
import com.example.ui.theme.VoidBlack
import com.example.ui.viewmodel.PChatViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilterStudioSheet(
    viewModel: PChatViewModel,
    currentSearchQuery: String = "",
    onDismiss: () -> Unit
) {
    val colors = PChatTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val discoverFilters by viewModel.discoverFilters.collectAsStateWithLifecycle()
    val serverFiltersState by viewModel.serverFilters.collectAsStateWithLifecycle()
    val presetsState by viewModel.filterPresets.collectAsStateWithLifecycle()
    val serverApiResponse by viewModel.serverApiResponse.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isFilterApiSyncing.collectAsStateWithLifecycle()

    var draftFilters by remember(discoverFilters) { mutableStateOf(discoverFilters) }
    var presetNameDraft by remember { mutableStateOf("") }

    val serverFilters = (serverFiltersState as? UiState.Success)?.data.orEmpty()
    val presets = (presetsState as? UiState.Success)?.data.orEmpty()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
        contentColor = colors.textPrimary
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Sheet Title + Server API Status Card
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(PChatBrandGradient),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Tune,
                                contentDescription = null,
                                tint = colors.onGradientText,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Filters & Theme Studio",
                                style = MaterialTheme.typography.titleLarge,
                                color = colors.textPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "White / Black Theme + P-CHAT Filter API",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textSecondary
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = { viewModel.syncAndQueryServerFilters(currentSearchQuery) },
                        enabled = !isSyncing,
                        modifier = Modifier.testTag("filter_api_refresh_btn"),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(50)
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = colors.accentBlue
                            )
                        } else {
                            Icon(
                                Icons.Filled.Refresh,
                                contentDescription = "Sync Filter Server API",
                                tint = colors.accentBlue,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("Sync API", color = colors.accentBlue, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    color = colors.elevated,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, colors.accentEmerald.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.CloudDone,
                            contentDescription = "Server Connected",
                            tint = colors.accentEmerald,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            val response = serverApiResponse
                            Text(
                                text = if (response != null) {
                                    "Server API Online • ${response.matchedUsers.size} matches (${response.latencyMs}ms)"
                                } else {
                                    "P-CHAT Cloud Server Ready • ${serverFilters.size} lenses synced"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                color = colors.accentEmerald,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = response?.endpoint ?: "firestore://serverFilters & users/{uid}/filterPresets",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Section 1: Dual Theme Switcher (Black Theme & White Theme)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Appearance Theme",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ThemePreviewCard(
                            title = "Black Theme",
                            subtitle = "Midnight Navy & Neon",
                            selected = themeMode == AppThemeMode.BLACK,
                            previewBg = VoidBlack,
                            previewSurface = DeepNavy,
                            previewText = PureWhite,
                            icon = Icons.Filled.DarkMode,
                            testTag = "filter_sheet_theme_black",
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.setThemeMode(AppThemeMode.BLACK) }
                        )
                        ThemePreviewCard(
                            title = "White Theme",
                            subtitle = "Crisp Pearl Daylight",
                            selected = themeMode == AppThemeMode.WHITE,
                            previewBg = PearlWhite,
                            previewSurface = PureWhite,
                            previewText = Color(0xFF091124),
                            icon = Icons.Filled.LightMode,
                            testTag = "filter_sheet_theme_white",
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.setThemeMode(AppThemeMode.WHITE) }
                        )
                    }
                }
            }

            item { HorizontalDivider(color = colors.border) }

            // Section 2: Connect People & Language/Country Filters
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Directory API Filters",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${draftFilters.activeFilterCount} active",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.accentBlue,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Text(
                        text = "Language",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.textSecondary
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("" to "All", "English" to "English", "Luganda" to "Luganda", "Swahili" to "Swahili", "Chinese" to "Chinese", "Russian" to "Russian", "Spanish" to "Spanish").forEach { (value, label) ->
                            val selected = draftFilters.language == value
                            FilterChip(
                                selected = selected,
                                onClick = { draftFilters = draftFilters.copy(language = value) },
                                label = { Text(label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = colors.card,
                                    labelColor = colors.textSecondary,
                                    selectedContainerColor = NeonPurple.copy(alpha = 0.25f),
                                    selectedLabelColor = colors.textPrimary
                                )
                            )
                        }
                    }

                    Text(
                        text = "Country / Region",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.textSecondary
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("" to "All", "Uganda" to "🇺🇬 Uganda", "China" to "🇨🇳 China", "Russia" to "🇷🇺 Russia", "Kenya" to "🇰🇪 Kenya", "USA" to "🇺🇸 USA", "UK" to "🇬🇧 UK", "Global" to "🌍 Global").forEach { (value, label) ->
                            val selected = draftFilters.country == value
                            FilterChip(
                                selected = selected,
                                onClick = { draftFilters = draftFilters.copy(country = value) },
                                label = { Text(label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = colors.card,
                                    labelColor = colors.textSecondary,
                                    selectedContainerColor = colors.accentBlue.copy(alpha = 0.22f),
                                    selectedLabelColor = colors.textPrimary
                                )
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Online Friends Only", color = colors.textPrimary, fontWeight = FontWeight.SemiBold)
                            Text("Filter active users currently online", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                        }
                        Switch(
                            checked = draftFilters.onlineOnly,
                            onCheckedChange = { draftFilters = draftFilters.copy(onlineOnly = it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = colors.primaryButtonContent,
                                checkedTrackColor = colors.accentEmerald
                            )
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Verified Accounts Only", color = colors.textPrimary, fontWeight = FontWeight.SemiBold)
                            Text("Prioritize verified badges", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                        }
                        Switch(
                            checked = draftFilters.verifiedOnly,
                            onCheckedChange = { draftFilters = draftFilters.copy(verifiedOnly = it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = colors.primaryButtonContent,
                                checkedTrackColor = colors.accentBlue
                            )
                        )
                    }

                    Text(
                        text = "Sort Order",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.textSecondary
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterSortOrder.entries.forEach { sortOption ->
                            FilterChip(
                                selected = draftFilters.sortBy == sortOption,
                                onClick = { draftFilters = draftFilters.copy(sortBy = sortOption) },
                                label = { Text(sortOption.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = colors.card,
                                    labelColor = colors.textSecondary,
                                    selectedContainerColor = HotPink.copy(alpha = 0.24f),
                                    selectedLabelColor = colors.textPrimary
                                )
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                draftFilters = DiscoverFilterState()
                                viewModel.resetDiscoverFilters(currentSearchQuery)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Reset", color = colors.textSecondary)
                        }
                        Button(
                            onClick = {
                                viewModel.updateDiscoverFilters(
                                    newState = draftFilters,
                                    runServerQuery = true,
                                    searchQuery = currentSearchQuery
                                )
                                onDismiss()
                            },
                            modifier = Modifier
                                .testTag("apply_server_filters_btn")
                                .weight(1.4f)
                                .height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.accentBlue,
                                contentColor = colors.primaryButtonContent
                            )
                        ) {
                            Text("Apply & Query Server", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            item { HorizontalDivider(color = colors.border) }

            // Section 3: Server-Backed AR Camera & Video Call Filter Catalog
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Server Lens & Filter Catalog",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Tap a server filter to set your default Camera & HD Video Call lens and record usage.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(serverFilters, key = { it.filterId.ifBlank { it.id } }) { item ->
                            val selected = discoverFilters.cameraFilterId == item.filterId
                            Surface(
                                color = if (selected) colors.accentBlue.copy(alpha = 0.16f) else colors.card,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .width(155.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable {
                                        viewModel.selectServerCameraFilter(item.filterId)
                                        draftFilters = draftFilters.copy(cameraFilterId = item.filterId)
                                    }
                                    .border(
                                        width = if (selected) 1.8.dp else 1.dp,
                                        brush = if (selected) PChatFollowGradient else Brush.linearGradient(listOf(colors.border, colors.border)),
                                        shape = RoundedCornerShape(16.dp)
                                    )
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(item.badge.ifBlank { "✨" }, fontSize = 20.sp)
                                        Text(
                                            text = "${item.usageCount} uses",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = colors.accentEmerald,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    Text(
                                        text = item.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = colors.textPrimary,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = item.description,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.textSecondary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item { HorizontalDivider(color = colors.border) }

            // Section 4: Cloud-Saved Filter & Theme Presets
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Saved Server Presets",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = presetNameDraft,
                            onValueChange = { presetNameDraft = it },
                            placeholder = { Text("Preset name (e.g. Daylight VIP)", color = colors.textMuted) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = colors.card,
                                unfocusedContainerColor = colors.card,
                                focusedBorderColor = colors.accentBlue,
                                unfocusedBorderColor = colors.border,
                                focusedTextColor = colors.textPrimary,
                                unfocusedTextColor = colors.textPrimary
                            )
                        )
                        Button(
                            onClick = {
                                viewModel.updateDiscoverFilters(draftFilters, runServerQuery = false)
                                val name = presetNameDraft.trim().ifBlank {
                                    "${themeMode.label} Mix"
                                }
                                viewModel.saveCurrentAsFilterPreset(name)
                                presetNameDraft = ""
                            },
                            modifier = Modifier
                                .testTag("save_filter_preset_btn")
                                .height(52.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NeonPurple,
                                contentColor = colors.onGradientText
                            )
                        ) {
                            Icon(Icons.Filled.BookmarkAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save")
                        }
                    }

                    if (presets.isEmpty()) {
                        Text(
                            text = "No saved presets yet. Save your favorite White/Black theme + filter combination to the server.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary
                        )
                    } else {
                        presets.forEach { preset ->
                            Surface(
                                color = colors.card,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable { viewModel.applyFilterPreset(preset, currentSearchQuery) }
                                    .border(1.dp, colors.border, RoundedCornerShape(16.dp))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = preset.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = colors.textPrimary,
                                            fontWeight = FontWeight.Bold
                                        )
                                        val details = buildList {
                                            add(AppThemeMode.fromId(preset.themeMode).label)
                                            if (preset.languageFilter.isNotBlank()) add(preset.languageFilter)
                                            if (preset.countryFilter.isNotBlank()) add(preset.countryFilter)
                                            if (preset.onlineOnly) add("Online")
                                            if (preset.verifiedOnly) add("Verified")
                                        }.joinToString(" • ")
                                        Text(
                                            text = details,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = colors.textSecondary
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.deleteFilterPreset(preset.id) },
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Icon(
                                            Icons.Filled.DeleteOutline,
                                            contentDescription = "Delete preset",
                                            tint = ErrorCrimson
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun ThemePreviewCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    previewBg: Color,
    previewSurface: Color,
    previewText: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    testTag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = PChatTheme.colors
    Surface(
        color = previewBg,
        shape = RoundedCornerShape(18.dp),
        modifier = modifier
            .testTag(testTag)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .border(
                width = if (selected) 2.5.dp else 1.dp,
                brush = if (selected) PChatFollowGradient else Brush.linearGradient(listOf(colors.border, colors.border)),
                shape = RoundedCornerShape(18.dp)
            )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(previewSurface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = title, tint = NeonPurple, modifier = Modifier.size(18.dp))
                }
                if (selected) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = "$title active",
                        tint = colors.accentEmerald,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = previewText,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = previewText.copy(alpha = 0.72f)
                )
            }
        }
    }
}
