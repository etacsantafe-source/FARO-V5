package ar.com.faro.v5.kernel.contracts

sealed interface OperationBindingExecutionResult {
    /**
     * The owner-local operation completed inside the active transaction scope.
     * This does not mean the transaction has committed.
     */
    data object Completed : OperationBindingExecutionResult

    data class CompletedWithOutbox(
        val outboxIntents: List<OutboxEffectIntent>,
    ) : OperationBindingExecutionResult {
        init {
            require(outboxIntents.isNotEmpty()) {
                "completed-with-outbox must contain at least one intent"
            }
        }
    }

    data class Rejected(
        val code: String,
    ) : OperationBindingExecutionResult {
        init {
            require(code.isNotBlank()) { "operation binding rejection code must not be blank" }
        }
    }
}

interface RealityTransactionScope {
    val transactionId: TransactionId
}

sealed interface AtomicApplyTransactionDecision<out T> {
    data class Commit<T>(val value: T) : AtomicApplyTransactionDecision<T>
    data class Rollback<T>(val value: T) : AtomicApplyTransactionDecision<T>
}

/**
 * Single logical Reality transaction boundary.
 *
 * Concrete store adapters must guarantee rollback on Rollback and on thrown
 * exceptions/cancellation. SQLite crash-atomicity is certified later at T2.
 */
interface AtomicApplyTransactionPort {
    fun <T> run(
        transactionId: TransactionId,
        block: (RealityTransactionScope) -> AtomicApplyTransactionDecision<T>,
    ): T
}

interface OperationExecutionPort {
    val descriptor: OperationBindingDescriptor
}

interface OperationBindingPort : OperationExecutionPort {
    override val descriptor: OperationBindingDescriptor

    /**
     * Executes only inside the RealityTransactionScope supplied by Apply.
     * External effects must not be dispatched from this callback.
     */
    fun execute(
        preparedOperation: PreparedOperation,
        transactionScope: RealityTransactionScope,
    ): OperationBindingExecutionResult
}

interface ProviderBindingPort {
    val descriptor: ProviderBindingDescriptor
}


enum class ModelCapability {
    TEXT,
    STRUCTURED_OUTPUT,
    TOOL_CALLING,
    MULTIMODAL_INPUT,
    REASONING,
    STREAMING,
}

enum class ModelLifecycle {
    ACTIVE,
    DEPRECATED,
    UNAVAILABLE,
}

data class ModelRequirements(
    val requiredCapabilities: Set<ModelCapability>,
    val minimumContextTokens: Int? = null,
) {
    init {
        if (minimumContextTokens != null) {
            require(minimumContextTokens > 0) {
                "minimum context tokens must be positive"
            }
        }
    }
}

data class ProviderCapabilitySnapshot(
    val bindingId: ModelBindingId,
    val lifecycle: ModelLifecycle,
    val capabilities: Set<ModelCapability>,
    val contextLimitTokens: Int?,
) {
    init {
        if (contextLimitTokens != null) {
            require(contextLimitTokens > 0) {
                "context limit tokens must be positive"
            }
        }
    }

    fun satisfies(requirements: ModelRequirements): Boolean {
        if (lifecycle != ModelLifecycle.ACTIVE) return false
        if (!capabilities.containsAll(requirements.requiredCapabilities)) return false

        val minimum = requirements.minimumContextTokens ?: return true
        val limit = contextLimitTokens ?: return false
        return limit >= minimum
    }
}


enum class AiFailureKind {
    OFFLINE,
    DNS,
    CONNECT,
    TIMEOUT,
    AUTH_MISSING,
    AUTH_REJECTED,
    RATE_LIMIT,
    PROVIDER_CAPACITY,
    TRANSIENT_SERVER,
    MODEL_UNAVAILABLE,
    MODEL_DEPRECATED,
    CONTEXT_TOO_LARGE,
    UNSUPPORTED_CAPABILITY,
    INVALID_STRUCTURED_RESPONSE,
    POLICY_EGRESS_BLOCKED,
    BUDGET_BLOCKED,
    CANCELLED,
    TERMINAL_REQUEST_ERROR,
}

data class AiFailure(
    val kind: AiFailureKind,
    val retryDisposition: RetryDisposition,
    val retryAfterMillis: Long? = null,
) {
    init {
        if (retryAfterMillis != null) {
            require(retryAfterMillis > 0L) {
                "retry-after must be positive"
            }
        }
    }
}

data class ReasoningRequest(
    val requestId: ModelRequestId,
    val profileId: ModelProfileId,
    val requirements: ModelRequirements,
    val input: CanonicalPayload,
)

sealed interface ReasoningResult {
    data class Success(
        val bindingId: ModelBindingId,
        val output: CanonicalPayload,
    ) : ReasoningResult

    data class Failure(
        val failure: AiFailure,
    ) : ReasoningResult
}

fun interface ModelGateway {
    fun reason(
        request: ReasoningRequest,
        cancellationSignal: CancellationSignal,
    ): ReasoningResult
}
