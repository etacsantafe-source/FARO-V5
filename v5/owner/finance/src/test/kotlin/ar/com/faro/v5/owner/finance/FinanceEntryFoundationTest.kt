package ar.com.faro.v5.owner.finance

import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.owner.finance.api.*
import ar.com.faro.v5.owner.finance.spi.FinancePersistencePort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class FinanceEntryFoundationTest {
    @Test
    fun entryPayloadRoundTripsFrozenBusinessTimeAndMetadata() {
        val record = record()

        val payload = FinanceEntryRecordPayload.payload(record)

        assertEquals(FinanceEntryRecordPayload.FORMAT_ID, payload.formatId)
        assertEquals(record, FinanceEntryRecordPayload.decode(payload))
    }

    @Test
    fun entryRejectsNonPositiveAmountAndFutureEffectiveTime() {
        assertThrows(IllegalArgumentException::class.java) {
            record(amountMinor = 0L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            record(
                effectiveAtEpochMillis = 2_000L,
                recordedAtEpochMillis = 1_000L,
            )
        }
    }

    @Test
    fun accountDependencyStampPinsOnlyAccountPresenceAndCurrency() {
        val snapshot = FinanceSnapshot(
            accounts = listOf(account()),
            revision = 8,
        )

        assertEquals(
            DependencyStamp(
                authorityBoundary = FinanceSnapshot.FINANCE_AUTHORITY,
                dependencyKey = FinanceSnapshot.accountDependencyKey(account().accountId),
                observedValue = DependencyStampValue("present:currency=ARS"),
            ),
            snapshot.accountDependencyStamp(account().accountId),
        )
    }

    @Test
    fun ledgerUsesOpeningBalanceAndEntryDirectionWithExactArithmetic() {
        val account = account(openingBalanceMinor = 100_000L)
        val snapshot = FinanceSnapshot(
            accounts = listOf(account),
            revision = 3,
            entries = listOf(
                entrySnapshot("income-1", 25_000L, FinanceEntryKind.INCOME),
                entrySnapshot("expense-1", 10_000L, FinanceEntryKind.EXPENSE),
            ),
        )

        assertEquals(115_000L, snapshot.ledgerMinor(account.accountId))
    }

    @Test
    fun entryBindingUsesCanonicalMetadataAndSameTransactionScope() {
        val persistence = RecordingPersistence(listOf(account()))
        val binding = FinanceEntryRecordBinding(persistence)
        val scope = scope("tx-entry")
        val record = record()

        val result = binding.execute(prepared(record), scope)

        assertEquals(OperationBindingExecutionResult.Completed, result)
        assertEquals(FinanceOperations.ENTRY_RECORD, binding.descriptor.operationId)
        assertEquals(FinanceOperations.EXECUTOR_ID, binding.descriptor.canonicalExecutor)
        assertEquals(ExecutionProfile.REALITY_MUTATION, binding.descriptor.executionProfile)
        assertSame(scope, persistence.lastScope)
        assertEquals(record.entryId, persistence.snapshot.entries.single().entryId)
        assertEquals("ARS", persistence.snapshot.entries.single().currency.value)
        assertEquals(1, persistence.writes)
    }

    @Test
    fun entryRevalidationFailsClosedWhenAccountIsMissing() {
        val persistence = RecordingPersistence(emptyList())

        assertEquals(
            OperationRevalidationResult.Rejected(
                OperationRevalidationFailure.PRECONDITION_FAILED,
                "finance_entry_account_missing",
            ),
            FinanceEntryRecordRevalidation(persistence)
                .revalidate(prepared(record())),
        )
        assertEquals(0, persistence.writes)
    }

    @Test
    fun malformedEntryPayloadRejectsBeforePersistenceWrite() {
        val persistence = RecordingPersistence(listOf(account()))
        val invalid = prepared(record()).copy(
            payload = CanonicalPayload.fromEncoded(
                FinanceEntryRecordPayload.FORMAT_ID,
                byteArrayOf(0, 0, 0, 9, 1),
            )
        )

        assertEquals(
            OperationBindingExecutionResult.Rejected(
                "finance_entry_payload_invalid"
            ),
            FinanceEntryRecordBinding(persistence)
                .execute(invalid, scope("tx-entry")),
        )
        assertEquals(0, persistence.writes)
    }

    private fun prepared(record: FinanceEntryRecord) = PreparedOperation(
        contract = OperationContractDescriptor(
            operationId = FinanceOperations.ENTRY_RECORD,
            capabilityId = FinanceOperations.CAPABILITY_ID,
            canonicalExecutor = FinanceOperations.EXECUTOR_ID,
            executionProfile = ExecutionProfile.REALITY_MUTATION,
            contractVersion = ContractVersion("fixture"),
            contractFingerprint =
                ContractFingerprint("fixture-finance-entry-record"),
            exposure = ContractExposure.BINDABLE,
        ),
        payload = FinanceEntryRecordPayload.payload(record),
        dependencyStamps = DependencyStampSet.of(
            FinanceSnapshot(
                accounts = listOf(account()),
                revision = 1,
            ).accountDependencyStamp(account().accountId)
        ),
        idempotencyKey = IdempotencyKey("entry-intent"),
    )

    private fun record(
        amountMinor: Long = 12_500L,
        effectiveAtEpochMillis: Long = 1_000L,
        recordedAtEpochMillis: Long = 2_000L,
    ) = FinanceEntryRecord(
        entryId = FinanceEntryId.of("entry-1"),
        accountId = account().accountId,
        amountMinor = amountMinor,
        kind = FinanceEntryKind.EXPENSE,
        concept = FinanceEntryConcept.of("Supermercado"),
        category = FinanceEntryCategory.of("Alimentos"),
        effectiveAtEpochMillis = effectiveAtEpochMillis,
        recordedAtEpochMillis = recordedAtEpochMillis,
        provenanceLabel = "Declarado por usuario",
    )

    private fun account(
        openingBalanceMinor: Long = 0L,
    ) = FinanceAccountSnapshot(
        accountId = FinanceAccountId.of("account-1"),
        displayName = FinanceAccountDisplayName.of("Principal"),
        currency = FinanceCurrencyCode.of("ARS"),
        nature = FinanceAccountNature.BANK,
        openingBalanceMinor = openingBalanceMinor,
    )

    private fun entrySnapshot(
        id: String,
        amountMinor: Long,
        kind: FinanceEntryKind,
    ) = FinanceEntrySnapshot(
        entryId = FinanceEntryId.of(id),
        accountId = account().accountId,
        amountMinor = amountMinor,
        kind = kind,
        currency = FinanceCurrencyCode.of("ARS"),
        concept = FinanceEntryConcept.of("Concepto"),
        category = FinanceEntryCategory.of("Categoría"),
        effectiveAtEpochMillis = 1_000L,
        recordedAtEpochMillis = 2_000L,
    )

    private fun scope(id: String) = object : RealityTransactionScope {
        override val transactionId = TransactionId(id)
    }

    private class RecordingPersistence(
        accounts: List<FinanceAccountSnapshot>,
    ) : FinancePersistencePort {
        var snapshot = FinanceSnapshot(accounts = accounts, revision = 0)
        var writes = 0
        var lastScope: RealityTransactionScope? = null

        override fun read(): FinanceSnapshot = snapshot

        override fun createAccount(
            create: FinanceAccountCreate,
            transactionScope: RealityTransactionScope,
        ): FinanceAccountSnapshot {
            error("createAccount is not used by entry foundation tests")
        }

        override fun recordEntry(
            record: FinanceEntryRecord,
            transactionScope: RealityTransactionScope,
        ): FinanceEntrySnapshot {
            val account = snapshot.accounts.single { it.accountId == record.accountId }
            val entry = FinanceEntrySnapshot(
                entryId = record.entryId,
                accountId = record.accountId,
                amountMinor = record.amountMinor,
                kind = record.kind,
                currency = account.currency,
                concept = record.concept,
                category = record.category,
                effectiveAtEpochMillis = record.effectiveAtEpochMillis,
                recordedAtEpochMillis = record.recordedAtEpochMillis,
                provenanceLabel = record.provenanceLabel,
            )
            writes += 1
            lastScope = transactionScope
            snapshot = FinanceSnapshot(
                accounts = snapshot.accounts,
                revision = snapshot.revision + 1,
                entries = snapshot.entries + entry,
            )
            return entry
        }
    }
}
