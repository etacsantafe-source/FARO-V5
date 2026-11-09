package ar.com.faro.v5.app

import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.maintenance.*
import ar.com.faro.v5.owner.finance.api.*
import ar.com.faro.v5.owner.objectives.api.*
import ar.com.faro.v5.owner.matters.api.*
import ar.com.faro.v5.surface.FaroV5SearchCoverage
import ar.com.faro.v5.surface.FaroV5SearchOpenResult
import java.nio.file.Path
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** New X07/X11 acceptance of owner revisions with a pinned Reality8 pointer.
 * Distinct from the six previously certified A5 suites (33/33).
 * All fixture writes are synchronous, one at a time, only in @TempDir.
 */
class FaroV5S3A57OwnerRevisionAcceptanceTest {
    @TempDir lateinit var root: Path
    private val id = StorageGenerationId("v2-core-dev-planning8-1")
    private val pointer get() = FileActiveGenerationPointer(root)

    private fun start() {
        FaroV5V2CoreDevAuthority(root).open().use { }
        FaroV5V2CoreDevAuthority(root)
            .upgradePlanning8AfterExplicitForegroundRequest { true }.use { }
        assertEquals(id, checkNotNull(pointer.read()).generationId)
    }

    private suspend fun write(owner: String, version: Int) {
        val active = checkNotNull(pointer.read())
        FaroV5ActiveGenerationGraphOwner.openExistingPlanning8ForCertifiedHost(
            root, id.value, active.sequence, SingleProcessMaterialWriteGate(), true,
        ).use { graph ->
            val reality = graph.reality
            reality.run(TransactionId("x07-owner-$owner-$version")) { scope ->
                when (owner) {
                    "objective" -> reality.objectivePersistence.upsert(
                        ObjectiveRecord(
                            ObjectiveId.of("x07-obj"),
                            ObjectiveTitle.of(if (version == 1) "X07AntesObjetivo" else "X07DespuesObjetivo"),
                            ObjectiveDetail.of("Detalle local"),
                            ObjectiveStatus.ACTIVE,
                            1000L * version,
                        ), scope,
                    )
                    "matter" -> reality.matterPersistence.upsert(
                        MatterRecord(
                            MatterId.of("x11-matter"),
                            MatterTitle.of(if (version == 1) "X11AntesAsunto" else "X11DespuesAsunto"),
                            MatterDetail.of("Detalle local"),
                            MatterStatus.WAITING,
                            10000L,
                            1000L * version,
                        ), scope,
                    )
                    "finance" -> {
                        val account = FinanceAccountId.of("private-x07-account")
                        if (version == 1) reality.financePersistence.createAccount(
                            FinanceAccountCreate(account, FinanceAccountDisplayName.of("Cuenta"),
                                FinanceCurrencyCode.of("ARS"), FinanceAccountNature.BANK), scope,
                        )
                        reality.financePersistence.recordEntry(
                            FinanceEntryRecord(
                                entryId = FinanceEntryId.of("private-x07-entry-$version"),
                                accountId = account, amountMinor = 991234L,
                                kind = FinanceEntryKind.EXPENSE,
                                concept = FinanceEntryConcept.of(
                                    if (version == 1) "X07AntesFinanzas" else "X07DespuesFinanzas"),
                                category = FinanceEntryCategory.of("Pruebas"),
                                effectiveAtEpochMillis = 1000L,
                                recordedAtEpochMillis = 1000L * version,
                            ), scope,
                        )
                    }
                    else -> error("Unexpected owner $owner")
                }
                AtomicApplyTransactionDecision.Commit(Unit)
            }
        }
    }

    @Test fun x07ObjectiveChangeInvalidatesIssuedHitWithoutPointerChange() = runBlocking {
        start()
        write("objective", 1)
        val pinned = checkNotNull(pointer.read())
        FaroV5V2CoreDevAuthority(root).open().use { session ->
            val port = session.searchReadPort
            val before = port.search("X07AntesObjetivo")
            assertEquals(FaroV5SearchCoverage.COMPLETE, before.coverage)
            val old = before.hits.single()
            assertEquals(FaroV5ObjectivesSearchReadSource.NAMESPACE, old.namespace)
            write("objective", 2)
            assertEquals(pinned, pointer.read())
            assertEquals(FaroV5SearchOpenResult.UNAVAILABLE, port.openAuthorized(old))
            assertNull(port.takeConfirmedDomainTarget())
            val fresh = port.search("X07DespuesObjetivo").hits.single()
            assertEquals(FaroV5SearchOpenResult.CONFIRMED, port.openAuthorized(fresh))
            assertNotNull(port.takeConfirmedDomainTarget())
            assertNull(port.takeConfirmedDomainTarget())
        }
    }

    @Test fun x11ConfirmedMatterCannotBeConsumedAfterCanonicalEdit() = runBlocking {
        start()
        write("matter", 1)
        val pinned = checkNotNull(pointer.read())
        FaroV5V2CoreDevAuthority(root).open().use { session ->
            val port = session.searchReadPort
            val old = port.search("X11AntesAsunto").hits.single()
            assertEquals(FaroV5MattersSearchReadSource.NAMESPACE, old.namespace)
            assertEquals(FaroV5SearchOpenResult.CONFIRMED, port.openAuthorized(old))
            write("matter", 2)
            assertEquals(pinned, pointer.read())
            assertNull(port.takeConfirmedDomainTarget(), "stale confirmed data must be discarded")
            val fresh = port.search("X11DespuesAsunto").hits.single()
            assertEquals(FaroV5SearchOpenResult.CONFIRMED, port.openAuthorized(fresh))
            assertNotNull(port.takeConfirmedDomainTarget())
        }
    }

    @Test fun x07FinanceRevisionFencesEvenUnchangedOldEntry() = runBlocking {
        start()
        write("finance", 1)
        val pinned = checkNotNull(pointer.read())
        FaroV5V2CoreDevAuthority(root).open().use { session ->
            val port = session.searchReadPort
            val old = port.search("X07AntesFinanzas").hits.single()
            assertEquals(FaroV5FinanceSearchReadSource.NAMESPACE, old.namespace)
            write("finance", 2)
            assertEquals(pinned, pointer.read())
            assertEquals(FaroV5SearchOpenResult.UNAVAILABLE, port.openAuthorized(old))
            assertNull(port.takeConfirmedDomainTarget())
            val fresh = port.search("X07DespuesFinanzas").hits.single()
            assertEquals(FaroV5SearchOpenResult.CONFIRMED, port.openAuthorized(fresh))
            val target = checkNotNull(port.takeConfirmedDomainTarget())
            assertFalse(target.summary.contains("991234"))
            assertFalse(target.summary.contains("private-x07-account"))
        }
    }

    @Test fun unrelatedOwnerRevisionDoesNotFalselyInvalidateObjectiveTarget() = runBlocking {
        start()
        write("objective", 1)
        write("matter", 1)
        val pinned = checkNotNull(pointer.read())
        FaroV5V2CoreDevAuthority(root).open().use { session ->
            val port = session.searchReadPort
            val old = port.search("X07AntesObjetivo").hits.single()
            write("matter", 2)
            assertEquals(pinned, pointer.read())
            assertEquals(FaroV5SearchOpenResult.CONFIRMED, port.openAuthorized(old))
            val target = checkNotNull(port.takeConfirmedDomainTarget())
            assertEquals(old.canonicalSource, target.canonicalSource)
            assertNull(port.takeConfirmedDomainTarget())
        }
    }
}
