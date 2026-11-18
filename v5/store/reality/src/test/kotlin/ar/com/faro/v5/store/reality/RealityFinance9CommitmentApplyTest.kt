package ar.com.faro.v5.store.reality

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import ar.com.faro.v5.kernel.apply.*
import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.kernel.runtime.BuiltInContractCatalog
import ar.com.faro.v5.kernel.runtime.PreparedPlan
import ar.com.faro.v5.owner.finance.api.*
import java.nio.file.Path
import java.time.YearMonth
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class RealityFinance9CommitmentApplyTest {
    @TempDir lateinit var root: Path

    private val catalog = BuiltInContractCatalog.create()
    private val ars = FinanceCurrencyCode.of("ARS")
    private val usd = FinanceCurrencyCode.of("USD")
    private val start = YearMonth.of(2026, 11)
    private val debt = FinanceCommitmentCreate.Debt(FinanceCommitmentId("loan"), "Préstamo", "Acreedor", ars, 3600, 1000)
    private val schedule = FinanceCommitmentCreate.Schedule(
        FinanceCommitmentId("monthly"), debt.id, ars, 100, start, FinanceScheduleRecurrence.MONTHLY, 37, 1000,
    )

    private fun installment(offset: Long) = FinanceCommitmentCreate.PlanItem(
        null, FinanceCommitmentCreate.ScheduleReference(schedule.id, 1), debt.id,
        start.plusMonths(offset), (offset + 1).toInt(), start.plusMonths(offset).atDay(15), ars, 100, 1000,
    )

    private fun openActive(file: Path) = RealityStore(
        file.toString(), materialWriteGate = SingleProcessMaterialWriteGate(),
        planningStage8 = true, financeActive9 = true,
    )

    private fun fixture(): Path {
        val file = root.resolve("active.db")
        RealityStore(file.toString(), planningStage8 = true).close()
        BundledSQLiteDriver().open(file.toString()).use { db ->
            db.execSQL("PRAGMA foreign_keys=ON")
            RealityFinance9Schema.installOnStagedEightConnection(db, "commitments-stage", "a".repeat(64), 1000)
        }
        return file
    }

    private fun plan(
        command: FinanceCommitmentCreate,
        key: String,
        payload: CanonicalPayload = FinanceCommitmentCreatePayload.payload(command),
    ): PreparedPlan {
        val operation = when (command) {
            is FinanceCommitmentCreate.Debt -> FinanceOperations.DEBT_CREATE
            is FinanceCommitmentCreate.Schedule -> FinanceOperations.SCHEDULE_CREATE
            is FinanceCommitmentCreate.PlanItem -> FinanceOperations.PLAN_ITEM_CREATE
        }
        return PreparedPlan.of(ApplyPlanNode(
            PlanNodeId("declaration"),
            PreparedOperation(requireNotNull(catalog.current(operation)), payload, DependencyStampSet.of(), IdempotencyKey(key)),
            emptySet(),
        ))
    }

    private fun execute(store: RealityStore, plan: PreparedPlan): ApplyTransactionExecution {
        val identity = store.applyAttemptIdentities.resolve(plan)
        val bindings = ApplyBindingResolutionGate(FinanceCommitmentCreateBinding.all(store.finance9CommitmentWriteForDevOnly()))
            .resolve(plan) as ApplyBindingResolution.Resolved
        return ApplyTransactionExecutor(store, store).execute(plan, identity, bindings)
    }

    private fun assertRejected(store: RealityStore, plan: PreparedPlan) {
        val identity = store.applyAttemptIdentities.resolve(plan)
        assertFalse(execute(store, plan) is ApplyTransactionExecution.Completed)
        assertNull(store.executionReceiptLookup.find(identity.transactionId))
    }

    @Test fun persistsDeclarationsAndReplaysReceiptsWithoutLedgerEffects() {
        val file = fixture()
        val manual = installment(0).copy(
            manualId = FinanceCommitmentId("manual-past"), schedule = null, debtId = null,
            period = start.minusMonths(1), dueDate = start.minusMonths(1).atEndOfMonth(), currency = usd,
        )
        val farItems = listOf(7L, 12L, 36L).map(::installment)
        val declarations = listOf(debt, schedule, manual) + farItems
        val plans = declarations.mapIndexed { index, command -> plan(command, "declare-$index") }
        val receipts = openActive(file).use { store ->
            plans.map { (execute(store, it) as ApplyTransactionExecution.Completed).receipt }
        }
        openActive(file).use { store ->
            for ((plan, receipt) in plans.zip(receipts)) {
                val retry = ApplyRetryReceiptGate(store.applyAttemptIdentities, store.executionReceiptLookup).resolve(plan)
                assertEquals(receipt, (retry as ApplyRetryResolution.AlreadyCommitted).receipt)
            }
            assertEquals(receipts.size, receipts.map { it.transactionId }.toSet().size)
            assertTrue(store.financePersistence.read().entries.isEmpty())
        }
        BundledSQLiteDriver().open(file.toString()).use { db ->
            db.prepare("SELECT title, creditor, currency_code, principal_minor FROM finance_debt").use {
                assertTrue(it.step())
                assertEquals(listOf(debt.title, debt.creditor, "ARS"), (0..2).map(it::getText))
                assertEquals(debt.principalMinor, it.getLong(3))
                assertFalse(it.step())
            }
            db.prepare("SELECT debt_id, start_period, total_count, version FROM finance_schedule").use {
                assertTrue(it.step())
                assertEquals(debt.id.value, it.getText(0))
                assertEquals(start.toString(), it.getText(1))
                assertEquals(37L, it.getLong(2))
                assertEquals(1L, it.getLong(3))
                assertFalse(it.step())
            }
            db.prepare(
                "SELECT plan_item_id, due_local_date, state, currency_code FROM finance_plan_item ORDER BY period",
            ).use {
                for (item in listOf(manual) + farItems) {
                    assertTrue(it.step())
                    assertEquals(listOf(item.id.value, item.dueDate.toString(), "PLANNED", item.currency.value),
                        (0..3).map(it::getText))
                }
                assertFalse(it.step())
            }
            db.prepare("SELECT count(*) FROM finance_plan_item_payment").use {
                assertTrue(it.step())
                assertEquals(0L, it.getLong(0))
            }
        }
    }

    @Test fun rejectsMissingDebtAndCurrencyMismatch() {
        openActive(fixture()).use { store ->
            assertRejected(store, plan(schedule, "schedule-no-debt"))
            assertTrue(execute(store, plan(debt, "debt")) is ApplyTransactionExecution.Completed)
            assertRejected(store, plan(schedule.copy(currency = usd), "schedule-currency"))
            assertRejected(store, plan(installment(0).copy(
                manualId = FinanceCommitmentId("manual-wrong-currency"), schedule = null, currency = usd,
            ), "item-currency"))
        }
    }

    @Test fun rejectsStaleDuplicateAndOutOfRangeInstallments() {
        openActive(fixture()).use { store ->
            assertTrue(execute(store, plan(debt, "debt")) is ApplyTransactionExecution.Completed)
            assertTrue(execute(store, plan(schedule, "schedule")) is ApplyTransactionExecution.Completed)
            val first = installment(0)
            val version = requireNotNull(first.schedule)
            assertRejected(store, plan(first.copy(schedule = version.copy(version = 2)), "stale"))
            assertRejected(store, plan(installment(37), "out-of-range"))
            assertRejected(store, plan(first.copy(ordinal = 2), "wrong-period"))
            assertRejected(store, plan(first.copy(expectedMinor = 101), "wrong-amount"))
            assertRejected(store, plan(first.copy(debtId = null), "wrong-debt"))
            assertTrue(execute(store, plan(first, "first")) is ApplyTransactionExecution.Completed)
            assertRejected(store, plan(first, "duplicate"))
            val once = schedule.copy(id = FinanceCommitmentId("once"), recurrence = FinanceScheduleRecurrence.ONCE, totalCount = 1)
            assertTrue(execute(store, plan(once, "once")) is ApplyTransactionExecution.Completed)
            assertRejected(store, plan(installment(1).copy(schedule = version.copy(id = once.id)), "once-second"))
        }
    }

    @Test fun rejectsNonCanonicalPayloadAndOtherRealityGraph() {
        val file = fixture()
        val wire = FinanceCommitmentCreatePayload.payload(debt)
        assertEquals(debt, FinanceCommitmentCreatePayload.decode(FinanceOperations.DEBT_CREATE, wire))
        val malformed = CanonicalPayload.fromEncoded(wire.formatId, wire.copyBytes() + byteArrayOf(0))
        openActive(file).use { store ->
            assertRejected(store, plan(debt, "malformed", malformed))
            assertRejected(store, plan(debt, "wrong-format", FinanceCommitmentCreatePayload.payload(schedule)))
        }
        RealityStore(root.resolve("other.db").toString(), planningStage8 = true).use { other ->
            assertThrows(IllegalStateException::class.java) { other.finance9CommitmentWriteForDevOnly() }
        }
    }
}
