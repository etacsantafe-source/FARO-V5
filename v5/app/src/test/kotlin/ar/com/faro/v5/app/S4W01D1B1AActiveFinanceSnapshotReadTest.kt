package ar.com.faro.v5.app

import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.StorageGenerationId
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
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * 10 synthetic JVM tests of a dormant read fence. NOT Android UID/SQL9/human
 * identity/UI certification. All authority callbacks are deliberately fake.
 */
class S4W01D1B1AActiveFinanceSnapshotReadTest {
    @TempDir lateinit var root: Path

    private val ars = FinanceCurrencyCode.of("ARS")
    private val usd = FinanceCurrencyCode.of("USD")
    private val arsId = FinanceAccountId.of("acc-ars")
    private val usdId = FinanceAccountId.of("acc-usd")

    private fun entry(
        account: FinanceAccountId = arsId,
        currency: FinanceCurrencyCode = ars,
        id: String = "entry-1",
        amount: Long = 900L,
    ) = FinanceEntrySnapshot(
        entryId = FinanceEntryId.of(id),
        accountId = account,
        amountMinor = amount,
        kind = FinanceEntryKind.EXPENSE,
        currency = currency,
        concept = FinanceEntryConcept.of("Pago local"),
        category = FinanceEntryCategory.of("Casa"),
        effectiveAtEpochMillis = 100L,
        recordedAtEpochMillis = 200L,
    )

    private fun snapshot(
        revision: Long = 3L,
        amount: Long = 900L,
        opening: Long = 10_000L,
    ) = FinanceSnapshot(
        accounts = listOf(
            FinanceAccountSnapshot(
                arsId, FinanceAccountDisplayName.of("Efectivo ARS"),
                ars, FinanceAccountNature.CASH, opening,
            ),
            FinanceAccountSnapshot(
                usdId, FinanceAccountDisplayName.of("Caja USD"),
                usd, FinanceAccountNature.CASH, 400L,
            ),
        ),
        revision = revision,
        entries = listOf(entry(amount = amount)),
    )

    private inner class Rig(name: String) {
        val pointer = FileActiveGenerationPointer(root.resolve("d1b1a-" + name))
        val pinned = requireNotNull(pointer.compareAndSet(
            null, StorageGenerationId("generation-" + name),
        ))
        val uid = 10123
        var ownerReady = true
        var foreground = true
        var hostEpoch: String? = "host-1"
        var acl: String? = "acl-1"
        var evidence: AndroidSandboxTechnicalEvidence? =
            AndroidSandboxTechnicalEvidence(
                "sandbox-" + name,
                S4W01D1B1AActiveFinanceSnapshotRead.DEV_APPLICATION_ID,
                uid, pinned,
            )
        var value: FinanceSnapshot = snapshot()
        var readCount = 0
        var onRead: (Int) -> Unit = {}

        fun guard() = S4W01D1B1AActiveFinanceSnapshotRead(
            pinned = pinned,
            pointer = pointer,
            expectedApplicationUid = uid,
            expectedHostEpoch = "host-1",
            hostEpochOrNull = { hostEpoch },
            ownerReady = { ownerReady },
            foregroundReady = { foreground },
            sessionAclEpochOrNull = { acl },
            sandboxEvidenceOrNull = { evidence },
            owner = FinanceSnapshotReadApi {
                readCount++
                onRead(readCount)
                value
            },
        )
    }

    @Test fun r01NoForegroundOrClosedOwnerDeniesBeforeSourceRead() {
        val f = Rig("paused")
        f.foreground = false
        f.guard().use {
            assertNull(it.observedForDevOnly())
            assertEquals(0, f.readCount)
            assertTrue(it.isClosed())
        }
        val g = Rig("owner")
        g.ownerReady = false
        g.guard().use {
            assertNull(it.observedForDevOnly())
            assertEquals(0, g.readCount)
        }
    }

    @Test fun r02StableActiveOwnerReadsTwiceAndNeverMixesCurrency() {
        val f = Rig("success")
        f.guard().use {
            val observed = requireNotNull(it.observedForDevOnly())
            assertEquals("generation-success", observed.generationId)
            assertEquals(f.pinned.sequence, observed.pointerSequence)
            assertEquals(3L, observed.ownerRevision)
            assertEquals(2, f.readCount)
            assertEquals(9_100L, observed.source.ledgerMinor(arsId))
            assertEquals(400L, observed.source.ledgerMinor(usdId))
            assertEquals(
                "ACTIVE_FINANCE_SNAPSHOT_NOT_FINANCE9",
                S4W01D1B1AActiveFinanceObservation.SOURCE_KIND,
            )
        }
    }

    @Test fun r03WrongPackageOrApplicationUidNeverReads() {
        val f = Rig("package")
        f.evidence = f.evidence!!.copy(applicationId = "ar.com.faro.alpha.s3integrateddev")
        f.guard().use {
            assertNull(it.observedForDevOnly())
            assertEquals(0, f.readCount)
        }
        val g = Rig("uid")
        g.evidence = g.evidence!!.copy(applicationUid = g.uid + 1)
        g.guard().use {
            assertNull(it.observedForDevOnly())
            assertEquals(0, g.readCount)
        }
    }

    @Test fun r04FullPointerAbaCannotReviveTheOldReadSession() {
        val f = Rig("aba")
        val guard = f.guard()
        val other = StorageGenerationId("other-generation")
        assertNotNull(f.pointer.compareAndSet(f.pinned.generationId, other))
        assertNotNull(f.pointer.compareAndSet(other, f.pinned.generationId))
        assertNull(guard.observedForDevOnly())
        assertTrue(guard.isClosed())
        assertNull(guard.observedForDevOnly())
        assertEquals(0, f.readCount)
    }

    @Test fun r05HostEpochDriftBetweenOwnerReadsDenies() {
        val f = Rig("host-drift")
        f.onRead = { if (it == 1) f.hostEpoch = "host-2" }
        f.guard().use {
            assertNull(it.observedForDevOnly())
            assertTrue(it.isClosed())
            assertEquals(1, f.readCount)
        }
    }

    @Test fun r06AclRevokeAndRegrantAfterFailedReadNeverRevives() {
        val f = Rig("acl-revoke")
        f.onRead = { if (it == 1) f.acl = null }
        f.guard().use {
            assertNull(it.observedForDevOnly())
            f.acl = "acl-1"
            assertNull(it.observedForDevOnly())
            assertEquals(1, f.readCount)
        }
    }

    @Test fun r07ChangingOwnerRevisionAcrossDoubleReadIsDenied() {
        val f = Rig("revision-drift")
        f.onRead = { if (it == 2) f.value = snapshot(revision = 4L) }
        f.guard().use {
            assertNull(it.observedForDevOnly())
            assertTrue(it.isClosed())
            assertEquals(2, f.readCount)
        }
    }

    @Test fun r08ChangingOwnerPayloadWithoutRevisionStillDenied() {
        val f = Rig("same-revision-drift")
        f.onRead = { if (it == 2) f.value = snapshot(revision = 3L, amount = 1_000L) }
        f.guard().use {
            assertNull(it.observedForDevOnly())
            assertEquals(2, f.readCount)
        }
    }

    @Test fun r09LedgerOverflowDoesNotProduceInventedBalance() {
        val f = Rig("ledger-overflow")
        f.value = snapshot(opening = Long.MAX_VALUE, amount = 900L)
        // Income would overflow; existing EXPENSE underflows only at Long.MIN_VALUE.
        f.value = f.value.copy(entries = listOf(
            FinanceEntrySnapshot(
                entryId = FinanceEntryId.of("overflow-entry"),
                accountId = arsId,
                amountMinor = 900L,
                kind = FinanceEntryKind.INCOME,
                currency = ars,
                concept = FinanceEntryConcept.of("Ingreso"),
                category = FinanceEntryCategory.of("Ingreso"),
                effectiveAtEpochMillis = 100L,
                recordedAtEpochMillis = 200L,
            ),
        ))
        f.guard().use {
            assertNull(it.observedForDevOnly())
            assertTrue(it.isClosed())
            assertEquals(2, f.readCount)
        }
    }

    @Test fun r10SqlFailureOrCloseDuringReadFailsClosed() {
        val failure = Rig("source-error")
        failure.onRead = { throw IllegalStateException("Synthetic Reality closed") }
        failure.guard().use {
            assertNull(it.observedForDevOnly())
            assertEquals(1, failure.readCount)
        }
        val f = Rig("close-while-reading")
        lateinit var guard: S4W01D1B1AActiveFinanceSnapshotRead
        f.onRead = { if (it == 2) guard.close() }
        guard = f.guard()
        guard.use {
            assertNull(it.observedForDevOnly())
            assertTrue(it.isClosed())
            assertEquals(2, f.readCount)
        }
    }
}
