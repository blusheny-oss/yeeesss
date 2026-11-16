package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.HandoverAct
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class HandoverRepositoryRuleTest : FirestoreEmulatorTestBase() {

    private suspend inline fun <reified T : Throwable> assertThrowsSuspend(
        crossinline block: suspend () -> Unit,
    ): T {
        try {
            block()
        } catch (t: Throwable) {
            if (t is T) return t
            throw AssertionError("Expected ${T::class.java.simpleName}, got ${t::class.java.simpleName}: ${t.message}", t)
        }
        fail("Expected ${T::class.java.simpleName} to be thrown")
        throw AssertionError("Unreachable")
    }

    @Test
    fun createActWithDefaultChecklist_validPayload_createsActAndItems() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = HandoverRepository(firestore, auth)

        val actId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createActWithDefaultChecklist(
                title = "Студия на Арбате",
                address = "г. Москва, ул. Арбат, 24",
                apartmentNumber = "12",
                depositAmountRub = 45000L,
                monthlyRentRub = 65000L,
                creatorRole = HandoverAct.ROLE_TENANT,
                landlordName = "Смирнов О.П.",
                landlordPhone = "+79161234567",
                tenantName = "Алиса К.",
                tenantPhone = "+79167654321",
                latitude = 55.7520,
                longitude = 37.5920,
                locationLabel = "55.75200, 37.59200",
            ).getOrThrow()
        }

        val act = withTimeout(DEFAULT_TIMEOUT_MS) { repository.getAct(actId).getOrThrow() }
        assertNotNull(act)
        assertEquals(uid, act?.userId)
        assertEquals(HandoverAct.STATUS_DRAFT, act?.status)

        val items = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeChecklistItems(uid, actId).first { it.isNotEmpty() }
        }
        assertEquals(10, items.size)
    }

    @Test
    fun observeActs_authenticatedOwner_emitsCreatedAct() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = HandoverRepository(firestore, auth)

        val actId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createActWithDefaultChecklist(
                title = "Двушка у метро",
                address = "г. Казань, ул. Баумана, 5",
                apartmentNumber = "8",
                depositAmountRub = 30000L,
                monthlyRentRub = 40000L,
                creatorRole = HandoverAct.ROLE_LANDLORD,
                landlordName = "Алиса К.",
                landlordPhone = "+79167654321",
                tenantName = "Иван П.",
                tenantPhone = "+79001234567",
                latitude = 55.7902,
                longitude = 49.1063,
                locationLabel = "55.79020, 49.10630",
            ).getOrThrow()
        }

        val emitted = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeActs(uid).first { list -> list.any { it.id == actId } }
        }
        assertTrue(emitted.any { it.id == actId && it.depositAmountRub == 30000L })
    }

    @Test
    fun signFlow_locksActAgainstModificationOnceSigned() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val repository = HandoverRepository(firestore, auth)

        val actId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createActWithDefaultChecklist(
                title = "Тест подписи",
                address = "г. Санкт-Петербург, Невский пр., 10",
                apartmentNumber = "3",
                depositAmountRub = 35000L,
                monthlyRentRub = 50000L,
                creatorRole = HandoverAct.ROLE_TENANT,
                landlordName = "Петров П.П.",
                landlordPhone = "+79210001122",
                tenantName = "Алиса К.",
                tenantPhone = "+79167654321",
                latitude = 59.9343,
                longitude = 30.3351,
                locationLabel = "59.93430, 30.33510",
            ).getOrThrow()
        }

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.signAsCreator(actId, "+79167654321", "482910", "TOKABC12").getOrThrow()
        }
        withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.confirmAsCounterparty(actId, "Петров П.П.", "+79210001122", "910284").getOrThrow()
        }

        val signedAct = withTimeout(DEFAULT_TIMEOUT_MS) { repository.getAct(actId).getOrThrow() }
        assertEquals(HandoverAct.STATUS_SIGNED, signedAct?.status)
        assertTrue(signedAct?.counterpartyConfirmed == true)

        val updateResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.updateActDetails(signedAct!!.copy(depositAmountRub = 0L))
        }
        assertTrue(updateResult.isFailure)
        val fsEx = updateResult.exceptionOrNull()?.unwrapFirestoreException()
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx?.code)

        val deleteResult = withTimeout(DEFAULT_TIMEOUT_MS) { repository.deleteDraftAct(actId) }
        assertTrue(deleteResult.isFailure)
        val delEx = deleteResult.exceptionOrNull()?.unwrapFirestoreException()
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, delEx?.code)
    }

    @Test
    fun observeActs_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repository = HandoverRepository(firestore, auth)

        val exception = assertThrowsSuspend<Exception> {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeActs("some_uid_123").first()
            }
        }
        val firestoreException = checkNotNull(exception.unwrapFirestoreException()) {
            "Expected nested FirebaseFirestoreException, got $exception"
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException.code)
    }

    @Test
    fun crossUserAccess_readingAnotherUsersAct_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = HandoverRepository(firestore, auth)
        val actId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createActWithDefaultChecklist(
                title = "Приватный акт Алисы",
                address = "ул. Ленина, 1",
                apartmentNumber = "1",
                depositAmountRub = 25000L,
                monthlyRentRub = 30000L,
                creatorRole = HandoverAct.ROLE_TENANT,
                landlordName = "Собственник",
                landlordPhone = "+79000000000",
                tenantName = "Алиса",
                tenantPhone = "+79000000001",
                latitude = 55.0,
                longitude = 37.0,
                locationLabel = "55.0, 37.0",
            ).getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = HandoverRepository(firestore, auth)

        val exception = assertThrowsSuspend<Exception> {
            withTimeout(FLOW_TIMEOUT_MS) {
                bobRepo.observeAct(aliceUid, actId).first()
            }
        }
        val firestoreException = checkNotNull(exception.unwrapFirestoreException()) {
            "Expected nested FirebaseFirestoreException, got $exception"
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException.code)
    }

    private companion object {
        const val ALICE_EMAIL = "alice.akt@test.com"
        const val BOB_EMAIL = "bob.akt@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
