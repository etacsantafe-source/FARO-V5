package ar.com.faro.v5.maintenance

import ar.com.faro.v5.evidence.CommercialEvidenceMaintenancePort
import ar.com.faro.v5.evidence.CommercialEvidenceMigrationRecord
import ar.com.faro.v5.evidence.CommercialEvidenceSemanticMigrationPort
import ar.com.faro.v5.kernel.contracts.CanonicalHashBuilder
import ar.com.faro.v5.store.auxiliary.AuxiliaryMaintenancePort
import ar.com.faro.v5.store.reality.RealityMaintenancePort
import ar.com.faro.v5.store.reality.RealityMigrationEntry
import ar.com.faro.v5.store.reality.RealitySemanticMigrationData
import ar.com.faro.v5.store.reality.RealitySemanticMigrationPort

data class NormalizedSemanticMigrationSource(
    val sourceSetFingerprint: String,
    val reality: RealitySemanticMigrationData,
    val commercialEvidence: List<CommercialEvidenceMigrationRecord>,
) {
    init {
        require(sourceSetFingerprint.isNotBlank()) {
            "normalized migration source fingerprint must not be blank"
        }
        require(sourceSetFingerprint == sourceSetFingerprint.trim()) {
            "normalized migration source fingerprint must be canonical"
        }
        require(sourceSetFingerprint.length <= 256) {
            "normalized migration source fingerprint is too long"
        }
        require(
            commercialEvidence
                .map { it.sourceIdentityFingerprint }
                .distinct()
                .size == commercialEvidence.size
        ) {
            "normalized migration source contains duplicate commercial identities"
        }
    }
}

data class FinanceSemanticAggregate(
    val accountId: String,
    val currencyCode: String,
    val ledgerMinor: Long,
)

data class FinanceCurrencySemanticAggregate(
    val currencyCode: String,
    val ledgerMinor: Long,
)

data class SemanticMigrationManifest(
    val semanticFingerprint: String,
    val profilePresent: Boolean,
    val financeAccountCount: Int,
    val financeEntryCount: Int,
    val financeStableIdDigest: String,
    val financeAggregates: List<FinanceSemanticAggregate>,
    val financeCurrencyAggregates: List<FinanceCurrencySemanticAggregate>,
    val commercialEvidenceCount: Int,
    val commercialStableIdDigest: String,
)

enum class SemanticMigrationBlockReason {
    SOURCE_INVALID,
    BOOTSTRAP_NOT_STAGED,
    TARGET_GENERATION_NOT_STAGED,
    TARGET_UNHEALTHY,
    TARGET_NOT_FRESH,
    IMPORT_FAILED,
    POST_IMPORT_UNHEALTHY,
    SEMANTIC_MISMATCH,
    JOURNAL_CONFLICT,
}

enum class SemanticMigrationTargetDisposition {
    NO_ACTIVATION,
    DISCARD_STAGED_TARGET,
}

sealed interface SemanticMigrationResult {
    data class Validated(
        val operationId: MaintenanceOperationId,
        val targetGenerationId: StorageGenerationId,
        val sourceSetFingerprint: String,
        val sourceManifest: SemanticMigrationManifest,
        val targetManifest: SemanticMigrationManifest,
        val recoveredFromExistingTarget: Boolean,
    ) : SemanticMigrationResult

    data class Blocked(
        val operationId: MaintenanceOperationId,
        val targetGenerationId: StorageGenerationId?,
        val reason: SemanticMigrationBlockReason,
        val targetDisposition: SemanticMigrationTargetDisposition,
        val failureClass: String? = null,
    ) : SemanticMigrationResult
}

class SemanticMigrationPipeline(
    private val journal: BootstrapJournalPort,
    private val generationLayout: FileStorageGenerationLayout,
    private val realityMaintenance: RealityMaintenancePort,
    private val realityMigration: RealitySemanticMigrationPort,
    private val commercialMaintenance: CommercialEvidenceMaintenancePort,
    private val commercialMigration: CommercialEvidenceSemanticMigrationPort,
    private val auxiliaryMaintenance: AuxiliaryMaintenancePort,
) {
    fun migrate(
        operationId: MaintenanceOperationId,
        source: NormalizedSemanticMigrationSource,
    ): SemanticMigrationResult {
        val sourceManifest =
            try {
                SemanticMigrationManifestBuilder.fromSource(source)
            } catch (failure: Throwable) {
                return SemanticMigrationResult.Blocked(
                    operationId = operationId,
                    targetGenerationId = null,
                    reason = SemanticMigrationBlockReason.SOURCE_INVALID,
                    targetDisposition = SemanticMigrationTargetDisposition.NO_ACTIVATION,
                    failureClass = failure::class.java.simpleName,
                )
            }

        val staged =
            runCatching { journal.read(operationId) }.getOrNull()
                ?: return SemanticMigrationResult.Blocked(
                    operationId = operationId,
                    targetGenerationId = null,
                    reason = SemanticMigrationBlockReason.BOOTSTRAP_NOT_STAGED,
                    targetDisposition = SemanticMigrationTargetDisposition.NO_ACTIVATION,
                )

        if (
            staged.kind != MaintenanceOperationKind.MIGRATION ||
                staged.phase !in setOf(BootstrapPhase.STAGED, BootstrapPhase.VALIDATED) ||
                staged.materialWritesAccepted
        ) {
            return SemanticMigrationResult.Blocked(
                operationId = operationId,
                targetGenerationId = staged.targetGenerationId,
                reason = SemanticMigrationBlockReason.BOOTSTRAP_NOT_STAGED,
                targetDisposition = SemanticMigrationTargetDisposition.NO_ACTIVATION,
            )
        }

        val targetGenerationId = staged.targetGenerationId
        val journalAlreadyValidated = staged.phase == BootstrapPhase.VALIDATED
        try {
            generationLayout.requireStaged(targetGenerationId)
        } catch (failure: Throwable) {
            abortBestEffort(staged)
            return SemanticMigrationResult.Blocked(
                operationId = operationId,
                targetGenerationId = targetGenerationId,
                reason = SemanticMigrationBlockReason.TARGET_GENERATION_NOT_STAGED,
                targetDisposition =
                    SemanticMigrationTargetDisposition.DISCARD_STAGED_TARGET,
                failureClass = failure::class.java.simpleName,
            )
        }
        val realityBefore =
            runCatching { realityMaintenance.inspect() }.getOrElse { failure ->
                abortBestEffort(staged)
                return unhealthyBefore(operationId, targetGenerationId, failure)
            }
        val commercialBefore =
            runCatching { commercialMaintenance.inspect() }.getOrElse { failure ->
                abortBestEffort(staged)
                return unhealthyBefore(operationId, targetGenerationId, failure)
            }
        val auxiliaryBefore =
            runCatching { auxiliaryMaintenance.inspect() }.getOrElse { failure ->
                abortBestEffort(staged)
                return unhealthyBefore(operationId, targetGenerationId, failure)
            }

        if (
            !realityBefore.integrityHealthy ||
                !realityBefore.foreignKeysHealthy ||
                !commercialBefore.integrityHealthy ||
                !commercialBefore.foreignKeysHealthy ||
                !auxiliaryBefore.integrityHealthy ||
                !auxiliaryBefore.foreignKeysHealthy
        ) {
            abortBestEffort(staged)
            return SemanticMigrationResult.Blocked(
                operationId = operationId,
                targetGenerationId = targetGenerationId,
                reason = SemanticMigrationBlockReason.TARGET_UNHEALTHY,
                targetDisposition =
                    SemanticMigrationTargetDisposition.DISCARD_STAGED_TARGET,
            )
        }

        if (
            realityBefore.applyAttemptCount != 0L ||
                realityBefore.executionReceiptCount != 0L ||
                realityBefore.outboxEffectCount != 0L ||
                !auxiliaryBefore.freshForSemanticMigration
        ) {
            abortBestEffort(staged)
            return SemanticMigrationResult.Blocked(
                operationId = operationId,
                targetGenerationId = targetGenerationId,
                reason = SemanticMigrationBlockReason.TARGET_NOT_FRESH,
                targetDisposition =
                    SemanticMigrationTargetDisposition.DISCARD_STAGED_TARGET,
            )
        }

        var recoveredFromExistingTarget = false
        val bothAuthorityTargetsFresh =
            realityBefore.freshForSemanticMigration &&
                commercialBefore.freshForSemanticMigration

        if (!journalAlreadyValidated && bothAuthorityTargetsFresh) {
            try {
                realityMigration.importFresh(source.reality)
                commercialMigration.importFresh(source.commercialEvidence)
            } catch (failure: Throwable) {
                abortBestEffort(staged)
                return SemanticMigrationResult.Blocked(
                    operationId = operationId,
                    targetGenerationId = targetGenerationId,
                    reason = SemanticMigrationBlockReason.IMPORT_FAILED,
                    targetDisposition =
                        SemanticMigrationTargetDisposition.DISCARD_STAGED_TARGET,
                    failureClass = failure::class.java.simpleName,
                )
            }
        } else {
            recoveredFromExistingTarget = true
        }

        val realityAfter =
            runCatching { realityMaintenance.inspect() }.getOrElse { failure ->
                abortBestEffort(staged)
                return unhealthyAfter(operationId, targetGenerationId, failure)
            }
        val commercialAfter =
            runCatching { commercialMaintenance.inspect() }.getOrElse { failure ->
                abortBestEffort(staged)
                return unhealthyAfter(operationId, targetGenerationId, failure)
            }
        val auxiliaryAfter =
            runCatching { auxiliaryMaintenance.inspect() }.getOrElse { failure ->
                abortBestEffort(staged)
                return unhealthyAfter(operationId, targetGenerationId, failure)
            }

        if (
            !realityAfter.integrityHealthy ||
                !realityAfter.foreignKeysHealthy ||
                !commercialAfter.integrityHealthy ||
                !commercialAfter.foreignKeysHealthy ||
                !auxiliaryAfter.integrityHealthy ||
                !auxiliaryAfter.foreignKeysHealthy ||
                !auxiliaryAfter.freshForSemanticMigration ||
                realityAfter.applyAttemptCount != 0L ||
                realityAfter.executionReceiptCount != 0L ||
                realityAfter.outboxEffectCount != 0L
        ) {
            abortBestEffort(staged)
            return SemanticMigrationResult.Blocked(
                operationId = operationId,
                targetGenerationId = targetGenerationId,
                reason = SemanticMigrationBlockReason.POST_IMPORT_UNHEALTHY,
                targetDisposition =
                    SemanticMigrationTargetDisposition.DISCARD_STAGED_TARGET,
            )
        }

        val targetManifest =
            try {
                SemanticMigrationManifestBuilder.fromTarget(
                    reality = realityMigration.snapshot(),
                    commercialEvidence = commercialMigration.snapshot(),
                )
            } catch (failure: Throwable) {
                abortBestEffort(staged)
                return SemanticMigrationResult.Blocked(
                    operationId = operationId,
                    targetGenerationId = targetGenerationId,
                    reason = SemanticMigrationBlockReason.POST_IMPORT_UNHEALTHY,
                    targetDisposition =
                        SemanticMigrationTargetDisposition.DISCARD_STAGED_TARGET,
                    failureClass = failure::class.java.simpleName,
                )
            }

        if (sourceManifest != targetManifest) {
            abortBestEffort(staged)
            return SemanticMigrationResult.Blocked(
                operationId = operationId,
                targetGenerationId = targetGenerationId,
                reason = SemanticMigrationBlockReason.SEMANTIC_MISMATCH,
                targetDisposition =
                    SemanticMigrationTargetDisposition.DISCARD_STAGED_TARGET,
            )
        }

        if (!journalAlreadyValidated) {
            val validated =
                BootstrapTransitionPolicy.transition(
                    staged,
                    BootstrapPhase.VALIDATED,
                )
            if (!journal.compareAndSet(staged, validated)) {
                val observed = journal.read(operationId)
                if (observed != validated) {
                    return SemanticMigrationResult.Blocked(
                        operationId = operationId,
                        targetGenerationId = targetGenerationId,
                        reason = SemanticMigrationBlockReason.JOURNAL_CONFLICT,
                        targetDisposition = SemanticMigrationTargetDisposition.NO_ACTIVATION,
                    )
                }
            }
        }

        return SemanticMigrationResult.Validated(
            operationId = operationId,
            targetGenerationId = targetGenerationId,
            sourceSetFingerprint = source.sourceSetFingerprint,
            sourceManifest = sourceManifest,
            targetManifest = targetManifest,
            recoveredFromExistingTarget = recoveredFromExistingTarget,
        )
    }

    private fun abortBestEffort(
        staged: BootstrapJournalEntry,
    ) {
        val aborted =
            BootstrapTransitionPolicy.transition(
                staged,
                BootstrapPhase.ABORTED_PRE_SWITCH,
            )
        runCatching { journal.compareAndSet(staged, aborted) }
    }

    private fun unhealthyBefore(
        operationId: MaintenanceOperationId,
        targetGenerationId: StorageGenerationId,
        failure: Throwable,
    ): SemanticMigrationResult.Blocked =
        SemanticMigrationResult.Blocked(
            operationId = operationId,
            targetGenerationId = targetGenerationId,
            reason = SemanticMigrationBlockReason.TARGET_UNHEALTHY,
            targetDisposition =
                SemanticMigrationTargetDisposition.DISCARD_STAGED_TARGET,
            failureClass = failure::class.java.simpleName,
        )

    private fun unhealthyAfter(
        operationId: MaintenanceOperationId,
        targetGenerationId: StorageGenerationId,
        failure: Throwable,
    ): SemanticMigrationResult.Blocked =
        SemanticMigrationResult.Blocked(
            operationId = operationId,
            targetGenerationId = targetGenerationId,
            reason = SemanticMigrationBlockReason.POST_IMPORT_UNHEALTHY,
            targetDisposition =
                SemanticMigrationTargetDisposition.DISCARD_STAGED_TARGET,
            failureClass = failure::class.java.simpleName,
        )
}

internal object SemanticMigrationManifestBuilder {
    fun fromSource(
        source: NormalizedSemanticMigrationSource,
    ): SemanticMigrationManifest =
        build(
            reality = source.reality,
            commercialEvidence = source.commercialEvidence,
        )

    fun fromTarget(
        reality: RealitySemanticMigrationData,
        commercialEvidence: List<CommercialEvidenceMigrationRecord>,
    ): SemanticMigrationManifest =
        build(
            reality = reality,
            commercialEvidence = commercialEvidence,
        )

    private fun build(
        reality: RealitySemanticMigrationData,
        commercialEvidence: List<CommercialEvidenceMigrationRecord>,
    ): SemanticMigrationManifest {
        val accounts = reality.accounts.sortedBy { it.accountId }
        val entries =
            reality.entries.sortedWith(
                compareBy<RealityMigrationEntry>(
                    { it.effectiveAtEpochMillis },
                    { it.recordedAtEpochMillis },
                    { it.entryId },
                )
            )
        val commercial =
            commercialEvidence.sortedBy { it.sourceIdentityFingerprint }

        val accountById = accounts.associateBy { it.accountId }
        check(entries.all { it.accountId in accountById }) {
            "semantic manifest entry references missing account"
        }

        val aggregates =
            accounts.map { account ->
                val ledger =
                    entries
                        .filter { it.accountId == account.accountId }
                        .fold(account.openingBalanceMinor) { balance, entry ->
                            when (entry.kind) {
                                "INCOME" -> Math.addExact(balance, entry.amountMinor)
                                "EXPENSE" -> Math.subtractExact(balance, entry.amountMinor)
                                else -> error("unsupported migration finance entry kind")
                            }
                        }
                FinanceSemanticAggregate(
                    accountId = account.accountId,
                    currencyCode = account.currencyCode,
                    ledgerMinor = ledger,
                )
            }

        val currencyAggregates =
            aggregates
                .groupBy { it.currencyCode }
                .toSortedMap()
                .map { (currencyCode, accountAggregates) ->
                    FinanceCurrencySemanticAggregate(
                        currencyCode = currencyCode,
                        ledgerMinor =
                            accountAggregates.fold(0L) { total, aggregate ->
                                Math.addExact(total, aggregate.ledgerMinor)
                            },
                    )
                }

        val semantic =
            CanonicalHashBuilder
                .domain("faro-v5-semantic-migration-manifest-v1")
                .string("profile.present", (reality.declaredDisplayName != null).toString())
                .string("profile.displayName", reality.declaredDisplayName ?: "")
                .string("finance.account.count", accounts.size.toString())
                .string("finance.entry.count", entries.size.toString())
                .string("commercial.count", commercial.size.toString())

        accounts.forEachIndexed { index, account ->
            semantic
                .string("finance.account.$index.id", account.accountId)
                .string("finance.account.$index.name", account.displayName)
                .string("finance.account.$index.currency", account.currencyCode)
                .string("finance.account.$index.nature", account.nature)
                .string(
                    "finance.account.$index.openingBalanceMinor",
                    account.openingBalanceMinor.toString(),
                )
        }
        entries.forEachIndexed { index, entry ->
            semantic
                .string("finance.entry.$index.id", entry.entryId)
                .string("finance.entry.$index.accountId", entry.accountId)
                .string("finance.entry.$index.amountMinor", entry.amountMinor.toString())
                .string("finance.entry.$index.kind", entry.kind)
                .string("finance.entry.$index.concept", entry.concept)
                .string("finance.entry.$index.category", entry.category)
                .string(
                    "finance.entry.$index.effectiveAtEpochMillis",
                    entry.effectiveAtEpochMillis.toString(),
                )
                .string(
                    "finance.entry.$index.recordedAtEpochMillis",
                    entry.recordedAtEpochMillis.toString(),
                )
                .string(
                    "finance.entry.$index.provenanceLabel",
                    entry.provenanceLabel ?: "",
                )
        }
        commercial.forEachIndexed { index, record ->
            semantic
                .string(
                    "commercial.$index.sourceIdentity",
                    record.sourceIdentityFingerprint,
                )
                .string("commercial.$index.productKey", record.productKey)
                .string("commercial.$index.merchant", record.merchant)
                .string("commercial.$index.currency", record.currencyCode)
                .string("commercial.$index.amountMinor", record.amountMinor.toString())
                .string("commercial.$index.sourceRef", record.sourceRef)
                .string(
                    "commercial.$index.observedAtEpochMillis",
                    record.observedAtEpochMillis?.toString() ?: "null",
                )
                .string(
                    "commercial.$index.acquiredAtEpochMillis",
                    record.acquiredAtEpochMillis.toString(),
                )
        }

        return SemanticMigrationManifest(
            semanticFingerprint = semantic.sha256Hex(),
            profilePresent = reality.declaredDisplayName != null,
            financeAccountCount = accounts.size,
            financeEntryCount = entries.size,
            financeStableIdDigest =
                stableIdDigest(
                    domain = "faro-v5-migration-finance-ids-v1",
                    ids =
                        accounts.map { "account:" + it.accountId } +
                            entries.map { "entry:" + it.entryId },
                ),
            financeAggregates = aggregates,
            financeCurrencyAggregates = currencyAggregates,
            commercialEvidenceCount = commercial.size,
            commercialStableIdDigest =
                stableIdDigest(
                    domain = "faro-v5-migration-commercial-ids-v1",
                    ids = commercial.map { it.sourceIdentityFingerprint },
                ),
        )
    }

    private fun stableIdDigest(
        domain: String,
        ids: List<String>,
    ): String {
        val sorted = ids.sorted()
        val builder =
            CanonicalHashBuilder
                .domain(domain)
                .string("count", sorted.size.toString())
        sorted.forEachIndexed { index, id ->
            builder.string("id.$index", id)
        }
        return builder.sha256Hex()
    }
}
