package ar.com.faro.v5.evidence

import ar.com.faro.v5.kernel.contracts.CancellationSignal
import ar.com.faro.v5.kernel.contracts.ContractExposure
import ar.com.faro.v5.kernel.contracts.EgressSensitivity
import ar.com.faro.v5.kernel.contracts.ExecutionProfile
import ar.com.faro.v5.kernel.contracts.IdempotencyKey
import ar.com.faro.v5.kernel.contracts.OperationBindingDescriptor
import ar.com.faro.v5.kernel.contracts.OperationContractDescriptor
import ar.com.faro.v5.kernel.contracts.OperationExecutionPort
import ar.com.faro.v5.kernel.contracts.PayloadFingerprint
import ar.com.faro.v5.kernel.contracts.PreparedOperation
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.Base64

data class WebSearchEvidenceRecord(
    val evidenceId: WebEvidenceId,
    val idempotencyKey: IdempotencyKey,
    val payloadFingerprint: PayloadFingerprint,
    val query: String,
    val maxResults: Int,
    val sensitivity: EgressSensitivity,
    val providerId: String,
    val providerRequestId: String?,
    val providerUsage: WebProviderUsage?,
    val acquiredAtEpochMillis: Long,
    val policyVersion: String,
    val adapterVersion: String,
    val hits: List<WebSearchHit>,
    val trust: EvidenceContentTrust = EvidenceContentTrust.UNTRUSTED_CONTENT,
) {
    init {
        require(query.isNotBlank() && query.length <= WebSearchRequest.MAX_QUERY_CHARS)
        require(maxResults in 1..WebSearchRequest.MAX_RESULTS)
        require(sensitivity == EgressSensitivity.PUBLIC)
        require(providerId.isNotBlank() && providerId.length <= 64)
        require(
            providerRequestId == null ||
                (
                    providerRequestId.isNotBlank() &&
                        providerRequestId.length <= WebSearchResponse.MAX_PROVIDER_REQUEST_ID_CHARS
                )
        )
        require(acquiredAtEpochMillis >= 0L)
        require(policyVersion.isNotBlank() && policyVersion.length <= WebSearchPolicy.MAX_VERSION_CHARS)
        require(adapterVersion.isNotBlank() && adapterVersion.length <= WebSearchPolicy.MAX_VERSION_CHARS)
        require(hits.size <= maxResults)
        require(hits.all { it.trust == EvidenceContentTrust.UNTRUSTED_CONTENT })
        require(trust == EvidenceContentTrust.UNTRUSTED_CONTENT)
    }

    override fun toString(): String =
        "WebSearchEvidenceRecord(evidenceId=" + evidenceId.value +
            ", maxResults=" + maxResults +
            ", providerId=" + providerId +
            ", hits=" + hits.size +
            ", trust=" + trust + ")"
}

sealed interface WebSearchEvidenceRecordOutcome {
    data class Committed(
        val record: WebSearchEvidenceRecord,
        val replayed: Boolean,
    ) : WebSearchEvidenceRecordOutcome

    data class RejectedIdentityConflict(
        val existing: WebSearchEvidenceRecord,
        val idempotencyKey: IdempotencyKey,
    ) : WebSearchEvidenceRecordOutcome
}

sealed interface WebEvidenceSearchExecution {
    data class Applied(
        val outcome: WebSearchEvidenceRecordOutcome.Committed,
        val searchResponse: WebSearchResponse?,
    ) : WebEvidenceSearchExecution

    data class IdentityConflict(
        val outcome: WebSearchEvidenceRecordOutcome.RejectedIdentityConflict,
    ) : WebEvidenceSearchExecution

    data class SearchFailed(
        val failure: WebSearchResult.Failure,
    ) : WebEvidenceSearchExecution

    data object RejectedContract : WebEvidenceSearchExecution
    data object RejectedDependency : WebEvidenceSearchExecution
    data object RejectedPayload : WebEvidenceSearchExecution
}

object WebEvidenceSearchPayload {
    const val FORMAT_ID = "web.evidence.search.v1"
    const val MAX_PAYLOAD_BYTES = 8192

    fun payload(
        request: WebSearchRequest,
    ): ar.com.faro.v5.kernel.contracts.CanonicalPayload {
        val bytes =
            ByteArrayOutputStream().use { buffer ->
                DataOutputStream(buffer).use { output ->
                    output.writeInt(1)
                    output.writeUTF(request.query)
                    output.writeInt(request.maxResults)
                    output.writeUTF(request.sensitivity.name)
                }
                buffer.toByteArray()
            }
        require(bytes.size <= MAX_PAYLOAD_BYTES) {
            "web evidence search payload exceeds bound"
        }
        return ar.com.faro.v5.kernel.contracts.CanonicalPayload.fromEncoded(
            FORMAT_ID,
            bytes,
        )
    }

    fun decode(
        payload: ar.com.faro.v5.kernel.contracts.CanonicalPayload,
    ): WebSearchRequest? {
        if (payload.formatId != FORMAT_ID || payload.size > MAX_PAYLOAD_BYTES) {
            return null
        }
        return runCatching {
            val bytes = payload.copyBytes()
            try {
                DataInputStream(ByteArrayInputStream(bytes)).use { input ->
                    require(input.readInt() == 1)
                    val request =
                        WebSearchRequest(
                            query = input.readUTF(),
                            maxResults = input.readInt(),
                            sensitivity = EgressSensitivity.valueOf(input.readUTF()),
                        )
                    require(input.available() == 0) {
                        "web evidence search payload has trailing bytes"
                    }
                    request
                }
            } finally {
                bytes.fill(0)
            }
        }.getOrNull()
    }
}

internal object WebSearchEvidenceHitsCodec {
    const val MAX_BYTES = 1024 * 1024
    const val MAX_ENCODED_CHARS = 1398104

    fun encode(
        hits: List<WebSearchHit>,
    ): String {
        require(hits.size <= WebSearchRequest.MAX_RESULTS)
        val bytes =
            ByteArrayOutputStream().use { buffer ->
                DataOutputStream(buffer).use { output ->
                    output.writeInt(1)
                    output.writeInt(hits.size)
                    hits.forEach { hit ->
                        output.writeUTF(hit.title)
                        output.writeUTF(hit.url)
                        output.writeUTF(hit.snippet)
                        output.writeBoolean(hit.score != null)
                        hit.score?.let(output::writeDouble)
                        output.writeUTF(hit.trust.name)
                    }
                }
                buffer.toByteArray()
            }
        require(bytes.size <= MAX_BYTES) {
            "web search evidence hits exceed bound"
        }
        return try {
            Base64.getEncoder().encodeToString(bytes)
        } finally {
            bytes.fill(0)
        }
    }

    fun decode(
        encoded: String,
    ): List<WebSearchHit>? {
        if (encoded.isEmpty() || encoded.length > MAX_ENCODED_CHARS) {
            return null
        }
        val bytes =
            runCatching { Base64.getDecoder().decode(encoded) }
                .getOrNull()
                ?: return null
        if (bytes.size > MAX_BYTES) {
            bytes.fill(0)
            return null
        }
        return try {
            runCatching {
                DataInputStream(ByteArrayInputStream(bytes)).use { input ->
                    require(input.readInt() == 1)
                    val count = input.readInt()
                    require(count in 0..WebSearchRequest.MAX_RESULTS)
                    val hits =
                        buildList(count) {
                            repeat(count) {
                                val title = input.readUTF()
                                val url = input.readUTF()
                                val snippet = input.readUTF()
                                val score =
                                    if (input.readBoolean()) {
                                        input.readDouble()
                                    } else {
                                        null
                                    }
                                val trust = EvidenceContentTrust.valueOf(input.readUTF())
                                add(
                                    WebSearchHit(
                                        title = title,
                                        url = url,
                                        snippet = snippet,
                                        score = score,
                                        trust = trust,
                                    )
                                )
                            }
                        }
                    require(input.available() == 0) {
                        "web search evidence hits have trailing bytes"
                    }
                    hits
                }
            }.getOrNull()
        } finally {
            bytes.fill(0)
        }
    }
}

class WebEvidenceSearchExecutor(
    private val store: WebEvidenceStore,
    private val searcher: SafeWebSearcher,
    private val policy: WebSearchPolicy,
    private val canonicalContract: OperationContractDescriptor,
) : OperationExecutionPort {
    private val lock = Any()

    init {
        require(canonicalContract.operationId == WebEvidenceOperations.SEARCH)
        require(canonicalContract.capabilityId == WebEvidenceOperations.CAPABILITY_ID)
        require(canonicalContract.canonicalExecutor == WebEvidenceOperations.EXECUTOR_ID)
        require(canonicalContract.executionProfile == ExecutionProfile.OBSERVATIONAL_WRITE)
        require(canonicalContract.exposure == ContractExposure.BINDABLE)
    }

    override val descriptor =
        OperationBindingDescriptor(
            operationId = canonicalContract.operationId,
            canonicalExecutor = canonicalContract.canonicalExecutor,
            executionProfile = canonicalContract.executionProfile,
        )

    fun execute(
        preparedOperation: PreparedOperation,
        cancellationSignal: CancellationSignal,
    ): WebEvidenceSearchExecution = synchronized(lock) {
        if (preparedOperation.contract != canonicalContract) {
            return@synchronized WebEvidenceSearchExecution.RejectedContract
        }
        if (preparedOperation.dependencyStamps.asCanonicalList().isNotEmpty()) {
            return@synchronized WebEvidenceSearchExecution.RejectedDependency
        }
        val request =
            WebEvidenceSearchPayload.decode(preparedOperation.payload)
                ?: return@synchronized WebEvidenceSearchExecution.RejectedPayload

        val existing =
            store.findSearchByIdempotencyKey(preparedOperation.idempotencyKey)
        if (existing != null) {
            return@synchronized if (
                existing.payloadFingerprint == preparedOperation.payloadFingerprint
            ) {
                WebEvidenceSearchExecution.Applied(
                    outcome =
                        WebSearchEvidenceRecordOutcome.Committed(
                            record = existing,
                            replayed = true,
                        ),
                    searchResponse = null,
                )
            } else {
                WebEvidenceSearchExecution.IdentityConflict(
                    WebSearchEvidenceRecordOutcome.RejectedIdentityConflict(
                        existing = existing,
                        idempotencyKey = preparedOperation.idempotencyKey,
                    )
                )
            }
        }

        if (!policy.allows(request)) {
            return@synchronized WebEvidenceSearchExecution.SearchFailed(
                WebSearchResult.Failure(
                    WebSearchFailureKind.POLICY_EGRESS_BLOCKED
                )
            )
        }

        when (
            val result =
                searcher.search(
                    request = request,
                    policy = policy,
                    cancellationSignal = cancellationSignal,
                )
        ) {
            is WebSearchResult.Failure ->
                WebEvidenceSearchExecution.SearchFailed(result)

            is WebSearchResult.Success -> {
                val response = result.response
                if (!responseMatches(request, response)) {
                    return@synchronized WebEvidenceSearchExecution.SearchFailed(
                        WebSearchResult.Failure(
                            WebSearchFailureKind.INVALID_RESPONSE
                        )
                    )
                }

                when (
                    val outcome =
                        store.recordSearch(
                            request = request,
                            response = response,
                            idempotencyKey = preparedOperation.idempotencyKey,
                            payloadFingerprint = preparedOperation.payloadFingerprint,
                        )
                ) {
                    is WebSearchEvidenceRecordOutcome.Committed ->
                        WebEvidenceSearchExecution.Applied(
                            outcome = outcome,
                            searchResponse = response,
                        )

                    is WebSearchEvidenceRecordOutcome.RejectedIdentityConflict ->
                        WebEvidenceSearchExecution.IdentityConflict(outcome)
                }
            }
        }
    }

    private fun responseMatches(
        request: WebSearchRequest,
        response: WebSearchResponse,
    ): Boolean =
        response.query == request.query &&
            response.hits.size <= request.maxResults &&
            response.hits.size <= policy.maxResults &&
            response.policyVersion == policy.policyVersion &&
            response.hits.all {
                it.trust == EvidenceContentTrust.UNTRUSTED_CONTENT
            }
}
