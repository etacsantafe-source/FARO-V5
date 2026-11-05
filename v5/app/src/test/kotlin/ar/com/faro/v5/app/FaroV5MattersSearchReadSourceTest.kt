package ar.com.faro.v5.app

import ar.com.faro.v5.control.search.CanonicalSearchCoordinator
import ar.com.faro.v5.control.search.SearchCanonicalDocument
import ar.com.faro.v5.control.search.SearchProjectionQuery
import ar.com.faro.v5.kernel.contracts.AtomicApplyTransactionDecision
import ar.com.faro.v5.kernel.contracts.RealityTransactionScope
import ar.com.faro.v5.kernel.contracts.TransactionId
import ar.com.faro.v5.owner.matters.api.*
import ar.com.faro.v5.owner.matters.spi.MatterPersistencePort
import ar.com.faro.v5.store.reality.RealityStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FaroV5MattersSearchReadSourceTest {
    private fun row(number: Int = 1, at: Long = 100L) = MatterRecord(
        MatterId.of("matter-secret-" + number),
        MatterTitle.of("Pagar expensas"),
        MatterDetail.of("Factura del edificio"),
        MatterStatus.WAITING,
        dueAtEpochMillis = 200L,
        updatedAtEpochMillis = at,
    )
    private fun query() = SearchProjectionQuery(
        FaroV5MattersSearchReadSource.NAMESPACE, "PAGAR", 10,
    )
    private fun source(
        owner: MatterPersistencePort,
        generation: () -> String? = { "g1" },
        acl: () -> String? = { "acl-1" },
    ) = FaroV5MattersSearchReadSource("g1", owner, generation, acl) { true }

    @Test
    fun canonicalMatterFromActualRealityOwnerIsTypedAndOpaque() {
        RealityStore(":memory:").use { reality ->
            reality.run(TransactionId("tx-matter")) { scope ->
                reality.matterPersistence.upsert(row(), scope)
                AtomicApplyTransactionDecision.Commit(Unit)
            }
            val owner = source(reality.matterPersistence)
            val result = CanonicalSearchCoordinator(owner, null).search(query())
            assertEquals(CanonicalSearchCoordinator.Coverage.COMPLETE, result.coverage)
            assertEquals(1, result.hits.size)
            assertEquals(SearchCanonicalDocument.Kind.MATTER, result.hits.single().kind)
            assertTrue(result.hits.single().key.documentId.startsWith("m:"))
            assertFalse(result.hits.single().key.documentId.contains("secret"))
            val doc = owner.readAuthorized(FaroV5MattersSearchReadSource.NAMESPACE, 500)
                .documents.single()
            assertTrue(doc.body.contains("Factura"))
            assertEquals(row().id, owner.resolveAuthorizedId(doc.key, checkNotNull(owner.authorityStamp())))
        }
    }

    @Test
    fun revokedOrRegrantedAclDoesNotReviveOldMatter() {
        RealityStore(":memory:").use { reality ->
            reality.run(TransactionId("tx-matter1")) { scope ->
                reality.matterPersistence.upsert(row(), scope)
                AtomicApplyTransactionDecision.Commit(Unit)
            }
            var acl: String? = "acl-1"
            val owner = source(reality.matterPersistence, { "g1" }, { acl })
            val stamp = checkNotNull(owner.authorityStamp())
            val key = CanonicalSearchCoordinator(owner, null).search(query()).hits.single().key
            acl = null
            assertNull(owner.resolveAuthorizedId(key, stamp))
            acl = "acl-2"
            assertNull(owner.resolveAuthorizedId(key, stamp))
            reality.run(TransactionId("tx-matter2")) { scope ->
                reality.matterPersistence.upsert(row(at = 201L), scope)
                AtomicApplyTransactionDecision.Commit(Unit)
            }
            assertNull(owner.resolveAuthorizedId(key, stamp))
        }
    }

    @Test
    fun boundIsPartialWhenMatterOwnerHasMoreThanFiveHundredRows() {
        val owner = object : MatterPersistencePort {
            override fun read() = MatterSnapshot(1, (1..501).map { row(it) })
            override fun upsert(record: MatterRecord, transactionScope: RealityTransactionScope) =
                error("read-only owner fixture")
        }
        val result = CanonicalSearchCoordinator(source(owner), null).search(query())
        assertEquals(CanonicalSearchCoordinator.Coverage.PARTIAL, result.coverage)
        assertEquals(10, result.hits.size)
        assertTrue(result.isIndexDegraded)
    }

    @Test
    fun generationSwitchOrOwnerCloseNeverReturnsEmptyComplete() {
        val reality = RealityStore(":memory:")
        var gen: String? = "g1"
        val owner = source(reality.matterPersistence, { gen })
        gen = "other"
        assertEquals(CanonicalSearchCoordinator.Coverage.UNAVAILABLE,
            CanonicalSearchCoordinator(owner, null).search(query()).coverage)
        gen = "g1"
        reality.close()
        assertEquals(CanonicalSearchCoordinator.Coverage.UNAVAILABLE,
            CanonicalSearchCoordinator(owner, null).search(query()).coverage)
    }
}
