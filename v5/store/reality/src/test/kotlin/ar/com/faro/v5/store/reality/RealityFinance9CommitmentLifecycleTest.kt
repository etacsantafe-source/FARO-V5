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

class RealityFinance9CommitmentLifecycleTest {
    @TempDir lateinit var root: Path

    private val ars = FinanceCurrencyCode.of("ARS")
    private val usd = FinanceCurrencyCode.of("USD")
    private val catalog = BuiltInContractCatalog.create()
    private val month = YearMonth.of(2026, 11)
    private val account = FinanceAccountId.of("cash")
    private val template = FinanceCommitmentCreate.Schedule(
        FinanceCommitmentId("monthly"), null, ars, 100, month, FinanceScheduleRecurrence.MONTHLY, 37, 1000,
    )

    private fun fixture(): RealityStore {
        val file = root.resolve("calendar.db")
        RealityStore(file.toString(), planningStage8 = true).close()
        BundledSQLiteDriver().open(file.toString()).use { db ->
            db.execSQL("PRAGMA foreign_keys=ON")
            RealityFinance9Schema.installOnStagedEightConnection(db, "calendar-stage", "a".repeat(64), 1000)
        }
        return RealityStore(file.toString(), materialWriteGate = SingleProcessMaterialWriteGate(),
            planningStage8 = true, financeActive9 = true).apply {
            run(TransactionId("seed-accounts")) { scope ->
                financePersistence.createAccount(FinanceAccountCreate(
                    account, FinanceAccountDisplayName.of("Efectivo"), ars, FinanceAccountNature.CASH, 0,
                ), scope)
                AtomicApplyTransactionDecision.Commit(Unit)
            }
        }
    }

    private fun create(store: RealityStore, command: FinanceCommitmentCreate) {
        val port = store.finance9CommitmentWriteForDevOnly()
        store.run(TransactionId("seed-declaration")) { scope ->
            port.create(command, scope)
            AtomicApplyTransactionDecision.Commit(Unit)
        }
    }

    private fun pay(store: RealityStore, item: FinanceCommitmentCreate.PlanItem, previouslyPaid: Long, amount: Long) {
        val port = store.finance9CommitmentPaymentForDevOnly()
        store.run(TransactionId("seed-payment")) { scope ->
            port.record(FinanceCommitmentPayment(item.id, item.debtId, ars, previouslyPaid, FinanceEntryRecord(
                FinanceEntryId.of("expense-$previouslyPaid"), account, amount, FinanceEntryKind.EXPENSE,
                FinanceEntryConcept.of("Pago registrado"), FinanceEntryCategory.of("Cuotas"), 1000, 1000,
            )), scope)
            AtomicApplyTransactionDecision.Commit(Unit)
        }
    }

    private fun item(offset: Long, version: Long = 1, amount: Long = 100): FinanceCommitmentCreate.PlanItem {
        val period = month.plusMonths(offset)
        return FinanceCommitmentCreate.PlanItem(
            null, FinanceCommitmentCreate.ScheduleReference(template.id, version), null,
            period, (offset + 1).toInt(), period.atDay(12), ars, amount, 1000,
        )
    }

    private fun calendar(store: RealityStore, currency: FinanceCurrencyCode = ars): FinanceCommitmentCalendar =
        requireNotNull(store.finance9ActiveDevCommitmentCalendar { true }.read(
            FinanceCommitmentCalendarQuery(month, month.plusMonths(36), month.atDay(13), currency),
        ))

    private fun plan(
        command: FinanceCommitmentLifecycle,
        key: String,
        payload: CanonicalPayload = FinanceCommitmentLifecyclePayload.payload(command),
    ) = PreparedPlan.of(ApplyPlanNode(
        PlanNodeId("calendar"),
        PreparedOperation(requireNotNull(catalog.current(FinanceCommitmentLifecyclePayload.operation(command))),
            payload, DependencyStampSet.empty(), IdempotencyKey(key)),
        emptySet(),
    ))

    private fun execute(store: RealityStore, plan: PreparedPlan): ApplyTransactionExecution {
        val id = store.applyAttemptIdentities.resolve(plan)
        val bindings = ApplyBindingResolutionGate(
            FinanceCommitmentLifecycleBinding.all(store.finance9CommitmentLifecycleForDevOnly()),
        ).resolve(plan) as ApplyBindingResolution.Resolved
        return ApplyTransactionExecutor(store, store).execute(plan, id, bindings)
    }

    @Test fun editsAndPausesTemplatesWithoutDisturbingPaidOccurrences() {
        fixture().use { store ->
            create(store, template)
            val original = item(0)
            create(store, original)
            pay(store, original, 0, 40)
            val edit = FinanceCommitmentLifecycle.Edit(template.copy(installmentMinor = 200, recordedAtEpochMillis = 1001), 1)
            assertTrue(execute(store, plan(edit, "edit")) is ApplyTransactionExecution.Completed)
            assertFalse(execute(store, plan(edit, "stale")) is ApplyTransactionExecution.Completed)
            assertThrows(IllegalStateException::class.java) { create(store, item(0, 2, 200)) }
            val future = item(36, 2, 200)
            create(store, future)
            val pause = FinanceCommitmentLifecycle.SetActive(template.id, 2, false, 1002)
            assertTrue(execute(store, plan(pause, "pause")) is ApplyTransactionExecution.Completed)
            assertThrows(IllegalStateException::class.java) { create(store, item(7, 3, 200)) }
            val calendar = calendar(store)
            assertFalse(calendar.schedules.single().active)
            assertEquals(3L, calendar.schedules.single().version)
            assertEquals(listOf(original.id, future.id), calendar.items.map { it.id })
            assertEquals(40L, calendar.items.first().paidMinor)
            assertEquals(60L, calendar.items.first().outstandingMinor)
            assertEquals(FinanceCommitmentState.PARTIAL, calendar.items.first().state)
            assertEquals(1, store.financePersistence.read().entries.size)
        }
    }

    @Test fun derivesDueAndPaidStatesFromCivilDateAndRealLinks() {
        fixture().use { store ->
            create(store, template)
            val due = item(0)
            create(store, due)
            create(store, item(7))
            create(store, item(12))
            create(store, item(36))
            val first = calendar(store)
            assertEquals(FinanceCommitmentState.DUE, first.items.first().state)
            assertTrue(first.items.drop(1).all { it.state == FinanceCommitmentState.PLANNED })
            assertTrue(store.financePersistence.read().entries.isEmpty())
            assertTrue(calendar(store, usd).items.isEmpty())
            pay(store, due, 0, 30)
            assertEquals(FinanceCommitmentState.PARTIAL, calendar(store).items.first().state)
            assertTrue(calendar(store).items.first().due)
            pay(store, due, 30, 70)
            assertEquals(FinanceCommitmentState.PAID_CONFIRMED, calendar(store).items.first().state)
            assertFalse(calendar(store).items.first().due)
        }
    }

    @Test fun cancelsUnpaidAndRetiresOnlyUnusedScheduleWithOriginalReceipt() {
        fixture().use { store ->
            create(store, template)
            val unused = template.copy(id = FinanceCommitmentId("unused"))
            create(store, unused)
            val due = item(0)
            val future = item(7)
            create(store, due)
            create(store, future)
            pay(store, due, 0, 35)
            val paid = FinanceCommitmentLifecycle.Cancel(due.id, 1001)
            assertFalse(execute(store, plan(paid, "deny-paid")) is ApplyTransactionExecution.Completed)
            val cancel = plan(FinanceCommitmentLifecycle.Cancel(future.id, 1001), "cancel")
            val receipt = (execute(store, cancel) as ApplyTransactionExecution.Completed).receipt
            val retry = ApplyRetryReceiptGate(store.applyAttemptIdentities, store.executionReceiptLookup).resolve(cancel)
            assertEquals(receipt, (retry as ApplyRetryResolution.AlreadyCommitted).receipt)
            assertEquals(FinanceCommitmentState.CANCELLED, calendar(store).items.last().state)
            assertFalse(execute(store, plan(FinanceCommitmentLifecycle.Retire(template.id, 1, 1002), "deny-used"))
                is ApplyTransactionExecution.Completed)
            val retire = FinanceCommitmentLifecycle.Retire(unused.id, 1, 1002)
            assertTrue(execute(store, plan(retire, "retire")) is ApplyTransactionExecution.Completed)
            assertEquals(listOf(template.id), calendar(store).schedules.map { it.definition.id })
            assertThrows(Exception::class.java) { create(store, unused) }
            assertEquals(1, store.financePersistence.read().entries.size)
        }
    }

    @Test fun rejectsMalformedPayloadAndRevokedCalendarRead() {
        fixture().use { store ->
            create(store, template)
            val query = FinanceCommitmentCalendarQuery(month, month.plusMonths(36), month.atDay(13), ars)
            var checks = 0
            assertNull(store.finance9ActiveDevCommitmentCalendar { ++checks == 1 }.read(query))
            val pause = FinanceCommitmentLifecycle.SetActive(template.id, 1, false, 1001)
            val valid = FinanceCommitmentLifecyclePayload.payload(pause)
            assertEquals(pause, FinanceCommitmentLifecyclePayload.decode(FinanceOperations.SCHEDULE_SET_ACTIVE, valid))
            val trailing = CanonicalPayload.fromEncoded(valid.formatId, valid.copyBytes() + byteArrayOf(0))
            assertFalse(execute(store, plan(pause, "invalid", trailing)) is ApplyTransactionExecution.Completed)
            assertTrue(calendar(store).schedules.single().active)
        }
    }
}
