package ar.com.faro.v5.kernel.runtime

import ar.com.faro.v5.kernel.contracts.Candidate
import ar.com.faro.v5.kernel.contracts.IdempotencyKey
import ar.com.faro.v5.kernel.contracts.OperationId
import ar.com.faro.v5.kernel.contracts.PreparedOperation

sealed interface PreparationValidation {
    data object Valid : PreparationValidation

    data class Invalid(val code: String) : PreparationValidation {
        init {
            require(code.isNotBlank()) { "preparation validation code must not be blank" }
        }
    }
}

fun interface PreparationValidator<I : Any> {
    fun validate(input: I): PreparationValidation
}

data class PreparationSpec<I : Any>(
    val operationId: OperationId,
    val inputEncoder: CanonicalInputEncoder<I>,
    val dependencyStampSource: DependencyStampSource<I>,
    val validator: PreparationValidator<I>,
)

sealed interface CandidateNormalization<out I : Any> {
    data class Normalized<I : Any>(
        val input: I,
    ) : CandidateNormalization<I>

    data class Rejected(
        val code: String,
    ) : CandidateNormalization<Nothing> {
        init {
            require(code.isNotBlank()) { "candidate rejection code must not be blank" }
        }
    }
}

fun interface CandidateNormalizer<P : Any, I : Any> {
    fun normalize(candidate: Candidate<P>): CandidateNormalization<I>
}

data class CandidatePreparationSpec<P : Any, I : Any>(
    val normalizer: CandidateNormalizer<P, I>,
    val preparationSpec: PreparationSpec<I>,
)

sealed interface PreparationRejection {
    data object UnknownOperation : PreparationRejection
    data object UnavailableOperation : PreparationRejection

    data class InvalidCandidate(val code: String) : PreparationRejection {
        init {
            require(code.isNotBlank()) { "candidate rejection code must not be blank" }
        }
    }

    data class InvalidInput(val code: String) : PreparationRejection {
        init {
            require(code.isNotBlank()) { "preparation rejection code must not be blank" }
        }
    }
}

sealed interface PreparationResult<I : Any> {
    data class Prepared<I : Any>(
        val operation: PreparedOperation,
    ) : PreparationResult<I>

    data class Rejected<I : Any>(
        val reason: PreparationRejection,
    ) : PreparationResult<I>
}

class PreparationRuntime(
    private val idempotencyKeySource: IdempotencyKeySource,
) {
    fun <P : Any, I : Any> prepareCandidate(
        session: CapabilitySession,
        candidate: Candidate<P>,
        spec: CandidatePreparationSpec<P, I>,
    ): PreparationResult<I> =
        when (val normalized = spec.normalizer.normalize(candidate)) {
            is CandidateNormalization.Normalized ->
                prepare(
                    session = session,
                    input = normalized.input,
                    spec = spec.preparationSpec,
                )

            is CandidateNormalization.Rejected ->
                PreparationResult.Rejected<I>(
                    PreparationRejection.InvalidCandidate(normalized.code)
                )
        }

    fun <I : Any> prepare(
        session: CapabilitySession,
        input: I,
        spec: PreparationSpec<I>,
    ): PreparationResult<I> {
        val contract = session.contract(spec.operationId)
            ?: return PreparationResult.Rejected<I>(
                PreparationRejection.UnknownOperation
            )

        val resolved = session.resolve(spec.operationId)
            ?: return PreparationResult.Rejected<I>(
                PreparationRejection.UnavailableOperation
            )

        check(resolved.contract == contract) {
            "capability session resolved contract mismatch"
        }

        when (val validation = spec.validator.validate(input)) {
            PreparationValidation.Valid -> Unit
            is PreparationValidation.Invalid ->
                return PreparationResult.Rejected<I>(
                    PreparationRejection.InvalidInput(validation.code)
                )
        }

        val payload =
            PayloadFingerprinter.canonicalize(input, spec.inputEncoder)
        val dependencyStamps =
            spec.dependencyStampSource.capture(input)
        val idempotencyKey: IdempotencyKey =
            idempotencyKeySource.newKey()

        return PreparationResult.Prepared(
            PreparedOperation(
                contract = contract,
                payload = payload,
                dependencyStamps = dependencyStamps,
                idempotencyKey = idempotencyKey,
            )
        )
    }
}
