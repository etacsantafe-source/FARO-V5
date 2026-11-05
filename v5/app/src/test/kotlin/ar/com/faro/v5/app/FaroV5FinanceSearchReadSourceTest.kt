package ar.com.faro.v5.app

import ar.com.faro.v5.control.search.CanonicalSearchCoordinator
import ar.com.faro.v5.control.search.SearchCanonicalDocument
import ar.com.faro.v5.control.search.SearchProjectionQuery
import ar.com.faro.v5.owner.finance.api.FinanceAccountDisplayName
import ar.com.faro.v5.owner.finance.api.FinanceAccountId
import ar.com.faro.v5.owner.finance.api.FinanceAccountNature
import ar.com.faro.v5.owner.finance.api.FinanceAccountSnapshot
import ar.com.faro.v5.owner.finance.api.FinanceCurrencyCode
import ar.com.faro.v5.owner.finance.api.FinanceEntryCategory
import ar.com.faro.v5.owner.finance.api.FinanceEntryConcept
import ar.com.faro.v5.owner.finance.api.FinanceEntryId
import ar.com.faro.v5.owner.finance.api.FinanceEntryKind
import ar.com.faro.v5.owner.finance.api.FinanceEntrySnapshot
import ar.com.faro.v5.owner.finance.api.FinanceSnapshot
import ar.com.faro.v5.owner.finance.api.FinanceSnapshotReadApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FaroV5FinanceSearchReadSourceTest {
    private val accountId = FinanceAccountId.of("private-account-123")
    private val currency = FinanceCurrencyCode.of("ARS")

    private fun entry(index: Int): FinanceEntrySnapshot = FinanceEntrySnapshot(
        entryId = FinanceEntryId.of("private-entry-" + index),
        accountId = accountId,
        amountMinor = 349_875L,
        kind = FinanceEntryKind.EXPENSE,
        currency = currency,
        concept = FinanceEntryConcept.of("Expensas de noviembre"),
        category = FinanceEntryCategory.of("Hogar"),
        effectiveAtEpochMillis = 100L,
        recordedAtEpochMillis = 200L + index,
    )

    private fun snapshot(revision: Long = 1L, entries: List<FinanceEntrySnapshot> = listOf(entry(1))):
        FinanceSnapshot = FinanceSnapshot(
        accounts = listOf(FinanceAccountSnapshot(
            accountId = accountId,
            displayName = FinanceAccountDisplayName.of("Cuenta Privada"),
            currency = currency,
            nature = FinanceAccountNature.BANK,
            openingBalanceMinor = 500_000L,
        )),
        revision = revision,
        entries = entries,
    )

    private fun source(
        reader: FinanceSnapshotReadApi,
        generation: () -> String? = { "generation-1" },
        acl: () -> String? = { "finance-acl-1" },
        admitted: () -> Boolean = { true },
    ) = FaroV5FinanceSearchReadSource(
        "generation-1", reader, generation, acl, admitted,
    )

    private fun query() = SearchProjectionQuery(
        FaroV5FinanceSearchReadSource.NAMESPACE, "EXPENSAS", 10,
    )

    @Test
    fun financeDocumentsComeOnlyFromCanonicalOwnerWithoutSensitiveAmountsOrAccountIds() {
        val owner = source(FinanceSnapshotReadApi { snapshot() })
        val result = CanonicalSearchCoordinator(owner, null).search(query())
        assertEquals(CanonicalSearchCoordinator.Coverage.COMPLETE, result.coverage)
        assertEquals(1, result.hits.size)
        assertEquals(SearchCanonicalDocument.Kind.FINANCE_ENTRY, result.hits.single().kind)
        assertEquals("Expensas de noviembre", result.hits.single().title)
        val doc = owner.readAuthorized(FaroV5FinanceSearchReadSource.NAMESPACE, 500)
            .documents.single()
        assertTrue(doc.key.documentId.startsWith("f:"))
        assertFalse(doc.key.documentId.contains("private-entry"))
        assertFalse(doc.key.documentId.contains("private-account"))
        assertEquals("Hogar", doc.body)
        assertFalse(doc.title.contains("349875"))
        assertFalse(doc.body.contains("Cuenta Privada"))
    }

    @Test
    fun revokedAclSwitchedGenerationOrRecoveryDeniedNeverReturnsEmptyComplete() {
        var acl: String? = "finance-acl-1"
        var generation = "generation-1"
        var admitted = true
        val owner = source(FinanceSnapshotReadApi { snapshot() },
            { generation }, { acl }, { admitted })
        val search = CanonicalSearchCoordinator(owner, null)
        assertEquals(CanonicalSearchCoordinator.Coverage.COMPLETE, search.search(query()).coverage)
        acl = null
        assertEquals(CanonicalSearchCoordinator.Coverage.UNAVAILABLE, search.search(query()).coverage)
        acl = "finance-acl-2"
        generation = "generation-2"
        assertEquals(CanonicalSearchCoordinator.Coverage.UNAVAILABLE, search.search(query()).coverage)
        generation = "generation-1"
        admitted = false
        assertEquals(CanonicalSearchCoordinator.Coverage.UNAVAILABLE, search.search(query()).coverage)
    }

    @Test
    fun ownerRevisionChangeAndOwnerFailureFailClosed() {
        var calls = 0
        val changingOwner = source(FinanceSnapshotReadApi {
            calls += 1
            snapshot(revision = calls.toLong())
        })
        assertEquals(
            CanonicalSearchCoordinator.Coverage.UNAVAILABLE,
            CanonicalSearchCoordinator(changingOwner, null).search(query()).coverage,
        )
        val failingOwner = source(FinanceSnapshotReadApi {
            throw IllegalStateException("Reality closed")
        })
        assertEquals(
            CanonicalSearchCoordinator.Coverage.UNAVAILABLE,
            CanonicalSearchCoordinator(failingOwner, null).search(query()).coverage,
        )
    }

    @Test
    fun moreThanFiveHundredEntriesYieldsPartialNeverFalseFullCoverage() {
        val owner = source(FinanceSnapshotReadApi {
            snapshot(entries = (1..501).map(::entry))
        })
        val result = CanonicalSearchCoordinator(owner, null).search(query())
        assertEquals(CanonicalSearchCoordinator.Coverage.PARTIAL, result.coverage)
        assertEquals(10, result.hits.size)
        assertTrue(result.isIndexDegraded)
    }
    @Test
    fun financeEntryTargetResolvesOnlyExactBoundedCanonicalKeyAndAuthority() {
        val owner = source(FinanceSnapshotReadApi { snapshot() })
        val issued = requireNotNull(owner.authorityStamp())
        val result = CanonicalSearchCoordinator(owner, null).search(query())
        val key = result.hits.single().key
        assertEquals(FinanceEntryId.of("private-entry-1"),
            owner.resolveAuthorizedEntryId(key, issued))
        assertEquals(null, owner.resolveAuthorizedEntryId(
            ar.com.faro.v5.control.search.SearchProjectionKey(
                "workspace.conversations", key.documentId), issued))
        assertEquals(null, owner.resolveAuthorizedEntryId(
            ar.com.faro.v5.control.search.SearchProjectionKey(
                FaroV5FinanceSearchReadSource.NAMESPACE, "f:" + "a".repeat(64)), issued))
    }

    @Test
    fun financeTargetRejectsRevokeRegrantRecoveryRevisionAndClosedOwner() {
        var acl: String? = "finance-acl-1"
        var generation = "generation-1"
        var revision = 1L
        var closed = false
        val owner = source(FinanceSnapshotReadApi {
            if (closed) error("Reality closed")
            snapshot(revision)
        }, { generation }, { acl }, { true })
        val issued = requireNotNull(owner.authorityStamp())
        val key = CanonicalSearchCoordinator(owner, null).search(query()).hits.single().key
        assertEquals(FinanceEntryId.of("private-entry-1"),
            owner.resolveAuthorizedEntryId(key, issued))
        acl = null
        assertEquals(null, owner.resolveAuthorizedEntryId(key, issued))
        acl = "finance-acl-2"
        assertEquals(null, owner.resolveAuthorizedEntryId(key, issued))
        acl = "finance-acl-1"
        generation = "generation-2"
        assertEquals(null, owner.resolveAuthorizedEntryId(key, issued))
        generation = "generation-1"
        revision = 2L
        assertEquals(null, owner.resolveAuthorizedEntryId(key, issued))
        revision = 1L
        closed = true
        assertEquals(null, owner.resolveAuthorizedEntryId(key, issued))
    }


}
