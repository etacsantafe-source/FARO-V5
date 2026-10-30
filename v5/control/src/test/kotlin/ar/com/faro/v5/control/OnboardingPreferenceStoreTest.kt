package ar.com.faro.v5.control

import ar.com.faro.v5.kernel.contracts.IdempotencyKey
import ar.com.faro.v5.kernel.runtime.BuiltInContractCatalog
import java.nio.file.Path
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class OnboardingPreferenceStoreTest {
    @Test
    fun `local onboarding descriptors match frozen runtime catalog`() {
        val catalog = BuiltInContractCatalog.create()
        assertEquals(
            catalog.operation(OnboardingOperations.STATE_READ),
            OnboardingOperations.STATE_READ_CONTRACT,
        )
        assertEquals(
            catalog.operation(OnboardingOperations.DEFER),
            OnboardingOperations.DEFER_CONTRACT,
        )
        assertEquals(
            catalog.operation(OnboardingOperations.COMPLETE),
            OnboardingOperations.COMPLETE_CONTRACT,
        )
    }

    @Test
    fun `facade routes defer and complete through canonical executors`() {
        val deferFile = java.nio.file.Files
            .createTempDirectory("faro-onboarding-facade-defer")
            .resolve("onboarding.preferences_pb")
            .toFile()
        OnboardingControlFacade(deferFile).use { facade ->
            val initial = facade.readBlocking()
            val result = facade.deferBlocking(
                expectedRevision = initial.revision,
                idempotencyKey = "facade-defer",
            )
            assertTrue(result is OnboardingMutationOutcome.Committed)
            assertEquals(OnboardingLifecycle.DEFERRED, result.snapshot.lifecycle)
        }

        val completeFile = java.nio.file.Files
            .createTempDirectory("faro-onboarding-facade-complete")
            .resolve("onboarding.preferences_pb")
            .toFile()
        OnboardingControlFacade(completeFile).use { facade ->
            val initial = facade.readBlocking()
            val result = facade.completeBlocking(
                expectedRevision = initial.revision,
                idempotencyKey = "facade-complete",
            )
            assertTrue(result is OnboardingMutationOutcome.Committed)
            assertEquals(OnboardingLifecycle.COMPLETED, result.snapshot.lifecycle)
        }
    }

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `read of pristine onboarding is pure and pending`() = runBlocking {
        val file = tempDir.resolve("onboarding.preferences_pb").toFile()
        OnboardingPreferenceStore(file, currentVersion = 1).use { store ->
            val snapshot = store.read()

            assertEquals(0, snapshot.storedVersion)
            assertEquals(OnboardingLifecycle.PENDING, snapshot.lifecycle)
            assertTrue(snapshot.shouldPresentAutomatically())
            assertFalse(file.exists())
        }
    }

    @Test
    fun `defer is durable and does not auto present on routine cold start`() = runBlocking {
        val file = tempDir.resolve("control/onboarding.preferences_pb").toFile()
        OnboardingPreferenceStore(file, currentVersion = 1).use { store ->
            val initial = store.read()
            val result = store.mutate(
                OnboardingMutationRequest(
                    target = OnboardingLifecycle.DEFERRED,
                    onboardingVersion = 1,
                    expectedRevision = initial.revision,
                    idempotencyKey = IdempotencyKey("defer-1"),
                )
            )

            assertTrue(result is OnboardingMutationOutcome.Committed)
            assertEquals(OnboardingLifecycle.DEFERRED, result.snapshot.lifecycle)
            assertFalse(result.snapshot.shouldPresentAutomatically())
        }

        OnboardingPreferenceStore(file, currentVersion = 1).use { reopened ->
            val snapshot = reopened.read()
            assertEquals(OnboardingLifecycle.DEFERRED, snapshot.lifecycle)
            assertFalse(snapshot.shouldPresentAutomatically())
        }
    }

    @Test
    fun `new onboarding version becomes pending without mutating on read`() = runBlocking {
        val file = tempDir.resolve("control/onboarding.preferences_pb").toFile()
        OnboardingPreferenceStore(file, currentVersion = 1).use { store ->
            val initial = store.read()
            store.mutate(
                OnboardingMutationRequest(
                    target = OnboardingLifecycle.COMPLETED,
                    onboardingVersion = 1,
                    expectedRevision = initial.revision,
                    idempotencyKey = IdempotencyKey("complete-1"),
                )
            )
        }
        val bytesBefore = file.readBytes()

        OnboardingPreferenceStore(file, currentVersion = 2).use { upgraded ->
            val snapshot = upgraded.read()
            assertEquals(1, snapshot.storedVersion)
            assertEquals(OnboardingLifecycle.PENDING, snapshot.lifecycle)
            assertTrue(snapshot.shouldPresentAutomatically())
        }

        assertTrue(bytesBefore.contentEquals(file.readBytes()))
    }

    @Test
    fun `stale onboarding revision does not overwrite current lifecycle`() = runBlocking {
        val file = tempDir.resolve("control/onboarding.preferences_pb").toFile()
        OnboardingPreferenceStore(file, currentVersion = 1).use { store ->
            val initial = store.read()
            store.mutate(
                OnboardingMutationRequest(
                    target = OnboardingLifecycle.DEFERRED,
                    onboardingVersion = 1,
                    expectedRevision = initial.revision,
                    idempotencyKey = IdempotencyKey("defer-current"),
                )
            )
            val stale = store.mutate(
                OnboardingMutationRequest(
                    target = OnboardingLifecycle.COMPLETED,
                    onboardingVersion = 1,
                    expectedRevision = initial.revision,
                    idempotencyKey = IdempotencyKey("complete-stale"),
                )
            )

            assertTrue(stale is OnboardingMutationOutcome.RejectedStale)
            assertEquals(OnboardingLifecycle.DEFERRED, store.read().lifecycle)
        }
    }

    @Test
    fun `same idempotency key with different target fails closed`() = runBlocking {
        val file = tempDir.resolve("control/onboarding.preferences_pb").toFile()
        OnboardingPreferenceStore(file, currentVersion = 1).use { store ->
            val initial = store.read()
            store.mutate(
                OnboardingMutationRequest(
                    target = OnboardingLifecycle.DEFERRED,
                    onboardingVersion = 1,
                    expectedRevision = initial.revision,
                    idempotencyKey = IdempotencyKey("same-key"),
                )
            )
            val conflict = store.mutate(
                OnboardingMutationRequest(
                    target = OnboardingLifecycle.COMPLETED,
                    onboardingVersion = 1,
                    expectedRevision = initial.revision,
                    idempotencyKey = IdempotencyKey("same-key"),
                )
            )

            assertTrue(conflict is OnboardingMutationOutcome.RejectedIdentityConflict)
            assertEquals(OnboardingLifecycle.DEFERRED, store.read().lifecycle)
        }
    }

    @Test
    fun `same idempotency key replays same target`() = runBlocking {
        val file = tempDir.resolve("control/onboarding.preferences_pb").toFile()
        OnboardingPreferenceStore(file, currentVersion = 1).use { store ->
            val initial = store.read()
            val first = store.mutate(
                OnboardingMutationRequest(
                    target = OnboardingLifecycle.COMPLETED,
                    onboardingVersion = 1,
                    expectedRevision = initial.revision,
                    idempotencyKey = IdempotencyKey("complete-replay"),
                )
            )
            val replay = store.mutate(
                OnboardingMutationRequest(
                    target = OnboardingLifecycle.COMPLETED,
                    onboardingVersion = 1,
                    expectedRevision = initial.revision,
                    idempotencyKey = IdempotencyKey("complete-replay"),
                )
            )

            assertTrue(first is OnboardingMutationOutcome.Committed)
            assertTrue(replay is OnboardingMutationOutcome.Committed)
            replay as OnboardingMutationOutcome.Committed
            assertTrue(replay.replayed)
            assertEquals(first.snapshot, replay.snapshot)
        }
    }
}
