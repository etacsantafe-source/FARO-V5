package ar.com.faro.v5.evidence

import androidx.sqlite.execSQL
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import ar.com.faro.v5.kernel.contracts.AuthorityBoundaryId
import ar.com.faro.v5.kernel.contracts.CancellationSignal
import ar.com.faro.v5.kernel.contracts.ContractExposure
import ar.com.faro.v5.kernel.contracts.ContractFingerprint
import ar.com.faro.v5.kernel.contracts.ContractVersion
import ar.com.faro.v5.kernel.contracts.DependencyKey
import ar.com.faro.v5.kernel.contracts.DependencyStamp
import ar.com.faro.v5.kernel.contracts.DependencyStampSet
import ar.com.faro.v5.kernel.contracts.DependencyStampValue
import ar.com.faro.v5.kernel.contracts.EgressSensitivity
import ar.com.faro.v5.kernel.contracts.ExecutionProfile
import ar.com.faro.v5.kernel.contracts.IdempotencyKey
import ar.com.faro.v5.kernel.contracts.OperationContractDescriptor
import ar.com.faro.v5.kernel.contracts.PreparedOperation
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class WebEvidenceSearchStoreTest {
    @Test
    fun `successful search persists bounded Evidence and exact reopen replay avoids network`(
        @TempDir tempDir: Path,
    ) {
        val calls = AtomicInteger(0)
        val file = tempDir.resolve("web-evidence.db").toString()
        val searcher = successfulSearcher(calls)

        WebEvidenceStore(file).use { store ->
            val executor =
                WebEvidenceSearchExecutor(
                    store = store,
                    searcher = searcher,
                    policy = POLICY,
                    canonicalContract = SEARCH_CONTRACT,
                )
            val execution =
                executor.execute(
                    prepared("faro architecture", "search-1"),
                    CancellationSignal { false },
                )
            val applied =
                assertInstanceOf(
                    WebEvidenceSearchExecution.Applied::class.java,
                    execution,
                )
            assertEquals(false, applied.outcome.replayed)
            assertEquals(1, calls.get())
            assertEquals(1L, store.searchRecordCount())
            assertEquals("ok", store.integrityCheck())
            assertEquals(1, applied.outcome.record.hits.size)
            assertEquals("https://example.com/faro", applied.outcome.record.hits.single().url)
            assertEquals(WebProviderUsage("credit", 1), applied.outcome.record.providerUsage)
            assertEquals(EvidenceContentTrust.UNTRUSTED_CONTENT, applied.outcome.record.trust)
        }

        WebEvidenceStore(file).use { reopened ->
            val executor =
                WebEvidenceSearchExecutor(
                    store = reopened,
                    searcher = searcher,
                    policy = POLICY,
                    canonicalContract = SEARCH_CONTRACT,
                )
            val replay =
                assertInstanceOf(
                    WebEvidenceSearchExecution.Applied::class.java,
                    executor.execute(
                        prepared("faro architecture", "search-1"),
                        CancellationSignal { false },
                    ),
                )
            assertEquals(true, replay.outcome.replayed)
            assertNull(replay.searchResponse)
            assertEquals(1, calls.get())
            assertEquals(1L, reopened.searchRecordCount())
            assertEquals("faro architecture", replay.outcome.record.query)
            assertEquals("bounded public snippet", replay.outcome.record.hits.single().snippet)
        }
    }

    @Test
    fun `same idempotency key with different search payload conflicts before provider`(
        @TempDir tempDir: Path,
    ) {
        val calls = AtomicInteger(0)
        WebEvidenceStore(tempDir.resolve("web-evidence.db").toString()).use { store ->
            val executor =
                WebEvidenceSearchExecutor(
                    store,
                    successfulSearcher(calls),
                    POLICY,
                    SEARCH_CONTRACT,
                )
            assertInstanceOf(
                WebEvidenceSearchExecution.Applied::class.java,
                executor.execute(
                    prepared("first public query", "same-key"),
                    CancellationSignal { false },
                ),
            )

            val conflict =
                executor.execute(
                    prepared("different public query", "same-key"),
                    CancellationSignal { false },
                )

            assertInstanceOf(
                WebEvidenceSearchExecution.IdentityConflict::class.java,
                conflict,
            )
            assertEquals(1, calls.get())
            assertEquals(1L, store.searchRecordCount())
        }
    }

    @Test
    fun `provider failure and mismatched response perform zero Evidence writes`(
        @TempDir tempDir: Path,
    ) {
        WebEvidenceStore(tempDir.resolve("web-evidence.db").toString()).use { store ->
            val failedExecutor =
                WebEvidenceSearchExecutor(
                    store = store,
                    searcher =
                        SafeWebSearcher { _, _, _ ->
                            WebSearchResult.Failure(WebSearchFailureKind.TIMEOUT)
                        },
                    policy = POLICY,
                    canonicalContract = SEARCH_CONTRACT,
                )
            val failed =
                failedExecutor.execute(
                    prepared("timeout query", "timeout-key"),
                    CancellationSignal { false },
                )
            assertInstanceOf(
                WebEvidenceSearchExecution.SearchFailed::class.java,
                failed,
            )
            assertEquals(0L, store.searchRecordCount())

            val mismatched =
                WebEvidenceSearchExecutor(
                    store = store,
                    searcher =
                        SafeWebSearcher { request, policy, _ ->
                            WebSearchResult.Success(
                                response(
                                    request = request,
                                    policy = policy,
                                    query = "different query",
                                )
                            )
                        },
                    policy = POLICY,
                    canonicalContract = SEARCH_CONTRACT,
                ).execute(
                    prepared("expected query", "mismatch-key"),
                    CancellationSignal { false },
                )
            val mismatchFailure =
                assertInstanceOf(
                    WebEvidenceSearchExecution.SearchFailed::class.java,
                    mismatched,
                )
            assertEquals(
                WebSearchFailureKind.INVALID_RESPONSE,
                mismatchFailure.failure.kind,
            )
            assertEquals(0L, store.searchRecordCount())
        }
    }

    @Test
    fun `non public request and dependencies are rejected before provider`(
        @TempDir tempDir: Path,
    ) {
        val calls = AtomicInteger(0)
        WebEvidenceStore(tempDir.resolve("web-evidence.db").toString()).use { store ->
            val executor =
                WebEvidenceSearchExecutor(
                    store,
                    successfulSearcher(calls),
                    POLICY,
                    SEARCH_CONTRACT,
                )

            val privateRequest =
                prepared(
                    query = "private query",
                    key = "private-key",
                    sensitivity = EgressSensitivity.PRIVATE,
                )
            val blocked =
                assertInstanceOf(
                    WebEvidenceSearchExecution.SearchFailed::class.java,
                    executor.execute(privateRequest, CancellationSignal { false }),
                )
            assertEquals(
                WebSearchFailureKind.POLICY_EGRESS_BLOCKED,
                blocked.failure.kind,
            )

            val base = prepared("dependency query", "dependency-key")
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
                WebEvidenceSearchExecution.RejectedDependency,
                executor.execute(withDependency, CancellationSignal { false }),
            )
            assertEquals(0, calls.get())
            assertEquals(0L, store.searchRecordCount())
        }
    }

    @Test
    fun `schema v1 migrates additively to search Evidence v2`(
        @TempDir tempDir: Path,
    ) {
        val file = tempDir.resolve("web-evidence.db").toString()
        WebEvidenceStore(file).use { store ->
            assertEquals("ok", store.integrityCheck())
        }

        val driver = BundledSQLiteDriver()
        val connection = driver.open(file)
        try {
            connection.execSQL("DROP TABLE web_search_evidence")
            connection.execSQL("PRAGMA user_version = 1")
        } finally {
            connection.close()
        }

        val calls = AtomicInteger(0)
        WebEvidenceStore(file).use { migrated ->
            assertEquals(0L, migrated.searchRecordCount())
            val executor =
                WebEvidenceSearchExecutor(
                    migrated,
                    successfulSearcher(calls),
                    POLICY,
                    SEARCH_CONTRACT,
                )
            assertInstanceOf(
                WebEvidenceSearchExecution.Applied::class.java,
                executor.execute(
                    prepared("migration query", "migration-key"),
                    CancellationSignal { false },
                ),
            )
            assertEquals(1L, migrated.searchRecordCount())
            assertEquals("ok", migrated.integrityCheck())
        }
        assertEquals(1, calls.get())
    }

    private fun successfulSearcher(
        calls: AtomicInteger,
    ): SafeWebSearcher =
        SafeWebSearcher { request, policy, _ ->
            calls.incrementAndGet()
            WebSearchResult.Success(response(request, policy))
        }

    private fun response(
        request: WebSearchRequest,
        policy: WebSearchPolicy,
        query: String = request.query,
    ): WebSearchResponse =
        WebSearchResponse(
            query = query,
            hits =
                listOf(
                    WebSearchHit(
                        title = "FARO source",
                        url = "https://example.com/faro",
                        snippet = "bounded public snippet",
                        score = 0.92,
                    )
                ),
            providerId = "test-search",
            providerRequestId = "req-123",
            providerUsage = WebProviderUsage("credit", 1),
            acquiredAtEpochMillis = 1234L,
            policyVersion = policy.policyVersion,
            adapterVersion = "test-search-v1",
        )

    private fun prepared(
        query: String,
        key: String,
        sensitivity: EgressSensitivity = EgressSensitivity.PUBLIC,
    ): PreparedOperation =
        PreparedOperation(
            contract = SEARCH_CONTRACT,
            payload =
                WebEvidenceSearchPayload.payload(
                    WebSearchRequest(
                        query = query,
                        maxResults = 5,
                        sensitivity = sensitivity,
                    )
                ),
            dependencyStamps = DependencyStampSet.empty(),
            idempotencyKey = IdempotencyKey(key),
        )

    private companion object {
        val POLICY =
            WebSearchPolicy(
                maxWireBytes = 128 * 1024,
                maxResults = 5,
                policyVersion = "p2-b3-test-v1",
            )

        val SEARCH_CONTRACT =
            OperationContractDescriptor(
                operationId = WebEvidenceOperations.SEARCH,
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
