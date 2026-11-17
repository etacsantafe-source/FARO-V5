package ar.com.faro.v5.store.reality

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * W01-B3B1B0 SQLite stage-only. Synthetic issuer is NOT human authentication.
 * This tests one WAL snapshot, not a shippable read authority or live UI.
 */
class RealityFinance9ObservedSourceReadTest {
    @TempDir lateinit var root: Path

    private fun open(file: Path): SQLiteConnection =
        BundledSQLiteDriver().open(file.toString()).also {
            it.execSQL("PRAGMA foreign_keys=ON")
        }

    private fun stageEight(name: String): Path =
        root.resolve(name + ".db").also { file ->
            RealityStore(file.toString(), planningStage8 = true).use {
                assertEquals(8, it.schemaVersion())
            }
        }

    private fun stageNine(name: String): Path = stageEight(name).also { file ->
        open(file).use {
            RealityFinance9Schema.installOnStagedEightConnection(
                it, "b3b1b0-" + name, "e".repeat(64), 1000L,
            )
        }
    }

    private fun req() = RealityFinance9ReadPolicyObservationRequest(
        principalHash = "a".repeat(64),
        generationId = "gen-b3b1b0", pointerSequence = 8L,
        expectedPolicyEpoch = 1L, observedAtMs = 1200L,
    )

    private fun key(request: RealityFinance9ReadPolicyObservationRequest = req(),
        operationId: String = RealityFinance9ReadPolicyObservationPort.OPERATION_ID,
    ) = Finance9AclScope(
        request.principalHash, request.generationId, request.pointerSequence,
        operationId, RealityFinance9ReadPolicyObservationPort.OBJECT_SCOPE,
    )

    private fun issue(file: Path,
        operationId: String = RealityFinance9ReadPolicyObservationPort.OPERATION_ID,
        expiry: Long = 5000L,
        issuedAt: Long = 1000L,
    ): Long = open(file).use {
        RealityFinance9Acl(it, { true }).issueAfterTrustedStageAdmission(
            key(operationId = operationId), issuedAt, expiry,
        )
    }

    private fun revoke(file: Path): Long? = open(file).use {
        RealityFinance9Acl(it, { true }).revoke(key())
    }

    private fun account(db: SQLiteConnection, id: String = "cash") {
        db.execSQL(
            "INSERT INTO finance_account(account_id,display_name,currency_code,nature) " +
                "VALUES('$id','Prueba','ARS','CASH')"
        )
    }

    private fun physical(db: SQLiteConnection, minor: Long, id: String = "cash") {
        db.execSQL(
            "INSERT INTO finance_account_opening(account_id,opening_cause_id,amount_minor) " +
                "VALUES('$id','cause-$id',$minor)"
        )
    }

    private fun confirm(db: SQLiteConnection, minor: Long, id: String = "cash") {
        db.execSQL(
            "INSERT INTO finance_opening_confirmation(" +
                "account_id,amount_minor,confirmation_state,confirmed_at_ms,provenance) " +
                "VALUES('$id',$minor,'CONFIRMED',1000,'SYNTHETIC_STAGE_ONLY')"
        )
    }

    private fun read(file: Path,
        request: RealityFinance9ReadPolicyObservationRequest = req(),
    ): RealityFinance9ObservedSourceRead? =
        RealityStore(file.toString(), planningStage8 = true, financeStage9 = true).use {
            it.finance9ObservedSourceReadForDevOnly().observe(request)
        }

    private fun count(file: Path, table: String): Long = open(file).use { db ->
        db.prepare("SELECT count(*) FROM " + table).use {
            assertTrue(it.step())
            it.getLong(0)
        }
    }

    @Test fun t01MissingPolicyNeverReleasesAnOpeningSource() {
        val file = stageNine("missing")
        open(file).use { account(it) }
        assertNull(read(file))
        assertEquals(0L, count(file, "finance_acl_policy"))
        assertEquals(0L, count(file, "finance_apply_grant"))
    }

    @Test fun t02ExactSqlPolicyAndConfirmedOpeningObservedTogether() {
        val file = stageNine("confirmed")
        open(file).use {
            account(it)
            physical(it, 500L)
            confirm(it, 500L)
        }
        assertEquals(1L, issue(file))
        val observed = requireNotNull(read(file))
        assertEquals(1L, observed.observedPolicy.policyEpoch)
        assertEquals(1L, observed.observedPolicy.globalPolicyRevision)
        assertEquals(0L, observed.openingSource.sourceRevision)
        val row = observed.openingSource.accounts.single()
        assertEquals(RealityFinance9OpeningProvenanceState.CONFIRMED_IN_SQL, row.state)
        assertEquals(500L, row.confirmedOpeningMinor)
        assertEquals(500L, row.ledgerBalanceMinor)
        assertEquals(0L, count(file, "finance_apply_grant"))
        assertEquals(0L, count(file, "execution_receipt"))
    }

    @Test fun t03WriteOnlyAndForeignReadPolicyCannotObserveData() {
        val file = stageNine("foreign")
        open(file).use { account(it) }
        assertEquals(1L, issue(file, "finance.account.create"))
        assertNull(read(file))
        assertEquals(2L, issue(file))
        assertNull(read(file, req().copy(expectedPolicyEpoch = 1L)))
        assertNull(read(file, req().copy(principalHash = "b".repeat(64),
            expectedPolicyEpoch = 2L)))
        assertNull(read(file, req().copy(pointerSequence = 9L,
            expectedPolicyEpoch = 2L)))
        assertNull(read(file, req().copy(generationId = "other",
            expectedPolicyEpoch = 2L)))
        assertNotNull(read(file, req().copy(expectedPolicyEpoch = 2L)))
    }

    @Test fun t04RevocationAndRenewalNeverRecycleTheOldReadEpoch() {
        val file = stageNine("revoke")
        open(file).use { account(it) }
        assertEquals(1L, issue(file))
        assertNotNull(read(file))
        assertEquals(2L, revoke(file))
        assertNull(read(file))
        assertEquals(3L, issue(file, issuedAt = 1201L, expiry = 8000L))
        assertNull(read(file))
        val renewed = requireNotNull(read(file,
            req().copy(expectedPolicyEpoch = 3L, observedAtMs = 1300L)))
        assertEquals(3L, renewed.observedPolicy.globalPolicyRevision)
        assertEquals(0L, count(file, "finance_apply_grant"))
    }

    @Test fun t05ExpiryAtTheBoundaryDeniesEvenCoherentLedger() {
        val file = stageNine("expiry")
        open(file).use {
            account(it)
            confirm(it, 0L)
        }
        issue(file, expiry = 1500L)
        assertNotNull(read(file, req().copy(observedAtMs = 1499L)))
        assertNull(read(file, req().copy(observedAtMs = 1500L)))
    }

    @Test fun t06UnknownOpeningIsNullEvenWhenPolicyObserved() {
        val file = stageEight("legacy")
        open(file).use {
            account(it)
            physical(it, 725L)
        }
        open(file).use {
            RealityFinance9Schema.installOnStagedEightConnection(
                it, "legacy-b3b1b0", "d".repeat(64), 1000L,
            )
        }
        issue(file)
        val row = requireNotNull(read(file)).openingSource.accounts.single()
        assertEquals(RealityFinance9OpeningProvenanceState.LEGACY_UNVERIFIED, row.state)
        assertNull(row.confirmedOpeningMinor)
        assertNull(row.ledgerBalanceMinor)
    }

    @Test fun t07ContradictionAndLedgerOverflowFailClosed() {
        val file = stageNine("contradiction")
        open(file).use {
            account(it)
            physical(it, 500L)
            confirm(it, 501L)
        }
        issue(file)
        assertNull(read(file))
        val overflow = stageNine("overflow")
        open(overflow).use {
            account(it)
            physical(it, 10L)
            confirm(it, 10L)
            it.execSQL(
                "INSERT INTO finance_entry(entry_id,account_id,amount_minor,kind," +
                    "currency_code,concept,category,effective_at_epoch_millis," +
                    "recorded_at_epoch_millis) VALUES " +
                    "('huge','cash',9223372036854775807,'INCOME','ARS'," +
                    "'Prueba','Prueba',1000,1000)"
            )
        }
        issue(overflow)
        assertNull(read(overflow))
    }

    @Test fun t08LegacyFactoriesClosedPortAndNoWriteSideEffects() {
        val seven = root.resolve("seven.db")
        RealityStore(seven.toString()).use {
            assertThrows(IllegalStateException::class.java) {
                it.finance9ObservedSourceReadForDevOnly()
            }
        }
        val eight = stageEight("eight")
        RealityStore(eight.toString(), planningStage8 = true).use {
            assertThrows(IllegalStateException::class.java) {
                it.finance9ObservedSourceReadForDevOnly()
            }
        }
        val nine = stageNine("closed")
        issue(nine)
        val store = RealityStore(nine.toString(),
            planningStage8 = true, financeStage9 = true)
        val port = store.finance9ObservedSourceReadForDevOnly()
        store.close()
        assertNull(port.observe(req()))
        assertEquals(0L, count(nine, "execution_receipt"))
        assertEquals(0L, count(nine, "finance_apply_grant"))
        assertEquals(1L, count(nine, "finance_acl_policy"))
    }
}
