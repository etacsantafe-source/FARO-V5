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

    @Test fun verifiedRecoveryIssuesNewGrantAndRejectsOldSearchHit(): Unit = runBlocking {
        val gate = SingleProcessMaterialWriteGate()
        val readiness = FaroV5StartupReadinessGate(gate)
        readiness.localReady()
        val owner = FaroV5ActiveWorkspaceSearchGrantOwner(
            "recovery-host", BooleanSupplier { readiness.snapshot().isLocalReady() },
        )
        val observer = FaroV5StartupReadinessListener { state ->
            if (!state.isLocalReady()) owner.revoke()
        }
        readiness.addListener(observer)
        try {
            ConversationWorkspaceStore(":memory:").use { workspace ->
                workspace.createAndSelect(
                    ConversationId("recovery-message"), 100L, IdempotencyKey("recovery-create"),
                )
                val source = FaroV5WorkspaceSearchReadSource(
                    "g1", workspace, { "g1" }, { owner.epochOrNull() },
                    { readiness.snapshot().isLocalReady() },
                )
                val adapter = FaroV5WorkspaceSearchSurfaceAdapter(
                    source, workspace, owner.invalidations,
                )
                val oldEpoch = owner.grantAfterVerifiedHostOpen()
                val oldHit = adapter.search("nuevo").hits.single()
                readiness.beginRecovery()
                assertNull(owner.renewAfterVerifiedRecovery())
                assertEquals(FaroV5SearchOpenResult.UNAVAILABLE, adapter.openAuthorized(oldHit))
                readiness.localReady()
                assertNull(owner.epochOrNull())
                // Only the trusted host may call this after successful recovery.
                val newEpoch = requireNotNull(owner.renewAfterVerifiedRecovery())
                assertNotEquals(oldEpoch, newEpoch)
                assertEquals(newEpoch, owner.renewAfterVerifiedRecovery())
                assertEquals(FaroV5SearchOpenResult.UNAVAILABLE, adapter.openAuthorized(oldHit))
                val newHit = adapter.search("nuevo").hits.single()
                assertEquals(FaroV5SearchOpenResult.CONFIRMED, adapter.openAuthorized(newHit))
            }
        } finally {
            readiness.removeListener(observer)
            owner.close()
        }
    }

    @Test fun explicitReadmissionCannotReviveStaleDurablePointer(): Unit {
        val pointer = FileActiveGenerationPointer(root)
        val generationA = StorageGenerationId("candidate-A")
        val generationB = StorageGenerationId("candidate-B")
        val first = requireNotNull(pointer.compareAndSet(null, generationA))
        val owner = FaroV5ActiveWorkspaceSearchGrantOwner(
            "original-host", BooleanSupplier { pointer.read() == first },
        )
        val old = owner.grantAfterVerifiedHostOpen()
        assertNotNull(pointer.compareAndSet(generationA, generationB))
        assertNull(owner.renewAfterVerifiedRecovery())
        val restored = requireNotNull(pointer.compareAndSet(generationB, generationA))
        assertTrue(restored.sequence > first.sequence)
        assertNull(owner.renewAfterVerifiedRecovery())
        assertNull(owner.epochOrNull())
        val reopened = FaroV5ActiveWorkspaceSearchGrantOwner(
            "restored-host", BooleanSupplier { pointer.read() == restored },
        )
        try {
            assertNull(reopened.epochOrNull())
            val fresh = requireNotNull(reopened.renewAfterVerifiedRecovery())
            assertNotEquals(old, fresh)
            assertEquals(fresh, reopened.renewAfterVerifiedRecovery())
        } finally {
            owner.close()
            reopened.close()
        }
    }

    @Test fun independentPointerInstancesRejectRestoreABAWithoutAnyListeners(): Unit = runBlocking {
        val writerPointer = FileActiveGenerationPointer(root)
        val readerPointerA = FileActiveGenerationPointer(root)
        val readerPointerB = FileActiveGenerationPointer(root)
        val a = StorageGenerationId("workspace-A")
        val b = StorageGenerationId("workspace-B")
        val pinnedA = requireNotNull(writerPointer.compareAndSet(null, a))
        val ownerA = FaroV5ActiveWorkspaceSearchGrantOwner(
            "reader-a", BooleanSupplier { readerPointerA.read() == pinnedA },
        )
        val ownerB = FaroV5ActiveWorkspaceSearchGrantOwner(
            "reader-b", BooleanSupplier { readerPointerB.read() == pinnedA },
        )
        try {
            ConversationWorkspaceStore(":memory:").use { store ->
                store.createAndSelect(
                    ConversationId("independent-pointer"), 100L,
                    IdempotencyKey("independent-create"),
                )
                val sourceA = FaroV5WorkspaceSearchReadSource(
                    a.value, store, { readerPointerA.read()?.generationId?.value },
                    { ownerA.epochOrNull() }, { true },
                )
                val sourceB = FaroV5WorkspaceSearchReadSource(
                    a.value, store, { readerPointerB.read()?.generationId?.value },
                    { ownerB.epochOrNull() }, { true },
                )
                val searchA = FaroV5WorkspaceSearchSurfaceAdapter(sourceA, store, ownerA.invalidations)
                val searchB = FaroV5WorkspaceSearchSurfaceAdapter(sourceB, store, ownerB.invalidations)
                ownerA.grantAfterVerifiedHostOpen()
                ownerB.grantAfterVerifiedHostOpen()
                val oldA = searchA.search("nuevo").hits.single()
                val oldB = searchB.search("nuevo").hits.single()
                assertNotNull(writerPointer.compareAndSet(a, b))
                assertNotNull(writerPointer.compareAndSet(b, a))
                // Neither reader registered a pointer listener nor a Flow subscriber.
                assertEquals(FaroV5SearchOpenResult.UNAVAILABLE, searchA.openAuthorized(oldA))
                assertEquals(FaroV5SearchOpenResult.UNAVAILABLE, searchB.openAuthorized(oldB))
                assertNull(ownerA.renewAfterVerifiedRecovery())
                assertNull(ownerB.renewAfterVerifiedRecovery())
            }
        } finally {
            ownerA.close()
            ownerB.close()
        }
    }

    @Test fun processRestartKeepsWorkspaceButNeverRestoresGrant(): Unit {
        val file = root.resolve("restart-workspace.db").toString()
        var oldGrant = ""
        ConversationWorkspaceStore(file).use { workspace ->
            workspace.createAndSelect(
                ConversationId("survives-restart"), 100L,
                IdempotencyKey("restart-create"),
            )
            FaroV5ActiveWorkspaceSearchGrantOwner(
                "same-host-scope", BooleanSupplier { true },
            ).use { owner ->
                oldGrant = owner.grantAfterVerifiedHostOpen()
            }
        }
        // New Application/host instance has no persisted ACL epoch. The
        // Workspace owner persists data, but the grant is NOT in its backup.
        ConversationWorkspaceStore.openExisting(file).use { workspace ->
            assertEquals(1, workspace.snapshot().workspace.conversations.size)
            FaroV5ActiveWorkspaceSearchGrantOwner(
                "same-host-scope", BooleanSupplier { true },
            ).use { owner ->
                assertNull(owner.epochOrNull())
                val newGrant = owner.grantAfterVerifiedHostOpen()
                assertNotEquals(oldGrant, newGrant)
            }
        }
    }
}
