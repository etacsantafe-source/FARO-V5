package ar.com.faro.v5.kernel.runtime

import ar.com.faro.v5.kernel.contracts.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

class CandidatePreparationTest {
    private data class Proposal(val account: String?, val amountText: String?)
    private data class Input(val account: String, val amountMinor: Long)

    @Test
    fun candidateIsNormalizedToTypedInputBeforeDirectPreparation() {
        val keys = CountingKeySource()
        val stamps = CountingStampSource()
        val runtime = PreparationRuntime(keys)

        val result = runtime.prepareCandidate(
            session = availableSession(),
            candidate = Candidate(
                Proposal(account = "checking", amountText = "5000")
            ),
            spec = candidateSpec(stamps),
        )

        val prepared = assertInstanceOf(
            PreparationResult.Prepared::class.java,
            result,
        ).operation

        assertEquals(
            "checking|5000",
            String(prepared.payload.copyBytes(), Charsets.UTF_8),
        )
        assertEquals(IdempotencyKey("intent-1"), prepared.idempotencyKey)
        assertEquals(1, stamps.calls)
        assertEquals(1, keys.calls)
    }

    @Test
    fun incompleteCandidateRejectsBeforeTypedValidationStampsAndKeyAllocation() {
        val keys = CountingKeySource()
        val stamps = CountingStampSource()
        val validator = CountingValidator()
        val runtime = PreparationRuntime(keys)

        val spec = CandidatePreparationSpec(
            normalizer = normalizer(),
            preparationSpec = preparationSpec(
                stamps = stamps,
                validator = validator,
            ),
        )

        val result = runtime.prepareCandidate(
            session = availableSession(),
            candidate = Candidate(
                Proposal(account = null, amountText = "5000")
            ),
            spec = spec,
        )

        assertEquals(
            PreparationRejection.InvalidCandidate("missing_account"),
            (result as PreparationResult.Rejected<*>).reason,
        )
        assertEquals(0, validator.calls)
        assertEquals(0, stamps.calls)
        assertEquals(0, keys.calls)
    }

    @Test
    fun candidateNormalizationFailureIsDeterministicAndProviderIndependent() {
        val normalizer = normalizer()

        assertEquals(
            CandidateNormalization.Rejected("invalid_amount"),
            normalizer.normalize(
                Candidate(Proposal(account = "checking", amountText = "oops"))
            ),
        )
        assertEquals(
            CandidateNormalization.Rejected("invalid_amount"),
            normalizer.normalize(
                Candidate(Proposal(account = "checking", amountText = "oops"))
            ),
        )
    }

    private fun candidateSpec(
        stamps: CountingStampSource = CountingStampSource(),
    ) = CandidatePreparationSpec(
        normalizer = normalizer(),
        preparationSpec = preparationSpec(stamps = stamps),
    )

    private fun normalizer() =
        CandidateNormalizer<Proposal, Input> { candidate ->
            val account = candidate.proposal.account
                ?: return@CandidateNormalizer CandidateNormalization.Rejected(
                    "missing_account"
                )
            val amount = candidate.proposal.amountText?.toLongOrNull()
                ?: return@CandidateNormalizer CandidateNormalization.Rejected(
                    "invalid_amount"
                )

            CandidateNormalization.Normalized(
                Input(account = account, amountMinor = amount)
            )
        }

    private fun preparationSpec(
        stamps: DependencyStampSource<Input>,
        validator: PreparationValidator<Input> = CountingValidator(),
    ) = PreparationSpec(
        operationId = OperationId(OP),
        inputEncoder = object : CanonicalInputEncoder<Input> {
            override val formatId = "finance-transfer-v1"

            override fun encode(input: Input): ByteArray =
                (input.account + "|" + input.amountMinor).toByteArray()
        },
        dependencyStampSource = stamps,
        validator = validator,
    )

    private fun availableSession(): CapabilitySession {
        val catalog = ContractCatalog.of(
            capabilities = listOf(
                CapabilityDescriptor(CapabilityId("N-025"))
            ),
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
                    dependencyKey = DependencyKey("account:" + input.account),
                    observedValue = DependencyStampValue("revision:42"),
                )
            )
        }
    }

    private class CountingValidator : PreparationValidator<Input> {
        var calls = 0
            private set

        override fun validate(input: Input): PreparationValidation {
            calls += 1
            return if (input.amountMinor > 0) {
                PreparationValidation.Valid
            } else {
                PreparationValidation.Invalid("amount_non_positive")
            }
        }
    }

    private companion object {
        const val OP = "finance.transfer.prepare"
    }
}
