package ar.com.faro.v5.maintenance

import ar.com.faro.v5.evidence.CommercialEvidenceStagedTypedPortabilityPort
import ar.com.faro.v5.evidence.CommercialEvidenceStagedTypedPortabilitySnapshot
import ar.com.faro.v5.kernel.contracts.CanonicalHashBuilder
import ar.com.faro.v5.kernel.contracts.EffectId
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/** Separate V2-only commercial document; V1 preview/serializer/artifacts remain unchanged. */
object CommercialTypedPortabilityV2Format {
    const val PROFILE = "PORTABLE_COMMERCIAL_TYPED_V2_USER_SELECTED"
    const val SCHEMA_VERSION = 2
    const val REDACTION_POLICY = "commercial-evidence-redaction-v2"
    const val NOTICE = "NOT A FARO BACKUP / NOT RESTORABLE"

    fun isSupported(profile: String): Boolean =
        profile == PortabilityPreview.FORMAT_PROFILE || profile == PROFILE
}

data class CommercialTypedPortabilityV2Preview(
    val formatProfile: String,
    val exportSchemaVersion: Int,
    val redactionPolicyVersion: String,
    val sourceWatermark: String,
    val recordCount: Long,
    val byteCount: Long,
    val sha256Hex: String,
    val exclusions: List<String>,
    val warnings: List<PortabilityWarning>,
    val exportPlanFingerprint: ExportPlanFingerprint,
) {
    init {
        require(formatProfile == CommercialTypedPortabilityV2Format.PROFILE)
        require(exportSchemaVersion == CommercialTypedPortabilityV2Format.SCHEMA_VERSION)
        require(redactionPolicyVersion == CommercialTypedPortabilityV2Format.REDACTION_POLICY)
        require(sourceWatermark.matches(Regex("[0-9a-f]{64}")))
        require(recordCount in 0..100_000L)
        require(byteCount in 1..(CommercialTypedPortabilityJsonCodec.MAX_JSON_BYTES + 512).toLong())
        require(sha256Hex.matches(Regex("[0-9a-f]{64}")))
        require(exclusions == PortabilityPreview.HARD_EXCLUSIONS)
        require(warnings == listOf(
            PortabilityWarning.COMMERCIAL_HISTORY_INCLUDED,
            PortabilityWarning.NOT_A_BACKUP,
            PortabilityWarning.OPEN_ARTIFACT,
        ))
    }
}

data class CommercialTypedPortabilityV2Approval(
    val exportPlanFingerprint: ExportPlanFingerprint,
    val approvedAtEpochMillis: Long,
    val humanConfirmed: Boolean,
    val destinationKind: PortabilityDestinationKind,
) {
    init { require(approvedAtEpochMillis >= 0L) }
}

enum class CommercialTypedPortabilityV2BlockReason {
    NO_EXPLICIT_USER_APPROVAL,
    APPROVAL_MISMATCH,
    SOURCE_CHANGED,
    INVALID_GENERATION_TIME,
}

sealed interface CommercialTypedPortabilityV2Preparation {
    data class Prepared(
        val source: PortabilityExternalWriteSource,
        val preview: CommercialTypedPortabilityV2Preview,
    ) : CommercialTypedPortabilityV2Preparation

    data class Blocked(
        val reason: CommercialTypedPortabilityV2BlockReason,
    ) : CommercialTypedPortabilityV2Preparation
}

/**
 * Does not open any URI, file, network connection, or install Android SAF adapters.
 * Caller must obtain a user-picked document, use the existing durable external-attempt
 * coordinator, mark WRITE_STARTED, perform copy, verify outcome, then terminalize receipt.
 */
class CommercialTypedPortabilityV2Service(
    private val port: CommercialEvidenceStagedTypedPortabilityPort,
) {
    fun preview(): CommercialTypedPortabilityV2Preview =
        assemble(port.snapshot()).first

    fun prepare(
        effectId: EffectId,
        reviewedPreview: CommercialTypedPortabilityV2Preview,
        approval: CommercialTypedPortabilityV2Approval,
        generatedAtEpochMillis: Long,
    ): CommercialTypedPortabilityV2Preparation {
        if (!approval.humanConfirmed ||
            approval.destinationKind != PortabilityDestinationKind.USER_SELECTED_DOCUMENT
        ) {
            return CommercialTypedPortabilityV2Preparation.Blocked(
                CommercialTypedPortabilityV2BlockReason.NO_EXPLICIT_USER_APPROVAL
            )
        }
        if (approval.exportPlanFingerprint != reviewedPreview.exportPlanFingerprint) {
            return CommercialTypedPortabilityV2Preparation.Blocked(
                CommercialTypedPortabilityV2BlockReason.APPROVAL_MISMATCH
            )
        }
        if (generatedAtEpochMillis < approval.approvedAtEpochMillis ||
            generatedAtEpochMillis < 0L
        ) {
            return CommercialTypedPortabilityV2Preparation.Blocked(
                CommercialTypedPortabilityV2BlockReason.INVALID_GENERATION_TIME
            )
        }
        val actual = try {
            assemble(port.snapshot())
        } catch (_: Exception) {
            return CommercialTypedPortabilityV2Preparation.Blocked(
                CommercialTypedPortabilityV2BlockReason.SOURCE_CHANGED
            )
        }
        if (actual.first != reviewedPreview) {
            return CommercialTypedPortabilityV2Preparation.Blocked(
                CommercialTypedPortabilityV2BlockReason.SOURCE_CHANGED
            )
        }
        return CommercialTypedPortabilityV2Preparation.Prepared(
            source = ApprovedDocumentSource(
                effect = effectId,
                approved = actual.first,
                immutableBytes = actual.second,
                generatedAt = generatedAtEpochMillis,
                currentPreview = { assemble(port.snapshot()).first },
            ),
            preview = actual.first,
        )
    }

    private fun assemble(
        snapshot: CommercialEvidenceStagedTypedPortabilitySnapshot,
    ): Pair<CommercialTypedPortabilityV2Preview, ByteArray> {
        val sectionBytes = CommercialTypedPortabilityJsonCodec.encode(snapshot)
        val start = ("{\"formatProfile\":\"" + CommercialTypedPortabilityV2Format.PROFILE +
            "\",\"exportSchemaVersion\":2,\"notice\":\"" +
            CommercialTypedPortabilityV2Format.NOTICE +
            "\",\"commercialSection\":").toByteArray(StandardCharsets.US_ASCII)
        val buffer = ByteArrayOutputStream(start.size + sectionBytes.size + 1)
        buffer.write(start)
        buffer.write(sectionBytes)
        buffer.write('}'.code)
        val bytes = buffer.toByteArray()
        check(bytes.size <= CommercialTypedPortabilityJsonCodec.MAX_JSON_BYTES + 512)
        val sha = digest(bytes)
        val count = snapshot.records.size.toLong()
        val fingerprint = CanonicalHashBuilder.domain("faro-portability-commercial-v2-plan")
            .string("profile", CommercialTypedPortabilityV2Format.PROFILE)
            .string("schema", "2")
            .string("redaction", CommercialTypedPortabilityV2Format.REDACTION_POLICY)
            .string("commercialWatermark", snapshot.sourceWatermark)
            .string("recordCount", count.toString())
            .string("sha256", sha)
            .string("byteCount", bytes.size.toString())
            .sha256Hex()
        return CommercialTypedPortabilityV2Preview(
            formatProfile = CommercialTypedPortabilityV2Format.PROFILE,
            exportSchemaVersion = 2,
            redactionPolicyVersion = CommercialTypedPortabilityV2Format.REDACTION_POLICY,
            sourceWatermark = snapshot.sourceWatermark,
            recordCount = count,
            byteCount = bytes.size.toLong(),
            sha256Hex = sha,
            exclusions = PortabilityPreview.HARD_EXCLUSIONS,
            warnings = listOf(
                PortabilityWarning.COMMERCIAL_HISTORY_INCLUDED,
                PortabilityWarning.NOT_A_BACKUP,
                PortabilityWarning.OPEN_ARTIFACT,
            ),
            exportPlanFingerprint = ExportPlanFingerprint(fingerprint),
        ) to bytes
    }

    private class ApprovedDocumentSource(
        effect: EffectId,
        private val approved: CommercialTypedPortabilityV2Preview,
        immutableBytes: ByteArray,
        generatedAt: Long,
        private val currentPreview: () -> CommercialTypedPortabilityV2Preview,
    ) : PortabilityExternalWriteSource {
        private val payload = immutableBytes.copyOf()
        override val effectIdentity = effect.value
        override val exportPlanFingerprintHex = approved.exportPlanFingerprint.value
        override val formatProfile = CommercialTypedPortabilityV2Format.PROFILE
        override val artifactHandleHex = CanonicalHashBuilder
            .domain("faro-commercial-typed-v2-document-handle")
            .string("effect", effect.value)
            .string("plan", approved.exportPlanFingerprint.value)
            .sha256Hex()
        override val suggestedFileName = "faro-commercial-typed-" + generatedAt + ".json"
        override val mimeType = "application/json"
        override val generatedAtEpochMillis = generatedAt
        override val byteCount = payload.size.toLong()
        override val sha256Hex = digest(payload)

        override fun copyTo(output: OutputStream): Long {
            check(currentPreview() == approved) { "approved commercial source changed before copy" }
            check(byteCount == approved.byteCount && sha256Hex == approved.sha256Hex)
            check(digest(payload) == sha256Hex)
            output.write(payload)
            output.flush()
            return byteCount
        }
    }

    private companion object {
        fun digest(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it) }
    }
}
