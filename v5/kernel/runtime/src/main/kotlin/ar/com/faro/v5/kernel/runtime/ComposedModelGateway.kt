package ar.com.faro.v5.kernel.runtime

import ar.com.faro.v5.kernel.contracts.AiBudgetAuthorizer
import ar.com.faro.v5.kernel.contracts.AiFailure
import ar.com.faro.v5.kernel.contracts.AiFailureKind
import ar.com.faro.v5.kernel.contracts.AiFallbackExecutionRequest
import ar.com.faro.v5.kernel.contracts.AiFallbackExecutionResult
import ar.com.faro.v5.kernel.contracts.AiMonetaryBudget
import ar.com.faro.v5.kernel.contracts.AiRetryJitterSource
import ar.com.faro.v5.kernel.contracts.AiRetryTimingConfig
import ar.com.faro.v5.kernel.contracts.AiRetryTimingPolicy
import ar.com.faro.v5.kernel.contracts.AiRetryWaitPort
import ar.com.faro.v5.kernel.contracts.AiRetryWaitResult
import ar.com.faro.v5.kernel.contracts.AiRouteFailurePolicy
import ar.com.faro.v5.kernel.contracts.AiTelemetrySink
import ar.com.faro.v5.kernel.contracts.AiUsageRecord
import ar.com.faro.v5.kernel.contracts.CancellationSignal
import ar.com.faro.v5.kernel.contracts.ContextAssembler
import ar.com.faro.v5.kernel.contracts.ContextBudget
import ar.com.faro.v5.kernel.contracts.ContextFragment
import ar.com.faro.v5.kernel.contracts.DataEgressPolicy
import ar.com.faro.v5.kernel.contracts.EgressSensitivity
import ar.com.faro.v5.kernel.contracts.ModelBindingRegistry
import ar.com.faro.v5.kernel.contracts.ModelGateway
import ar.com.faro.v5.kernel.contracts.ModelLifecycle
import ar.com.faro.v5.kernel.contracts.ModelProfileRef
import ar.com.faro.v5.kernel.contracts.ModelProfileRegistry
import ar.com.faro.v5.kernel.contracts.ModelRoutePlan
import ar.com.faro.v5.kernel.contracts.ModelRouteRejectionReason
import ar.com.faro.v5.kernel.contracts.ProjectedModelUsage
import ar.com.faro.v5.kernel.contracts.PromptRef
import ar.com.faro.v5.kernel.contracts.PromptRegistry
import ar.com.faro.v5.kernel.contracts.ReasoningContextPreparationRequest
import ar.com.faro.v5.kernel.contracts.ReasoningContextPreparationResult
import ar.com.faro.v5.kernel.contracts.ReasoningRequest
import ar.com.faro.v5.kernel.contracts.ReasoningResult
import ar.com.faro.v5.kernel.contracts.RetryDisposition
import ar.com.faro.v5.kernel.contracts.TrustedBindingPricing

class ModelGatewayPolicySnapshot(
    val profileRef: ModelProfileRef,
    val promptRef: PromptRef,
    contextualFragments: List<ContextFragment>,
    val contextBudget: ContextBudget,
    val promptEstimatedTokens: Int,
    val requestEstimatedTokens: Int,
    val promptSensitivity: EgressSensitivity,
    val requestSensitivity: EgressSensitivity,
    val maxOutputTokens: Long,
    pricing: List<TrustedBindingPricing>,
    val monetaryBudget: AiMonetaryBudget,
    val retryTimingConfig: AiRetryTimingConfig,
) {
    val contextualFragments: List<ContextFragment> = contextualFragments.toList()
    val pricing: List<TrustedBindingPricing> = pricing.toList()

    init {
        require(promptEstimatedTokens > 0) {
            "gateway prompt token estimate must be positive"
        }
        require(requestEstimatedTokens > 0) {
            "gateway request token estimate must be positive"
        }
        require(maxOutputTokens > 0L) {
            "gateway max output tokens must be positive"
        }
        val pricingIds = this.pricing.map { it.bindingId }
        require(pricingIds.size == pricingIds.distinct().size) {
            "gateway trusted pricing entries must have unique binding ids"
        }
    }
}

fun interface ModelGatewayPolicyResolver {
    fun resolve(request: ReasoningRequest): ModelGatewayPolicySnapshot?
}

/**
 * Canonical provider-neutral composition of the V5 reasoning path.
 *
 * This class owns no provider configuration or durable truth. It composes frozen request
 * policy, prompt/context preparation, fresh provider observations, capability/egress routing,
 * monetary budget authorization and fallback execution.
 *
 * Same-binding retry is bounded by the frozen request policy and delegated to an injected,
 * cancellation-aware wait port. Fallback across authorized bindings remains deterministic.
 */
class ComposedModelGateway(
    private val policyResolver: ModelGatewayPolicyResolver,
    private val profileRegistry: ModelProfileRegistry,
    private val promptRegistry: PromptRegistry,
    private val contextAssembler: ContextAssembler,
    private val capabilityCache: FreshProviderCapabilityCache,
    private val egressPolicy: DataEgressPolicy,
    private val bindingRegistry: ModelBindingRegistry,
    private val retryTimingPolicy: AiRetryTimingPolicy,
    private val retryJitterSource: AiRetryJitterSource,
    private val retryWaitPort: AiRetryWaitPort,
    private val telemetrySink: AiTelemetrySink = AiTelemetrySink { },
    private val budgetAuthorizer: AiBudgetAuthorizer = ConservativeAiBudgetAuthorizer,
    private val failurePolicy: AiRouteFailurePolicy = ConservativeAiRouteFailurePolicy,
) : ModelGateway {
    override fun reason(
        request: ReasoningRequest,
        cancellationSignal: CancellationSignal,
    ): ReasoningResult {
        if (cancellationSignal.isCancelled()) {
            return failure(
                AiFailureKind.CANCELLED,
                RetryDisposition.NOT_RETRYABLE,
            )
        }

        val policy =
            policyResolver.resolve(request)
                ?: return failure(
                    AiFailureKind.TERMINAL_REQUEST_ERROR,
                    RetryDisposition.NOT_RETRYABLE,
                )

        if (policy.profileRef.profileId != request.profileId) {
            return failure(
                AiFailureKind.TERMINAL_REQUEST_ERROR,
                RetryDisposition.NOT_RETRYABLE,
            )
        }

        val profile =
            profileRegistry.resolve(policy.profileRef)
                ?: return failure(
                    AiFailureKind.MODEL_UNAVAILABLE,
                    RetryDisposition.NOT_RETRYABLE,
                )

        val prepared =
            DeterministicReasoningContextPreparer.prepare(
                request =
                    ReasoningContextPreparationRequest(
                        request = request,
                        promptRef = policy.promptRef,
                        contextualFragments = policy.contextualFragments,
                        budget = policy.contextBudget,
                        promptEstimatedTokens = policy.promptEstimatedTokens,
                        requestEstimatedTokens = policy.requestEstimatedTokens,
                        promptSensitivity = policy.promptSensitivity,
                        requestSensitivity = policy.requestSensitivity,
                    ),
                promptRegistry = promptRegistry,
                assembler = contextAssembler,
            )

        val invocation =
            when (prepared) {
                is ReasoningContextPreparationResult.Ready ->
                    prepared.invocation

                is ReasoningContextPreparationResult.PromptMissing ->
                    return failure(
                        AiFailureKind.TERMINAL_REQUEST_ERROR,
                        RetryDisposition.NOT_RETRYABLE,
                    )

                is ReasoningContextPreparationResult.RequiredContentTooLarge ->
                    return failure(
                        AiFailureKind.CONTEXT_TOO_LARGE,
                        RetryDisposition.REQUIRES_REPREPARE,
                    )
            }

        val snapshots = capabilityCache.freshSnapshots()
        val routePlan =
            DeterministicModelRoutePlanner.plan(
                profile = profile,
                snapshots = snapshots,
                requirements = request.requirements,
                contextPack = invocation.contextPack,
                egressPolicy = egressPolicy,
            )

        if (routePlan.eligibleBindings.isEmpty()) {
            return ReasoningResult.Failure(
                routeFailure(routePlan, snapshots)
            )
        }

        val projectedUsage =
            ProjectedModelUsage(
                inputTokens = invocation.contextPack.estimatedTokens.toLong(),
                maxOutputTokens = policy.maxOutputTokens,
            )

        val budgeted =
            DeterministicAiBudgetRoutePlanner.plan(
                routePlan = routePlan,
                projectedUsage = projectedUsage,
                pricing = policy.pricing,
                budget = policy.monetaryBudget,
                authorizer = budgetAuthorizer,
            )

        if (budgeted.authorizedBindings.isEmpty()) {
            return failure(
                AiFailureKind.BUDGET_BLOCKED,
                RetryDisposition.NOT_RETRYABLE,
            )
        }

        var retryOrdinal = 0
        while (true) {
            val result =
                DeterministicAiFallbackExecutor.execute(
                    request =
                        AiFallbackExecutionRequest(
                            routePlan = budgeted,
                            invocation = invocation,
                            sameBindingRetryAllowed =
                                retryOrdinal <
                                    policy.retryTimingConfig.maxSameBindingRetries,
                        ),
                    registry = bindingRegistry,
                    failurePolicy = failurePolicy,
                    cancellationSignal = cancellationSignal,
                )

            when (result) {
                is AiFallbackExecutionResult.Success -> {
                    recordTelemetryBestEffort(
                        request = request,
                        policy = policy,
                        result = result,
                    )
                    return ReasoningResult.Success(
                        bindingId = result.bindingId,
                        output = result.output,
                    )
                }

                is AiFallbackExecutionResult.Deferred ->
                    return ReasoningResult.Failure(
                        result.failure.copy(
                            retryAfterMillis =
                                result.retryAfterMillis
                                    ?: result.failure.retryAfterMillis,
                        )
                    )

                is AiFallbackExecutionResult.RetrySameBinding -> {
                    retryOrdinal += 1
                    val delay =
                        retryTimingPolicy.delayFor(
                            retryOrdinal = retryOrdinal,
                            serverMinimumDelayMillis = result.failure.retryAfterMillis,
                            config = policy.retryTimingConfig,
                            jitterSource = retryJitterSource,
                        )
                            ?: return ReasoningResult.Failure(result.failure)

                    if (
                        retryWaitPort.await(
                            delay = delay,
                            cancellationSignal = cancellationSignal,
                        ) == AiRetryWaitResult.CANCELLED
                    ) {
                        return failure(
                            AiFailureKind.CANCELLED,
                            RetryDisposition.NOT_RETRYABLE,
                        )
                    }
                }

                is AiFallbackExecutionResult.Stopped ->
                    return ReasoningResult.Failure(result.failure)
            }
        }
    }


    private fun recordTelemetryBestEffort(
        request: ReasoningRequest,
        policy: ModelGatewayPolicySnapshot,
        result: AiFallbackExecutionResult.Success,
    ) {
        try {
            val pricing =
                policy.pricing.firstOrNull {
                    it.bindingId == result.bindingId
                }
            val estimatedCost =
                ModelCostEstimator.estimate(
                    usage = result.usage,
                    pricing = pricing,
                )

            telemetrySink.record(
                AiUsageRecord(
                    requestId = request.requestId,
                    bindingId = result.bindingId,
                    usage = result.usage,
                    estimatedCost = estimatedCost,
                )
            )
        } catch (_: RuntimeException) {
            // Telemetry is observational and must never overturn a successful model response.
        }
    }

    private fun routeFailure(
        routePlan: ModelRoutePlan,
        snapshots: List<ar.com.faro.v5.kernel.contracts.ProviderCapabilitySnapshot>,
    ): AiFailure {
        val first =
            routePlan.rejections.firstOrNull()
                ?: return AiFailure(
                    kind = AiFailureKind.MODEL_UNAVAILABLE,
                    retryDisposition = RetryDisposition.NOT_RETRYABLE,
                )

        val kind =
            when (first.reason) {
                ModelRouteRejectionReason.SNAPSHOT_MISSING ->
                    AiFailureKind.MODEL_UNAVAILABLE

                ModelRouteRejectionReason.LIFECYCLE_NOT_ACTIVE -> {
                    val lifecycle =
                        snapshots
                            .firstOrNull { it.bindingId == first.bindingId }
                            ?.lifecycle
                    if (lifecycle == ModelLifecycle.DEPRECATED) {
                        AiFailureKind.MODEL_DEPRECATED
                    } else {
                        AiFailureKind.MODEL_UNAVAILABLE
                    }
                }

                ModelRouteRejectionReason.CAPABILITY_MISMATCH ->
                    AiFailureKind.UNSUPPORTED_CAPABILITY

                ModelRouteRejectionReason.CONTEXT_LIMIT_UNKNOWN,
                ModelRouteRejectionReason.CONTEXT_LIMIT_TOO_SMALL ->
                    AiFailureKind.CONTEXT_TOO_LARGE

                ModelRouteRejectionReason.EGRESS_BLOCKED ->
                    AiFailureKind.POLICY_EGRESS_BLOCKED
            }

        return AiFailure(
            kind = kind,
            retryDisposition =
                if (kind == AiFailureKind.CONTEXT_TOO_LARGE) {
                    RetryDisposition.REQUIRES_REPREPARE
                } else {
                    RetryDisposition.NOT_RETRYABLE
                },
        )
    }

    private fun failure(
        kind: AiFailureKind,
        retryDisposition: RetryDisposition,
    ): ReasoningResult.Failure =
        ReasoningResult.Failure(
            AiFailure(
                kind = kind,
                retryDisposition = retryDisposition,
            )
        )
}
