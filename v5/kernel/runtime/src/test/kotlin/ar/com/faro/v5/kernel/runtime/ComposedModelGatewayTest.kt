package ar.com.faro.v5.kernel.runtime

import ar.com.faro.v5.kernel.contracts.AiFailure
import ar.com.faro.v5.kernel.contracts.AiFailureKind
import ar.com.faro.v5.kernel.contracts.AiMonetaryBudget
import ar.com.faro.v5.kernel.contracts.AiRetryJitterSource
import ar.com.faro.v5.kernel.contracts.AiRetryTimingConfig
import ar.com.faro.v5.kernel.contracts.AiRetryWaitPort
import ar.com.faro.v5.kernel.contracts.AiRetryWaitResult
import ar.com.faro.v5.kernel.contracts.AiTelemetrySink
import ar.com.faro.v5.kernel.contracts.AiUsage
import ar.com.faro.v5.kernel.contracts.AiUsageRecord
import ar.com.faro.v5.kernel.contracts.CancellationSignal
import ar.com.faro.v5.kernel.contracts.CanonicalPayload
import ar.com.faro.v5.kernel.contracts.ContextBudget
import ar.com.faro.v5.kernel.contracts.DataEgressDecision
import ar.com.faro.v5.kernel.contracts.DataEgressPolicy
import ar.com.faro.v5.kernel.contracts.DataEgressTarget
import ar.com.faro.v5.kernel.contracts.EgressSensitivity
import ar.com.faro.v5.kernel.contracts.ModelBindingId
import ar.com.faro.v5.kernel.contracts.ModelBindingInvocation
import ar.com.faro.v5.kernel.contracts.ModelBindingInvocationResult
import ar.com.faro.v5.kernel.contracts.ModelBindingReasoningPort
import ar.com.faro.v5.kernel.contracts.ModelCapability
import ar.com.faro.v5.kernel.contracts.ModelLifecycle
import ar.com.faro.v5.kernel.contracts.ModelProfile
import ar.com.faro.v5.kernel.contracts.ModelProfileBinding
import ar.com.faro.v5.kernel.contracts.ModelProfileId
import ar.com.faro.v5.kernel.contracts.ModelProfileRef
import ar.com.faro.v5.kernel.contracts.ModelProfileVersion
import ar.com.faro.v5.kernel.contracts.ModelRequestId
import ar.com.faro.v5.kernel.contracts.ModelRequirements
import ar.com.faro.v5.kernel.contracts.MonotonicClock
import ar.com.faro.v5.kernel.contracts.PromptArtifact
import ar.com.faro.v5.kernel.contracts.PromptId
import ar.com.faro.v5.kernel.contracts.PromptRef
import ar.com.faro.v5.kernel.contracts.PromptVersion
import ar.com.faro.v5.kernel.contracts.ProviderCapabilitySnapshot
import ar.com.faro.v5.kernel.contracts.ReasoningRequest
import ar.com.faro.v5.kernel.contracts.ReasoningResult
import ar.com.faro.v5.kernel.contracts.RetryDisposition
import ar.com.faro.v5.kernel.contracts.TrustedBindingPricing
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

class ComposedModelGatewayTest {
    @Test
    fun `gateway composes prepare route budget and binding success`() {
        val fixture = fixture(
            bindings =
                listOf(
                    successBinding("preferred", "answer")
                ),
        )

        val result = fixture.gateway.reason(request(), CancellationSignal { false })

        val success = assertInstanceOf(ReasoningResult.Success::class.java, result)
        assertEquals(ModelBindingId("preferred"), success.bindingId)
        assertEquals("answer", success.output.copyBytes().decodeToString())
    }

    @Test
    fun `gateway falls back to next authorized binding on model unavailable`() {
        val fixture = fixture(
            profileIds = listOf("preferred", "alternate"),
            snapshots = listOf(snapshot("preferred"), snapshot("alternate")),
            bindings =
                listOf(
                    failureBinding(
                        "preferred",
                        AiFailureKind.MODEL_UNAVAILABLE,
                        RetryDisposition.NOT_RETRYABLE,
                    ),
                    successBinding("alternate", "fallback"),
                ),
        )

        val result = fixture.gateway.reason(request(), CancellationSignal { false })

        val success = assertInstanceOf(ReasoningResult.Success::class.java, result)
        assertEquals(ModelBindingId("alternate"), success.bindingId)
        assertEquals("fallback", success.output.copyBytes().decodeToString())
    }

    @Test
    fun `expired provider observation fails closed as model unavailable`() {
        val clock = FakeClock(10L)
        val fixture = fixture(clock = clock, ttlNanos = 5L)
        clock.value = 15L

        val result = fixture.gateway.reason(request(), CancellationSignal { false })

        assertFailure(result, AiFailureKind.MODEL_UNAVAILABLE)
    }

    @Test
    fun `capability mismatch stays typed`() {
        val fixture = fixture(
            snapshots =
                listOf(
                    snapshot(
                        "preferred",
                        capabilities = setOf(ModelCapability.TEXT),
                    )
                )
        )
        val request =
            request(
                requirements =
                    ModelRequirements(
                        requiredCapabilities =
                            setOf(
                                ModelCapability.TEXT,
                                ModelCapability.STRUCTURED_OUTPUT,
                            )
                    )
            )

        val result = fixture.gateway.reason(request, CancellationSignal { false })

        assertFailure(result, AiFailureKind.UNSUPPORTED_CAPABILITY)
    }

    @Test
    fun `egress block stays typed and never invokes binding`() {
        var calls = 0
        val fixture = fixture(
            binding =
                object : ModelBindingReasoningPort {
                    override val bindingId = ModelBindingId("preferred")

                    override fun reason(
                        invocation: ModelBindingInvocation,
                        cancellationSignal: CancellationSignal,
                    ): ModelBindingInvocationResult {
                        calls += 1
                        return ModelBindingInvocationResult.Success(
                            output = payload("unexpected"),
                            usage = AiUsage(1, 1),
                        )
                    }
                },
            egressPolicy =
                DataEgressPolicy { pack, _ ->
                    DataEgressDecision.Blocked(
                        pack.fragments.map { it.id }
                    )
                },
        )

        val result = fixture.gateway.reason(request(), CancellationSignal { false })

        assertFailure(result, AiFailureKind.POLICY_EGRESS_BLOCKED)
        assertEquals(0, calls)
    }

    @Test
    fun `monetary budget rejection stays typed and never invokes binding`() {
        var calls = 0
        val fixture = fixture(
            binding =
                object : ModelBindingReasoningPort {
                    override val bindingId = ModelBindingId("preferred")

                    override fun reason(
                        invocation: ModelBindingInvocation,
                        cancellationSignal: CancellationSignal,
                    ): ModelBindingInvocationResult {
                        calls += 1
                        return ModelBindingInvocationResult.Success(
                            output = payload("unexpected"),
                            usage = AiUsage(1, 1),
                        )
                    }
                },
            monetaryBudget =
                AiMonetaryBudget.Capped(
                    currencyCode = "USD",
                    remainingMinor = 0,
                ),
        )

        val result = fixture.gateway.reason(request(), CancellationSignal { false })

        assertFailure(result, AiFailureKind.BUDGET_BLOCKED)
        assertEquals(0, calls)
    }

    @Test
    fun `early cancellation performs no binding call`() {
        var calls = 0
        val fixture = fixture(
            binding =
                object : ModelBindingReasoningPort {
                    override val bindingId = ModelBindingId("preferred")

                    override fun reason(
                        invocation: ModelBindingInvocation,
                        cancellationSignal: CancellationSignal,
                    ): ModelBindingInvocationResult {
                        calls += 1
                        return ModelBindingInvocationResult.Success(
                            output = payload("unexpected"),
                            usage = AiUsage(1, 1),
                        )
                    }
                },
        )

        val result = fixture.gateway.reason(request(), CancellationSignal { true })

        assertFailure(result, AiFailureKind.CANCELLED)
        assertEquals(0, calls)
    }

    @Test
    fun `retryable timeout retries same binding once with bounded delay`() {
        var calls = 0
        val waits = mutableListOf<Long>()
        val binding =
            object : ModelBindingReasoningPort {
                override val bindingId = ModelBindingId("preferred")

                override fun reason(
                    invocation: ModelBindingInvocation,
                    cancellationSignal: CancellationSignal,
                ): ModelBindingInvocationResult {
                    calls += 1
                    return if (calls == 1) {
                        ModelBindingInvocationResult.Failure(
                            AiFailure(
                                kind = AiFailureKind.TIMEOUT,
                                retryDisposition = RetryDisposition.RETRYABLE,
                            )
                        )
                    } else {
                        ModelBindingInvocationResult.Success(
                            output = payload("after-retry"),
                            usage = AiUsage(10, 4),
                        )
                    }
                }
            }
        val fixture =
            fixture(
                binding = binding,
                retryTimingConfig =
                    AiRetryTimingConfig(
                        baseDelayMillis = 100,
                        maxDelayMillis = 1_000,
                        maxSameBindingRetries = 1,
                        maxJitterMillis = 20,
                    ),
                retryJitterSource = AiRetryJitterSource { 7L },
                retryWaitPort =
                    AiRetryWaitPort { delay, _ ->
                        waits += delay.delayMillis
                        AiRetryWaitResult.COMPLETED
                    },
            )

        val result = fixture.gateway.reason(request(), CancellationSignal { false })

        val success = assertInstanceOf(ReasoningResult.Success::class.java, result)
        assertEquals("after-retry", success.output.copyBytes().decodeToString())
        assertEquals(2, calls)
        assertEquals(listOf(107L), waits)
    }

    @Test
    fun `server retry-after is a lower bound for same-binding retry delay`() {
        var calls = 0
        val waits = mutableListOf<Long>()
        val binding =
            object : ModelBindingReasoningPort {
                override val bindingId = ModelBindingId("preferred")

                override fun reason(
                    invocation: ModelBindingInvocation,
                    cancellationSignal: CancellationSignal,
                ): ModelBindingInvocationResult {
                    calls += 1
                    return if (calls == 1) {
                        ModelBindingInvocationResult.Failure(
                            AiFailure(
                                kind = AiFailureKind.TIMEOUT,
                                retryDisposition = RetryDisposition.RETRYABLE,
                                retryAfterMillis = 500,
                            )
                        )
                    } else {
                        ModelBindingInvocationResult.Success(
                            output = payload("after-server-delay"),
                            usage = AiUsage(10, 4),
                        )
                    }
                }
            }
        val fixture =
            fixture(
                binding = binding,
                retryTimingConfig =
                    AiRetryTimingConfig(
                        baseDelayMillis = 100,
                        maxDelayMillis = 1_000,
                        maxSameBindingRetries = 1,
                        maxJitterMillis = 0,
                    ),
                retryWaitPort =
                    AiRetryWaitPort { delay, _ ->
                        waits += delay.delayMillis
                        AiRetryWaitResult.COMPLETED
                    },
            )

        val result = fixture.gateway.reason(request(), CancellationSignal { false })

        assertInstanceOf(ReasoningResult.Success::class.java, result)
        assertEquals(listOf(500L), waits)
    }

    @Test
    fun `retry exhaustion falls through to alternate binding without another wait`() {
        var preferredCalls = 0
        var alternateCalls = 0
        var waits = 0
        val preferred =
            object : ModelBindingReasoningPort {
                override val bindingId = ModelBindingId("preferred")

                override fun reason(
                    invocation: ModelBindingInvocation,
                    cancellationSignal: CancellationSignal,
                ): ModelBindingInvocationResult {
                    preferredCalls += 1
                    return ModelBindingInvocationResult.Failure(
                        AiFailure(
                            kind = AiFailureKind.TIMEOUT,
                            retryDisposition = RetryDisposition.RETRYABLE,
                        )
                    )
                }
            }
        val alternate =
            object : ModelBindingReasoningPort {
                override val bindingId = ModelBindingId("alternate")

                override fun reason(
                    invocation: ModelBindingInvocation,
                    cancellationSignal: CancellationSignal,
                ): ModelBindingInvocationResult {
                    alternateCalls += 1
                    return ModelBindingInvocationResult.Success(
                        output = payload("alternate"),
                        usage = AiUsage(10, 4),
                    )
                }
            }
        val fixture =
            fixture(
                profileIds = listOf("preferred", "alternate"),
                snapshots = listOf(snapshot("preferred"), snapshot("alternate")),
                bindings = listOf(preferred, alternate),
                retryTimingConfig =
                    AiRetryTimingConfig(
                        baseDelayMillis = 10,
                        maxDelayMillis = 100,
                        maxSameBindingRetries = 1,
                        maxJitterMillis = 0,
                    ),
                retryWaitPort =
                    AiRetryWaitPort { _, _ ->
                        waits += 1
                        AiRetryWaitResult.COMPLETED
                    },
            )

        val result = fixture.gateway.reason(request(), CancellationSignal { false })

        val success = assertInstanceOf(ReasoningResult.Success::class.java, result)
        assertEquals(ModelBindingId("alternate"), success.bindingId)
        assertEquals(2, preferredCalls)
        assertEquals(1, alternateCalls)
        assertEquals(1, waits)
    }

    @Test
    fun `cancellation during retry wait stops before second provider call`() {
        var calls = 0
        val binding =
            object : ModelBindingReasoningPort {
                override val bindingId = ModelBindingId("preferred")

                override fun reason(
                    invocation: ModelBindingInvocation,
                    cancellationSignal: CancellationSignal,
                ): ModelBindingInvocationResult {
                    calls += 1
                    return ModelBindingInvocationResult.Failure(
                        AiFailure(
                            kind = AiFailureKind.TIMEOUT,
                            retryDisposition = RetryDisposition.RETRYABLE,
                        )
                    )
                }
            }
        val fixture =
            fixture(
                binding = binding,
                retryWaitPort =
                    AiRetryWaitPort { _, _ ->
                        AiRetryWaitResult.CANCELLED
                    },
            )

        val result = fixture.gateway.reason(request(), CancellationSignal { false })

        assertFailure(result, AiFailureKind.CANCELLED)
        assertEquals(1, calls)
    }

    @Test
    fun `rate limit falls back immediately and never same-binding retries`() {
        var preferredCalls = 0
        var alternateCalls = 0
        var waits = 0
        val preferred =
            object : ModelBindingReasoningPort {
                override val bindingId = ModelBindingId("preferred")

                override fun reason(
                    invocation: ModelBindingInvocation,
                    cancellationSignal: CancellationSignal,
                ): ModelBindingInvocationResult {
                    preferredCalls += 1
                    return ModelBindingInvocationResult.Failure(
                        AiFailure(
                            kind = AiFailureKind.RATE_LIMIT,
                            retryDisposition = RetryDisposition.RETRYABLE,
                            retryAfterMillis = 2_000,
                        )
                    )
                }
            }
        val alternate =
            object : ModelBindingReasoningPort {
                override val bindingId = ModelBindingId("alternate")

                override fun reason(
                    invocation: ModelBindingInvocation,
                    cancellationSignal: CancellationSignal,
                ): ModelBindingInvocationResult {
                    alternateCalls += 1
                    return ModelBindingInvocationResult.Success(
                        output = payload("fallback"),
                        usage = AiUsage(10, 4),
                    )
                }
            }
        val fixture =
            fixture(
                profileIds = listOf("preferred", "alternate"),
                snapshots = listOf(snapshot("preferred"), snapshot("alternate")),
                bindings = listOf(preferred, alternate),
                retryWaitPort =
                    AiRetryWaitPort { _, _ ->
                        waits += 1
                        AiRetryWaitResult.COMPLETED
                    },
            )

        val result = fixture.gateway.reason(request(), CancellationSignal { false })

        val success = assertInstanceOf(ReasoningResult.Success::class.java, result)
        assertEquals(ModelBindingId("alternate"), success.bindingId)
        assertEquals(1, preferredCalls)
        assertEquals(1, alternateCalls)
        assertEquals(0, waits)
    }

    @Test
    fun `successful winner records actual usage and trusted estimated cost`() {
        val records = mutableListOf<AiUsageRecord>()
        val fixture =
            fixture(
                pricing =
                    listOf(
                        TrustedBindingPricing(
                            bindingId = ModelBindingId("preferred"),
                            currencyCode = "USD",
                            inputPerMillionMinor = 1_000_000,
                            outputPerMillionMinor = 2_000_000,
                            source = "trusted-test",
                            effectiveAtEpochMillis = 1,
                        )
                    ),
                telemetrySink = AiTelemetrySink { records += it },
            )

        val result = fixture.gateway.reason(request(), CancellationSignal { false })

        assertInstanceOf(ReasoningResult.Success::class.java, result)
        val record = records.single()
        assertEquals(ModelRequestId("request-1"), record.requestId)
        assertEquals(ModelBindingId("preferred"), record.bindingId)
        assertEquals(AiUsage(inputTokens = 10, outputTokens = 5), record.usage)
        assertEquals(20L, record.estimatedCost?.minorUnits)
        assertEquals("USD", record.estimatedCost?.currencyCode)
        assertEquals("trusted-test", record.estimatedCost?.pricingSource)
    }

    @Test
    fun `incomplete usage is recorded without estimated cost`() {
        val records = mutableListOf<AiUsageRecord>()
        val binding =
            object : ModelBindingReasoningPort {
                override val bindingId = ModelBindingId("preferred")

                override fun reason(
                    invocation: ModelBindingInvocation,
                    cancellationSignal: CancellationSignal,
                ): ModelBindingInvocationResult =
                    ModelBindingInvocationResult.Success(
                        output = payload("partial-usage"),
                        usage = AiUsage(inputTokens = 10, outputTokens = null),
                    )
            }
        val fixture =
            fixture(
                binding = binding,
                pricing =
                    listOf(
                        TrustedBindingPricing(
                            bindingId = ModelBindingId("preferred"),
                            currencyCode = "USD",
                            inputPerMillionMinor = 1,
                            outputPerMillionMinor = 1,
                            source = "trusted-test",
                            effectiveAtEpochMillis = 1,
                        )
                    ),
                telemetrySink = AiTelemetrySink { records += it },
            )

        val result = fixture.gateway.reason(request(), CancellationSignal { false })

        assertInstanceOf(ReasoningResult.Success::class.java, result)
        val record = records.single()
        assertEquals(AiUsage(inputTokens = 10, outputTokens = null), record.usage)
        assertEquals(null, record.estimatedCost)
    }

    @Test
    fun `fallback records only the winning binding telemetry`() {
        val records = mutableListOf<AiUsageRecord>()
        val fixture =
            fixture(
                profileIds = listOf("preferred", "alternate"),
                snapshots = listOf(snapshot("preferred"), snapshot("alternate")),
                bindings =
                    listOf(
                        failureBinding(
                            "preferred",
                            AiFailureKind.MODEL_UNAVAILABLE,
                            RetryDisposition.NOT_RETRYABLE,
                        ),
                        successBinding("alternate", "fallback"),
                    ),
                telemetrySink = AiTelemetrySink { records += it },
            )

        val result = fixture.gateway.reason(request(), CancellationSignal { false })

        val success = assertInstanceOf(ReasoningResult.Success::class.java, result)
        assertEquals(ModelBindingId("alternate"), success.bindingId)
        assertEquals(listOf(ModelBindingId("alternate")), records.map { it.bindingId })
    }

    @Test
    fun `telemetry sink failure never overturns successful reasoning`() {
        val fixture =
            fixture(
                telemetrySink =
                    AiTelemetrySink {
                        throw IllegalStateException("telemetry unavailable")
                    },
            )

        val result = fixture.gateway.reason(request(), CancellationSignal { false })

        val success = assertInstanceOf(ReasoningResult.Success::class.java, result)
        assertEquals(ModelBindingId("preferred"), success.bindingId)
    }

    @Test
    fun `failed reasoning emits no success telemetry record`() {
        val records = mutableListOf<AiUsageRecord>()
        val fixture =
            fixture(
                binding =
                    failureBinding(
                        "preferred",
                        AiFailureKind.MODEL_UNAVAILABLE,
                        RetryDisposition.NOT_RETRYABLE,
                    ),
                telemetrySink = AiTelemetrySink { records += it },
            )

        val result = fixture.gateway.reason(request(), CancellationSignal { false })

        assertFailure(result, AiFailureKind.MODEL_UNAVAILABLE)
        assertEquals(emptyList<AiUsageRecord>(), records)
    }

    @Test
    fun `missing prompt and required context overflow fail closed`() {
        val missingPrompt =
            fixture(
                promptRegistry =
                    ImmutablePromptRegistry.of(emptyList()),
            )
        assertFailure(
            missingPrompt.gateway.reason(request(), CancellationSignal { false }),
            AiFailureKind.TERMINAL_REQUEST_ERROR,
        )

        val overflow =
            fixture(
                contextBudget =
                    ContextBudget(
                        maxEstimatedTokens = 1,
                        maxBytes = 1,
                        maxFragments = 1,
                    )
            )
        val result = overflow.gateway.reason(request(), CancellationSignal { false })
        val failure = assertInstanceOf(ReasoningResult.Failure::class.java, result)
        assertEquals(AiFailureKind.CONTEXT_TOO_LARGE, failure.failure.kind)
        assertEquals(RetryDisposition.REQUIRES_REPREPARE, failure.failure.retryDisposition)
    }

    private fun fixture(
        profileIds: List<String> = listOf("preferred"),
        snapshots: List<ProviderCapabilitySnapshot> =
            listOf(snapshot("preferred")),
        bindings: List<ModelBindingReasoningPort>? = null,
        binding: ModelBindingReasoningPort? = null,
        clock: FakeClock = FakeClock(100L),
        ttlNanos: Long = 1_000L,
        egressPolicy: DataEgressPolicy =
            DataEgressPolicy { _, _ -> DataEgressDecision.Allowed },
        monetaryBudget: AiMonetaryBudget = AiMonetaryBudget.Disabled,
        pricing: List<TrustedBindingPricing> = emptyList(),
        telemetrySink: AiTelemetrySink = AiTelemetrySink { },
        promptRegistry: ImmutablePromptRegistry =
            ImmutablePromptRegistry.of(
                listOf(
                    PromptArtifact(
                        ref = promptRef(),
                        content = payload("system"),
                    )
                )
            ),
        contextBudget: ContextBudget =
            ContextBudget(
                maxEstimatedTokens = 10_000,
                maxBytes = 64 * 1024,
                maxFragments = 16,
            ),
        retryTimingConfig: AiRetryTimingConfig =
            AiRetryTimingConfig(
                baseDelayMillis = 10,
                maxDelayMillis = 100,
                maxSameBindingRetries = 1,
                maxJitterMillis = 0,
            ),
        retryJitterSource: AiRetryJitterSource = AiRetryJitterSource { 0L },
        retryWaitPort: AiRetryWaitPort =
            AiRetryWaitPort { _, _ ->
                AiRetryWaitResult.COMPLETED
            },
    ): Fixture {
        val profile =
            ModelProfile(
                ref = profileRef(),
                orderedBindings =
                    profileIds.map {
                        ModelProfileBinding(
                            bindingId = ModelBindingId(it),
                            target = DataEgressTarget.REMOTE_MODEL,
                        )
                    },
            )
        val cache =
            FreshProviderCapabilityCache(
                clock = clock,
                ttlNanos = ttlNanos,
            )
        cache.replace(snapshots)

        val actualBindings =
            bindings
                ?: listOfNotNull(
                    binding ?: successBinding("preferred", "answer")
                )

        val gateway =
            ComposedModelGateway(
                policyResolver =
                    ModelGatewayPolicyResolver {
                        ModelGatewayPolicySnapshot(
                            profileRef = profileRef(),
                            promptRef = promptRef(),
                            contextualFragments = emptyList(),
                            contextBudget = contextBudget,
                            promptEstimatedTokens = 2,
                            requestEstimatedTokens = 2,
                            promptSensitivity = EgressSensitivity.PUBLIC,
                            requestSensitivity = EgressSensitivity.PUBLIC,
                            maxOutputTokens = 64,
                            pricing = pricing,
                            monetaryBudget = monetaryBudget,
                            retryTimingConfig = retryTimingConfig,
                        )
                    },
                profileRegistry = ImmutableModelProfileRegistry.of(listOf(profile)),
                promptRegistry = promptRegistry,
                contextAssembler = DeterministicContextAssembler(),
                capabilityCache = cache,
                egressPolicy = egressPolicy,
                bindingRegistry = ImmutableModelBindingRegistry(actualBindings),
                retryTimingPolicy = BoundedExponentialAiRetryTimingPolicy,
                retryJitterSource = retryJitterSource,
                retryWaitPort = retryWaitPort,
                telemetrySink = telemetrySink,
            )

        return Fixture(gateway)
    }

    private fun request(
        requirements: ModelRequirements =
            ModelRequirements(
                requiredCapabilities = setOf(ModelCapability.TEXT),
            ),
    ): ReasoningRequest =
        ReasoningRequest(
            requestId = ModelRequestId("request-1"),
            profileId = ModelProfileId("conversation"),
            requirements = requirements,
            input = payload("hello"),
        )

    private fun profileRef(): ModelProfileRef =
        ModelProfileRef(
            profileId = ModelProfileId("conversation"),
            version = ModelProfileVersion(1),
        )

    private fun promptRef(): PromptRef =
        PromptRef(
            promptId = PromptId("conversation"),
            version = PromptVersion(1),
        )

    private fun snapshot(
        id: String,
        capabilities: Set<ModelCapability> = setOf(ModelCapability.TEXT),
    ): ProviderCapabilitySnapshot =
        ProviderCapabilitySnapshot(
            bindingId = ModelBindingId(id),
            lifecycle = ModelLifecycle.ACTIVE,
            capabilities = capabilities,
            contextLimitTokens = 32_000,
        )

    private fun successBinding(
        id: String,
        output: String,
    ): ModelBindingReasoningPort =
        object : ModelBindingReasoningPort {
            override val bindingId = ModelBindingId(id)

            override fun reason(
                invocation: ModelBindingInvocation,
                cancellationSignal: CancellationSignal,
            ): ModelBindingInvocationResult =
                ModelBindingInvocationResult.Success(
                    output = payload(output),
                    usage = AiUsage(inputTokens = 10, outputTokens = 5),
                )
        }

    private fun failureBinding(
        id: String,
        kind: AiFailureKind,
        disposition: RetryDisposition,
    ): ModelBindingReasoningPort =
        object : ModelBindingReasoningPort {
            override val bindingId = ModelBindingId(id)

            override fun reason(
                invocation: ModelBindingInvocation,
                cancellationSignal: CancellationSignal,
            ): ModelBindingInvocationResult =
                ModelBindingInvocationResult.Failure(
                    AiFailure(
                        kind = kind,
                        retryDisposition = disposition,
                    )
                )
        }

    private fun assertFailure(
        result: ReasoningResult,
        kind: AiFailureKind,
    ) {
        val failure = assertInstanceOf(ReasoningResult.Failure::class.java, result)
        assertEquals(kind, failure.failure.kind)
    }

    private fun payload(value: String): CanonicalPayload =
        CanonicalPayload.fromEncoded(
            formatId = "text/plain",
            bytes = value.encodeToByteArray(),
        )

    private data class Fixture(
        val gateway: ComposedModelGateway,
    )

    private class FakeClock(
        var value: Long,
    ) : MonotonicClock {
        override fun readNanos(): Long = value
    }
}
