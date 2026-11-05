package ar.com.faro.v5.app

import ar.com.faro.v5.control.search.CanonicalSearchCoordinator
import ar.com.faro.v5.control.search.SearchCanonicalDocument
import ar.com.faro.v5.control.search.SearchProjectionQuery
import ar.com.faro.v5.kernel.contracts.AtomicApplyTransactionDecision
import ar.com.faro.v5.kernel.contracts.RealityTransactionScope
import ar.com.faro.v5.kernel.contracts.TransactionId
import ar.com.faro.v5.owner.objectives.api.*
import ar.com.faro.v5.owner.objectives.spi.ObjectivePersistencePort
import ar.com.faro.v5.store.reality.RealityStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FaroV5ObjectivesSearchReadSourceTest {
    private fun row(number: Int = 1, at: Long = 100L) = ObjectiveRecord(
        ObjectiveId.of("objective-secret-" + number),
        ObjectiveTitle.of("Ahorrar para emergencias"),
        ObjectiveDetail.of("Fondo de seguridad familiar"),
        ObjectiveStatus.ACTIVE,
        at,
    )
    private fun query() = SearchProjectionQuery(
        FaroV5ObjectivesSearchReadSource.NAMESPACE, "AHORRAR", 10,
    )
    private fun source(
        owner: ObjectivePersistencePort,
        generation: () -> String? = { "g1" },
        acl: () -> String? = { "acl-1" },
    ) = FaroV5ObjectivesSearchReadSource("g1", owner, generation, acl) { true }

    @Test
    fun canonicalObjectiveFromActualRealityOwnerIsTypedAndOpaque() {
        RealityStore(":memory:").use { reality ->
            reality.run(TransactionId("tx-obj")) { scope ->
                reality.objectivePersistence.upsert(row(), scope)
                AtomicApplyTransactionDecision.Commit(Unit)
            }
            val owner = source(reality.objectivePersistence)
            val result = CanonicalSearchCoordinator(owner, null).search(query())
            assertEquals(CanonicalSearchCoordinator.Coverage.COMPLETE, result.coverage)
            assertEquals(1, result.hits.size)
            assertEquals(SearchCanonicalDocument.Kind.OBJECTIVE, result.hits.single().kind)
            assertTrue(result.hits.single().key.documentId.startsWith("o:"))
            assertFalse(result.hits.single().key.documentId.contains("secret"))
            val doc = owner.readAuthorized(FaroV5ObjectivesSearchReadSource.NAMESPACE, 500)
                .documents.single()
            assertTrue(doc.body.contains("seguridad familiar"))
            assertEquals(row().id, owner.resolveAuthorizedId(doc.key, checkNotNull(owner.authorityStamp())))
        }
    }

    @Test
    fun changedOwnerRevisionOrRevokedAclNeverResolvesOldObjective() {
        RealityStore(":memory:").use { reality ->
            reality.run(TransactionId("tx-obj1")) { scope ->
                reality.objectivePersistence.upsert(row(), scope)
                AtomicApplyTransactionDecision.Commit(Unit)
            }
            var acl: String? = "acl-1"
            val owner = source(reality.objectivePersistence, { "g1" }, { acl })
            val stamp = checkNotNull(owner.authorityStamp())
            val key = CanonicalSearchCoordinator(owner, null).search(query()).hits.single().key
            acl = null
            assertNull(owner.resolveAuthorizedId(key, stamp))
            assertEquals(CanonicalSearchCoordinator.Coverage.UNAVAILABLE,
                CanonicalSearchCoordinator(owner, null).search(query()).coverage)
            acl = "acl-2"
            assertNull(owner.resolveAuthorizedId(key, stamp))
            reality.run(TransactionId("tx-obj2")) { scope ->
                reality.objectivePersistence.upsert(row(at = 200L), scope)
                AtomicApplyTransactionDecision.Commit(Unit)
            }
            assertNull(owner.resolveAuthorizedId(key, stamp))
        }
    }

    @Test
    fun moreThanFiveHundredObjectivesNeverClaimsComplete() {
        val owner = object : ObjectivePersistencePort {
            override fun read() = ObjectiveSnapshot(1, (1..501).map { row(it) })
            override fun upsert(record: ObjectiveRecord, transactionScope: RealityTransactionScope) =
                error("read-only owner fixture")
        }
        val result = CanonicalSearchCoordinator(source(owner), null).search(query())
        assertEquals(CanonicalSearchCoordinator.Coverage.PARTIAL, result.coverage)
        assertEquals(10, result.hits.size)
        assertTrue(result.isIndexDegraded)
    }

    @Test
    fun closedOwnerAndSwitchedGenerationAreUnavailable() {
        val reality = RealityStore(":memory:")
        var current: String? = "g1"
        val owner = source(reality.objectivePersistence, { current })
        current = "g2"
        assertEquals(CanonicalSearchCoordinator.Coverage.UNAVAILABLE,
            CanonicalSearchCoordinator(owner, null).search(query()).coverage)
        current = "g1"
        reality.close()
        assertEquals(CanonicalSearchCoordinator.Coverage.UNAVAILABLE,
            CanonicalSearchCoordinator(owner, null).search(query()).coverage)
    }
}
