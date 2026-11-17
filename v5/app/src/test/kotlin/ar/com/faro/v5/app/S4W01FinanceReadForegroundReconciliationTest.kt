package ar.com.faro.v5.app

import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.StorageGenerationId
import ar.com.faro.v5.store.reality.RealityFinance9ObservedReadPolicy
import ar.com.faro.v5.store.reality.RealityFinance9ObservedSourceRead
import ar.com.faro.v5.store.reality.RealityFinance9ObservedSourceReadPort
import ar.com.faro.v5.store.reality.RealityFinance9OpeningProvenanceAccount
import ar.com.faro.v5.store.reality.RealityFinance9OpeningProvenanceSnapshot
import ar.com.faro.v5.store.reality.RealityFinance9OpeningProvenanceState
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Fresh-fence and foreground policy unit tests with SYNTHETIC source.
 * These do NOT certify two Android PIDs, ContentResolver, human biometric
 * presence, or revocation of balances displayed by a shipping screen.
 */
class S4W01FinanceReadForegroundReconciliationTest {
    @TempDir lateinit var root: Path

    private inner class Rig(name: String) {
        val pointer = FileActiveGenerationPointer(root.resolve("foreground-" + name))
        val pinned = requireNotNull(pointer.compareAndSet(
            null, StorageGenerationId("generation-" + name),
        ))
        var hostEpoch = "host-1"
        var ready = true
        var principal = "a".repeat(64)
        var now = 1_000L
        var revoked = false
        var policyRevision = 2L
        var sourceRevision = 5L
        var reads = 0
        var fences = 0
        var clears = 0
        var onRead: (Int) -> Unit = {}
        var onClear: () -> Unit = { clears++ }

        fun observed(): RealityFinance9ObservedSourceRead =
            RealityFinance9ObservedSourceRead(
                RealityFinance9ObservedReadPolicy(
                    pinned.generationId.value, pinned.sequence, 7L, policyRevision,
                ),
                RealityFinance9OpeningProvenanceSnapshot(
                    sourceRevision, listOf(RealityFinance9OpeningProvenanceAccount(
                        "acc-1", "Cuenta de prueba", "ARS",
                        RealityFinance9OpeningProvenanceState.CONFIRMED_IN_SQL,
                        0L, -57L,
                    )),
                ),
            )

        fun freshFence(): S4W01B3B1B1B0ObservedReadFence {
            fences++
            return S4W01B3B1B1B0ObservedReadFence(
                pinned, pointer, "host-1", { hostEpoch }, { ready },
                { principal }, 7L,
                RealityFinance9ObservedSourceReadPort { request ->
                    reads++
                    onRead(reads)
                    if (revoked || request.principalHash != principal ||
                        request.generationId != pinned.generationId.value ||
                        request.pointerSequence != pinned.sequence ||
                        request.expectedPolicyEpoch != 7L
                    ) null else observed()
                },
                { now },
            )
        }

        fun owner() = S4W01FinanceReadForegroundReconciliation(
            newObservedFence = ::freshFence,
            clearBeforeRead = { onClear() },
        )
    }

    @Test fun r01NoReadBeforeForegroundResume() {
        val rig = Rig("before")
        rig.owner().use {
            assertNull(it.observedAfterResumeForDevOnly())
            assertEquals(0, rig.reads)
            assertEquals(0, rig.fences)
        }
    }

    @Test fun r02ResumeClearsBeforeEveryNewDoubleSqlFence() {
        val rig = Rig("resume")
        rig.onRead = { assertTrue(rig.clears >= 1) }
        rig.owner().use {
            it.onResume()
            assertNotNull(it.observedAfterResumeForDevOnly())
            assertNotNull(it.observedAfterResumeForDevOnly())
            assertEquals(2, rig.fences)
            assertEquals(4, rig.reads)
        }
    }

    @Test fun r03PauseStopDenyUntilFreshResumeAndClearAgain() {
        val rig = Rig("leave")
        rig.owner().use {
            it.onResume()
            assertNotNull(it.observedAfterResumeForDevOnly())
            it.onPause()
            it.onStop()
            assertNull(it.observedAfterResumeForDevOnly())
            val before = rig.fences
            it.onResume()
            assertNotNull(it.observedAfterResumeForDevOnly())
            assertEquals(before + 1, rig.fences)
            assertTrue(rig.clears >= 3)
        }
    }

    @Test fun r04LostRemoteSqlRevocationDuringStopStillDeniesOnResume() {
        val rig = Rig("missing-notice")
        rig.owner().use {
            it.onResume()
            assertNotNull(it.observedAfterResumeForDevOnly())
            it.onStop()
            rig.revoked = true // synthetic external SQL state change, NO notify
            it.onResume()
            assertNull(it.observedAfterResumeForDevOnly())
            assertTrue(rig.clears >= 3)
            assertEquals(2, rig.fences)
        }
    }

    @Test fun r05PointerAbaAcrossBackgroundDeniedEvenOriginalGeneration() {
        val rig = Rig("pointer-aba")
        rig.owner().use {
            it.onResume()
            assertNotNull(it.observedAfterResumeForDevOnly())
            it.onStop()
            val other = StorageGenerationId("other-generation")
            assertNotNull(rig.pointer.compareAndSet(rig.pinned.generationId, other))
            assertNotNull(rig.pointer.compareAndSet(other, rig.pinned.generationId))
            it.onResume()
            assertNull(it.observedAfterResumeForDevOnly())
        }
    }

    @Test fun r06RevisionChangesBetweenFreshSqlSnapshotsNeverReturned() {
        val rig = Rig("revision")
        rig.onRead = {
            if (it == 2) rig.policyRevision += 1L
        }
        rig.owner().use {
            it.onResume()
            assertNull(it.observedAfterResumeForDevOnly())
            assertEquals(2, rig.reads)
        }
    }

    @Test fun r07ConcurrentExternalSignalDuringReadInvalidatesResult() {
        val rig = Rig("race")
        lateinit var owner: S4W01FinanceReadForegroundReconciliation
        rig.onRead = {
            if (it == 2) owner.onExternalSignal()
        }
        owner = rig.owner()
        owner.use {
            it.onResume()
            assertNull(it.observedAfterResumeForDevOnly())
            assertTrue(rig.clears >= 2)
            assertEquals(2, rig.reads)
        }
    }

    @Test fun r08ClearExceptionPoisonAndCloseNeverResurrect() {
        val rig = Rig("failure")
        rig.onClear = { rig.clears++; throw IllegalStateException("synthetic clear failure") }
        val owner = rig.owner()
        owner.onResume()
        assertTrue(owner.isClosed())
        assertNull(owner.observedAfterResumeForDevOnly())
        owner.onResume()
        owner.onExternalSignal()
        owner.close()
        assertTrue(owner.isClosed())
        assertEquals(0, rig.fences)
        assertEquals(1, rig.clears)
    }
}
