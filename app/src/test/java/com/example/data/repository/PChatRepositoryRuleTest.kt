package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class PChatRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun ensureUserInitialized_authenticatedOwner_createsProfileAndLounge() = runBlocking {
        val uid = signInTestUser("alice_${UUID.randomUUID()}@pchat.test")
        val repo = PChatRepository(firestore, auth)

        val initResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.ensureUserInitialized(uid, "Alice Neon", "alice@pchat.test")
        }
        assertTrue(initResult.isSuccess)

        val profile = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeMyProfile(uid).first { it != null }
        }
        assertEquals("Alice Neon", profile?.displayName)
    }

    @Test
    fun sendMessage_conversationMember_persistsAndEmitsInFlow() = runBlocking {
        val aliceUid = signInTestUser("alice_msg_${UUID.randomUUID()}@pchat.test")
        val repo = PChatRepository(firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.ensureUserInitialized(aliceUid, "Alice", "alice@test.com").getOrThrow()
        }

        val conversations = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeConversations(aliceUid).first { it.isNotEmpty() }
        }
        val conv = conversations.first()

        val msgText = "Hello P-CHAT ${UUID.randomUUID()}"
        val msgResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.sendMessage(conv, "Alice", msgText, "text")
        }
        assertTrue(msgResult.isSuccess)

        val messages = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeMessages(conv.id, aliceUid).first { list -> list.any { it.text == msgText } }
        }
        assertTrue(messages.any { it.text == msgText })
    }

    @Test
    fun createMoment_publicMoment_visibleToOtherSignedInUser() = runBlocking {
        val aliceUid = signInTestUser("alice_mom_${UUID.randomUUID()}@pchat.test")
        val aliceRepo = PChatRepository(firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.ensureUserInitialized(aliceUid, "Alice", "alice@test.com").getOrThrow()
        }
        val caption = "Neon sky ${UUID.randomUUID()}"
        val momResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createMoment("Alice", "alice_neon", caption, "status", isStory = true)
        }
        assertTrue(momResult.isSuccess)

        val bobUid = signInTestUser("bob_mom_${UUID.randomUUID()}@pchat.test")
        val bobRepo = PChatRepository(firestore, auth)
        val moments = withTimeout(FLOW_TIMEOUT_MS) {
            bobRepo.observePublicMoments(bobUid).first { list -> list.any { it.caption == caption } }
        }
        assertTrue(moments.any { it.caption == caption })
    }

    @Test
    fun crossUserPrivateSettings_nonOwner_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser("alice_priv_${UUID.randomUUID()}@pchat.test")
        val aliceRepo = PChatRepository(firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.ensureUserInitialized(aliceUid, "Alice", "alice@test.com").getOrThrow()
        }

        signInTestUser("bob_priv_${UUID.randomUUID()}@pchat.test")
        var caught: Throwable? = null
        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("users").document(aliceUid)
                    .collection("private").document("account")
                    .get().await()
            }
        } catch (t: Throwable) {
            caught = t
        }
        val firestoreEx = caught?.unwrapFirestoreException()
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
    }

    @Test
    fun observeConversations_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repo = PChatRepository(firestore, auth)
        var caught: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeConversations("someone").first()
            }
        } catch (t: Throwable) {
            caught = t
        }
        val firestoreEx = caught?.unwrapFirestoreException()
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
    }

    @Test
    fun walletAndTransfer_ownerOperationsAndCrossUserClaim_succeeds() = runBlocking {
        val aliceUid = signInTestUser("alice_wal_${UUID.randomUUID()}@pchat.test")
        val aliceRepo = PChatRepository(firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.ensureUserInitialized(aliceUid, "Alice Wallet", "alicew@pchat.test").getOrThrow()
            aliceRepo.depositToWallet(1500L, "Google Pay").getOrThrow()
            aliceRepo.convertUsdToCoins(500L).getOrThrow()
        }
        val aliceWallet = withTimeout(FLOW_TIMEOUT_MS) {
            aliceRepo.observeWalletSummary(aliceUid).first { it != null && it.balanceCents == 3500L }
        }
        assertEquals(3500L, aliceWallet?.balanceCents)
        assertEquals(750L, aliceWallet?.coins)

        val bobUid = signInTestUser("bob_wal_${UUID.randomUUID()}@pchat.test")
        val bobRepo = PChatRepository(firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.ensureUserInitialized(bobUid, "Bob Wallet", "bobw@pchat.test").getOrThrow()
        }
        val bobProfile = withTimeout(FLOW_TIMEOUT_MS) {
            bobRepo.observeMyProfile(bobUid).first { it != null }!!
        }

        // Switch back to Alice to send transfer
        signInTestUser("alice_wal_sender_${UUID.randomUUID()}@pchat.test")
        val senderUid = auth.currentUser!!.uid
        val senderRepo = PChatRepository(firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            senderRepo.ensureUserInitialized(senderUid, "Sender", "sender@pchat.test").getOrThrow()
            senderRepo.sendWalletTransfer("Sender", bobProfile, 500L, 50L, "Gift").getOrThrow()
        }

        val senderTxs = withTimeout(FLOW_TIMEOUT_MS) {
            senderRepo.observeWalletTransactions(senderUid).first { txs -> txs.any { it.txType == "transfer_out" } }
        }
        assertTrue(senderTxs.any { it.amountCents == -500L && it.coinDelta == -50L })
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}
