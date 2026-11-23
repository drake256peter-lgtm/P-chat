package com.example

import com.example.auth.sanitizeUsernameHandle
import com.example.data.repository.MalakMode
import com.example.data.repository.StudioArtStyle
import com.example.data.repository.buildStyledImagePrompt
import com.example.model.AppThemeMode
import com.example.model.DiscoverFilterState
import com.example.model.FilterSortOrder
import com.example.ui.components.PChatRoutes
import com.example.util.CameraPermissionStatus
import com.example.util.SnapFilter
import com.example.util.evaluateCameraPermission
import com.example.util.formatDurationMmSs
import com.example.util.resolvePreferredLens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM unit tests for P-CHAT domain models, theme tokens, optical filters,
 * camera permission/hardware helpers, navigation routes, and MALAK AI creator tools.
 */
class ExampleUnitTest {

    @Test
    fun `theme mode deserializes white and black safely`() {
        assertEquals(AppThemeMode.BLACK, AppThemeMode.fromId("black"))
        assertEquals(AppThemeMode.WHITE, AppThemeMode.fromId("white"))
        assertEquals(AppThemeMode.BLACK, AppThemeMode.fromId(null))
        assertEquals(AppThemeMode.BLACK, AppThemeMode.fromId("invalid_theme"))
    }

    @Test
    fun `snap filters resolve only optical presets and strip legacy cartoon lenses`() {
        assertEquals(SnapFilter.NONE, SnapFilter.fromId(null))
        assertEquals(SnapFilter.BEAUTY_GLOW, SnapFilter.fromId("beauty_glow"))
        assertEquals(SnapFilter.GOLDEN_HOUR, SnapFilter.fromId("golden_hour"))
        assertEquals(SnapFilter.NEON_NIGHT, SnapFilter.fromId("neon_night"))
        assertEquals(SnapFilter.MONO_FILM, SnapFilter.fromId("mono_film"))
        // Legacy cartoon filters must map cleanly away from cartoon overlays
        assertEquals(SnapFilter.NONE, SnapFilter.fromId("dog_ears"))
        assertEquals(SnapFilter.NONE, SnapFilter.fromId("neon_crown"))
        assertEquals(SnapFilter.NONE, SnapFilter.fromId("cat_whiskers"))
        assertTrue(SnapFilter.isLegacyCartoonFilterId("dog_ears"))
        assertFalse(SnapFilter.isLegacyCartoonFilterId("golden_hour"))
        assertEquals(SnapFilter.NONE, SnapFilter.fromId("unknown_lens"))
    }

    @Test
    fun `camera permission evaluator handles granted request and permanently denied states`() {
        assertEquals(
            CameraPermissionStatus.GRANTED,
            evaluateCameraPermission(isGranted = true, shouldShowRationale = false, hasRequestedBefore = true)
        )
        assertEquals(
            CameraPermissionStatus.NEEDS_REQUEST,
            evaluateCameraPermission(isGranted = false, shouldShowRationale = false, hasRequestedBefore = false)
        )
        assertEquals(
            CameraPermissionStatus.NEEDS_REQUEST,
            evaluateCameraPermission(isGranted = false, shouldShowRationale = true, hasRequestedBefore = true)
        )
        assertEquals(
            CameraPermissionStatus.PERMANENTLY_DENIED,
            evaluateCameraPermission(isGranted = false, shouldShowRationale = false, hasRequestedBefore = true)
        )
    }

    @Test
    fun `preferred lens resolver switches front and rear with single-lens fallback`() {
        assertEquals(true, resolvePreferredLens(hasFront = true, hasBack = true, preferFront = true))
        assertEquals(false, resolvePreferredLens(hasFront = true, hasBack = true, preferFront = false))
        assertEquals(false, resolvePreferredLens(hasFront = false, hasBack = true, preferFront = true))
        assertEquals(true, resolvePreferredLens(hasFront = true, hasBack = false, preferFront = false))
        assertNull(resolvePreferredLens(hasFront = false, hasBack = false, preferFront = true))
        assertEquals("00:05", formatDurationMmSs(5))
        assertEquals("01:25", formatDurationMmSs(85))
    }

    @Test
    fun `discover filter count and sort orders compute accurately`() {
        val empty = DiscoverFilterState()
        assertEquals(0, empty.activeFilterCount)
        assertEquals(FilterSortOrder.RECOMMENDED, empty.sortBy)

        val filtered = DiscoverFilterState(
            language = "English",
            country = "Uganda",
            onlineOnly = true,
            verifiedOnly = true,
            cameraFilterId = "neon_night",
            sortBy = FilterSortOrder.ONLINE_FIRST
        )
        assertEquals(6, filtered.activeFilterCount)
        assertEquals(FilterSortOrder.VERIFIED_FIRST, FilterSortOrder.fromId("verified_first"))
        assertEquals(FilterSortOrder.RECOMMENDED, FilterSortOrder.fromId("unsupported"))
    }

    @Test
    fun `username handle sanitizer strips invalid characters and caps length`() {
        assertEquals("sophia_kirabo", sanitizeUsernameHandle("@Sophia Kirabo!"))
        assertEquals("marcus_chen_25", sanitizeUsernameHandle("Marcus.Chen#25"))
        assertTrue(sanitizeUsernameHandle("a".repeat(50)).length <= 24)
    }

    @Test
    fun `navigation routes and malak creator modes contain expected destinations`() {
        assertEquals("chat_detail/conv_123", PChatRoutes.chatDetail("conv_123"))
        assertEquals("call/call_999", PChatRoutes.call("call_999"))
        assertEquals("chat", PChatRoutes.CHAT)
        assertEquals("connect", PChatRoutes.CONNECT)
        assertEquals("plus", PChatRoutes.PLUS)
        assertEquals("moments", PChatRoutes.MOMENTS)
        assertEquals("me", PChatRoutes.ME)
        assertEquals("malak", PChatRoutes.MALAK)
        assertEquals("wallet", PChatRoutes.WALLET)
        assertTrue(MalakMode.entries.containsAll(listOf(
            MalakMode.MAKER, MalakMode.CODE_LAB, MalakMode.SONG_STORY, MalakMode.QUIZ_LAB, MalakMode.SOCIAL_CREATOR
        )))
        assertEquals("Neon tiger", buildStyledImagePrompt("  Neon tiger ", StudioArtStyle.NONE))
        val styled = buildStyledImagePrompt("Neon tiger", StudioArtStyle.CYBERPUNK_3D)
        assertTrue(styled.contains("Neon tiger") && styled.contains("Cyberpunk", ignoreCase = true).not() || styled.contains("Unreal Engine"))
    }
}
