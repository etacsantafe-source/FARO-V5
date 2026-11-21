package ar.com.faro.v5.maintenance

import ar.com.faro.v5.evidence.CommercialEligibility
import ar.com.faro.v5.evidence.CommercialEvidenceStagedTypedPortabilitySnapshot
import ar.com.faro.v5.evidence.CommercialEvidenceTypedTerms
import ar.com.faro.v5.evidence.CommercialLocalityKind
import ar.com.faro.v5.evidence.CommercialPaymentMethod
import ar.com.faro.v5.evidence.CommercialPresentationUnit
import ar.com.faro.v5.evidence.CommercialPromotionKind
import ar.com.faro.v5.evidence.PortableCommercialObservation
import ar.com.faro.v5.evidence.PortableCommercialTypedObservation
import ar.com.faro.v5.kernel.contracts.CanonicalHashBuilder
import java.nio.charset.StandardCharsets
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CommercialTypedPortabilityJsonCodecTest {
    private val codec = CommercialTypedPortabilityJsonCodec

    private fun record(
        product: String = "arroz",
        currency: String = "ARS",
        source: String = "manual",
        terms: CommercialEvidenceTypedTerms? = null,
    ) = PortableCommercialTypedObservation(
        observation = PortableCommercialObservation(
            productKey = product,
            merchant = "Comercio",
            currencyCode = currency,
            amountMinor = 120000,
            sourceKind = "MANUAL",
            sourceRef = source,
            observedAtEpochMillis = null,
            acquiredAtEpochMillis = 1000,
        ),
        terms = terms,
    )

    private fun snapshot(
        rows: List<PortableCommercialTypedObservation>,
    ): CommercialEvidenceStagedTypedPortabilitySnapshot {
        val sorted = rows.sortedWith(compareBy(
            { it.observation.productKey },
            { it.observation.merchant },
            { it.observation.currencyCode },
            { it.observation.observedAtEpochMillis ?: Long.MAX_VALUE },
            { it.observation.acquiredAtEpochMillis },
            { it.observation.amountMinor },
            { it.observation.sourceKind },
            { it.observation.sourceRef },
            { it.terms != null },
            { it.terms?.fingerprint() ?: "" },
        ))
        val hash = CanonicalHashBuilder.domain("faro-portability-commercial-v2")
            .string("schemaVersion", "2")
            .string("count", sorted.size.toString())
        sorted.forEachIndexed { index, row ->
            val p = "record[$index]."
            val r = row.observation
            hash.string(p + "productKey", r.productKey)
                .string(p + "merchant", r.merchant)
                .string(p + "currencyCode", r.currencyCode)
                .string(p + "amountMinor", r.amountMinor.toString())
                .string(p + "sourceKind", r.sourceKind)
                .string(p + "sourceRef", r.sourceRef)
                .string(p + "observed.present", (r.observedAtEpochMillis != null).toString())
                .string(p + "observed", r.observedAtEpochMillis?.toString() ?: "")
                .string(p + "acquired", r.acquiredAtEpochMillis.toString())
                .string(p + "terms.present", (row.terms != null).toString())
                .string(p + "terms.fingerprint", row.terms?.fingerprint() ?: "")
        }
        return CommercialEvidenceStagedTypedPortabilitySnapshot(2, hash.sha256Hex(), sorted)
    }

    private fun json(s: CommercialEvidenceStagedTypedPortabilitySnapshot) =
        Json.parseToJsonElement(String(codec.encode(s), StandardCharsets.UTF_8)).jsonObject

    @Test
    fun knownTermsPreserveAllFourteenFieldsWithoutLoss() {
        val t = CommercialEvidenceTypedTerms(
            localityKind = CommercialLocalityKind.KNOWN,
            localityCode = "AR-SF",
            issuedAtEpochMillis = 100,
            validFromEpochMillis = 120,
            validUntilEpochMillis = 200,
            presentationQuantityThousandths = 2500,
            presentationUnit = CommercialPresentationUnit.GRAM,
            packCount = 2,
            paymentMethod = CommercialPaymentMethod.CREDIT,
            eligibility = CommercialEligibility.RESTRICTED,
            conditions = "Solo martes",
            promotionKind = CommercialPromotionKind.DISCOUNT_PERCENT,
            promotionValue = 1500,
            promotionSourceRef = "ticket literal",
        )
        val root = json(snapshot(listOf(record(terms = t))))
        val fields = root.getValue("records").jsonArray.single().jsonObject.getValue("terms").jsonObject
        assertEquals(14, fields.size)
        assertEquals("AR-SF", fields.getValue("localityCode").jsonPrimitive.content)
        assertEquals("120", fields.getValue("validFromEpochMillis").jsonPrimitive.content)
        assertEquals("200", fields.getValue("validUntilEpochMillis").jsonPrimitive.content)
        assertEquals("2500", fields.getValue("presentationQuantityThousandths").jsonPrimitive.content)
        assertEquals("GRAM", fields.getValue("presentationUnit").jsonPrimitive.content)
        assertEquals("CREDIT", fields.getValue("paymentMethod").jsonPrimitive.content)
        assertEquals("RESTRICTED", fields.getValue("eligibility").jsonPrimitive.content)
        assertEquals("1500", fields.getValue("promotionValue").jsonPrimitive.content)
        assertEquals("ticket literal", fields.getValue("promotionSourceRef").jsonPrimitive.content)
    }

    @Test
    fun physicalNullAndExplicitUnknownRemainDifferent() {
        val root = json(snapshot(listOf(record("a", terms = null), record("b", terms = CommercialEvidenceTypedTerms.UNKNOWN))))
        val rows = root.getValue("records").jsonArray
        assertEquals(JsonNull, rows[0].jsonObject.getValue("terms"))
        val unknown = rows[1].jsonObject.getValue("terms").jsonObject
        assertEquals("UNKNOWN", unknown.getValue("localityKind").jsonPrimitive.content)
        assertEquals(JsonNull, unknown.getValue("localityCode"))
        assertEquals("UNKNOWN", unknown.getValue("promotionKind").jsonPrimitive.content)
    }

    @Test
    fun rejectsChangedWatermarkBeforeEmittingAnyBytes() {
        val original = snapshot(listOf(record()))
        assertThrows(IllegalArgumentException::class.java) {
            codec.encode(original.copy(sourceWatermark = "0".repeat(64)))
        }
    }

    @Test
    fun rejectsOutOfOrderInputEvenWithValidOriginalWatermark() {
        val original = snapshot(listOf(record("b"), record("a")))
        assertThrows(IllegalArgumentException::class.java) {
            codec.encode(original.copy(records = original.records.reversed()))
        }
    }

    @Test
    fun hostileSourceRemainsEscapedLiteralIncludingUnicodeAndControlChars() {
        val hostile = "file:///secret?url=\"https://host/ñ\\r\n\t" + '\u0001'
        val text = String(codec.encode(snapshot(listOf(record(source = hostile)))), StandardCharsets.UTF_8)
        val got = Json.parseToJsonElement(text).jsonObject.getValue("records")
            .jsonArray.single().jsonObject.getValue("sourceRef").jsonPrimitive.content
        assertEquals(hostile, got)
        assertTrue(text.contains("\\\"https"))
        assertFalse(text.contains("idempotencyKey"))
        assertFalse(text.contains("payloadFingerprint"))
        assertFalse(text.contains("receiptId"))
    }

    @Test
    fun rejectsMalformedUnicodeInsteadOfSilentlyReplacing() {
        val bad = "x" + 0xD800.toChar()
        assertThrows(IllegalArgumentException::class.java) {
            codec.encode(snapshot(listOf(record(source = bad))))
        }
    }

    @Test
    fun currenciesAreNotConvertedAndOldProfileIsUntouched() {
        val root = json(snapshot(listOf(record("a", "ARS"), record("b", "USD"))))
        val currencies = root.getValue("records").jsonArray.map {
            it.jsonObject.getValue("currencyCode").jsonPrimitive.content
        }
        assertEquals(listOf("ARS", "USD"), currencies)
        assertEquals("PORTABLE_BUNDLE_V1", PortabilityPreview.FORMAT_PROFILE)
        assertEquals("PORTABLE_COMMERCIAL_TYPED_SECTION_V2_LOCAL", root.getValue("formatProfile").jsonPrimitive.content)
        assertEquals("2", root.getValue("schemaVersion").jsonPrimitive.content)
        assertTrue(root.getValue("notice").jsonPrimitive.content.contains("NOT RESTORABLE"))
    }

    @Test
    fun deterministicBoundedAndNoExternalSideEffects() {
        val stable = snapshot(listOf(record()))
        assertArrayEquals(codec.encode(stable), codec.encode(stable))
        assertThrows(IllegalStateException::class.java) {
            codec.encode(snapshot(listOf(record(source = "x".repeat(codec.MAX_JSON_BYTES + 1)))))
        }
    }
}
