package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import com.example.auth.PendingAuthPrefs
import com.example.auth.isValidEmailAddress
import com.example.auth.normalizePhoneE164
import com.example.auth.sanitizeUsernameHandle
import com.example.model.AppThemeMode
import com.example.model.DiscoverFilterState
import com.example.model.FilterSortOrder
import com.example.ui.components.PChatBottomBar
import com.example.ui.components.PChatLogo
import com.example.ui.components.PChatRoutes
import com.example.ui.screens.AuthScreen
import com.example.ui.theme.PChatTheme
import com.example.util.SnapFilter
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(
                context,
                FirebaseOptions.Builder()
                    .setApplicationId("1:269683768722:android:b741d844294d0c0bb6954b")
                    .setApiKey("AIzaSyDummyTestKeyForRobolectric00000")
                    .setProjectId("gen-lang-client-0321953317")
                    .build()
            )
        }
    }

    @Test
    fun `pchat branding strings and resources are properly bundled`() {
        assertEquals("P-CHAT", context.getString(R.string.app_name))
        assertTrue(context.getString(R.string.firestore_database_id).isNotBlank())
        assertNotNull(ContextCompat.getDrawable(context, R.drawable.img_pchat_login_backdrop))
        assertNotNull(ContextCompat.getDrawable(context, R.drawable.img_malak_avatar))
        assertNotNull(ContextCompat.getDrawable(context, R.drawable.img_pchat_hero_banner))
    }

    @Test
    fun `auth input validators and pending sign up prefs work accurately`() {
        assertTrue(isValidEmailAddress("explorer@pchat.app"))
        assertFalse(isValidEmailAddress("invalid-email"))
        assertEquals("+256700123456", normalizePhoneE164("+256", "700 123 456"))
        assertEquals("+14155550199", normalizePhoneE164("+1", "+1 (415) 555-0199"))
        assertEquals("sophia_kirabo", sanitizeUsernameHandle("@Sophia Kirabo!"))

        PendingAuthPrefs.saveSignUpDraft(context, "Sophia Kirabo", "@sophia_kirabo")
        assertEquals("Sophia Kirabo", PendingAuthPrefs.getPendingDisplayName(context))
        assertEquals("sophia_kirabo", PendingAuthPrefs.consumePendingUsername(context))
        assertEquals(null, PendingAuthPrefs.consumePendingUsername(context))
        PendingAuthPrefs.clearSignUpDisplayName(context)
    }

    @Test
    fun `theme modes, filter counts, and snap filters resolve cleanly`() {
        assertEquals(AppThemeMode.WHITE, AppThemeMode.fromId("white"))
        assertEquals(AppThemeMode.BLACK, AppThemeMode.fromId("unknown"))

        assertEquals(SnapFilter.NEON_NIGHT, SnapFilter.fromId("neon_night"))
        assertEquals(SnapFilter.NONE, SnapFilter.fromId("neon_crown"))
        assertEquals(SnapFilter.NONE, SnapFilter.fromId("missing_id"))

        val defaultState = DiscoverFilterState()
        assertEquals(0, defaultState.activeFilterCount)

        val customizedState = DiscoverFilterState(
            language = "English",
            onlineOnly = true,
            cameraFilterId = "beauty_glow",
            sortBy = FilterSortOrder.ONLINE_FIRST
        )
        assertEquals(4, customizedState.activeFilterCount)
    }

    @Test
    fun `bottom navigation displays Chat Connect Plus Moments and Me tabs`() {
        var navigatedRoute = ""
        composeRule.setContent {
            PChatTheme(darkTheme = true) {
                PChatBottomBar(
                    currentRoute = PChatRoutes.CHAT,
                    unreadCount = 3,
                    pendingAlertCount = 1,
                    onNavigate = { navigatedRoute = it }
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Chat").assertIsDisplayed()
        composeRule.onNodeWithText("Connect").assertIsDisplayed()
        composeRule.onNodeWithText("Moments").assertIsDisplayed()
        composeRule.onNodeWithText("Me").assertIsDisplayed()

        composeRule.onNodeWithText("Connect").performClick()
        assertEquals(PChatRoutes.CONNECT, navigatedRoute)

        composeRule.onNodeWithText("Moments").performClick()
        assertEquals(PChatRoutes.MOMENTS, navigatedRoute)

        composeRule.onNodeWithText("Me").performClick()
        assertEquals(PChatRoutes.ME, navigatedRoute)
    }

    @Test
    fun `auth screen renders all login options and interactive modals`() {
        var darkState = true
        composeRule.setContent {
            PChatTheme(darkTheme = darkState) {
                AuthScreen(
                    isDarkTheme = darkState,
                    onToggleTheme = { darkState = !darkState },
                    onAuthSuccess = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("google_sign_in_button").assertIsDisplayed()
        composeRule.onNodeWithTag("phone_sign_in_button").assertIsDisplayed()
        composeRule.onNodeWithTag("email_sign_in_button").assertIsDisplayed()
        composeRule.onNodeWithTag("sign_up_button").assertIsDisplayed()
        composeRule.onNodeWithTag("already_have_account_login").assertIsDisplayed()

        // Open Phone Auth modal and enter digits
        composeRule.onNodeWithTag("phone_sign_in_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("phone_number_input").performTextInput("700123456")
        composeRule.onNodeWithTag("phone_send_code_button").assertIsDisplayed()
    }

    @Test
    fun `pchat logo renders dark and light themes without crashing`() {
        composeRule.setContent {
            PChatTheme(darkTheme = false) {
                PChatLogo(showLiveBadge = true, showEmblem = true)
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("P-CHAT").assertIsDisplayed()
        composeRule.onNodeWithText("LIVE").assertIsDisplayed()
    }
}
