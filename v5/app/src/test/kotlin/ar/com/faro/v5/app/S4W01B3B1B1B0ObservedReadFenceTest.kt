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

/** Synthetic caller and SQL DTOs: cannot attest real Android Keystore or user access. */
class S4W01B3B1B1B0ObservedReadFenceTest {
    @TempDir lateinit var root: Path

    private inner class Rig(name: String) {
        val pointer = FileActiveGenerationPointer(root.resolve("fence-" + name))
        val pinned = requireNotNull(pointer.compareAndSet(
            null, StorageGenerationId("generation-" + name),
        ))
        var hostEpoch: String? = "host-1"
        var ready = true
        var hash: String? = "a".repeat(64)
        var now: Long = 1_000L
        var calls = 0
        var policyRevision = 2L
        var sourceRevision = 5L
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
        val port = RealityFinance9ObservedSourceReadPort { request ->
            calls++
            if (request.principalHash == hash &&
                request.generationId == pinned.generationId.value &&
                request.expectedPolicyEpoch == 7L
            ) observed() else null
        }
        fun gate(
            given: RealityFinance9ObservedSourceReadPort = port,
            epoch: Long = 7L,
        ) = S4W01B3B1B1B0ObservedReadFence(
            pinned, pointer, "host-1", { hostEpoch }, { ready },
            { hash }, epoch, given, { now },
        )
    }

    @Test fun f01MissingOrMalformedPrincipalCannotCallSource() {
        val f = Rig("principal")
        f.hash = null
        assertNull(f.gate().observedForDevOnly())
        f.hash = "bad-hash"
        assertNull(f.gate().observedForDevOnly())
        assertEquals(0, f.calls)
        assertThrows(IllegalArgumentException::class.java) { f.gate(epoch = 0L) }
    }

    @Test fun f02TwoIdenticalFreshObservationsReturnOnlyObservedDto() {
        val f = Rig("match")
        val row = requireNotNull(f.gate().observedForDevOnly())
        assertEquals(2, f.calls)
        assertEquals(-57L, row.openingSource.accounts.single().ledgerBalanceMinor)
        assertEquals(7L, row.observedPolicy.policyEpoch)
        assertEquals(f.pinned.sequence, row.observedPolicy.pointerSequence)
    }

    @Test fun f03RevocationBetweenSnapshotsFailsClosed() {
        val f = Rig("revoke")
        val port = RealityFinance9ObservedSourceReadPort {
            f.calls++
            if (f.calls == 1) f.observed() else null
        }
        assertNull(f.gate(port).observedForDevOnly())
        assertEquals(2, f.calls)
    }

    @Test fun f04PointerAbaWithSameGenerationIdDenies() {
        val f = Rig("aba")
        val port = RealityFinance9ObservedSourceReadPort {
            f.calls++
            val current = f.observed()
            if (f.calls == 1) {
                val other = StorageGenerationId("foreign-generation")
                assertNotNull(f.pointer.compareAndSet(f.pinned.generationId, other))
                assertNotNull(f.pointer.compareAndSet(other, f.pinned.generationId))
            }
            current
        }
        assertNull(f.gate(port).observedForDevOnly())
        assertEquals(1, f.calls)
    }

    @Test fun f05HostEpochReadinessOrPrincipalChangeDenies() {
        val f = Rig("host")
        val port = RealityFinance9ObservedSourceReadPort {
            f.calls++
            f.hostEpoch = "host-2"
            f.observed()
        }
        assertNull(f.gate(port).observedForDevOnly())
        assertEquals(1, f.calls)
        val other = Rig("ready")
        other.ready = false
        assertNull(other.gate().observedForDevOnly())
        assertEquals(0, other.calls)
        val changed = Rig("hash-change")
        val altered = RealityFinance9ObservedSourceReadPort {
            changed.calls++
            changed.hash = "b".repeat(64)
            changed.observed()
        }
        assertNull(changed.gate(altered).observedForDevOnly())
    }

    @Test fun f06GlobalPolicyOrSourceRevisionChangedDenies() {
        val f = Rig("revision")
        val port = RealityFinance9ObservedSourceReadPort {
            f.calls++
            if (f.calls == 2) f.policyRevision += 1L
            f.observed()
        }
        assertNull(f.gate(port).observedForDevOnly())
        assertEquals(2, f.calls)
        val other = Rig("opening")
        val port2 = RealityFinance9ObservedSourceReadPort {
            other.calls++
            if (other.calls == 2) other.sourceRevision += 1L
            other.observed()
        }
        assertNull(other.gate(port2).observedForDevOnly())
    }

    @Test fun f07ExceptionsNegativeClockAndExpiredSecondSnapshotDeny() {
        val f = Rig("throws")
        val exploding = RealityFinance9ObservedSourceReadPort {
            throw IllegalStateException("synthetic closed SQLite stage")
        }
        assertNull(f.gate(exploding).observedForDevOnly())
        val clock = Rig("clock")
        clock.now = -1L
        assertNull(clock.gate().observedForDevOnly())
        assertEquals(0, clock.calls)
        val expiry = Rig("expired")
        val port = RealityFinance9ObservedSourceReadPort {
            expiry.calls++
            if (expiry.calls == 1) {
                expiry.now = 2_000L
                expiry.observed()
            } else null
        }
        assertNull(expiry.gate(port).observedForDevOnly())
        assertEquals(2, expiry.calls)
    }

    @Test fun f08CloseAndPostFailureCannotReuseOrRetainBalances() {
        val f = Rig("close")
        val gate = f.gate()
        assertNotNull(gate.observedForDevOnly())
        assertEquals(2, f.calls)
        gate.close()
        assertNull(gate.observedForDevOnly())
        assertEquals(2, f.calls)
        val rejected = Rig("poison")
        val failOnce = RealityFinance9ObservedSourceReadPort {
            rejected.calls++
            null
        }
        val poisoned = rejected.gate(failOnce)
        assertNull(poisoned.observedForDevOnly())
        assertNull(poisoned.observedForDevOnly())
        assertEquals(1, rejected.calls)
    }
}
