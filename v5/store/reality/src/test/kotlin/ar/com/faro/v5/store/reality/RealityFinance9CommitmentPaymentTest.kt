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

class RealityFinance9CommitmentPaymentTest {
    @TempDir lateinit var root: Path

    private val catalog = BuiltInContractCatalog.create()
    private val ars = FinanceCurrencyCode.of("ARS")
    private val usd = FinanceCurrencyCode.of("USD")
    private val debtId = FinanceCommitmentId("debt")
    private val item = FinanceCommitmentId("item")
    private val manual = FinanceCommitmentId("manual")
    private val arsAccount = FinanceAccountId.of("cash")
    private val usdAccount = FinanceAccountId.of("dollars")

    private fun open(file: Path) = RealityStore(
        file.toString(), materialWriteGate = SingleProcessMaterialWriteGate(),
        planningStage8 = true, financeActive9 = true,
    )

    private fun fixture(): Path {
        val file = root.resolve("payments.db")
        RealityStore(file.toString(), planningStage8 = true).close()
        BundledSQLiteDriver().open(file.toString()).use { db ->
            db.execSQL("PRAGMA foreign_keys=ON")
            RealityFinance9Schema.installOnStagedEightConnection(db, "payments-stage", "a".repeat(64), 1000)
        }
        open(file).use { store ->
            val declarations = store.finance9CommitmentWriteForDevOnly()
            val period = YearMonth.of(2026, 11)
            store.run(TransactionId("seed")) { scope ->
                declarations.create(FinanceCommitmentCreate.Debt(debtId, "Deuda", "Acreedor", ars, 100, 1000), scope)
                declarations.create(FinanceCommitmentCreate.PlanItem(
                    item, null, debtId, period, 1, period.atDay(10), ars, 100, 1000,
                ), scope)
                declarations.create(FinanceCommitmentCreate.PlanItem(
                    manual, null, null, period, 2, period.atDay(12), ars, 70, 1000,
                ), scope)
                for ((id, currency) in listOf(arsAccount to ars, usdAccount to usd)) {
                    store.financePersistence.createAccount(FinanceAccountCreate(
                        id, FinanceAccountDisplayName.of(id.value), currency, FinanceAccountNature.CASH, 0,
                    ), scope)
                }
                AtomicApplyTransactionDecision.Commit(Unit)
            }
        }
        return file
    }

    private fun payment(id: String, amount: Long = 35, reviewedPaid: Long = 0) = FinanceCommitmentPayment(
        item, debtId, ars, reviewedPaid, FinanceEntryRecord(
            FinanceEntryId.of(id), arsAccount, amount, FinanceEntryKind.EXPENSE,
            FinanceEntryConcept.of("Pago registrado"), FinanceEntryCategory.of("Cuotas"), 900, 1000,
        ),
    )

    private fun plan(
        payment: FinanceCommitmentPayment,
        operation: OperationId = FinanceOperations.PLAN_ITEM_EXECUTE,
        key: String = payment.expense.entryId.value,
        payload: CanonicalPayload = FinanceCommitmentPaymentPayload.payload(operation, payment),
    ) = PreparedPlan.of(ApplyPlanNode(
        PlanNodeId("payment"),
        PreparedOperation(requireNotNull(catalog.current(operation)), payload, DependencyStampSet.empty(), IdempotencyKey(key)),
        emptySet(),
    ))

    private fun execute(store: RealityStore, plan: PreparedPlan): ApplyTransactionExecution {
        val id = store.applyAttemptIdentities.resolve(plan)
        val bindings = ApplyBindingResolutionGate(FinanceCommitmentPaymentBinding.all(store.finance9CommitmentPaymentForDevOnly()))
            .resolve(plan) as ApplyBindingResolution.Resolved
        return ApplyTransactionExecutor(store, store).execute(plan, id, bindings)
    }

    private fun assertRejected(store: RealityStore, plan: PreparedPlan) {
        val before = store.financePersistence.read()
        val id = store.applyAttemptIdentities.resolve(plan)
        assertFalse(execute(store, plan) is ApplyTransactionExecution.Completed)
        assertNull(store.executionReceiptLookup.find(id.transactionId))
        assertEquals(before, store.financePersistence.read())
    }

    private fun expectedAndPaid(file: Path): Pair<Long, Long> =
        BundledSQLiteDriver().open(file.toString()).use { db ->
            db.prepare(
                """
                SELECT i.expected_minor, COALESCE(SUM(p.amount_minor), 0)
                FROM finance_plan_item i LEFT JOIN finance_plan_item_payment p USING (plan_item_id)
                WHERE i.plan_item_id = 'item' GROUP BY i.plan_item_id
                """.trimIndent(),
            ).use { st ->
                check(st.step())
                st.getLong(0) to st.getLong(1)
            }
        }

    @Test fun allocatesPartialAndFullPaymentsWithOriginalReceipts() {
        val file = fixture()
        assertEquals(100L to 0L, expectedAndPaid(file))
        val first = plan(payment("partial"))
        val complete = plan(payment("complete", 65, 35), FinanceOperations.DEBT_PAYMENT_RECORD)
        val receipt1 = open(file).use { store ->
            assertTrue(store.financePersistence.read().entries.isEmpty())
            (execute(store, first) as ApplyTransactionExecution.Completed).receipt
        }
        assertEquals(100L to 35L, expectedAndPaid(file))
        val receipt2 = open(file).use { store ->
            assertRejected(store, plan(payment("stale", 1)))
            (execute(store, complete) as ApplyTransactionExecution.Completed).receipt
        }
        assertEquals(100L to 100L, expectedAndPaid(file))
        open(file).use { store ->
            val gate = ApplyRetryReceiptGate(store.applyAttemptIdentities, store.executionReceiptLookup)
            assertEquals(receipt1, (gate.resolve(first) as ApplyRetryResolution.AlreadyCommitted).receipt)
            assertEquals(receipt2, (gate.resolve(complete) as ApplyRetryResolution.AlreadyCommitted).receipt)
            assertRejected(store, plan(payment("excess", 1, 100)))
            assertEquals(listOf(35L, 65L), store.financePersistence.read().entries.map { it.amountMinor }.sorted())
        }
    }

    @Test fun rejectsDebtMismatchCurrencyCancelledAndExcessBeforePosting() {
        val file = fixture()
        BundledSQLiteDriver().open(file.toString()).use { db ->
            db.execSQL("UPDATE finance_plan_item SET state = 'CANCELLED' WHERE plan_item_id = 'manual'")
        }
        open(file).use { store ->
            assertRejected(store, plan(payment("cancelled").copy(planItemId = manual, debtId = null)))
            assertRejected(store, plan(payment("wrong-debt").copy(debtId = FinanceCommitmentId("missing"))))
            assertRejected(store, plan(payment("wrong-currency").copy(currency = usd)))
            assertRejected(store, plan(payment("over-cap", 101)))
            val wrongAccount = payment("wrong-account")
            assertRejected(store, plan(wrongAccount.copy(expense = wrongAccount.expense.copy(accountId = usdAccount))))
        }
        assertEquals(100L to 0L, expectedAndPaid(file))
    }

    @Test fun rollsBackExpenseWhenPaymentLinkFails() {
        val file = fixture()
        BundledSQLiteDriver().open(file.toString()).use { db ->
            db.execSQL(
                """
                CREATE TRIGGER test_reject_payment BEFORE INSERT ON finance_plan_item_payment
                BEGIN SELECT RAISE(ABORT, 'payment link rejected'); END
                """.trimIndent(),
            )
        }
        open(file).use { store ->
            assertRejected(store, plan(payment("rollback")))
            assertTrue(store.financePersistence.read().entries.isEmpty())
        }
        assertEquals(100L to 0L, expectedAndPaid(file))
    }

    @Test fun rejectsDuplicateEntryAndMalformedPayload() {
        val file = fixture()
        open(file).use { store ->
            val payment = payment("shared")
            assertTrue(execute(store, plan(payment)) is ApplyTransactionExecution.Completed)
            assertRejected(store, plan(payment.copy(
                planItemId = manual, debtId = null,
            ), key = "double-allocation"))
            val wire = FinanceCommitmentPaymentPayload.payload(FinanceOperations.PLAN_ITEM_EXECUTE, payment)
            assertEquals(payment, FinanceCommitmentPaymentPayload.decode(FinanceOperations.PLAN_ITEM_EXECUTE, wire))
            assertRejected(store, plan(payment, key = "trailing", payload = CanonicalPayload.fromEncoded(
                wire.formatId, wire.copyBytes() + byteArrayOf(0),
            )))
            assertThrows(IllegalArgumentException::class.java) {
                FinanceCommitmentPaymentPayload.payload(FinanceOperations.DEBT_PAYMENT_RECORD, payment.copy(debtId = null))
            }
        }
        assertEquals(100L to 35L, expectedAndPaid(file))
    }
}
