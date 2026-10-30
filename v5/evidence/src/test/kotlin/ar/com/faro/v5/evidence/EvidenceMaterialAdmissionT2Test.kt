package ar.com.faro.v5.evidence

import ar.com.faro.v5.kernel.contracts.EgressSensitivity
import ar.com.faro.v5.kernel.contracts.IdempotencyKey
import ar.com.faro.v5.kernel.contracts.PayloadFingerprint
import ar.com.faro.v5.kernel.contracts.SingleProcessMaterialWriteGate
import java.nio.file.Path
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** C117: two staged Evidence owners, not yet host-wide material-write admission. */
class EvidenceMaterialAdmissionT2Test {
    @TempDir lateinit var directory: Path

    private fun commercialFile() = directory.resolve("commercial-gated.db").toString()
    private fun webFile() = directory.resolve("web-gated.db").toString()

    private fun commercialRecord(store: CommercialEvidenceStore) =
        store.record(
            observation = CommercialPriceObservation(
                productKey = CommercialProductKey("product:milk"),
                merchant = CommercialMerchant("shop"),
                currencyCode = CommercialCurrencyCode("ARS"),
                amountMinor = 12345L,
                sourceKind = CommercialEvidenceSourceKind.MANUAL,
                sourceRef = "receipt:test",
                observedAtEpochMillis = 10L,
            ),
            idempotencyKey = IdempotencyKey("commercial-gated-1"),
            payloadFingerprint = PayloadFingerprint("a".repeat(64)),
        )

    private fun webRecord(store: WebEvidenceStore) =
        store.record(
            source = WebFetchedSource(
                requestedUrl = "https://example.com/gated",
                finalUrl = "https://example.com/gated",
                method = WebFetchMethod.HEAD,
                statusCode = 200,
                mediaType = null,
                redirectCount = 0,
                acquiredAtEpochMillis = 10L,
                contentSha256Hex = null,
                body = null,
                policyVersion = "test-p1",
                fetcherVersion = "test-f1",
            ),
            idempotencyKey = IdempotencyKey("web-gated-1"),
            payloadFingerprint = PayloadFingerprint("b".repeat(64)),
        )

    private fun webSearch(store: WebEvidenceStore) =
        store.recordSearch(
            request = WebSearchRequest(
                query = "milk",
                maxResults = 1,
                sensitivity = EgressSensitivity.PUBLIC,
            ),
            response = WebSearchResponse(
                query = "milk",
                hits = emptyList(),
                providerId = "test-provider",
                providerRequestId = null,
                acquiredAtEpochMillis = 12L,
                policyVersion = "test-p1",
                adapterVersion = "test-a1",
            ),
            idempotencyKey = IdempotencyKey("search-gated-1"),
            payloadFingerprint = PayloadFingerprint("c".repeat(64)),
        )

    @Test fun closedGateRejectsCommercialOwnerMutationBeforeAnyRow() {
        val gate = SingleProcessMaterialWriteGate()
        CommercialEvidenceStore(commercialFile(), materialWriteGate = gate).use { store ->
            gate.closeMaterialWriteAdmission()
            val error = assertThrows(IllegalStateException::class.java) {
                commercialRecord(store)
            }
            assertTrue(error.message!!.contains("material writes are suspended"))
            assertEquals(0L, store.recordCount())
            gate.openMaterialWriteAdmission()
            assertTrue(commercialRecord(store) is CommercialEvidenceRecordOutcome.Committed)
            assertEquals(1L, store.recordCount())
        }
    }

    @Test fun closedGateRejectsBothWebEvidenceMutationRoutes() {
        val gate = SingleProcessMaterialWriteGate()
        WebEvidenceStore(webFile(), materialWriteGate = gate).use { store ->
            gate.closeMaterialWriteAdmission()
            assertThrows(IllegalStateException::class.java) { webRecord(store) }
            assertThrows(IllegalStateException::class.java) { webSearch(store) }
            assertEquals(0L, store.recordCount())
            assertEquals(0L, store.searchRecordCount())
            gate.openMaterialWriteAdmission()
            assertTrue(webRecord(store) is WebEvidenceRecordOutcome.Committed)
            assertTrue(webSearch(store) is WebSearchEvidenceRecordOutcome.Committed)
            assertEquals(1L, store.recordCount())
            assertEquals(1L, store.searchRecordCount())
        }
    }

    @Test fun exclusiveMaintenanceLeaseBlocksBothOwnersUntilReleased() {
        val gate = SingleProcessMaterialWriteGate()
        CommercialEvidenceStore(commercialFile(), materialWriteGate = gate).use { commercial ->
            WebEvidenceStore(webFile(), materialWriteGate = gate).use { web ->
                val entered = CountDownLatch(1)
                val release = CountDownLatch(1)
                val attempted = CountDownLatch(2)
                val pool = Executors.newFixedThreadPool(3)
                try {
                    val maintenance = pool.submit(Callable<String> {
                        gate.withQuiescedMaterialWrites {
                            entered.countDown()
                            check(release.await(5, TimeUnit.SECONDS))
                            "released"
                        }
                    })
                    assertTrue(entered.await(5, TimeUnit.SECONDS))
                    val cWrite = pool.submit(Callable<CommercialEvidenceRecordOutcome> {
                        attempted.countDown()
                        commercialRecord(commercial)
                    })
                    val wWrite = pool.submit(Callable<WebEvidenceRecordOutcome> {
                        attempted.countDown()
                        webRecord(web)
                    })
                    assertTrue(attempted.await(5, TimeUnit.SECONDS))
                    assertThrows(TimeoutException::class.java) { cWrite.get(150, TimeUnit.MILLISECONDS) }
                    assertThrows(TimeoutException::class.java) { wWrite.get(150, TimeUnit.MILLISECONDS) }
                    assertEquals(0L, commercial.recordCount())
                    assertEquals(0L, web.recordCount())
                    release.countDown()
                    assertEquals("released", maintenance.get(5, TimeUnit.SECONDS))
                    assertTrue(cWrite.get(5, TimeUnit.SECONDS) is CommercialEvidenceRecordOutcome.Committed)
                    assertTrue(wWrite.get(5, TimeUnit.SECONDS) is WebEvidenceRecordOutcome.Committed)
                    assertEquals(1L, commercial.recordCount())
                    assertEquals(1L, web.recordCount())
                } finally {
                    release.countDown()
                    pool.shutdownNow()
                }
            }
        }
    }

    @Test fun maintenanceOwnerCanReenterTheSameSharedGate() {
        val gate = SingleProcessMaterialWriteGate()
        CommercialEvidenceStore(commercialFile(), materialWriteGate = gate).use { commercial ->
            WebEvidenceStore(webFile(), materialWriteGate = gate).use { web ->
                gate.withQuiescedMaterialWrites {
                    assertTrue(commercialRecord(commercial) is CommercialEvidenceRecordOutcome.Committed)
                    assertTrue(webRecord(web) is WebEvidenceRecordOutcome.Committed)
                    assertTrue(webSearch(web) is WebSearchEvidenceRecordOutcome.Committed)
                }
                assertEquals(1L, commercial.recordCount())
                assertEquals(1L, web.recordCount())
                assertEquals(1L, web.searchRecordCount())
            }
        }
    }
}
