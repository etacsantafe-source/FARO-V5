package ar.com.faro.v5.app

import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.StorageGenerationId
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * JVM checks for a real pointer and pure sandbox policy. The fake facts source
 * is NEVER a production principal or Android/G15 physical certification.
 */
class FaroV5AndroidSandboxPrincipalT2Test {
    @TempDir lateinit var root: Path

    private inner class Fixture {
        val pointer = FileActiveGenerationPointer(root)
        val generation = StorageGenerationId("sandbox-eight")
        val pinned = requireNotNull(pointer.compareAndSet(null, generation))
        val packageName = "ar.com.faro.alpha.v5dev"
        var expectedHostEpoch: String? = "session-host-a"
        var observed = AndroidSandboxProcessFacts(
            processUid = 10123,
            applicationUid = 10123,
            processIsApplicationUid = true,
            processIsIsolated = false,
            processIsSdkSandbox = false,
            applicationId = packageName,
        )
        var missing = false
        var throws = false
        var differentSecondRead = false
        var readCount = 0
        val fakeFacts = AndroidSandboxFactsReadPort {
            readCount++
            if (throws) throw IllegalStateException("Android facts unavailable")
            if (missing) null
            else if (differentSecondRead && readCount % 2 == 0) {
                observed.copy(applicationUid = observed.applicationUid + 1)
            } else observed
        }

        fun probe(packageExpected: String = packageName) =
            FaroV5AndroidSandboxPrincipal(
                expectedApplicationId = packageExpected,
                pinned = pinned,
                activePointer = pointer,
                expectedHostEpoch = "session-host-a",
                hostEpochOrNull = { expectedHostEpoch },
                facts = fakeFacts,
            )
    }

    @Test fun exactSandboxProducesOnlyStableSessionEvidence() {
        val f = Fixture()
        f.probe().use { probe ->
            val first = requireNotNull(probe.currentEvidenceOrNull())
            assertEquals(f.packageName, first.applicationId)
            assertEquals(f.observed.processUid, first.applicationUid)
            assertEquals(f.pinned, first.pinnedGeneration)
            assertEquals(first, probe.currentEvidenceOrNull())
        }
    }

    @Test fun processAndApplicationUidMismatchDenies() {
        val f = Fixture()
        f.observed = f.observed.copy(applicationUid = 10124)
        f.probe().use { assertNull(it.currentEvidenceOrNull()) }
    }

    @Test fun invalidZeroUidDenies() {
        val f = Fixture()
        f.observed = f.observed.copy(processUid = 0, applicationUid = 0)
        f.probe().use { assertNull(it.currentEvidenceOrNull()) }
    }

    @Test fun nonApplicationUidDenies() {
        val f = Fixture()
        f.observed = f.observed.copy(processIsApplicationUid = false)
        f.probe().use { assertNull(it.currentEvidenceOrNull()) }
    }

    @Test fun isolatedAndroidProcessDenies() {
        val f = Fixture()
        f.observed = f.observed.copy(processIsIsolated = true)
        f.probe().use { assertNull(it.currentEvidenceOrNull()) }
    }

    @Test fun sdkSandboxProcessDenies() {
        val f = Fixture()
        f.observed = f.observed.copy(processIsSdkSandbox = true)
        f.probe().use { assertNull(it.currentEvidenceOrNull()) }
    }

    @Test fun anotherPackageDoesNotMatchTrustedExpectedApplicationId() {
        val f = Fixture()
        f.observed = f.observed.copy(applicationId = "ar.com.other.v5dev")
        f.probe().use { assertNull(it.currentEvidenceOrNull()) }
    }

    @Test fun blankPackageFactsDeny() {
        val f = Fixture()
        f.observed = f.observed.copy(applicationId = "")
        f.probe().use { assertNull(it.currentEvidenceOrNull()) }
    }

    @Test fun missingAndroidFactsFailClosed() {
        val f = Fixture()
        f.missing = true
        f.probe().use { assertNull(it.currentEvidenceOrNull()) }
    }

    @Test fun androidFactsReadExceptionFailsClosedAndLatches() {
        val f = Fixture()
        f.probe().use { probe ->
            f.throws = true
            assertNull(probe.currentEvidenceOrNull())
            f.throws = false
            assertNull(probe.currentEvidenceOrNull())
        }
    }

    @Test fun pointerAtoBtoAIsNotTheOriginalSession() {
        val f = Fixture()
        f.probe().use { probe ->
            assertNotNull(probe.currentEvidenceOrNull())
            val other = StorageGenerationId("sandbox-another")
            assertNotNull(f.pointer.compareAndSet(f.generation, other))
            val repointed = requireNotNull(f.pointer.compareAndSet(other, f.generation))
            assertTrue(repointed.sequence > f.pinned.sequence)
            assertNull(probe.currentEvidenceOrNull())
        }
    }

    @Test fun hostEpochChangePermanentlyRevokesOriginalSession() {
        val f = Fixture()
        f.probe().use { probe ->
            assertNotNull(probe.currentEvidenceOrNull())
            f.expectedHostEpoch = "session-host-b"
            assertNull(probe.currentEvidenceOrNull())
            f.expectedHostEpoch = "session-host-a"
            assertNull(probe.currentEvidenceOrNull())
        }
    }

    @Test fun factsChangeBetweenDoubleSampleFailsClosedAndLatches() {
        val f = Fixture()
        f.probe().use { probe ->
            f.differentSecondRead = true
            assertNull(probe.currentEvidenceOrNull())
            f.differentSecondRead = false
            assertNull(probe.currentEvidenceOrNull())
        }
    }

    @Test fun closedProbeCannotReturnEvidenceAgain() {
        val f = Fixture()
        val probe = f.probe()
        assertNotNull(probe.currentEvidenceOrNull())
        probe.close()
        assertNull(probe.currentEvidenceOrNull())
    }

    @Test fun freshHostProbeCannotInheritOldSessionNonce() {
        val f = Fixture()
        val old = f.probe()
        val first = requireNotNull(old.currentEvidenceOrNull())
        f.probe().use { next ->
            val second = requireNotNull(next.currentEvidenceOrNull())
            assertNotEquals(first.sessionNonce, second.sessionNonce)
        }
        old.close()
    }

    @Test fun differentTrustedExpectedPackageDenies() {
        val f = Fixture()
        f.probe(packageExpected = "ar.com.faro.alpha.v2coredev").use {
            assertNull(it.currentEvidenceOrNull())
        }
    }
}
