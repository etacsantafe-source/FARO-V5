package ar.com.faro.v5.maintenance

import ar.com.faro.v5.evidence.CommercialEvidencePortabilityPort
import ar.com.faro.v5.evidence.CommercialEvidencePortabilitySnapshot
import ar.com.faro.v5.evidence.WebEvidencePortabilityPort
import ar.com.faro.v5.evidence.WebEvidencePortabilitySnapshot
import ar.com.faro.v5.kernel.contracts.CanonicalHashBuilder
import ar.com.faro.v5.kernel.contracts.CapabilityId
import ar.com.faro.v5.kernel.contracts.EffectId
import ar.com.faro.v5.kernel.contracts.ExecutionProfile
import ar.com.faro.v5.kernel.contracts.ExecutorId
import ar.com.faro.v5.kernel.contracts.OperationId
import ar.com.faro.v5.owner.finance.api.FinanceSnapshot
import ar.com.faro.v5.owner.finance.api.FinanceSnapshotReadApi
import ar.com.faro.v5.owner.profile.api.ProfileReadApi
import ar.com.faro.v5.owner.profile.api.ProfileSnapshot
import ar.com.faro.v5.store.auxiliary.AuxiliaryPortabilityPort
import ar.com.faro.v5.store.auxiliary.AuxiliaryPortabilitySnapshot

object PortabilityOperations {
    val CAPABILITY_ID = CapabilityId("N-041")
    val EXECUTOR_ID = ExecutorId("maintenance.portability")
    val PREVIEW = OperationId("portability.preview")
    val EXPORT = OperationId("portability.export")
    val PREVIEW_PROFILE = ExecutionProfile.READ_ONLY
    val EXPORT_PROFILE = ExecutionProfile.EXTERNAL_EFFECT
}

enum class PortabilitySection(
    val wireName: String,
) {
    PROFILE("profile"),
    FINANCE("finance"),
    COMMERCIAL_EVIDENCE("commercial_evidence"),
    WEB_EVIDENCE("web_evidence"),
    NOTICES("notices"),
}

data class PortabilityScope(
    val sections: List<PortabilitySection>,
) {
    init {
        require(sections.isNotEmpty()) { "portability scope must not be empty" }
        require(sections.size == sections.distinct().size) {
            "portability scope contains duplicate sections"
        }
        require(sections == sections.sortedBy { it.wireName }) {
            "portability scope must be canonical"
        }
    }

    companion object {
        fun of(vararg sections: PortabilitySection): PortabilityScope =
            PortabilityScope(sections.toList().distinct().sortedBy { it.wireName })

        fun all(): PortabilityScope =
            PortabilityScope(PortabilitySection.entries.sortedBy { it.wireName })
    }
}

enum class PortabilitySensitivity {
    PRIVATE,
    RESTRICTED,
}

enum class PortabilityWarning {
    NOT_A_BACKUP,
    OPEN_ARTIFACT,
    PERSONAL_DATA_INCLUDED,
    FINANCIAL_DATA_INCLUDED,
    COMMERCIAL_HISTORY_INCLUDED,
    WEB_HISTORY_INCLUDED,
    SENSITIVE_NOTICES_INCLUDED,
}

@JvmInline
value class ExportPlanFingerprint(val value: String) {
    init {
        require(value.matches(Regex("[0-9a-f]{64}"))) {
            "export plan fingerprint must be lowercase SHA-256"
        }
    }
}

data class PortabilitySectionPreview(
    val section: PortabilitySection,
    val itemCount: Long,
    val sourceWatermark: String,
    val sensitivity: PortabilitySensitivity,
) {
    init {
        require(itemCount >= 0L)
        require(sourceWatermark.isNotBlank() && sourceWatermark.length <= 256)
    }
}

data class PortabilityPreview(
    val formatProfile: String,
    val exportSchemaVersion: Int,
    val scope: PortabilityScope,
    val exclusions: List<String>,
    val redactionPolicyVersion: String,
    val sections: List<PortabilitySectionPreview>,
    val warnings: List<PortabilityWarning>,
    val estimatedBytes: Long?,
    val exportPlanFingerprint: ExportPlanFingerprint,
) {
    init {
        require(formatProfile == FORMAT_PROFILE)
        require(exportSchemaVersion == EXPORT_SCHEMA_VERSION)
        require(redactionPolicyVersion == REDACTION_POLICY_VERSION)
        require(exclusions == HARD_EXCLUSIONS)
        require(sections.map { it.section } == scope.sections)
        require(warnings == warnings.distinct().sortedBy { it.name })
        require(estimatedBytes == null || estimatedBytes >= 0L)
    }

    companion object {
        const val FORMAT_PROFILE = "PORTABLE_BUNDLE_V1"
        const val EXPORT_SCHEMA_VERSION = 1
        const val REDACTION_POLICY_VERSION = "portability-redaction-v1"

        val HARD_EXCLUSIONS =
            listOf(
                "apply_attempts",
                "credentials",
                "delivery_intents",
                "idempotency_keys",
                "os_registrations",
                "outbox_effects",
                "payload_fingerprints",
                "provider_request_ids",
                "provider_usage",
                "recovery_journals",
                "receipts",
                "vault_secrets",
            )
    }
}

data class PortabilityExportApproval(
    val exportPlanFingerprint: ExportPlanFingerprint,
    val approvedAtEpochMillis: Long,
) {
    init {
        require(approvedAtEpochMillis >= 0L)
    }
}

data class PortabilitySourceSnapshot(
    val scope: PortabilityScope,
    val profile: ProfileSnapshot?,
    val finance: FinanceSnapshot?,
    val commercialEvidence: CommercialEvidencePortabilitySnapshot?,
    val webEvidence: WebEvidencePortabilitySnapshot?,
    val notices: AuxiliaryPortabilitySnapshot?,
) {
    init {
        require((PortabilitySection.PROFILE in scope.sections) == (profile != null))
        require((PortabilitySection.FINANCE in scope.sections) == (finance != null))
        require(
            (PortabilitySection.COMMERCIAL_EVIDENCE in scope.sections) ==
                (commercialEvidence != null)
        )
        require((PortabilitySection.WEB_EVIDENCE in scope.sections) == (webEvidence != null))
        require((PortabilitySection.NOTICES in scope.sections) == (notices != null))
    }
}

class PortabilitySourceReader(
    private val profile: ProfileReadApi,
    private val finance: FinanceSnapshotReadApi,
    private val commercialEvidence: CommercialEvidencePortabilityPort,
    private val webEvidence: WebEvidencePortabilityPort,
    private val notices: AuxiliaryPortabilityPort,
) {
    fun read(scope: PortabilityScope): PortabilitySourceSnapshot =
        PortabilitySourceSnapshot(
            scope = scope,
            profile =
                if (PortabilitySection.PROFILE in scope.sections) {
                    profile.read()
                } else {
                    null
                },
            finance =
                if (PortabilitySection.FINANCE in scope.sections) {
                    finance.read()
                } else {
                    null
                },
            commercialEvidence =
                if (PortabilitySection.COMMERCIAL_EVIDENCE in scope.sections) {
                    commercialEvidence.snapshot()
                } else {
                    null
                },
            webEvidence =
                if (PortabilitySection.WEB_EVIDENCE in scope.sections) {
                    webEvidence.snapshot()
                } else {
                    null
                },
            notices =
                if (PortabilitySection.NOTICES in scope.sections) {
                    notices.snapshot()
                } else {
                    null
                },
        )
}

class PortabilityPreviewService(
    private val sourceReader: PortabilitySourceReader,
) {
    fun preview(scope: PortabilityScope): PortabilityPreview =
        PortabilityPlanBuilder.preview(sourceReader.read(scope))
}

internal object PortabilityPlanBuilder {
    fun preview(source: PortabilitySourceSnapshot): PortabilityPreview {
        val sections =
            source.scope.sections.map { section ->
                when (section) {
                    PortabilitySection.PROFILE -> {
                        val value = checkNotNull(source.profile)
                        PortabilitySectionPreview(
                            section = section,
                            itemCount = if (value.displayName == null) 0L else 1L,
                            sourceWatermark = "revision:" + value.revision,
                            sensitivity = PortabilitySensitivity.PRIVATE,
                        )
                    }

                    PortabilitySection.FINANCE -> {
                        val value = checkNotNull(source.finance)
                        PortabilitySectionPreview(
                            section = section,
                            itemCount = value.accounts.size.toLong() + value.entries.size.toLong(),
                            sourceWatermark = "revision:" + value.revision,
                            sensitivity = PortabilitySensitivity.RESTRICTED,
                        )
                    }

                    PortabilitySection.COMMERCIAL_EVIDENCE -> {
                        val value = checkNotNull(source.commercialEvidence)
                        PortabilitySectionPreview(
                            section = section,
                            itemCount = value.records.size.toLong(),
                            sourceWatermark = value.sourceWatermark,
                            sensitivity = PortabilitySensitivity.PRIVATE,
                        )
                    }

                    PortabilitySection.WEB_EVIDENCE -> {
                        val value = checkNotNull(source.webEvidence)
                        PortabilitySectionPreview(
                            section = section,
                            itemCount =
                                value.fetchRecords.size.toLong() +
                                    value.searchRecords.size.toLong(),
                            sourceWatermark = value.sourceWatermark,
                            sensitivity = PortabilitySensitivity.PRIVATE,
                        )
                    }

                    PortabilitySection.NOTICES -> {
                        val value = checkNotNull(source.notices)
                        PortabilitySectionPreview(
                            section = section,
                            itemCount = value.notices.size.toLong(),
                            sourceWatermark = value.sourceWatermark,
                            sensitivity =
                                if (value.notices.any { it.sensitivity == "SENSITIVE" }) {
                                    PortabilitySensitivity.RESTRICTED
                                } else {
                                    PortabilitySensitivity.PRIVATE
                                },
                        )
                    }
                }
            }

        val warnings =
            buildSet {
                add(PortabilityWarning.NOT_A_BACKUP)
                add(PortabilityWarning.OPEN_ARTIFACT)
                if (PortabilitySection.PROFILE in source.scope.sections) {
                    add(PortabilityWarning.PERSONAL_DATA_INCLUDED)
                }
                if (PortabilitySection.FINANCE in source.scope.sections) {
                    add(PortabilityWarning.FINANCIAL_DATA_INCLUDED)
                }
                if (PortabilitySection.COMMERCIAL_EVIDENCE in source.scope.sections) {
                    add(PortabilityWarning.COMMERCIAL_HISTORY_INCLUDED)
                }
                if (PortabilitySection.WEB_EVIDENCE in source.scope.sections) {
                    add(PortabilityWarning.WEB_HISTORY_INCLUDED)
                }
                if (
                    PortabilitySection.NOTICES in source.scope.sections &&
                    source.notices?.notices?.any { it.sensitivity == "SENSITIVE" } == true
                ) {
                    add(PortabilityWarning.SENSITIVE_NOTICES_INCLUDED)
                }
            }.sortedBy { it.name }

        val fingerprint =
            CanonicalHashBuilder
                .domain("faro-portability-plan-v1")
                .string("formatProfile", PortabilityPreview.FORMAT_PROFILE)
                .string(
                    "exportSchemaVersion",
                    PortabilityPreview.EXPORT_SCHEMA_VERSION.toString(),
                )
                .string("redactionPolicy", PortabilityPreview.REDACTION_POLICY_VERSION)
                .string("scope.count", source.scope.sections.size.toString())

        source.scope.sections.forEachIndexed { index, section ->
            fingerprint.string("scope[$index]", section.wireName)
        }
        PortabilityPreview.HARD_EXCLUSIONS.forEachIndexed { index, exclusion ->
            fingerprint.string("exclusion[$index]", exclusion)
        }
        sections.forEachIndexed { index, section ->
            fingerprint
                .string("section[$index].id", section.section.wireName)
                .string("section[$index].count", section.itemCount.toString())
                .string("section[$index].watermark", section.sourceWatermark)
                .string("section[$index].sensitivity", section.sensitivity.name)
        }
        warnings.forEachIndexed { index, warning ->
            fingerprint.string("warning[$index]", warning.name)
        }

        return PortabilityPreview(
            formatProfile = PortabilityPreview.FORMAT_PROFILE,
            exportSchemaVersion = PortabilityPreview.EXPORT_SCHEMA_VERSION,
            scope = source.scope,
            exclusions = PortabilityPreview.HARD_EXCLUSIONS,
            redactionPolicyVersion = PortabilityPreview.REDACTION_POLICY_VERSION,
            sections = sections,
            warnings = warnings,
            estimatedBytes = null,
            exportPlanFingerprint = ExportPlanFingerprint(fingerprint.sha256Hex()),
        )
    }
}

data class PortabilityExportRequest(
    val effectId: EffectId,
    val preview: PortabilityPreview,
    val approval: PortabilityExportApproval,
    val generatedAtEpochMillis: Long,
) {
    init {
        require(generatedAtEpochMillis >= 0L)
        require(generatedAtEpochMillis >= approval.approvedAtEpochMillis) {
            "portability artifact cannot predate human approval"
        }
    }
}

enum class PortabilityPreparationBlockReason {
    APPROVAL_MISMATCH,
    SOURCE_CHANGED,
    STAGING_FAILED,
}

sealed interface PortabilityPreparationResult {
    data class Prepared(
        val artifact: PreparedPortabilityArtifact,
    ) : PortabilityPreparationResult

    data class Blocked(
        val reason: PortabilityPreparationBlockReason,
        val failureClass: String? = null,
    ) : PortabilityPreparationResult
}

class PortabilityBundlePreparer(
    private val sourceReader: PortabilitySourceReader,
    private val artifactStore: PortabilityArtifactStore,
) {
    fun prepare(request: PortabilityExportRequest): PortabilityPreparationResult {
        if (request.approval.exportPlanFingerprint != request.preview.exportPlanFingerprint) {
            return PortabilityPreparationResult.Blocked(
                PortabilityPreparationBlockReason.APPROVAL_MISMATCH
            )
        }

        val source =
            try {
                sourceReader.read(request.preview.scope)
            } catch (failure: Exception) {
                return PortabilityPreparationResult.Blocked(
                    reason = PortabilityPreparationBlockReason.SOURCE_CHANGED,
                    failureClass = failure::class.java.simpleName,
                )
            }
        val currentPreview = PortabilityPlanBuilder.preview(source)
        if (currentPreview.exportPlanFingerprint != request.preview.exportPlanFingerprint) {
            return PortabilityPreparationResult.Blocked(
                PortabilityPreparationBlockReason.SOURCE_CHANGED
            )
        }

        return try {
            val artifact =
                artifactStore.stage(
                    effectId = request.effectId,
                    planFingerprint = request.preview.exportPlanFingerprint,
                    generatedAtEpochMillis = request.generatedAtEpochMillis,
                ) { output ->
                    PortableBundleJsonWriter.write(
                        output = output,
                        preview = currentPreview,
                        source = source,
                        generatedAtEpochMillis = request.generatedAtEpochMillis,
                    )
                }
            PortabilityPreparationResult.Prepared(artifact)
        } catch (failure: Exception) {
            PortabilityPreparationResult.Blocked(
                reason = PortabilityPreparationBlockReason.STAGING_FAILED,
                failureClass = failure::class.java.simpleName,
            )
        }
    }
}

enum class PortabilityExternalStatus {
    SUCCESS,
    CANCELLED,
    FAILED,
    EFFECT_UNKNOWN,
}

enum class PortabilityDestinationKind {
    USER_SELECTED_DOCUMENT,
}

data class PortabilityExternalEffectReceipt(
    val effectId: EffectId,
    val exportPlanFingerprint: ExportPlanFingerprint,
    val formatProfile: String,
    val generatedAtEpochMillis: Long,
    val byteCount: Long,
    val sha256Hex: String,
    val destinationKind: PortabilityDestinationKind,
    val status: PortabilityExternalStatus,
    val reason: String?,
) {
    init {
        require(formatProfile == PortabilityPreview.FORMAT_PROFILE)
        require(generatedAtEpochMillis >= 0L)
        require(byteCount >= 0L)
        require(sha256Hex.matches(Regex("[0-9a-f]{64}")))
        require(reason == null || (reason.isNotBlank() && reason.length <= 256))
    }
}
