package ar.com.faro.v5.app

import ar.com.faro.v5.kernel.contracts.ConversationId
import ar.com.faro.v5.kernel.contracts.IdempotencyKey
import ar.com.faro.v5.kernel.contracts.SingleProcessMaterialWriteGate
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.StorageGenerationId
import ar.com.faro.v5.surface.FaroV5SearchCoverage
import ar.com.faro.v5.surface.FaroV5SearchOpenResult
import ar.com.faro.v5.workspace.ConversationWorkspaceStore
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.BooleanSupplier
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** Shipping-host grant contract tested in isolation; productive V2 remains OFF. */
class FaroV5ActiveWorkspaceSearchGrantOwnerTest {
    @TempDir lateinit var root: Path

    @Test fun deniedWithoutVerifiedHostAndNeverReopensAfterClose(): Unit {
        val admitted = AtomicBoolean(false)
        val owner = FaroV5ActiveWorkspaceSearchGrantOwner(
            "host-A", BooleanSupplier { admitted.get() },
        )
        assertNull(owner.epochOrNull())
        assertThrows(IllegalStateException::class.java) { owner.grantAfterVerifiedHostOpen() }
        admitted.set(true)
        val first = owner.grantAfterVerifiedHostOpen()
        assertEquals(first, owner.epochOrNull())
        assertThrows(IllegalStateException::class.java) { owner.grantAfterVerifiedHostOpen() }
        admitted.set(false)
        assertNull(owner.epochOrNull())
        owner.close()
        admitted.set(true)
        assertNull(owner.epochOrNull())
        assertThrows(IllegalStateException::class.java) { owner.grantAfterVerifiedHostOpen() }
    }

    @Test fun revocationIsScopeLocalAndRegrantHasFreshIdentity(): Unit {
        val admitted = BooleanSupplier { true }
        val a = FaroV5ActiveWorkspaceSearchGrantOwner("host-A", admitted)
        val b = FaroV5ActiveWorkspaceSearchGrantOwner("host-B", admitted)
        try {
            val a1 = a.grantAfterVerifiedHostOpen()
            val b1 = b.grantAfterVerifiedHostOpen()
            assertNotEquals(a1, b1)
            a.revoke()
            assertNull(a.epochOrNull())
            assertEquals(b1, b.epochOrNull())
            val a2 = a.grantAfterVerifiedHostOpen()
            assertNotEquals(a1, a2)
            assertEquals(a2, a.epochOrNull())
            assertEquals(b1, b.epochOrNull())
        } finally {
            a.close()
            b.close()
        }
    }

    @Test fun realWorkspaceSearchRevocationDeniesOldHitAfterRegrant(): Unit = runBlocking {
        ConversationWorkspaceStore(":memory:").use { workspace ->
            workspace.createAndSelect(
                ConversationId("local-granted-chat"), 100L, IdempotencyKey("create-granted"),
            )
            val admitted = AtomicBoolean(true)
            val owner = FaroV5ActiveWorkspaceSearchGrantOwner(
                "active-workspace", BooleanSupplier { admitted.get() },
            )
            try {
                val source = FaroV5WorkspaceSearchReadSource(
                    "g1", workspace, { "g1" }, { owner.epochOrNull() }, { admitted.get() },
                )
                val adapter = FaroV5WorkspaceSearchSurfaceAdapter(
                    source, workspace, owner.invalidations,
                )
                assertEquals(FaroV5SearchCoverage.UNAVAILABLE, adapter.search("nuevo").coverage)
                val oldGrant = owner.grantAfterVerifiedHostOpen()
                val hit = adapter.search("nuevo").hits.single()
                owner.revoke()
                assertEquals(FaroV5SearchOpenResult.UNAVAILABLE, adapter.openAuthorized(hit))
                val newGrant = owner.grantAfterVerifiedHostOpen()
                assertNotEquals(oldGrant, newGrant)
                assertEquals(FaroV5SearchOpenResult.UNAVAILABLE, adapter.openAuthorized(hit))
                val fresh = adapter.search("nuevo").hits.single()
                assertNotEquals(hit.issuanceId, fresh.issuanceId)
                assertEquals(FaroV5SearchOpenResult.CONFIRMED, adapter.openAuthorized(fresh))
                admitted.set(false)
                assertEquals(FaroV5SearchCoverage.UNAVAILABLE, adapter.search("nuevo").coverage)
                assertEquals(FaroV5SearchOpenResult.UNAVAILABLE, adapter.openAuthorized(fresh))
            } finally {
                owner.close()
            }
        }
    }

    @Test fun durablePointerAbaRejectsOldGrantWithoutAnyObserver(): Unit {
        val pointer = FileActiveGenerationPointer(root)
        val selected = StorageGenerationId("host-generation")
        val other = StorageGenerationId("host-other")
        val pinned = requireNotNull(pointer.compareAndSet(null, selected))
        val admission = BooleanSupplier { pointer.read() == pinned }
        val oldOwner = FaroV5ActiveWorkspaceSearchGrantOwner(
            "host-generation:1", admission,
        )
        val oldGrant = oldOwner.grantAfterVerifiedHostOpen()
        assertEquals(oldGrant, oldOwner.epochOrNull())
        assertNotNull(pointer.compareAndSet(selected, other))
        val returned = requireNotNull(pointer.compareAndSet(other, selected))
        assertTrue(returned.sequence > pinned.sequence)
        assertNull(oldOwner.epochOrNull())
        oldOwner.revoke()
        assertThrows(IllegalStateException::class.java) {
            oldOwner.grantAfterVerifiedHostOpen()
        }
        oldOwner.close()
        val reopened = FaroV5ActiveWorkspaceSearchGrantOwner(
            "host-generation:2", BooleanSupplier { pointer.read() == returned },
        )
        try {
            val fresh = reopened.grantAfterVerifiedHostOpen()
            assertNotEquals(oldGrant, fresh)
            assertEquals(fresh, reopened.epochOrNull())
            assertNull(oldOwner.epochOrNull())
        } finally {
            reopened.close()
        }
    }

    @Test fun observedAdmissionLossRevokesInsteadOfSuspendingPriorGrant(): Unit {
        val admitted = AtomicBoolean(true)
        val owner = FaroV5ActiveWorkspaceSearchGrantOwner(
            "recovery-aba", BooleanSupplier { admitted.get() },
        )
        try {
            val original = owner.grantAfterVerifiedHostOpen()
            admitted.set(false)
            assertNull(owner.epochOrNull())
            admitted.set(true)
            // Returning to true is NOT a regrant: old ACL epoch stays revoked.
            assertNull(owner.epochOrNull())
            val fresh = owner.grantAfterVerifiedHostOpen()
            assertNotEquals(original, fresh)
            assertEquals(fresh, owner.epochOrNull())
        } finally {
            owner.close()
        }
    }

    @Test fun readinessOwnerRevokesAcrossFalseTrueWithoutAnySearchSubscriber(): Unit {
        val writeGate = SingleProcessMaterialWriteGate()
        val readiness = FaroV5StartupReadinessGate(writeGate)
        readiness.localReady()
        val owner = FaroV5ActiveWorkspaceSearchGrantOwner(
            "readiness-lifecycle", BooleanSupplier { readiness.snapshot().isLocalReady() },
        )
        val observer = FaroV5StartupReadinessListener { state ->
            if (!state.isLocalReady()) owner.revoke()
        }
        readiness.addListener(observer)
        try {
            val original = owner.grantAfterVerifiedHostOpen()
            assertEquals(original, owner.epochOrNull())
            // No Flow collector and no call to epochOrNull() while not-ready.
            readiness.beginRecovery()
            readiness.localReady()
            assertNull(owner.epochOrNull())
            val renewed = owner.grantAfterVerifiedHostOpen()
            assertNotEquals(original, renewed)
            assertEquals(renewed, owner.epochOrNull())
        } finally {
            readiness.removeListener(observer)
            owner.close()
        }
    }
}
