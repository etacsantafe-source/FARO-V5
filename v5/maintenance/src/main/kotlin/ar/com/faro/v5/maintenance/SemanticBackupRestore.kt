package ar.com.faro.v5.maintenance

import ar.com.faro.v5.evidence.CommercialEvidenceBackupPort
import ar.com.faro.v5.evidence.CommercialEvidenceBackupSnapshot
import ar.com.faro.v5.evidence.CommercialEvidenceRecord
import ar.com.faro.v5.evidence.WebEvidenceBackupPort
import ar.com.faro.v5.evidence.WebEvidenceBackupSnapshot
import ar.com.faro.v5.evidence.WebEvidenceRecord
import ar.com.faro.v5.evidence.WebSearchEvidenceRecord
import ar.com.faro.v5.kernel.contracts.CanonicalHashBuilder
import ar.com.faro.v5.store.auxiliary.AuxiliaryBackupPort
import ar.com.faro.v5.store.auxiliary.AuxiliaryBackupSnapshot
import ar.com.faro.v5.store.reality.RealityBackupPort
import ar.com.faro.v5.store.reality.RealityBackupSnapshot
import ar.com.faro.v5.store.reality.RealityCommittedReceiptBackup
import ar.com.faro.v5.store.reality.RealityMigrationAccount
import ar.com.faro.v5.store.reality.RealityMigrationEntry

@JvmInline
value class BackupSetId(val value: String) {
    init {
        require(value.isNotBlank()) { "backup set id must not be blank" }
        require(value == value.trim()) { "backup set id must be canonical" }
        require(value.length <= 160) { "backup set id is too long" }
    }
}

enum class BackupStateClass {
    RESTORABLE_AUTHORITY,
    REBUILDABLE,
    SECRET,
    OPERATIONAL_TRANSIENT,
}

enum class BackupContentClass(
    val stateClass: BackupStateClass,
    val included: Boolean,
) {
    REALITY_DOMAIN(BackupStateClass.RESTORABLE_AUTHORITY, true),
    REALITY_COMMITTED_RECEIPTS(BackupStateClass.RESTORABLE_AUTHORITY, true),
    COMMERCIAL_EVIDENCE(BackupStateClass.RESTORABLE_AUTHORITY, true),
    WEB_EVIDENCE(BackupStateClass.RESTORABLE_AUTHORITY, true),
    AUXILIARY_NOTICES(BackupStateClass.RESTORABLE_AUTHORITY, true),
    REBUILDABLE_PROJECTIONS(BackupStateClass.REBUILDABLE, false),
    SECRETS(BackupStateClass.SECRET, false),
    PREPARED_APPLY_ATTEMPTS(BackupStateClass.OPERATIONAL_TRANSIENT, false),
    REALITY_OUTBOX_EFFECTS(BackupStateClass.OPERATIONAL_TRANSIENT, false),
    DELIVERY_INTENTS(BackupStateClass.OPERATIONAL_TRANSIENT, false),
    TEMP_STAGING(BackupStateClass.OPERATIONAL_TRANSIENT, false),
}

data class BackupClassDecision(
    val contentClass: BackupContentClass,
    val stateClass: BackupStateClass,
    val included: Boolean,
    val reason: String,
) {
    init {
        require(stateClass == contentClass.stateClass) {
            "backup class state does not match canonical policy"
        }
        require(included == contentClass.included) {
            "backup class inclusion does not match canonical policy"
        }
        require(reason.isNotBlank() && reason.length <= 256) {
            "backup class reason must be non-blank and bounded"
        }
    }
}

object BackupClassPolicy {
    val decisions: List<BackupClassDecision> =
        BackupContentClass.entries.map { contentClass ->
            BackupClassDecision(
                contentClass = contentClass,
                stateClass = contentClass.stateClass,
                included = contentClass.included,
                reason =
                    when (contentClass) {
                        BackupContentClass.REALITY_DOMAIN ->
                            "restorable Reality domain authority"
                        BackupContentClass.REALITY_COMMITTED_RECEIPTS ->
                            "committed receipts retained without pending Apply state"
                        BackupContentClass.COMMERCIAL_EVIDENCE ->
                            "restorable commercial Evidence authority"
                        BackupContentClass.WEB_EVIDENCE ->
                            "restorable retained Web Evidence"
                        BackupContentClass.AUXILIARY_NOTICES ->
                            "retained Notice authority without delivery projections"
                        BackupContentClass.REBUILDABLE_PROJECTIONS ->
                            "rebuild from restored authority"
                        BackupContentClass.SECRETS ->
                            "Vault and credential material is excluded"
                        BackupContentClass.PREPARED_APPLY_ATTEMPTS ->
                            "prepared Apply state is unsafe to replay"
                        BackupContentClass.REALITY_OUTBOX_EFFECTS ->
                            "pending external effects are unsafe to replay"
                        BackupContentClass.DELIVERY_INTENTS ->
                            "delivery intents are reprojected after restore"
                        BackupContentClass.TEMP_STAGING ->
                            "temporary and staged runtime state is excluded"
                    },
            )
        }
}

data class BackupSectionManifest(
    val sectionId: String,
    val schemaVersion: Int,
    val revisionToken: String,
    val recordCount: Long,
    val fingerprint: String,
) {
    init {
        require(sectionId.matches(Regex("[a-z][a-z0-9.-]{0,63}"))) {
            "backup section id is invalid"
        }
        require(schemaVersion >= 0)
        require(revisionToken.isNotBlank() && revisionToken.length <= 256)
        require(recordCount >= 0L)
        require(fingerprint.matches(Regex("[0-9a-f]{64}"))) {
            "backup section fingerprint must be lowercase SHA-256"
        }
    }
}

data class BackupSetManifest(
    val formatVersion: Int,
    val backupSetId: BackupSetId,
    val sourceGenerationId: StorageGenerationId,
    val productVersion: String,
    val createdAtEpochMillis: Long,
    val sections: List<BackupSectionManifest>,
    val classDecisions: List<BackupClassDecision>,
    val restoreReplayFenceRequired: Boolean,
    val setFingerprint: String,
) {
    init {
        require(formatVersion == FORMAT_VERSION) {
            "unsupported backup-set manifest format"
        }
        require(productVersion.isNotBlank() && productVersion.length <= 128) {
            "backup product version must be non-blank and bounded"
        }
        require(createdAtEpochMillis >= 0L)
        require(sections.map { it.sectionId }.distinct().size == sections.size)
        require(sections == sections.sortedBy { it.sectionId }) {
            "backup sections must be in canonical order"
        }
        require(classDecisions == BackupClassPolicy.decisions) {
            "backup class policy does not match canonical FARO policy"
        }
        require(restoreReplayFenceRequired) {
            "FARO restore must require a replay fence"
        }
        require(setFingerprint.matches(Regex("[0-9a-f]{64}"))) {
            "backup set fingerprint must be lowercase SHA-256"
        }
    }

    companion object {
        const val FORMAT_VERSION: Int = 1
    }
}

data class SemanticBackupSet(
    val manifest: BackupSetManifest,
    val reality: RealityBackupSnapshot,
    val commercialEvidence: CommercialEvidenceBackupSnapshot,
    val webEvidence: WebEvidenceBackupSnapshot,
    val auxiliary: AuxiliaryBackupSnapshot,
)

object SemanticBackupSetIntegrity {
    fun matches(backupSet: SemanticBackupSet): Boolean =
        BackupManifestBuilder.matches(backupSet)
}

class SemanticBackupCaptureCoordinator(
    private val quiescence: MaterialWriteQuiescencePort,
    private val reality: RealityBackupPort,
    private val commercialEvidence: CommercialEvidenceBackupPort,
    private val webEvidence: WebEvidenceBackupPort,
    private val auxiliary: AuxiliaryBackupPort,
) {
    fun capture(
        operationId: MaintenanceOperationId,
        backupSetId: BackupSetId,
        sourceGenerationId: StorageGenerationId,
        productVersion: String,
        createdAtEpochMillis: Long,
    ): SemanticBackupSet =
        captureWithAdditionalSnapshot(
            operationId, backupSetId, sourceGenerationId,
            productVersion, createdAtEpochMillis,
        ) { Unit }.first

    /**
     * Capture the legacy four authorities and one additional read under EXACTLY
     * ONE write-quiescence lease. The caller must supply a lease that covers
     * the additional authority as well; a per-store lock is NOT sufficient.
     * This primitive does not authorize archive export or restore.
     */
    fun <T> captureWithAdditionalSnapshot(
        operationId: MaintenanceOperationId,
        backupSetId: BackupSetId,
        sourceGenerationId: StorageGenerationId,
        productVersion: String,
        createdAtEpochMillis: Long,
        additionalSnapshot: () -> T,
    ): Pair<SemanticBackupSet, T> =
        quiescence.withMaterialWritesQuiesced(operationId) {
            val payload =
                BackupPayload(
                    reality = reality.snapshot(),
                    commercialEvidence = commercialEvidence.snapshot(),
                    webEvidence = webEvidence.snapshot(),
                    auxiliary = auxiliary.snapshot(),
                )
            SemanticBackupSet(
                manifest =
                    BackupManifestBuilder.build(
                        backupSetId = backupSetId,
                        sourceGenerationId = sourceGenerationId,
                        productVersion = productVersion,
                        createdAtEpochMillis = createdAtEpochMillis,
                        payload = payload,
                    ),
                reality = payload.reality,
                commercialEvidence = payload.commercialEvidence,
                webEvidence = payload.webEvidence,
                auxiliary = payload.auxiliary,
            ) to additionalSnapshot()
        }
}

enum class SemanticRestoreBlockReason {
    INVALID_BACKUP_SET,
    BOOTSTRAP_NOT_STAGED,
    TARGET_GENERATION_NOT_STAGED,
    RESTORE_FAILED,
    SEMANTIC_MISMATCH,
    JOURNAL_CONFLICT,
}

enum class SemanticRestoreTargetDisposition {
    NO_ACTIVATION,
    DISCARD_STAGED_TARGET,
}

enum class SemanticRestoreFaultPoint {
    AFTER_REALITY,
    AFTER_COMMERCIAL_EVIDENCE,
    AFTER_WEB_EVIDENCE,
    AFTER_AUXILIARY,
    BEFORE_JOURNAL_VALIDATED,
}

sealed interface SemanticRestoreResult {
    data class Validated(
        val operationId: MaintenanceOperationId,
        val targetGenerationId: StorageGenerationId,
        val backupSetId: BackupSetId,
        val recoveredFromExistingTarget: Boolean,
    ) : SemanticRestoreResult

    data class Blocked(
        val operationId: MaintenanceOperationId,
        val targetGenerationId: StorageGenerationId?,
        val reason: SemanticRestoreBlockReason,
        val targetDisposition: SemanticRestoreTargetDisposition,
        val failureClass: String? = null,
    ) : SemanticRestoreResult
}

class SemanticBackupRestoreCoordinator(
    private val journal: BootstrapJournalPort,
    private val generationLayout: FileStorageGenerationLayout,
    private val reality: RealityBackupPort,
    private val commercialEvidence: CommercialEvidenceBackupPort,
    private val webEvidence: WebEvidenceBackupPort,
    private val auxiliary: AuxiliaryBackupPort,
    private val faultInjector: (SemanticRestoreFaultPoint) -> Unit = {},
) {
    fun restore(
        operationId: MaintenanceOperationId,
        backupSet: SemanticBackupSet,
    ): SemanticRestoreResult {
        if (!BackupManifestBuilder.matches(backupSet)) {
            return SemanticRestoreResult.Blocked(
                operationId = operationId,
                targetGenerationId = null,
                reason = SemanticRestoreBlockReason.INVALID_BACKUP_SET,
                targetDisposition = SemanticRestoreTargetDisposition.NO_ACTIVATION,
            )
        }

        val staged =
            runCatching { journal.read(operationId) }.getOrNull()
                ?: return SemanticRestoreResult.Blocked(
                    operationId = operationId,
                    targetGenerationId = null,
                    reason = SemanticRestoreBlockReason.BOOTSTRAP_NOT_STAGED,
                    targetDisposition = SemanticRestoreTargetDisposition.NO_ACTIVATION,
                )

        if (
            staged.kind != MaintenanceOperationKind.RESTORE ||
                staged.phase !in setOf(BootstrapPhase.STAGED, BootstrapPhase.VALIDATED) ||
                staged.materialWritesAccepted
        ) {
            return SemanticRestoreResult.Blocked(
                operationId = operationId,
                targetGenerationId = staged.targetGenerationId,
                reason = SemanticRestoreBlockReason.BOOTSTRAP_NOT_STAGED,
                targetDisposition = SemanticRestoreTargetDisposition.NO_ACTIVATION,
            )
        }

        val targetGenerationId = staged.targetGenerationId
        try {
            generationLayout.requireStaged(targetGenerationId)
        } catch (failure: Throwable) {
            abortBestEffort(staged)
            return SemanticRestoreResult.Blocked(
                operationId = operationId,
                targetGenerationId = targetGenerationId,
                reason = SemanticRestoreBlockReason.TARGET_GENERATION_NOT_STAGED,
                targetDisposition = SemanticRestoreTargetDisposition.DISCARD_STAGED_TARGET,
                failureClass = failure::class.java.simpleName,
            )
        }

        val payload =
            BackupPayload(
                reality = backupSet.reality,
                commercialEvidence = backupSet.commercialEvidence,
                webEvidence = backupSet.webEvidence,
                auxiliary = backupSet.auxiliary,
            )
        val matchedBefore =
            runCatching {
                (
                    (!payload.reality.domain.isEmpty ||
                        payload.reality.committedReceipts.isNotEmpty()) &&
                        reality.snapshot() == payload.reality
                ) ||
                    (
                        payload.commercialEvidence.records.isNotEmpty() &&
                            commercialEvidence.snapshot() == payload.commercialEvidence
                    ) ||
                    (
                        (
                            payload.webEvidence.fetchRecords.isNotEmpty() ||
                                payload.webEvidence.searchRecords.isNotEmpty()
                        ) &&
                            webEvidence.snapshot() == payload.webEvidence
                    ) ||
                    (
                        payload.auxiliary.notices.isNotEmpty() &&
                            auxiliary.snapshot() == payload.auxiliary
                    )
            }.getOrDefault(false)
        val journalAlreadyValidated = staged.phase == BootstrapPhase.VALIDATED

        try {
            reality.restoreOrVerify(payload.reality)
            faultInjector(SemanticRestoreFaultPoint.AFTER_REALITY)
            commercialEvidence.restoreOrVerify(payload.commercialEvidence)
            faultInjector(SemanticRestoreFaultPoint.AFTER_COMMERCIAL_EVIDENCE)
            webEvidence.restoreOrVerify(payload.webEvidence)
            faultInjector(SemanticRestoreFaultPoint.AFTER_WEB_EVIDENCE)
            auxiliary.restoreOrVerify(payload.auxiliary)
            faultInjector(SemanticRestoreFaultPoint.AFTER_AUXILIARY)
        } catch (crash: SemanticRestoreInjectedCrash) {
            throw crash
        } catch (failure: Throwable) {
            abortBestEffort(staged)
            return SemanticRestoreResult.Blocked(
                operationId = operationId,
                targetGenerationId = targetGenerationId,
                reason = SemanticRestoreBlockReason.RESTORE_FAILED,
                targetDisposition = SemanticRestoreTargetDisposition.DISCARD_STAGED_TARGET,
                failureClass = failure::class.java.simpleName,
            )
        }

        val targetSet =
            SemanticBackupSet(
                manifest = backupSet.manifest,
                reality = reality.snapshot(),
                commercialEvidence = commercialEvidence.snapshot(),
                webEvidence = webEvidence.snapshot(),
                auxiliary = auxiliary.snapshot(),
            )
        if (!BackupManifestBuilder.matches(targetSet) || !payloadMatches(backupSet, targetSet)) {
            abortBestEffort(staged)
            return SemanticRestoreResult.Blocked(
                operationId = operationId,
                targetGenerationId = targetGenerationId,
                reason = SemanticRestoreBlockReason.SEMANTIC_MISMATCH,
                targetDisposition = SemanticRestoreTargetDisposition.DISCARD_STAGED_TARGET,
            )
        }

        if (!journalAlreadyValidated) {
            faultInjector(SemanticRestoreFaultPoint.BEFORE_JOURNAL_VALIDATED)
            val validated =
                BootstrapTransitionPolicy.transition(
                    staged,
                    BootstrapPhase.VALIDATED,
                )
            if (!journal.compareAndSet(staged, validated)) {
                val observed = journal.read(operationId)
                if (observed != validated) {
                    return SemanticRestoreResult.Blocked(
                        operationId = operationId,
                        targetGenerationId = targetGenerationId,
                        reason = SemanticRestoreBlockReason.JOURNAL_CONFLICT,
                        targetDisposition = SemanticRestoreTargetDisposition.NO_ACTIVATION,
                    )
                }
            }
        }

        return SemanticRestoreResult.Validated(
            operationId = operationId,
            targetGenerationId = targetGenerationId,
            backupSetId = backupSet.manifest.backupSetId,
            recoveredFromExistingTarget = journalAlreadyValidated || matchedBefore,
        )
    }

    private fun abortBestEffort(entry: BootstrapJournalEntry) {
        if (entry.phase !in setOf(BootstrapPhase.PREPARING, BootstrapPhase.STAGED, BootstrapPhase.VALIDATED)) {
            return
        }
        runCatching {
            val aborted =
                BootstrapTransitionPolicy.transition(
                    entry,
                    BootstrapPhase.ABORTED_PRE_SWITCH,
                )
            if (!journal.compareAndSet(entry, aborted)) {
                val observed = journal.read(entry.operationId)
                check(observed == aborted || observed?.phase == BootstrapPhase.ABORTED_PRE_SWITCH)
            }
        }
    }

    private fun payloadMatches(
        expected: SemanticBackupSet,
        actual: SemanticBackupSet,
    ): Boolean =
        expected.reality == actual.reality &&
            expected.commercialEvidence == actual.commercialEvidence &&
            expected.webEvidence == actual.webEvidence &&
            expected.auxiliary == actual.auxiliary
}

class SemanticRestoreInjectedCrash(
    message: String,
) : RuntimeException(message)

private data class BackupPayload(
    val reality: RealityBackupSnapshot,
    val commercialEvidence: CommercialEvidenceBackupSnapshot,
    val webEvidence: WebEvidenceBackupSnapshot,
    val auxiliary: AuxiliaryBackupSnapshot,
)

private object BackupManifestBuilder {
    fun build(
        backupSetId: BackupSetId,
        sourceGenerationId: StorageGenerationId,
        productVersion: String,
        createdAtEpochMillis: Long,
        payload: BackupPayload,
    ): BackupSetManifest {
        require(productVersion.isNotBlank() && productVersion.length <= 128)
        require(createdAtEpochMillis >= 0L)

        val sections =
            listOf(
                commercialSection(payload.commercialEvidence),
                auxiliarySection(payload.auxiliary),
                realitySection(payload.reality),
                webSection(payload.webEvidence),
            ).sortedBy { it.sectionId }

        val fingerprint =
            manifestFingerprint(
                backupSetId = backupSetId,
                sourceGenerationId = sourceGenerationId,
                productVersion = productVersion,
                createdAtEpochMillis = createdAtEpochMillis,
                sections = sections,
                classDecisions = BackupClassPolicy.decisions,
            )

        return BackupSetManifest(
            formatVersion = BackupSetManifest.FORMAT_VERSION,
            backupSetId = backupSetId,
            sourceGenerationId = sourceGenerationId,
            productVersion = productVersion,
            createdAtEpochMillis = createdAtEpochMillis,
            sections = sections,
            classDecisions = BackupClassPolicy.decisions,
            restoreReplayFenceRequired = true,
            setFingerprint = fingerprint,
        )
    }

    fun matches(backupSet: SemanticBackupSet): Boolean =
        runCatching {
            val rebuilt =
                build(
                    backupSetId = backupSet.manifest.backupSetId,
                    sourceGenerationId = backupSet.manifest.sourceGenerationId,
                    productVersion = backupSet.manifest.productVersion,
                    createdAtEpochMillis = backupSet.manifest.createdAtEpochMillis,
                    payload =
                        BackupPayload(
                            reality = backupSet.reality,
                            commercialEvidence = backupSet.commercialEvidence,
                            webEvidence = backupSet.webEvidence,
                            auxiliary = backupSet.auxiliary,
                        ),
                )
            rebuilt == backupSet.manifest
        }.getOrDefault(false)

    private fun realitySection(snapshot: RealityBackupSnapshot): BackupSectionManifest {
        val hash = CanonicalHashBuilder.domain("faro-v5-backup-reality-v1")
            .string("schemaVersion", snapshot.schemaVersion.toString())
            .string("profileRevision", snapshot.profileRevision.toString())
            .string("financeRevision", snapshot.financeRevision.toString())
            .string("profile", snapshot.domain.declaredDisplayName ?: "")
        snapshot.domain.accounts.forEachIndexed { index, account ->
            addRealityAccount(hash, index, account)
        }
        snapshot.domain.entries.forEachIndexed { index, entry ->
            addRealityEntry(hash, index, entry)
        }
        snapshot.committedReceipts.forEachIndexed { index, receipt ->
            addReceipt(hash, index, receipt)
        }
        return BackupSectionManifest(
            sectionId = "reality",
            schemaVersion = snapshot.schemaVersion,
            revisionToken = snapshot.profileRevision.toString() + ":" + snapshot.financeRevision,
            recordCount =
                snapshot.domain.accounts.size.toLong() +
                    snapshot.domain.entries.size +
                    snapshot.committedReceipts.size +
                    if (snapshot.domain.declaredDisplayName == null) 0L else 1L,
            fingerprint = hash.sha256Hex(),
        )
    }

    private fun commercialSection(
        snapshot: CommercialEvidenceBackupSnapshot,
    ): BackupSectionManifest {
        val hash =
            CanonicalHashBuilder
                .domain("faro-v5-backup-commercial-evidence-v1")
                .string("schemaVersion", snapshot.schemaVersion.toString())
        snapshot.records.forEachIndexed { index, record ->
            addCommercialRecord(hash, index, record)
        }
        return BackupSectionManifest(
            sectionId = "commercial-evidence",
            schemaVersion = snapshot.schemaVersion,
            revisionToken = "records:" + snapshot.records.size,
            recordCount = snapshot.records.size.toLong(),
            fingerprint = hash.sha256Hex(),
        )
    }

    private fun webSection(snapshot: WebEvidenceBackupSnapshot): BackupSectionManifest {
        val hash =
            CanonicalHashBuilder
                .domain("faro-v5-backup-web-evidence-v1")
                .string("schemaVersion", snapshot.schemaVersion.toString())
        snapshot.fetchRecords.forEachIndexed { index, record ->
            addWebFetch(hash, index, record)
        }
        snapshot.searchRecords.forEachIndexed { index, record ->
            addWebSearch(hash, index, record)
        }
        return BackupSectionManifest(
            sectionId = "web-evidence",
            schemaVersion = snapshot.schemaVersion,
            revisionToken =
                "fetch:" + snapshot.fetchRecords.size + ":search:" + snapshot.searchRecords.size,
            recordCount = snapshot.fetchRecords.size.toLong() + snapshot.searchRecords.size,
            fingerprint = hash.sha256Hex(),
        )
    }

    private fun auxiliarySection(snapshot: AuxiliaryBackupSnapshot): BackupSectionManifest {
        val hash =
            CanonicalHashBuilder
                .domain("faro-v5-backup-auxiliary-v1")
                .string("schemaVersion", snapshot.schemaVersion.toString())
        snapshot.notices.forEachIndexed { index, notice ->
            hash
                .string("notice.$index.id", notice.noticeId.value)
                .string("notice.$index.key", notice.noticeKey.value)
                .string("notice.$index.source", notice.sourceRef.value)
                .string("notice.$index.occurrence", notice.occurrenceRef.value)
                .string("notice.$index.materiality", notice.materiality.name)
                .string("notice.$index.sensitivity", notice.sensitivity.name)
                .string("notice.$index.summary", notice.summary.value)
                .string("notice.$index.destination", notice.destinationRef.value)
                .string("notice.$index.relevance", notice.relevance.name)
                .string("notice.$index.attention", notice.attention.name)
                .string("notice.$index.revision", notice.revision.toString())
                .string(
                    "notice.$index.resolution",
                    notice.resolutionReason?.name ?: "",
                )
        }
        return BackupSectionManifest(
            sectionId = "auxiliary",
            schemaVersion = snapshot.schemaVersion,
            revisionToken = "notices:" + snapshot.notices.size,
            recordCount = snapshot.notices.size.toLong(),
            fingerprint = hash.sha256Hex(),
        )
    }

    private fun manifestFingerprint(
        backupSetId: BackupSetId,
        sourceGenerationId: StorageGenerationId,
        productVersion: String,
        createdAtEpochMillis: Long,
        sections: List<BackupSectionManifest>,
        classDecisions: List<BackupClassDecision>,
    ): String {
        val hash =
            CanonicalHashBuilder
                .domain("faro-v5-backup-set-manifest-v1")
                .string("formatVersion", BackupSetManifest.FORMAT_VERSION.toString())
                .string("backupSetId", backupSetId.value)
                .string("sourceGenerationId", sourceGenerationId.value)
                .string("productVersion", productVersion)
                .string("createdAtEpochMillis", createdAtEpochMillis.toString())
                .string("restoreReplayFenceRequired", "true")
        sections.forEachIndexed { index, section ->
            hash
                .string("section.$index.id", section.sectionId)
                .string("section.$index.schema", section.schemaVersion.toString())
                .string("section.$index.revision", section.revisionToken)
                .string("section.$index.count", section.recordCount.toString())
                .string("section.$index.fingerprint", section.fingerprint)
        }
        classDecisions.forEachIndexed { index, decision ->
            hash
                .string("class.$index.content", decision.contentClass.name)
                .string("class.$index.state", decision.stateClass.name)
                .string("class.$index.included", decision.included.toString())
                .string("class.$index.reason", decision.reason)
        }
        return hash.sha256Hex()
    }

    private fun addRealityAccount(
        hash: CanonicalHashBuilder,
        index: Int,
        account: RealityMigrationAccount,
    ) {
        hash
            .string("account.$index.id", account.accountId)
            .string("account.$index.name", account.displayName)
            .string("account.$index.currency", account.currencyCode)
            .string("account.$index.nature", account.nature)
            .string("account.$index.opening", account.openingBalanceMinor.toString())
    }

    private fun addRealityEntry(
        hash: CanonicalHashBuilder,
        index: Int,
        entry: RealityMigrationEntry,
    ) {
        hash
            .string("entry.$index.id", entry.entryId)
            .string("entry.$index.account", entry.accountId)
            .string("entry.$index.amount", entry.amountMinor.toString())
            .string("entry.$index.kind", entry.kind)
            .string("entry.$index.concept", entry.concept)
            .string("entry.$index.category", entry.category)
            .string("entry.$index.effectiveAt", entry.effectiveAtEpochMillis.toString())
            .string("entry.$index.recordedAt", entry.recordedAtEpochMillis.toString())
            .string("entry.$index.provenance", entry.provenanceLabel ?: "")
    }

    private fun addReceipt(
        hash: CanonicalHashBuilder,
        index: Int,
        receipt: RealityCommittedReceiptBackup,
    ) {
        hash
            .string("receipt.$index.id", receipt.receiptId)
            .string("receipt.$index.transaction", receipt.transactionId)
            .string("receipt.$index.plan", receipt.planFingerprint)
    }

    private fun addCommercialRecord(
        hash: CanonicalHashBuilder,
        index: Int,
        record: CommercialEvidenceRecord,
    ) {
        hash
            .string("record.$index.id", record.evidenceId.value)
            .string("record.$index.idempotency", record.idempotencyKey.value)
            .string("record.$index.payloadFingerprint", record.payloadFingerprint.value)
            .string("record.$index.product", record.observation.productKey.value)
            .string("record.$index.merchant", record.observation.merchant.value)
            .string("record.$index.currency", record.observation.currencyCode.value)
            .string("record.$index.amount", record.observation.amountMinor.toString())
            .string("record.$index.sourceKind", record.observation.sourceKind.name)
            .string("record.$index.sourceRef", record.observation.sourceRef)
            .string(
                "record.$index.observedAt",
                record.observation.observedAtEpochMillis?.toString() ?: "",
            )
            .string("record.$index.acquiredAt", record.acquiredAtEpochMillis.toString())
    }

    private fun addWebFetch(
        hash: CanonicalHashBuilder,
        index: Int,
        record: WebEvidenceRecord,
    ) {
        hash
            .string("fetch.$index.id", record.evidenceId.value)
            .string("fetch.$index.idempotency", record.idempotencyKey.value)
            .string("fetch.$index.payloadFingerprint", record.payloadFingerprint.value)
            .string("fetch.$index.requestedUrl", record.requestedUrl)
            .string("fetch.$index.finalUrl", record.finalUrl)
            .string("fetch.$index.method", record.method.name)
            .string("fetch.$index.status", record.statusCode.toString())
            .string("fetch.$index.mediaType", record.mediaType ?: "")
            .string("fetch.$index.redirects", record.redirectCount.toString())
            .string("fetch.$index.acquiredAt", record.acquiredAtEpochMillis.toString())
            .string("fetch.$index.contentSha256", record.contentSha256Hex ?: "")
            .string("fetch.$index.contentBytes", record.contentBytes?.toString() ?: "")
            .string("fetch.$index.policyVersion", record.policyVersion)
            .string("fetch.$index.fetcherVersion", record.fetcherVersion)
            .string("fetch.$index.trust", record.trust.name)
    }

    private fun addWebSearch(
        hash: CanonicalHashBuilder,
        index: Int,
        record: WebSearchEvidenceRecord,
    ) {
        hash
            .string("search.$index.id", record.evidenceId.value)
            .string("search.$index.idempotency", record.idempotencyKey.value)
            .string("search.$index.payloadFingerprint", record.payloadFingerprint.value)
            .string("search.$index.query", record.query)
            .string("search.$index.maxResults", record.maxResults.toString())
            .string("search.$index.sensitivity", record.sensitivity.name)
            .string("search.$index.providerId", record.providerId)
            .string("search.$index.providerRequestId", record.providerRequestId ?: "")
            .string("search.$index.usageUnit", record.providerUsage?.unitId ?: "")
            .string("search.$index.usageAmount", record.providerUsage?.amount?.toString() ?: "")
            .string("search.$index.acquiredAt", record.acquiredAtEpochMillis.toString())
            .string("search.$index.policyVersion", record.policyVersion)
            .string("search.$index.adapterVersion", record.adapterVersion)
            .string("search.$index.trust", record.trust.name)
        record.hits.forEachIndexed { hitIndex, hit ->
            hash
                .string("search.$index.hit.$hitIndex.title", hit.title)
                .string("search.$index.hit.$hitIndex.url", hit.url)
                .string("search.$index.hit.$hitIndex.snippet", hit.snippet)
                .string("search.$index.hit.$hitIndex.score", hit.score?.toString() ?: "")
                .string("search.$index.hit.$hitIndex.trust", hit.trust.name)
        }
    }
}
