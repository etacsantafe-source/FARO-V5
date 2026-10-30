package ar.com.faro.v5.owner.finance

import ar.com.faro.v5.kernel.contracts.CanonicalPayload
import ar.com.faro.v5.kernel.contracts.ContractExposure
import ar.com.faro.v5.kernel.contracts.ContractFingerprint
import ar.com.faro.v5.kernel.contracts.ContractVersion
import ar.com.faro.v5.kernel.contracts.DependencyStamp
import ar.com.faro.v5.kernel.contracts.DependencyStampSet
import ar.com.faro.v5.kernel.contracts.DependencyStampValue
import ar.com.faro.v5.kernel.contracts.ExecutionProfile
import ar.com.faro.v5.kernel.contracts.IdempotencyKey
import ar.com.faro.v5.kernel.contracts.OperationBindingExecutionResult
import ar.com.faro.v5.kernel.contracts.OperationContractDescriptor
import ar.com.faro.v5.kernel.contracts.OperationRevalidationFailure
import ar.com.faro.v5.kernel.contracts.OperationRevalidationResult
import ar.com.faro.v5.kernel.contracts.PreparedOperation
import ar.com.faro.v5.kernel.contracts.RealityTransactionScope
import ar.com.faro.v5.kernel.contracts.TransactionId
import ar.com.faro.v5.owner.finance.api.FinanceAccountCreate
import ar.com.faro.v5.owner.finance.api.FinanceAccountCreateBinding
import ar.com.faro.v5.owner.finance.api.FinanceAccountCreatePayload
import ar.com.faro.v5.owner.finance.api.FinanceAccountCreateRevalidation
import ar.com.faro.v5.owner.finance.api.FinanceAccountDisplayName
import ar.com.faro.v5.owner.finance.api.FinanceAccountId
import ar.com.faro.v5.owner.finance.api.FinanceAccountNature
import ar.com.faro.v5.owner.finance.api.FinanceAccountSnapshot
import ar.com.faro.v5.owner.finance.api.FinanceCurrencyCode
import ar.com.faro.v5.owner.finance.api.FinanceEntryRecord
import ar.com.faro.v5.owner.finance.api.FinanceEntrySnapshot
import ar.com.faro.v5.owner.finance.api.FinanceOperations
import ar.com.faro.v5.owner.finance.api.FinanceSnapshot
import ar.com.faro.v5.owner.finance.api.FinanceSnapshotReader
import ar.com.faro.v5.owner.finance.spi.FinancePersistencePort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class FinanceOwnerFoundationTest {
    @Test
    fun accountFieldsNormalizeWithoutAssumingCurrencyScale() {
        assertEquals("Principal", FinanceAccountDisplayName.of("  Principal  ").value)
        assertEquals("ARS", FinanceCurrencyCode.of(" ars ").value)
        assertEquals("account-1", FinanceAccountId.of(" account-1 ").value)

        assertThrows(IllegalArgumentException::class.java) {
            FinanceAccountDisplayName.of(" ")
        }
        assertThrows(IllegalArgumentException::class.java) {
            FinanceCurrencyCode.of("AR")
        }
        assertThrows(IllegalArgumentException::class.java) {
            FinanceCurrencyCode.of("ARS1")
        }
    }

    @Test
    fun snapshotPublishesAggregateAccountsDependencyStamp() {
        val snapshot = FinanceSnapshot(
            accounts = listOf(accountSnapshot()),
            revision = 4,
        )

        assertEquals(
            DependencyStamp(
                authorityBoundary = FinanceSnapshot.FINANCE_AUTHORITY,
                dependencyKey = FinanceSnapshot.FINANCE_ACCOUNTS_KEY,
                observedValue = DependencyStampValue("revision:4"),
            ),
            snapshot.accountsDependencyStamp(),
        )
    }

    @Test
    fun accountCreatePayloadRoundTripsNatureAndOpeningBalance() {
        val create = accountCreate(openingBalanceMinor = 125_000L)
        val payload = FinanceAccountCreatePayload.payload(create)

        assertEquals(FinanceAccountCreatePayload.FORMAT_ID, payload.formatId)
        assertEquals(create, FinanceAccountCreatePayload.decode(payload))
    }

    @Test
    fun accountCreateRejectsNegativeOpeningBalance() {
        assertThrows(IllegalArgumentException::class.java) {
            accountCreate(openingBalanceMinor = -1L)
        }
    }

    @Test
    fun snapshotReaderDelegatesWithoutMutation() {
        val persistence = RecordingPersistence()
        val reader = FinanceSnapshotReader(persistence)

        assertEquals(persistence.snapshot, reader.read())
        assertEquals(1, persistence.reads)
        assertEquals(0, persistence.writes)
    }

    @Test
    fun accountCreateBindingUsesCanonicalMetadataAndSameTransactionScope() {
        val persistence = RecordingPersistence()
        val binding = FinanceAccountCreateBinding(persistence)
        val scope = scope("tx-finance-account")
        val create = accountCreate(openingBalanceMinor = 50_000L)

        val result = binding.execute(prepared(create), scope)

        assertEquals(OperationBindingExecutionResult.Completed, result)
        assertEquals(FinanceOperations.ACCOUNT_CREATE, binding.descriptor.operationId)
        assertEquals(FinanceOperations.EXECUTOR_ID, binding.descriptor.canonicalExecutor)
        assertEquals(ExecutionProfile.REALITY_MUTATION, binding.descriptor.executionProfile)
        assertSame(scope, persistence.lastScope)
        assertEquals(create.accountId, persistence.snapshot.accounts.single().accountId)
        assertEquals(50_000L, persistence.snapshot.accounts.single().openingBalanceMinor)
        assertEquals(1, persistence.writes)
    }

    @Test
    fun malformedPayloadRejectsBeforePersistenceWrite() {
        val persistence = RecordingPersistence()
        val invalid = prepared(accountCreate()).copy(
            payload = CanonicalPayload.fromEncoded(
                FinanceAccountCreatePayload.FORMAT_ID,
                byteArrayOf(0, 0, 0, 4, 1),
            ),
        )

        val result = FinanceAccountCreateBinding(persistence)
            .execute(invalid, scope("tx-finance-account"))

        assertEquals(
            OperationBindingExecutionResult.Rejected(
                "finance_account_payload_invalid"
            ),
            result,
        )
        assertEquals(0, persistence.writes)
    }

    @Test
    fun revalidationRejectsWrongPayloadFormat() {
        val invalid = prepared(accountCreate()).copy(
            payload = CanonicalPayload.fromEncoded(
                "wrong-format",
                byteArrayOf(1),
            ),
        )

        assertEquals(
            OperationRevalidationResult.Rejected(
                OperationRevalidationFailure.PRECONDITION_FAILED,
                "finance_account_payload_invalid",
            ),
            FinanceAccountCreateRevalidation().revalidate(invalid),
        )
    }

    private fun prepared(create: FinanceAccountCreate) = PreparedOperation(
        contract = OperationContractDescriptor(
            operationId = FinanceOperations.ACCOUNT_CREATE,
            capabilityId = FinanceOperations.CAPABILITY_ID,
            canonicalExecutor = FinanceOperations.EXECUTOR_ID,
            executionProfile = ExecutionProfile.REALITY_MUTATION,
            contractVersion = ContractVersion("fixture"),
            contractFingerprint = ContractFingerprint("fixture-finance-account-create"),
            exposure = ContractExposure.BINDABLE,
        ),
        payload = FinanceAccountCreatePayload.payload(create),
        dependencyStamps = DependencyStampSet.of(
            FinanceSnapshot(emptyList(), 0).accountsDependencyStamp()
        ),
        idempotencyKey = IdempotencyKey("finance-account-intent"),
    )

    private fun accountCreate(
        openingBalanceMinor: Long = 0L,
    ) = FinanceAccountCreate(
        accountId = FinanceAccountId.of("account-1"),
        displayName = FinanceAccountDisplayName.of("Principal"),
        currency = FinanceCurrencyCode.of("ARS"),
        nature = FinanceAccountNature.BANK,
        openingBalanceMinor = openingBalanceMinor,
    )

    private fun accountSnapshot() = FinanceAccountSnapshot(
        accountId = FinanceAccountId.of("account-1"),
        displayName = FinanceAccountDisplayName.of("Principal"),
        currency = FinanceCurrencyCode.of("ARS"),
        nature = FinanceAccountNature.BANK,
        openingBalanceMinor = 0L,
    )

    private fun scope(id: String) = object : RealityTransactionScope {
        override val transactionId = TransactionId(id)
    }

    private class RecordingPersistence : FinancePersistencePort {
        var snapshot = FinanceSnapshot(emptyList(), 0)
        var reads = 0
        var writes = 0
        var lastScope: RealityTransactionScope? = null

        override fun read(): FinanceSnapshot {
            reads += 1
            return snapshot
        }

        override fun createAccount(
            create: FinanceAccountCreate,
            transactionScope: RealityTransactionScope,
        ): FinanceAccountSnapshot {
            writes += 1
            lastScope = transactionScope
            val account = FinanceAccountSnapshot(
                accountId = create.accountId,
                displayName = create.displayName,
                currency = create.currency,
                nature = create.nature,
                openingBalanceMinor = create.openingBalanceMinor,
            )
            snapshot = FinanceSnapshot(
                accounts = snapshot.accounts + account,
                revision = snapshot.revision + 1,
                entries = snapshot.entries,
            )
            return account
        }

        override fun recordEntry(
            record: FinanceEntryRecord,
            transactionScope: RealityTransactionScope,
        ): FinanceEntrySnapshot {
            error("recordEntry is not used by account foundation tests")
        }
    }
}
