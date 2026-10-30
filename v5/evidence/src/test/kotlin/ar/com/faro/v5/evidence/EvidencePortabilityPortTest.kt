package ar.com.faro.v5.evidence

import ar.com.faro.v5.kernel.contracts.CanonicalPayload
import ar.com.faro.v5.kernel.contracts.EgressSensitivity
import ar.com.faro.v5.kernel.contracts.IdempotencyKey
import ar.com.faro.v5.kernel.contracts.PayloadFingerprint
import java.nio.file.Path
import java.security.MessageDigest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class EvidencePortabilityPortTest {
    @Test
    fun `commercial portability projection excludes execution identities`(
        @TempDir tempDir: Path,
    ) {
        var nextEvidenceId = 0
        CommercialEvidenceStore(
            tempDir.resolve("commercial.db").toString(),
            clock = { 200L },
            evidenceIdFactory = {
                nextEvidenceId += 1
                CommercialEvidenceId("internal-evidence-id-" + nextEvidenceId)
            },
        ).use { store ->
            store.record(
                observation =
                    CommercialPriceObservation(
                        productKey = CommercialProductKey("milk"),
                        merchant = CommercialMerchant("market"),
                        currencyCode = CommercialCurrencyCode("ARS"),
                        amountMinor = 2_500L,
                        sourceKind = CommercialEvidenceSourceKind.MANUAL,
                        sourceRef = "manual:ticket",
                        observedAtEpochMillis = 100L,
                    ),
                idempotencyKey = IdempotencyKey("internal-idempotency-key"),
                payloadFingerprint = PayloadFingerprint("internal-payload-fingerprint"),
            )

            val first = store.portability.snapshot()
            assertEquals(1, first.records.size)
            assertEquals("milk", first.records.single().productKey)
            assertFalse(first.toString().contains("internal-evidence-id"))
            assertFalse(first.toString().contains("internal-idempotency-key"))
            assertFalse(first.toString().contains("internal-payload-fingerprint"))

            store.record(
                observation =
                    CommercialPriceObservation(
                        productKey = CommercialProductKey("bread"),
                        merchant = CommercialMerchant("market"),
                        currencyCode = CommercialCurrencyCode("ARS"),
                        amountMinor = 1_500L,
                        sourceKind = CommercialEvidenceSourceKind.TOOL,
                        sourceRef = "tool:quote",
                        observedAtEpochMillis = 300L,
                    ),
                idempotencyKey = IdempotencyKey("internal-idempotency-key-2"),
                payloadFingerprint = PayloadFingerprint("internal-payload-fingerprint-2"),
            )
            val second = store.portability.snapshot()
            assertNotEquals(first.sourceWatermark, second.sourceWatermark)
            assertEquals(2, second.records.size)
        }
    }

    @Test
    fun `web portability projection drops provider request and usage internals`(
        @TempDir tempDir: Path,
    ) {
        WebEvidenceStore(tempDir.resolve("web.db").toString()).use { store ->
            val bodyBytes = "hello web".toByteArray()
            val body =
                CanonicalPayload.fromEncoded(
                    "web.portability.test",
                    bodyBytes,
                )
            val bodyDigest =
                MessageDigest.getInstance("SHA-256")
                    .digest(bodyBytes)
                    .joinToString("") { "%02x".format(it) }

            store.record(
                source =
                    WebFetchedSource(
                        requestedUrl = "https://example.com/a",
                        finalUrl = "https://example.com/a",
                        method = WebFetchMethod.GET,
                        statusCode = 200,
                        mediaType = "text/plain",
                        redirectCount = 0,
                        acquiredAtEpochMillis = 400L,
                        contentSha256Hex = bodyDigest,
                        body = body,
                        policyVersion = "policy-v1",
                        fetcherVersion = "fetcher-v1",
                    ),
                idempotencyKey = IdempotencyKey("internal-web-idempotency"),
                payloadFingerprint = PayloadFingerprint("internal-web-payload"),
            )

            val request =
                WebSearchRequest(
                    query = "milk price",
                    maxResults = 1,
                    sensitivity = EgressSensitivity.PUBLIC,
                )
            store.recordSearch(
                request = request,
                response =
                    WebSearchResponse(
                        query = request.query,
                        hits =
                            listOf(
                                WebSearchHit(
                                    title = "Milk",
                                    url = "https://example.com/milk",
                                    snippet = "public snippet",
                                    score = 0.9,
                                )
                            ),
                        providerId = "test-provider",
                        providerRequestId = "provider-secret-request-id",
                        providerUsage = WebProviderUsage("credit", 99L),
                        acquiredAtEpochMillis = 500L,
                        policyVersion = "search-policy-v1",
                        adapterVersion = "adapter-v1",
                    ),
                idempotencyKey = IdempotencyKey("internal-search-idempotency"),
                payloadFingerprint = PayloadFingerprint("internal-search-payload"),
            )

            val snapshot = store.portability.snapshot()
            assertEquals(1, snapshot.fetchRecords.size)
            assertEquals(1, snapshot.searchRecords.size)
            assertEquals("test-provider", snapshot.searchRecords.single().providerId)
            val rendered = snapshot.toString()
            assertFalse(rendered.contains("provider-secret-request-id"))
            assertFalse(rendered.contains("credit"))
            assertFalse(rendered.contains("internal-web-idempotency"))
            assertFalse(rendered.contains("internal-web-payload"))
            assertFalse(rendered.contains("internal-search-idempotency"))
            assertFalse(rendered.contains("internal-search-payload"))
            assertTrue(snapshot.sourceWatermark.matches(Regex("[0-9a-f]{64}")))
        }
    }
}
