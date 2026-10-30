package ar.com.faro.v5.kernel.runtime

import ar.com.faro.v5.kernel.contracts.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PreparationRuntimeTest {
    private data class Input(
        val accountId: String,
        var amountMinor: Long,
    )

    @Test
    fun validTypedInputBecomesImmutablePreparedOperationPinnedToExactContract() {
        val keySource = CountingKeySource()
        val runtime = PreparationRuntime(keySource)
        val session = availableSession()

        val result = runtime.prepare(
            session = session,
            input = Input("checking", 5000),
            spec = spec(),
        )

        val prepared = assertInstanceOf(
            PreparationResult.Prepared::class.java,
            result,
        ).operation

        assertEquals(OP, prepared.contract.operationId.value)
        assertEquals("contract-v1", prepared.contract.contractFingerprint.value)
        assertEquals("finance-transfer-v1", prepared.payload.formatId)
        assertEquals(
            "checking|5000",
            String(prepared.payload.copyBytes(), Charsets.UTF_8),
        )
        assertEquals(1, prepared.dependencyStamps.asCanonicalList().size)
        assertEquals(IdempotencyKey("intent-1"), prepared.idempotencyKey)
        assertEquals(64, prepared.payloadFingerprint.value.length)
        assertEquals(1, keySource.calls)
    }

    @Test
    fun mutatingOriginalTypedInputAfterPrepareCannotMutateCanonicalPayload() {
        val runtime = PreparationRuntime(CountingKeySource())
        val input = Input("checking", 5000)

        val prepared = (
            runtime.prepare(availableSession(), input, spec())
                as PreparationResult.Prepared<*>
            ).operation
        val fingerprint = prepared.payloadFingerprint

        input.amountMinor = 9999

        assertEquals(
            "checking|5000",
            String(prepared.payload.copyBytes(), Charsets.UTF_8),
        )
        assertEquals(fingerprint, prepared.payloadFingerprint)
    }

    @Test
    fun unknownOperationFailsClosedBeforeValidationStampsAndKeyAllocation() {
        val keySource = CountingKeySource()
        val stamps = CountingStampSource()
        val validator = CountingValidator(PreparationValidation.Valid)
        val runtime = PreparationRuntime(keySource)

        val result = runtime.prepare(
            session = availableSession(),
            input = Input("checking", 5000),
            spec = spec(
                operationId = OperationId("missing.operation"),
                stampSource = stamps,
                validator = validator,
            ),
        )

        assertEquals(
            PreparationRejection.UnknownOperation,
            (result as PreparationResult.Rejected<*>).reason,
        )
        assertEquals(0, validator.calls)
        assertEquals(0, stamps.calls)
        assertEquals(0, keySource.calls)
    }

    @Test
    fun knownButUnavailableOperationFailsClosedBeforeValidationStampsAndKeyAllocation() {
        val keySource = CountingKeySource()
        val stamps = CountingStampSource()
        val validator = CountingValidator(PreparationValidation.Valid)
        val runtime = PreparationRuntime(keySource)

        val result = runtime.prepare(
            session = unavailableSession(),
            input = Input("checking", 5000),
            spec = spec(stampSource = stamps, validator = validator),
        )

        assertEquals(
            PreparationRejection.UnavailableOperation,
            (result as PreparationResult.Rejected<*>).reason,
        )
        assertEquals(0, validator.calls)
        assertEquals(0, stamps.calls)
        assertEquals(0, keySource.calls)
    }

    @Test
    fun invalidInputRejectsBeforeDependencyCaptureAndKeyAllocation() {
        val keySource = CountingKeySource()
        val stamps = CountingStampSource()
        val validator = CountingValidator(
            PreparationValidation.Invalid("amount_non_positive")
        )
        val runtime = PreparationRuntime(keySource)

        val result = runtime.prepare(
            session = availableSession(),
            input = Input("checking", 0),
            spec = spec(stampSource = stamps, validator = validator),
        )

        assertEquals(
            PreparationRejection.InvalidInput("amount_non_positive"),
            (result as PreparationResult.Rejected<*>).reason,
        )
        assertEquals(1, validator.calls)
        assertEquals(0, stamps.calls)
        assertEquals(0, keySource.calls)
    }

    @Test
    fun materialInputChangeChangesPayloadFingerprintWhileNewIntentionsGetNewKeys() {
        val keySource = CountingKeySource()
        val runtime = PreparationRuntime(keySource)
        val session = availableSession()

        val first = (
            runtime.prepare(session, Input("checking", 5000), spec())
                as PreparationResult.Prepared<*>
            ).operation
        val second = (
            runtime.prepare(session, Input("checking", 5001), spec())
                as PreparationResult.Prepared<*>
            ).operation

        assertNotEquals(first.payloadFingerprint, second.payloadFingerprint)
        assertNotEquals(first.idempotencyKey, second.idempotencyKey)
        assertEquals(2, keySource.calls)
    }

    private fun spec(
        operationId: OperationId = OperationId(OP),
        stampSource: DependencyStampSource<Input> = CountingStampSource(),
        validator: PreparationValidator<Input> =
            CountingValidator(PreparationValidation.Valid),
    ) = PreparationSpec(
        operationId = operationId,
        inputEncoder = object : CanonicalInputEncoder<Input> {
            override val formatId = "finance-transfer-v1"
            override fun encode(input: Input): ByteArray =
                (input.accountId + "|" + input.amountMinor).toByteArray()
        },
        dependencyStampSource = stampSource,
        validator = validator,
    )

    private fun catalog() = ContractCatalog.of(
        capabilities = listOf(CapabilityDescriptor(CapabilityId("N-025"))),
        operations = listOf(
            OperationContractDescriptor(
                operationId = OperationId(OP),
                capabilityId = CapabilityId("N-025"),
                canonicalExecutor = ExecutorId("finance"),
                executionProfile = ExecutionProfile.REALITY_MUTATION,
                contractVersion = ContractVersion("1"),
                contractFingerprint = ContractFingerprint("contract-v1"),
                exposure = ContractExposure.BINDABLE,
            )
        ),
    )

    private fun availableSession(): CapabilitySession {
        val catalog = catalog()
        val binding = object : OperationBindingPort {
            override val descriptor = OperationBindingDescriptor(
                operationId = OperationId(OP),
                canonicalExecutor = ExecutorId("finance"),
                executionProfile = ExecutionProfile.REALITY_MUTATION,
            )
        override fun execute(preparedOperation: PreparedOperation, transactionScope: RealityTransactionScope): OperationBindingExecutionResult = OperationBindingExecutionResult.Completed
        }
        return CapabilitySession.open(
            BindingRegistry.of(catalog, operationBindings = listOf(binding))
        )
    }

    private fun unavailableSession(): CapabilitySession =
        CapabilitySession.open(BindingRegistry.of(catalog()))

    private class CountingKeySource : IdempotencyKeySource {
        var calls = 0
            private set

        override fun newKey(): IdempotencyKey {
            calls += 1
            return IdempotencyKey("intent-" + calls)
        }
    }

    private class CountingStampSource : DependencyStampSource<Input> {
        var calls = 0
            private set

        override fun capture(input: Input): DependencyStampSet {
            calls += 1
            return DependencyStampSet.of(
                DependencyStamp(
                    authorityBoundary = AuthorityBoundaryId("reality.finance"),
                    dependencyKey = DependencyKey("account:" + input.accountId),
                    observedValue = DependencyStampValue("revision:42"),
                )
            )
        }
    }

    private class CountingValidator(
        private val result: PreparationValidation,
    ) : PreparationValidator<Input> {
        var calls = 0
            private set

        override fun validate(input: Input): PreparationValidation {
            calls += 1
            return result
        }
    }

    private companion object {
        const val OP = "finance.transfer.prepare"
    }
}
