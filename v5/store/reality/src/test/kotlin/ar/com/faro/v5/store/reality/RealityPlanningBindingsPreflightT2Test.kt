package ar.com.faro.v5.store.reality

import ar.com.faro.v5.kernel.apply.*
import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.owner.reminders.api.*
import ar.com.faro.v5.owner.reminders.spi.ReminderOwnerDependency
import ar.com.faro.v5.owner.monthlyplan.api.*
import ar.com.faro.v5.owner.monthlyplan.spi.MonthlyPlanOwnerDependency
import java.nio.file.Path
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * N033B3: REAL Reality8 owner SPI, catalog equality, exact stamps and preflight.
 * Developer-only integration, NO user-authorized Chat/Review session.
 */
class RealityPlanningBindingsPreflightT2Test {
    @TempDir lateinit var root: Path

    private fun reminder(id: String = "r1", matter: String? = null) = ReminderRule(
        ReminderRuleId.of(id), "Vencimiento", "UTC",
        LocalDate.of(2026, 11, 30), LocalTime.of(9, 0),
        ReminderRecurrence.ONCE, true, matter, 100L,
    )
    private fun monthly(
        id: String = "m1",
        status: MonthlyPlanItemStatus = MonthlyPlanItemStatus.PLANNED,
    ) = MonthlyPlanItem(
        MonthlyPlanItemId.of(id), "Alquiler", YearMonth.of(2026, 11),
        LocalDate.of(2026, 12, 5), status, 120_000L, "ARS",
        if (status == MonthlyPlanItemStatus.PAID_CONFIRMED) 200L else null,
        100L,
    )

    private fun descriptor(id: OperationId): OperationContractDescriptor {
        val reminder = id == ReminderOperations.CREATE
        return OperationContractDescriptor(
            operationId = id,
            capabilityId = if (reminder) ReminderOperations.CAPABILITY_ID
                else MonthlyPlanOperations.CAPABILITY_ID,
            canonicalExecutor = if (reminder) ReminderOperations.EXECUTOR_ID
                else MonthlyPlanOperations.EXECUTOR_ID,
            executionProfile = ExecutionProfile.REALITY_MUTATION,
            contractVersion = ContractVersion("1"),
            contractFingerprint = ContractFingerprint("n033b3-catalog-fixture-" + id.value),
            exposure = ContractExposure.BINDABLE,
        )
    }

    private fun catalog(vararg descriptions: OperationContractDescriptor): OperationContractLookupPort =
        OperationContractLookupPort { id -> descriptions.singleOrNull { it.operationId == id } }

    private fun preparedReminder(
        db: RealityStore,
        rule: ReminderRule = reminder(),
        contract: OperationContractDescriptor = descriptor(ReminderOperations.CREATE),
        deps: DependencyStampSet = DependencyStampSet.of(
            ReminderOwnerDependency.stamp(db.reminderPersistence.read())
        ),
        payload: CanonicalPayload = ReminderCreatePayload.payload(rule),
    ) = PreparedOperation(contract, payload, deps, IdempotencyKey("prepared-reminder-" + rule.id.value))

    private fun preparedMonthly(
        db: RealityStore,
        item: MonthlyPlanItem = monthly(),
        contract: OperationContractDescriptor = descriptor(MonthlyPlanOperations.ITEM_ADD),
        deps: DependencyStampSet = DependencyStampSet.of(
            MonthlyPlanOwnerDependency.stamp(db.monthlyPlanPersistence.read())
        ),
        payload: CanonicalPayload = MonthlyPlanItemAddPayload.payload(item),
    ) = PreparedOperation(contract, payload, deps, IdempotencyKey("prepared-monthly-" + item.id.value))

    private fun plan(vararg ops: PreparedOperation): PreparedPlanView {
        val nodes = ops.mapIndexed { index, op ->
            ApplyPlanNode(
                nodeId = PlanNodeId("n" + index),
                preparedOperation = op,
                dependsOn = emptySet(),
            )
        }
        val apply = ApplyPlan.of(*nodes.toTypedArray())
        return object : PreparedPlanView {
            override val applyPlan: ApplyPlan = apply
            override val planFingerprint = PlanFingerprint("n033b3-plan-" + ops.size)
        }
    }

    private fun gate(db: RealityStore, contracts: OperationContractLookupPort): ApplyPreflightGate =
        ApplyPreflightGate(
            contracts = contracts,
            dependencyStamps = db,
            operationRevalidators = listOf(
                ReminderCreateRevalidation(db.reminderPersistence, contracts),
                MonthlyPlanItemAddRevalidation(db.monthlyPlanPersistence, contracts),
            ),
        )

    @Test fun bothOfficialBindingsPreflightAndShareAtomicReceipt() {
        val file = root.resolve("n033b3-atomic.db").toString()
        val tx = TransactionId("n033b3-both")
        val reminderContract = descriptor(ReminderOperations.CREATE)
        val monthlyContract = descriptor(MonthlyPlanOperations.ITEM_ADD)
        val lookup = catalog(reminderContract, monthlyContract)
        RealityStore(file, planningStage8 = true,
            transactionIdFactory = { tx },
            receiptIdFactory = { ReceiptId("n033b3-receipt") }).use { db ->
            val reminder = preparedReminder(db)
            val monthly = preparedMonthly(db)
            val preparedPlan = plan(reminder, monthly)
            assertEquals(ApplyPreflightResult.Accepted,
                gate(db, lookup).validate(preparedPlan, PlanAuthorizationRef(preparedPlan.planFingerprint)))
            val identity = db.applyAttemptIdentities.resolve(preparedPlan)
            db.run(tx) { scope ->
                assertEquals(OperationBindingExecutionResult.Completed,
                    ReminderCreateBinding(db.reminderPersistence, lookup).execute(reminder, scope))
                assertEquals(OperationBindingExecutionResult.Completed,
                    MonthlyPlanItemAddBinding(db.monthlyPlanPersistence, lookup).execute(monthly, scope))
                db.commit(scope, identity, emptyList())
                AtomicApplyTransactionDecision.Commit(Unit)
            }
            assertEquals(tx, db.executionReceiptLookup.find(tx)?.transactionId)
        }
        RealityStore(file, planningStage8 = true).use { db ->
            assertEquals(1, db.reminderPersistence.read().rules.size)
            assertEquals(1, db.monthlyPlanPersistence.read().items.size)
            assertEquals("revision:1",
                db.current(ReminderOwnerDependency.AUTHORITY, ReminderOwnerDependency.STATE_KEY)?.value)
            assertEquals("revision:1",
                db.current(MonthlyPlanOwnerDependency.AUTHORITY, MonthlyPlanOwnerDependency.STATE_KEY)?.value)
            assertEquals(tx, db.executionReceiptLookup.find(tx)?.transactionId)
        }
    }

    @Test fun staleOwnerStampFailsPreflightAndBindingWithoutSecondWrite() {
        RealityStore(":memory:", planningStage8 = true).use { db ->
            val lookup = catalog(descriptor(ReminderOperations.CREATE), descriptor(MonthlyPlanOperations.ITEM_ADD))
            val old = preparedReminder(db, reminder("stale"))
            val oldMonthly = preparedMonthly(db, monthly("stale"))
            db.run(TransactionId("revision-move")) { scope ->
                db.reminderPersistence.createRule(reminder("fresh"), 0L, scope)
                db.monthlyPlanPersistence.createItem(monthly("fresh"), 0L, scope)
                AtomicApplyTransactionDecision.Commit(Unit)
            }
            val p = plan(old)
            assertEquals(ApplyPreflightRejection.STALE_DEPENDENCY,
                (gate(db, lookup).validate(p, PlanAuthorizationRef(p.planFingerprint))
                    as ApplyPreflightResult.Rejected).reason)
            db.run(TransactionId("reject-stale")) { scope ->
                assertEquals(OperationBindingExecutionResult.Rejected("reminder_create_stale_owner"),
                    ReminderCreateBinding(db.reminderPersistence, lookup).execute(old, scope))
                assertEquals(OperationBindingExecutionResult.Rejected("monthly_add_stale_owner"),
                    MonthlyPlanItemAddBinding(db.monthlyPlanPersistence, lookup).execute(oldMonthly, scope))
                AtomicApplyTransactionDecision.Rollback(Unit)
            }
            assertEquals(1, db.reminderPersistence.read().rules.size)
            assertEquals(1, db.monthlyPlanPersistence.read().items.size)
        }
    }

    @Test fun changedFingerprintVersionCapabilityAndExposureFailClosed() {
        RealityStore(":memory:", planningStage8 = true).use { db ->
            val base = preparedReminder(db)
            val lookup = catalog(base.contract)
            val binding = ReminderCreateBinding(db.reminderPersistence, lookup)
            val revalidate = ReminderCreateRevalidation(db.reminderPersistence, lookup)
            val wrong = listOf(
                base.contract.copy(contractFingerprint = ContractFingerprint("fake-fingerprint")),
                base.contract.copy(contractVersion = ContractVersion("2")),
                base.contract.copy(capabilityId = CapabilityId("N-025")),
                base.contract.copy(exposure = ContractExposure.INTERNAL_ONLY),
                base.contract.copy(canonicalExecutor = ExecutorId("finance")),
            )
            for (contract in wrong) {
                val altered = base.copy(contract = contract)
                assertEquals("reminder_create_contract_mismatch",
                    (revalidate.revalidate(altered) as OperationRevalidationResult.Rejected).code)
                db.run(TransactionId("bad-contract-" + contract.hashCode())) { scope ->
                    assertEquals(OperationBindingExecutionResult.Rejected("reminder_create_contract_mismatch"),
                        binding.execute(altered, scope))
                    AtomicApplyTransactionDecision.Rollback(Unit)
                }
            }
            assertTrue(db.reminderPersistence.read().rules.isEmpty())
        }
    }

    @Test fun unknownAndChangedCatalogFingerprintAreDeniedByGate() {
        RealityStore(":memory:", planningStage8 = true).use { db ->
            val prepared = preparedReminder(db)
            val p = plan(prepared)
            val noCatalog = gate(db, catalog())
            assertEquals(ApplyPreflightRejection.CONTRACT_UNKNOWN,
                (noCatalog.validate(p, PlanAuthorizationRef(p.planFingerprint))
                    as ApplyPreflightResult.Rejected).reason)
            val changed = gate(db, catalog(prepared.contract.copy(
                contractFingerprint=ContractFingerprint("different-current-fingerprint"))))
            assertEquals(ApplyPreflightRejection.CONTRACT_CHANGED,
                (changed.validate(p, PlanAuthorizationRef(p.planFingerprint))
                    as ApplyPreflightResult.Rejected).reason)
            assertNull(catalog().current(ReminderOperations.CREATE))
            assertTrue(db.reminderPersistence.read().rules.isEmpty())
        }
    }

    @Test fun malformedAndForeignPayloadsCannotBeExecuted() {
        RealityStore(":memory:", planningStage8 = true).use { db ->
            val r = preparedReminder(db)
            val m = preparedMonthly(db)
            val lookup = catalog(r.contract, m.contract)
            val malformed = r.copy(payload = CanonicalPayload.fromEncoded(ReminderCreatePayload.FORMAT_ID, byteArrayOf(7)))
            val swapped = m.copy(payload = ReminderCreatePayload.payload(reminder()))
            assertEquals("reminder_create_payload_invalid",
                (ReminderCreateRevalidation(db.reminderPersistence, lookup)
                    .revalidate(malformed) as OperationRevalidationResult.Rejected).code)
            assertEquals("monthly_add_payload_invalid",
                (MonthlyPlanItemAddRevalidation(db.monthlyPlanPersistence, lookup)
                    .revalidate(swapped) as OperationRevalidationResult.Rejected).code)
            db.run(TransactionId("bad-codecs")) { scope ->
                assertEquals(OperationBindingExecutionResult.Rejected("reminder_create_payload_invalid"),
                    ReminderCreateBinding(db.reminderPersistence, lookup).execute(malformed, scope))
                assertEquals(OperationBindingExecutionResult.Rejected("monthly_add_payload_invalid"),
                    MonthlyPlanItemAddBinding(db.monthlyPlanPersistence, lookup).execute(swapped, scope))
                AtomicApplyTransactionDecision.Rollback(Unit)
            }
            assertTrue(db.reminderPersistence.read().rules.isEmpty())
        }
    }

    @Test fun missingExtraAndCrossOwnerDependenciesFailClosed() {
        RealityStore(":memory:", planningStage8 = true).use { db ->
            val r = preparedReminder(db)
            val m = preparedMonthly(db)
            val lookup = catalog(r.contract, m.contract)
            val missing = r.copy(dependencyStamps = DependencyStampSet.empty())
            val cross = m.copy(dependencyStamps = DependencyStampSet.of(
                ReminderOwnerDependency.stamp(db.reminderPersistence.read())))
            val extra = r.copy(dependencyStamps = DependencyStampSet.of(
                ReminderOwnerDependency.stamp(db.reminderPersistence.read()),
                MonthlyPlanOwnerDependency.stamp(db.monthlyPlanPersistence.read())))
            assertEquals("reminder_create_stale_owner",
                (ReminderCreateRevalidation(db.reminderPersistence, lookup)
                    .revalidate(missing) as OperationRevalidationResult.Rejected).code)
            assertEquals("monthly_add_stale_owner",
                (MonthlyPlanItemAddRevalidation(db.monthlyPlanPersistence, lookup)
                    .revalidate(cross) as OperationRevalidationResult.Rejected).code)
            db.run(TransactionId("bad-dependencies")) { scope ->
                val rb = ReminderCreateBinding(db.reminderPersistence, lookup)
                val mb = MonthlyPlanItemAddBinding(db.monthlyPlanPersistence, lookup)
                assertEquals(OperationBindingExecutionResult.Rejected("reminder_create_stale_owner"),
                    rb.execute(extra, scope))
                assertEquals(OperationBindingExecutionResult.Rejected("monthly_add_stale_owner"),
                    mb.execute(cross, scope))
                AtomicApplyTransactionDecision.Rollback(Unit)
            }
            assertTrue(db.reminderPersistence.read().rules.isEmpty())
        }
    }

    @Test fun duplicateIdsRejectBothOwnerOperations() {
        RealityStore(":memory:", planningStage8 = true).use { db ->
            db.run(TransactionId("insert-before-test")) { scope ->
                db.reminderPersistence.createRule(reminder(), 0L, scope)
                db.monthlyPlanPersistence.createItem(monthly(), 0L, scope)
                AtomicApplyTransactionDecision.Commit(Unit)
            }
            val r = preparedReminder(db)
            val m = preparedMonthly(db)
            val lookup = catalog(r.contract, m.contract)
            assertEquals("reminder_create_duplicate_id",
                (ReminderCreateRevalidation(db.reminderPersistence, lookup)
                    .revalidate(r) as OperationRevalidationResult.Rejected).code)
            assertEquals("monthly_add_duplicate_id",
                (MonthlyPlanItemAddRevalidation(db.monthlyPlanPersistence, lookup)
                    .revalidate(m) as OperationRevalidationResult.Rejected).code)
            assertEquals(1L, db.reminderPersistence.read().revision)
            assertEquals(1L, db.monthlyPlanPersistence.read().revision)
        }
    }

    @Test fun matterLinkWithoutAclAndPaidConfirmedStateAreDenied() {
        RealityStore(":memory:", planningStage8 = true).use { db ->
            val r = preparedReminder(db, reminder(matter="some-matter"))
            val m = preparedMonthly(db, monthly(status=MonthlyPlanItemStatus.PAID_CONFIRMED))
            val lookup = catalog(r.contract, m.contract)
            assertEquals("reminder_create_matter_acl_unavailable",
                (ReminderCreateRevalidation(db.reminderPersistence, lookup)
                    .revalidate(r) as OperationRevalidationResult.Rejected).code)
            assertEquals("monthly_add_payment_state_denied",
                (MonthlyPlanItemAddRevalidation(db.monthlyPlanPersistence, lookup)
                    .revalidate(m) as OperationRevalidationResult.Rejected).code)
            db.run(TransactionId("sensitive-policy")) { scope ->
                assertEquals(OperationBindingExecutionResult.Rejected("reminder_create_matter_acl_unavailable"),
                    ReminderCreateBinding(db.reminderPersistence, lookup).execute(r, scope))
                assertEquals(OperationBindingExecutionResult.Rejected("monthly_add_payment_state_denied"),
                    MonthlyPlanItemAddBinding(db.monthlyPlanPersistence, lookup).execute(m, scope))
                AtomicApplyTransactionDecision.Rollback(Unit)
            }
            assertTrue(db.reminderPersistence.read().rules.isEmpty())
            assertTrue(db.monthlyPlanPersistence.read().items.isEmpty())
        }
    }

    @Test fun schemaSevenDoesNotPretendOwnerExists() {
        RealityStore(":memory:").use { db ->
            val r = PreparedOperation(descriptor(ReminderOperations.CREATE),
                ReminderCreatePayload.payload(reminder()), DependencyStampSet.empty(), IdempotencyKey("legacy-r"))
            val m = PreparedOperation(descriptor(MonthlyPlanOperations.ITEM_ADD),
                MonthlyPlanItemAddPayload.payload(monthly()), DependencyStampSet.empty(), IdempotencyKey("legacy-m"))
            val lookup = catalog(r.contract, m.contract)
            assertEquals("reminder_owner_unavailable",
                (ReminderCreateRevalidation(db.reminderPersistence, lookup)
                    .revalidate(r) as OperationRevalidationResult.Rejected).code)
            assertEquals("monthly_owner_unavailable",
                (MonthlyPlanItemAddRevalidation(db.monthlyPlanPersistence, lookup)
                    .revalidate(m) as OperationRevalidationResult.Rejected).code)
            assertNull(db.current(ReminderOwnerDependency.AUTHORITY, ReminderOwnerDependency.STATE_KEY))
            assertNull(db.current(MonthlyPlanOwnerDependency.AUTHORITY, MonthlyPlanOwnerDependency.STATE_KEY))
        }
    }

    @Test fun realLeaseEnforcedAtStoreEvenWithValidLocalPreflight() {
        RealityStore(":memory:", planningStage8 = true).use { db ->
            val r = preparedReminder(db)
            val m = preparedMonthly(db)
            val lookup = catalog(r.contract, m.contract)
            val fake = object : RealityTransactionScope {
                override val transactionId = TransactionId("forged")
            }
            assertTrue(ReminderCreateRevalidation(db.reminderPersistence, lookup)
                .revalidate(r) is OperationRevalidationResult.Valid)
            assertThrows(IllegalStateException::class.java) {
                ReminderCreateBinding(db.reminderPersistence, lookup).execute(r, fake)
            }
            assertThrows(IllegalStateException::class.java) {
                MonthlyPlanItemAddBinding(db.monthlyPlanPersistence, lookup).execute(m, fake)
            }
            assertTrue(db.reminderPersistence.read().rules.isEmpty())
            assertTrue(db.monthlyPlanPersistence.read().items.isEmpty())
        }
    }
}
