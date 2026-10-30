package ar.com.faro.v5.evidence

import ar.com.faro.v5.kernel.contracts.CancellationSignal
import ar.com.faro.v5.kernel.contracts.ContractExposure
import ar.com.faro.v5.kernel.contracts.ContractFingerprint
import ar.com.faro.v5.kernel.contracts.ContractVersion
import ar.com.faro.v5.kernel.contracts.DependencyKey
import ar.com.faro.v5.kernel.contracts.DependencyStamp
import ar.com.faro.v5.kernel.contracts.DependencyStampSet
import ar.com.faro.v5.kernel.contracts.DependencyStampValue
import ar.com.faro.v5.kernel.contracts.ExecutionProfile
import ar.com.faro.v5.kernel.contracts.IdempotencyKey
import ar.com.faro.v5.kernel.contracts.OperationContractDescriptor
import ar.com.faro.v5.kernel.contracts.PreparedOperation
import ar.com.faro.v5.kernel.contracts.AuthorityBoundaryId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger

class WebEvidenceStoreTest {
    @Test
    fun `successful fetch persists provenance metadata without retaining raw body`(
        @TempDir tempDir: Path,
    ) {
        val calls = AtomicInteger(0)
        val fetcher =
            SafeHttpFetcher { request, policy, _ ->
                calls.incrementAndGet()
                WebFetchResult.Success(
                    source(
                        request = request,
                        policy = policy,
                        bodyText = "untrusted external content",
                    )
                )
            }

        WebEvidenceStore(tempDir.resolve("web-evidence.db").toString()).use { store ->
            val executor = WebEvidenceFetchExecutor(
                store = store,
                fetcher = fetcher,
                policy = POLICY,
                canonicalContract = FETCH_CONTRACT,
            )
            val prepared = prepared("https://example.com/page", "fetch-1")

            val execution =
                executor.execute(prepared, CancellationSignal { false })

            val applied =
                assertInstanceOf(
                    WebEvidenceFetchExecution.Applied::class.java,
                    execution,
                )
            assertEquals(false, applied.outcome.replayed)
            assertEquals(1, calls.get())
            assertEquals(1L, store.recordCount())
            assertEquals("ok", store.integrityCheck())

            val record = applied.outcome.record
            assertEquals("https://example.com/page", record.requestedUrl)
            assertEquals("https://example.com/page", record.finalUrl)
            assertEquals(WebFetchMethod.GET, record.method)
            assertEquals(200, record.statusCode)
            assertEquals("text/plain", record.mediaType)
            assertEquals(26, record.contentBytes)
            assertTrue(record.contentSha256Hex != null)
            assertEquals(EvidenceContentTrust.UNTRUSTED_CONTENT, record.trust)
            assertEquals(false, record.bodyRetained)
            assertEquals(
                "untrusted external content",
                applied.fetchedSource?.body?.copyBytes()?.decodeToString(),
            )
            assertTrue(!record.toString().contains("example.com"))
            assertTrue(!record.toString().contains("untrusted external content"))
        }
    }

    @Test
    fun `exact replay returns committed Evidence without another network fetch`(
        @TempDir tempDir: Path,
    ) {
        val calls = AtomicInteger(0)
        val fetcher =
            SafeHttpFetcher { request, policy, _ ->
                calls.incrementAndGet()
                WebFetchResult.Success(source(request, policy, "once"))
            }

        WebEvidenceStore(tempDir.resolve("web-evidence.db").toString()).use { store ->
            val executor = WebEvidenceFetchExecutor(store, fetcher, POLICY, FETCH_CONTRACT)
            val prepared = prepared("https://example.com/replay", "same-key")

            val first = executor.execute(prepared, CancellationSignal { false })
            val second = executor.execute(prepared, CancellationSignal { false })

            assertInstanceOf(WebEvidenceFetchExecution.Applied::class.java, first)
            val replay =
                assertInstanceOf(
                    WebEvidenceFetchExecution.Applied::class.java,
                    second,
                )
            assertEquals(true, replay.outcome.replayed)
            assertNull(replay.fetchedSource)
            assertEquals(1, calls.get())
            assertEquals(1L, store.recordCount())
        }
    }

    @Test
    fun `same idempotency key with different payload conflicts before network`(
        @TempDir tempDir: Path,
    ) {
        val calls = AtomicInteger(0)
        val fetcher =
            SafeHttpFetcher { request, policy, _ ->
                calls.incrementAndGet()
                WebFetchResult.Success(source(request, policy, "content"))
            }

        WebEvidenceStore(tempDir.resolve("web-evidence.db").toString()).use { store ->
            val executor = WebEvidenceFetchExecutor(store, fetcher, POLICY, FETCH_CONTRACT)
            val first = prepared("https://example.com/a", "conflict-key")
            val conflicting = prepared("https://example.com/b", "conflict-key")

            assertInstanceOf(
                WebEvidenceFetchExecution.Applied::class.java,
                executor.execute(first, CancellationSignal { false }),
            )
            val conflict =
                executor.execute(conflicting, CancellationSignal { false })

            assertInstanceOf(
                WebEvidenceFetchExecution.IdentityConflict::class.java,
                conflict,
            )
            assertEquals(1, calls.get())
            assertEquals(1L, store.recordCount())
        }
    }

    @Test
    fun `fetch failure performs no Evidence write`(
        @TempDir tempDir: Path,
    ) {
        val fetcher =
            SafeHttpFetcher { _, _, _ ->
                WebFetchResult.Failure(WebFetchFailureKind.TIMEOUT)
            }

        WebEvidenceStore(tempDir.resolve("web-evidence.db").toString()).use { store ->
            val executor = WebEvidenceFetchExecutor(store, fetcher, POLICY, FETCH_CONTRACT)
            val result =
                executor.execute(
                    prepared("https://example.com/timeout", "timeout-key"),
                    CancellationSignal { false },
                )

            val failure =
                assertInstanceOf(
                    WebEvidenceFetchExecution.FetchFailed::class.java,
                    result,
                )
            assertEquals(WebFetchFailureKind.TIMEOUT, failure.failure.kind)
            assertEquals(0L, store.recordCount())
        }
    }

    @Test
    fun `mismatched fetched provenance is rejected without write`(
        @TempDir tempDir: Path,
    ) {
        val fetcher =
            SafeHttpFetcher { _, policy, _ ->
                WebFetchResult.Success(
                    source(
                        request = WebFetchRequest("https://other.example/page"),
                        policy = policy,
                        bodyText = "hostile mismatch",
                    )
                )
            }

        WebEvidenceStore(tempDir.resolve("web-evidence.db").toString()).use { store ->
            val executor = WebEvidenceFetchExecutor(store, fetcher, POLICY, FETCH_CONTRACT)
            val result =
                executor.execute(
                    prepared("https://example.com/page", "mismatch-key"),
                    CancellationSignal { false },
                )

            val failure =
                assertInstanceOf(
                    WebEvidenceFetchExecution.FetchFailed::class.java,
                    result,
                )
            assertEquals(WebFetchFailureKind.INVALID_RESPONSE, failure.failure.kind)
            assertEquals(0L, store.recordCount())
        }
    }

    @Test
    fun `digest mismatch from fetch adapter is rejected before Evidence commit`(
        @TempDir tempDir: Path,
    ) {
        val fetcher =
            SafeHttpFetcher { request, policy, _ ->
                val valid = source(request, policy, "tampered")
                WebFetchResult.Success(
                    WebFetchedSource(
                        requestedUrl = valid.requestedUrl,
                        finalUrl = valid.finalUrl,
                        method = valid.method,
                        statusCode = valid.statusCode,
                        mediaType = valid.mediaType,
                        redirectCount = valid.redirectCount,
                        acquiredAtEpochMillis = valid.acquiredAtEpochMillis,
                        contentSha256Hex = "0".repeat(64),
                        body = valid.body,
                        policyVersion = valid.policyVersion,
                        fetcherVersion = valid.fetcherVersion,
                    )
                )
            }

        WebEvidenceStore(tempDir.resolve("web-evidence.db").toString()).use { store ->
            val executor = WebEvidenceFetchExecutor(store, fetcher, POLICY, FETCH_CONTRACT)
            val result =
                executor.execute(
                    prepared("https://example.com/digest", "digest-key"),
                    CancellationSignal { false },
                )

            val failure =
                assertInstanceOf(
                    WebEvidenceFetchExecution.FetchFailed::class.java,
                    result,
                )
            assertEquals(WebFetchFailureKind.INVALID_RESPONSE, failure.failure.kind)
            assertEquals(0L, store.recordCount())
        }
    }

    @Test
    fun `dependency stamps and wrong contract are rejected before fetch`(
        @TempDir tempDir: Path,
    ) {
        val calls = AtomicInteger(0)
        val fetcher =
            SafeHttpFetcher { request, policy, _ ->
                calls.incrementAndGet()
                WebFetchResult.Success(source(request, policy, "unexpected"))
            }

        WebEvidenceStore(tempDir.resolve("web-evidence.db").toString()).use { store ->
            val executor = WebEvidenceFetchExecutor(store, fetcher, POLICY, FETCH_CONTRACT)
            val base = prepared("https://example.com/dependency", "dependency-key")
            val withDependency =
                PreparedOperation(
                    contract = base.contract,
                    payload = base.payload,
                    dependencyStamps =
                        DependencyStampSet.of(
                            DependencyStamp(
                                authorityBoundary = AuthorityBoundaryId("reality.finance"),
                                dependencyKey = DependencyKey("finance:any"),
                                observedValue = DependencyStampValue("revision:1"),
                            )
                        ),
                    idempotencyKey = base.idempotencyKey,
                )

            assertEquals(
                WebEvidenceFetchExecution.RejectedDependency,
                executor.execute(withDependency, CancellationSignal { false }),
            )

            val wrong =
                PreparedOperation(
                    contract = WRONG_CONTRACT,
                    payload = base.payload,
                    dependencyStamps = DependencyStampSet.empty(),
                    idempotencyKey = IdempotencyKey("wrong-contract"),
                )
            assertEquals(
                WebEvidenceFetchExecution.RejectedContract,
                executor.execute(wrong, CancellationSignal { false }),
            )
            assertEquals(0, calls.get())
            assertEquals(0L, store.recordCount())
        }
    }

    private fun prepared(
        url: String,
        key: String,
    ): PreparedOperation =
        PreparedOperation(
            contract = FETCH_CONTRACT,
            payload = WebEvidenceFetchPayload.payload(WebFetchRequest(url)),
            dependencyStamps = DependencyStampSet.empty(),
            idempotencyKey = IdempotencyKey(key),
        )

    private fun source(
        request: WebFetchRequest,
        policy: WebFetchPolicy,
        bodyText: String,
    ): WebFetchedSource {
        val bytes = bodyText.encodeToByteArray()
        return WebFetchedSource(
            requestedUrl = request.url,
            finalUrl = request.url,
            method = request.method,
            statusCode = 200,
            mediaType = "text/plain",
            redirectCount = 0,
            acquiredAtEpochMillis = 1234,
            contentSha256Hex = sha256(bytes),
            body =
                ar.com.faro.v5.kernel.contracts.CanonicalPayload.fromEncoded(
                    "text/plain",
                    bytes,
                ),
            policyVersion = policy.policyVersion,
            fetcherVersion = policy.fetcherVersion,
        )
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { byte -> "%02x".format(byte) }

    private companion object {
        val POLICY =
            WebFetchPolicy(
                maxWireBytes = 4096,
                maxDecodedBytes = 4096,
                maxRedirects = 3,
                allowedMediaTypes = setOf("text/plain", "text/html"),
                policyVersion = "p2-a4-test",
                fetcherVersion = "safe-http-test-v1",
            )

        val FETCH_CONTRACT =
            OperationContractDescriptor(
                operationId = WebEvidenceOperations.FETCH,
                capabilityId = WebEvidenceOperations.CAPABILITY_ID,
                canonicalExecutor = WebEvidenceOperations.EXECUTOR_ID,
                executionProfile = ExecutionProfile.OBSERVATIONAL_WRITE,
                contractVersion = ContractVersion("metadata-v1"),
                contractFingerprint =
                    ContractFingerprint(
                        "7b507c212d81e5ea59af63f0f24d3b7548656a6ffae29c918496144a2c8c74cc"
                    ),
                exposure = ContractExposure.BINDABLE,
            )

        val WRONG_CONTRACT =
            OperationContractDescriptor(
                operationId = ar.com.faro.v5.kernel.contracts.OperationId("web.search"),
                capabilityId = WebEvidenceOperations.CAPABILITY_ID,
                canonicalExecutor = WebEvidenceOperations.EXECUTOR_ID,
                executionProfile = ExecutionProfile.OBSERVATIONAL_WRITE,
                contractVersion = ContractVersion("metadata-v1"),
                contractFingerprint =
                    ContractFingerprint(
                        "15724a0d9c0f8c9c2df4fbf0e26868cc8f4d56f1537fb128f38a5bd3cd249ca2"
                    ),
                exposure = ContractExposure.BINDABLE,
            )
    }
}
