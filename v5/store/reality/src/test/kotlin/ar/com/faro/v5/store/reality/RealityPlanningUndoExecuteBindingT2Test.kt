package ar.com.faro.v5.store.reality

import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.kernel.apply.*
import ar.com.faro.v5.owner.reminders.api.*
import ar.com.faro.v5.owner.reminders.spi.ReminderOwnerDependency
import ar.com.faro.v5.owner.monthlyplan.api.*
import ar.com.faro.v5.owner.monthlyplan.spi.MonthlyPlanOwnerDependency
import java.nio.file.Path
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.util.concurrent.atomic.AtomicInteger
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Internal REAL SQLite tests, no human Review, Android/Chat or production grant.
 * An origin receipt alone is never sufficient proof for the future host.
 */
class RealityPlanningUndoExecuteBindingT2Test {
    @TempDir lateinit var root: Path

    private val descriptor = OperationContractDescriptor(
        RealityPlanningUndoOperations.EXECUTE,
        RealityPlanningUndoOperations.CAPABILITY_ID,
        RealityPlanningUndoOperations.EXECUTOR_ID,
        ExecutionProfile.REALITY_MUTATION,
        ContractVersion("1"),
        ContractFingerprint("n033c3-undo-binding-fixture"),
        ContractExposure.BINDABLE,
    )
    private val catalog = OperationContractLookupPort { id ->
        descriptor.takeIf { it.operationId == id }
    }
    private val txCount = AtomicInteger(0)
    private val receiptCount = AtomicInteger(0)
    private fun db(file: String = ":memory:") = RealityStore(
        file, planningStage8 = true,
        transactionIdFactory = {
            TransactionId("undo-binding-tx-" + txCount.incrementAndGet())
        },
        receiptIdFactory = {
            ReceiptId("undo-binding-receipt-" + receiptCount.incrementAndGet())
        },
    )
    private fun originalRule() = ReminderRule(
        ReminderRuleId.of("r1"), "Recordar control", "UTC",
        LocalDate.of(2026, 11, 20), LocalTime.of(10, 0),
        ReminderRecurrence.ONCE, true, null, 100L,
    )
    private fun originalItem(paid: Boolean = false) = MonthlyPlanItem(
        MonthlyPlanItemId.of("m1"), "Alquiler", YearMonth.of(2026, 11),
        LocalDate.of(2026, 11, 30),
        if (paid) MonthlyPlanItemStatus.PAID_CONFIRMED else MonthlyPlanItemStatus.PLANNED,
        25000L, "ARS", if (paid) 120L else null, 100L,
    )
    private fun placeholderPlan(label: String) = object : PreparedPlanView {
        override val applyPlan: ApplyPlan get() = error("owner storage test only")
        override val planFingerprint = PlanFingerprint("undo-binding-" + label)
    }
    private fun source(
        db: RealityStore,
        monthly: Boolean = false,
        paid: Boolean = false,
    ): Pair<ExecutionReceipt, CanonicalPayload> {
        val created = placeholderPlan("origin-" + txCount.get())
        val identity = db.applyAttemptIdentities.resolve(created)
        val payload = if (monthly) MonthlyPlanItemAddPayload.payload(originalItem(paid))
            else ReminderCreatePayload.payload(originalRule())
        val receipt = db.run(identity.transactionId) { scope ->
            if (monthly) db.monthlyPlanPersistence.createItem(
                originalItem(paid), 0L, scope,
            ) else db.reminderPersistence.createRule(originalRule(), 0L, scope)
            val committed = db.commit(scope, identity, emptyList())
            AtomicApplyTransactionDecision.Commit(committed)
        }
        return receipt to payload
    }
    private fun operation(
        db: RealityStore,
        old: ExecutionReceipt,
        original: CanonicalPayload,
        monthly: Boolean = false,
        objectId: String = if (monthly) "m1" else "r1",
        cancelAt: Long = 200L,
        desc: OperationContractDescriptor = descriptor,
    ): PreparedOperation {
        val sourceContext = ConversationPlanReviewContext(
            ConversationId("source"), "source-review",
            "source-token", 3L, old.planFingerprint,
        )
        val command = UndoExecuteIntent(
            sourceContext, old, if (monthly) MonthlyPlanOperations.ITEM_ADD else ReminderOperations.CREATE,
            objectId, original, cancelAt,
        )
        val dependency = if (monthly) MonthlyPlanOwnerDependency.stamp(
            db.monthlyPlanCancellation.read(),
        ) else ReminderOwnerDependency.stamp(db.reminderCancellation.read())
        return PreparedOperation(
            desc, UndoExecutePayload.payload(command),
            DependencyStampSet.of(dependency),
            IdempotencyKey("undo-owner-" + objectId),
        )
    }
    private fun revalidator(db: RealityStore) =
        db.planningUndoRevalidatorsForTrustedHost(catalog).single()
    private fun binding(db: RealityStore) =
        db.planningUndoBindingsForTrustedHost(catalog).single()

    @Test fun committedReminderSourceProducesSeparateUndoReceiptAndRetainsRows() {
        val path = root.resolve("undo-reminder.db").toString()
        lateinit var old: ExecutionReceipt
        lateinit var undo: ExecutionReceipt
        db(path).use { store ->
            val (originalReceipt, payload) = source(store)
            old = originalReceipt
            val prepared = operation(store, old, payload)
            assertEquals(OperationRevalidationResult.Valid,
                revalidator(store).revalidate(prepared))
            val newIdentity = store.applyAttemptIdentities.resolve(placeholderPlan("undo-new-r"))
            assertNotEquals(old.transactionId, newIdentity.transactionId)
            store.run(newIdentity.transactionId) { scope ->
                assertEquals(OperationBindingExecutionResult.Completed,
                    binding(store).execute(prepared, scope))
                undo = store.commit(scope, newIdentity, emptyList())
                AtomicApplyTransactionDecision.Commit(Unit)
            }
            assertFalse(store.planningBackup.snapshot().reminders.rules.single().enabled)
            assertEquals(old, store.executionReceiptLookup.find(old.transactionId))
            assertEquals(undo, store.executionReceiptLookup.find(undo.transactionId))
            assertEquals(2L, store.planningBackup.snapshot().reminders.revision)
        }
        RealityStore(path, planningStage8 = true).use { reopened ->
            assertFalse(reopened.planningBackup.snapshot().reminders.rules.single().enabled)
            assertEquals(old, reopened.executionReceiptLookup.find(old.transactionId))
            assertEquals(undo, reopened.executionReceiptLookup.find(undo.transactionId))
            assertEquals("ok", reopened.integrityCheck())
        }
    }

    @Test fun committedMonthlySourceOnlyCancelsPlannedItemNotFinancePayment() {
        db().use { store ->
            val (old, payload) = source(store, monthly = true)
            val prepared = operation(store, old, payload, monthly = true)
            assertEquals(OperationRevalidationResult.Valid,
                revalidator(store).revalidate(prepared))
            store.run(TransactionId("undo-monthly-tx")) { scope ->
                assertEquals(OperationBindingExecutionResult.Completed,
                    binding(store).execute(prepared, scope))
                AtomicApplyTransactionDecision.Commit(Unit)
            }
            val after = store.planningBackup.snapshot()
            assertEquals(MonthlyPlanItemStatus.CANCELLED, after.monthlyPlan.items.single().status)
            assertNull(after.monthlyPlan.items.single().paymentConfirmedAtEpochMillis)
            assertEquals(25000L, after.monthlyPlan.items.single().expectedAmountMinor)
            assertEquals(2L, after.monthlyPlan.revision)
            assertEquals(0L, after.reminders.revision)
        }
    }

    @Test fun falseReceiptAndUncommittedOriginAreDenied() {
        db().use { store ->
            val (old, payload) = source(store)
            val foreign = old.copy(receiptId = ReceiptId("receipt-foreign"))
            val invalid = operation(store, foreign, payload)
            assertTrue(revalidator(store).revalidate(invalid) is OperationRevalidationResult.Rejected)
            store.run(TransactionId("false-origin")) { scope ->
                assertTrue(binding(store).execute(invalid, scope) is OperationBindingExecutionResult.Rejected)
                AtomicApplyTransactionDecision.Rollback(Unit)
            }
            assertTrue(store.planningBackup.snapshot().reminders.rules.single().enabled)
        }
        db().use { store ->
            val identity = store.applyAttemptIdentities.resolve(placeholderPlan("pending"))
            store.run(TransactionId("seed-without-receipt")) { scope ->
                store.reminderPersistence.createRule(originalRule(), 0L, scope)
                AtomicApplyTransactionDecision.Commit(Unit)
            }
            val fakeReceipt = ExecutionReceipt(
                ReceiptId("missing-receipt"), identity.transactionId, identity.planFingerprint,
            )
            val prepared = operation(store, fakeReceipt,
                ReminderCreatePayload.payload(originalRule()))
            assertTrue(revalidator(store).revalidate(prepared) is OperationRevalidationResult.Rejected)
            assertTrue(store.planningBackup.snapshot().reminders.rules.single().enabled)
        }
    }

    @Test fun substitutedTargetPayloadOrOwnerStampAreDenied() {
        db().use { store ->
            val (old, payload) = source(store)
            val wrongTarget = operation(store, old, payload, objectId = "another-id")
            assertTrue(revalidator(store).revalidate(wrongTarget) is OperationRevalidationResult.Rejected)
            val tampered = operation(store, old,
                ReminderCreatePayload.payload(originalRule().copy(title = "alterado")))
            assertTrue(revalidator(store).revalidate(tampered) is OperationRevalidationResult.Rejected)
            val proper = operation(store, old, payload)
            val noStamps = proper.copy(dependencyStamps = DependencyStampSet.empty())
            assertTrue(revalidator(store).revalidate(noStamps) is OperationRevalidationResult.Rejected)
            assertTrue(store.planningBackup.snapshot().reminders.rules.single().enabled)
        }
    }

    @Test fun doubleUndoAndPaidMonthlySourceCannotBeReapplied() {
        db().use { store ->
            val (old, payload) = source(store)
            val prepared = operation(store, old, payload)
            store.run(TransactionId("first-undo")) { scope ->
                assertEquals(OperationBindingExecutionResult.Completed,
                    binding(store).execute(prepared, scope))
                AtomicApplyTransactionDecision.Commit(Unit)
            }
            assertTrue(revalidator(store).revalidate(prepared) is OperationRevalidationResult.Rejected)
            store.run(TransactionId("second-undo")) { scope ->
                assertTrue(binding(store).execute(prepared, scope) is OperationBindingExecutionResult.Rejected)
                AtomicApplyTransactionDecision.Rollback(Unit)
            }
            assertEquals(2L, store.planningBackup.snapshot().reminders.revision)
        }
        db().use { store ->
            val (old, payload) = source(store, monthly = true, paid = true)
            val prepared = operation(store, old, payload, monthly = true)
            assertTrue(revalidator(store).revalidate(prepared) is OperationRevalidationResult.Rejected)
            assertEquals(MonthlyPlanItemStatus.PAID_CONFIRMED,
                store.planningBackup.snapshot().monthlyPlan.items.single().status)
        }
    }

    @Test fun alteredCatalogDescriptorAndUnknownOperationAreDenied() {
        db().use { store ->
            val (old, payload) = source(store)
            val invalid = operation(store, old, payload,
                desc = descriptor.copy(contractFingerprint = ContractFingerprint("changed")))
            assertTrue(revalidator(store).revalidate(invalid) is OperationRevalidationResult.Rejected)
            store.run(TransactionId("wrong-contract")) { scope ->
                assertTrue(binding(store).execute(invalid, scope) is OperationBindingExecutionResult.Rejected)
                AtomicApplyTransactionDecision.Rollback(Unit)
            }
            val wrongOwnerPayload = UndoExecuteIntent(
                ConversationPlanReviewContext(
                    ConversationId("source"), "review", "token", 3L, old.planFingerprint,
                ), old, OperationId("undo.execute"), "r1", payload, 200L,
            )
            val prepared = operation(store, old, payload).copy(
                payload = UndoExecutePayload.payload(wrongOwnerPayload),
            )
            assertTrue(revalidator(store).revalidate(prepared) is OperationRevalidationResult.Rejected)
            assertTrue(store.planningBackup.snapshot().reminders.rules.single().enabled)
        }
    }

    @Test fun schemaSevenAndUnavailableOwnerFactoriesFailClosed() {
        RealityStore(":memory:").use { legacy ->
            assertThrows(IllegalStateException::class.java) {
                legacy.planningUndoBindingsForTrustedHost(catalog)
            }
            assertThrows(IllegalStateException::class.java) {
                legacy.planningUndoRevalidatorsForTrustedHost(catalog)
            }
            assertEquals(7, legacy.schemaVersion())
        }
    }
}
