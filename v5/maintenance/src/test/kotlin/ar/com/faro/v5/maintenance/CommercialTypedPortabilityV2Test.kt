package ar.com.faro.v5.maintenance

import ar.com.faro.v5.evidence.CommercialEvidenceStagedTypedPortabilityPort
import ar.com.faro.v5.evidence.CommercialEvidenceStagedTypedPortabilitySnapshot
import ar.com.faro.v5.evidence.CommercialEvidenceTypedTerms
import ar.com.faro.v5.evidence.PortableCommercialObservation
import ar.com.faro.v5.evidence.PortableCommercialTypedObservation
import ar.com.faro.v5.kernel.contracts.CanonicalHashBuilder
import ar.com.faro.v5.kernel.contracts.EffectId
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class CommercialTypedPortabilityV2Test {
    private fun snapshot(terms: CommercialEvidenceTypedTerms? = null): CommercialEvidenceStagedTypedPortabilitySnapshot {
        val row = PortableCommercialTypedObservation(
            observation = PortableCommercialObservation(
                productKey = "arroz",
                merchant = "Comercio",
                currencyCode = "ARS",
                amountMinor = 150000,
                sourceKind = "MANUAL",
                sourceRef = "file:///literal-source",
                observedAtEpochMillis = null,
                acquiredAtEpochMillis = 100,
            ),
            terms = terms,
        )
        val r = row.observation
        val hash = CanonicalHashBuilder.domain("faro-portability-commercial-v2")
            .string("schemaVersion", "2")
            .string("count", "1")
            .string("record[0].productKey", r.productKey)
            .string("record[0].merchant", r.merchant)
            .string("record[0].currencyCode", r.currencyCode)
            .string("record[0].amountMinor", r.amountMinor.toString())
            .string("record[0].sourceKind", r.sourceKind)
            .string("record[0].sourceRef", r.sourceRef)
            .string("record[0].observed.present", "false")
            .string("record[0].observed", "")
            .string("record[0].acquired", "100")
            .string("record[0].terms.present", (terms != null).toString())
            .string("record[0].terms.fingerprint", terms?.fingerprint() ?: "")
            .sha256Hex()
        return CommercialEvidenceStagedTypedPortabilitySnapshot(2, hash, listOf(row))
    }

    private fun fixture(): Triple<CommercialTypedPortabilityV2Service,
        AtomicReference<CommercialEvidenceStagedTypedPortabilitySnapshot>, CommercialTypedPortabilityV2Preview> {
        val ref = AtomicReference(snapshot())
        val svc = CommercialTypedPortabilityV2Service(
            CommercialEvidenceStagedTypedPortabilityPort { ref.get() }
        )
        return Triple(svc, ref, svc.preview())
    }

    private fun approve(p: CommercialTypedPortabilityV2Preview, yes: Boolean = true) =
        CommercialTypedPortabilityV2Approval(
            exportPlanFingerprint = p.exportPlanFingerprint,
            approvedAtEpochMillis = 500,
            humanConfirmed = yes,
            destinationKind = PortabilityDestinationKind.USER_SELECTED_DOCUMENT,
        )

    private fun prepare(
        svc: CommercialTypedPortabilityV2Service,
        preview: CommercialTypedPortabilityV2Preview,
        id: String = "effect/commercial-typed",
    ): PortabilityExternalWriteSource = assertInstanceOf(
        CommercialTypedPortabilityV2Preparation.Prepared::class.java,
        svc.prepare(EffectId(id), preview, approve(preview), 1000),
    ).source

    @Test
    fun previewHasStrictNewProfileRedactionAndWarning() {
        val (_, _, p) = fixture()
        assertEquals("PORTABLE_COMMERCIAL_TYPED_V2_USER_SELECTED", p.formatProfile)
        assertEquals(2, p.exportSchemaVersion)
        assertEquals(1L, p.recordCount)
        assertEquals(64, p.sha256Hex.length)
        assertTrue(PortabilityWarning.NOT_A_BACKUP in p.warnings)
        assertEquals("PORTABLE_BUNDLE_V1", PortabilityPreview.FORMAT_PROFILE)
    }

    @Test
    fun absentHumanConfirmationDeniesPreparationBeforeWriting() {
        val (s, _, p) = fixture()
        val result = s.prepare(EffectId("effect/no-consent"), p, approve(p, false), 1000)
        assertEquals(
            CommercialTypedPortabilityV2BlockReason.NO_EXPLICIT_USER_APPROVAL,
            assertInstanceOf(CommercialTypedPortabilityV2Preparation.Blocked::class.java, result).reason
        )
    }

    @Test
    fun wrongApprovalFingerprintAndTimeAreRejected() {
        val (s, _, p) = fixture()
        val bad = approve(p).copy(exportPlanFingerprint = ExportPlanFingerprint("a".repeat(64)))
        assertEquals(CommercialTypedPortabilityV2BlockReason.APPROVAL_MISMATCH,
            assertInstanceOf(CommercialTypedPortabilityV2Preparation.Blocked::class.java,
                s.prepare(EffectId("effect/mismatch"), p, bad, 1000)).reason)
        assertEquals(CommercialTypedPortabilityV2BlockReason.INVALID_GENERATION_TIME,
            assertInstanceOf(CommercialTypedPortabilityV2Preparation.Blocked::class.java,
                s.prepare(EffectId("effect/time"), p, approve(p), 499)).reason)
    }

    @Test
    fun changingTermsAfterPreviewInvalidatesPlanWithoutOutput() {
        val (s, ref, p) = fixture()
        ref.set(snapshot(CommercialEvidenceTypedTerms.UNKNOWN))
        assertEquals(CommercialTypedPortabilityV2BlockReason.SOURCE_CHANGED,
            assertInstanceOf(CommercialTypedPortabilityV2Preparation.Blocked::class.java,
                s.prepare(EffectId("effect/stale"), p, approve(p), 1000)).reason)
    }

    @Test
    fun approvedUserSelectedBytesContainLosslessCommercialSection() {
        val (s, _, p) = fixture()
        val source = prepare(s, p)
        val bytes = ByteArrayOutputStream()
        assertEquals(source.byteCount, source.copyTo(bytes))
        val root = Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject
        assertEquals(source.formatProfile, root.getValue("formatProfile").jsonPrimitive.content)
        val section = root.getValue("commercialSection").jsonObject
        assertEquals("2", section.getValue("schemaVersion").jsonPrimitive.content)
        assertEquals("file:///literal-source",
            section.getValue("records").jsonArray.single().jsonObject.getValue("sourceRef").jsonPrimitive.content)
        assertFalse(bytes.toString(Charsets.UTF_8).contains("idempotencyKey"))
        assertFalse(bytes.toString(Charsets.UTF_8).contains("payloadFingerprint"))
    }

    @Test
    fun changesAfterPrepareAbortBeforeAnyDocumentBytes() {
        val (s, ref, p) = fixture()
        val source = prepare(s, p)
        ref.set(snapshot(CommercialEvidenceTypedTerms.UNKNOWN))
        val out = ByteArrayOutputStream()
        assertThrows(IllegalStateException::class.java) { source.copyTo(out) }
        assertEquals(0, out.size())
    }

    @Test
    fun coordinatorCommitsDurableV2SuccessWithVerifiedBytes(@TempDir root: Path) {
        val (s, _, p) = fixture()
        val source = prepare(s, p, "effect/verified")
        val store = FilePortabilityExternalAttemptStore(root)
        val coordinator = PortabilityExternalEffectCoordinator(store)
        coordinator.begin(source)
        coordinator.markWriteStarted(source)
        val out = ByteArrayOutputStream()
        assertEquals(source.byteCount, source.copyTo(out))
        val receipt = coordinator.completeVerifiedSuccess(source)
        assertEquals(PortabilityExternalStatus.SUCCESS, receipt.status)
        assertEquals(CommercialTypedPortabilityV2Format.PROFILE, receipt.formatProfile)
        assertEquals(source.sha256Hex, receipt.sha256Hex)
        assertEquals(PortabilityExternalStatus.SUCCESS,
            FilePortabilityExternalAttemptStore(root).read(source.effectIdentity)!!.terminalStatus)
    }

    @Test
    fun pickerCancellationIsDurableAndWritesNoBytes(@TempDir root: Path) {
        val (s, _, p) = fixture()
        val source = prepare(s, p, "effect/cancel")
        val coordinator = PortabilityExternalEffectCoordinator(FilePortabilityExternalAttemptStore(root))
        coordinator.begin(source)
        assertEquals(PortabilityExternalStatus.CANCELLED, coordinator.pickerCancelled(source).status)
        assertThrows(IllegalStateException::class.java) { coordinator.markWriteStarted(source) }
    }

    @Test
    fun crashedWriteCannotBeReplayedAndBecomesUnknown(@TempDir root: Path) {
        val (s, _, p) = fixture()
        val source = prepare(s, p, "effect/crash")
        val start = PortabilityExternalEffectCoordinator(FilePortabilityExternalAttemptStore(root))
        start.begin(source)
        start.markWriteStarted(source)
        val reopened = PortabilityExternalEffectCoordinator(FilePortabilityExternalAttemptStore(root))
        assertEquals(PortabilityExternalStatus.EFFECT_UNKNOWN, reopened.reconcileInterruptedWrites().single().status)
        assertThrows(IllegalStateException::class.java) { reopened.markWriteStarted(source) }
    }

    @Test
    fun partialCopyExceptionCannotProduceSuccessReceipt(@TempDir root: Path) {
        val (s, _, p) = fixture()
        val source = prepare(s, p, "effect/partial")
        val c = PortabilityExternalEffectCoordinator(FilePortabilityExternalAttemptStore(root))
        c.begin(source)
        c.markWriteStarted(source)
        val failing = object : OutputStream() {
            private var count = 0
            override fun write(b: Int) {
                if (++count > 4) throw java.io.IOException("simulated destination failure")
            }
        }
        assertThrows(java.io.IOException::class.java) { source.copyTo(failing) }
        assertEquals(PortabilityExternalStatus.EFFECT_UNKNOWN,
            c.completeUnknown(source, "partial_copy_uncertain").status)
        assertThrows(IllegalStateException::class.java) { c.completeVerifiedSuccess(source) }
    }
}
