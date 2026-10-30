package ar.com.faro.v5.maintenance

import ar.com.faro.v5.delivery.api.DestinationRef
import ar.com.faro.v5.delivery.api.NoticeAttention
import ar.com.faro.v5.delivery.api.NoticeId
import ar.com.faro.v5.delivery.api.NoticeKey
import ar.com.faro.v5.delivery.api.NoticeMateriality
import ar.com.faro.v5.delivery.api.NoticeRelevance
import ar.com.faro.v5.delivery.api.NoticeResolutionReason
import ar.com.faro.v5.delivery.api.NoticeSensitivity
import ar.com.faro.v5.delivery.api.NoticeSnapshot
import ar.com.faro.v5.delivery.api.NoticeSummary
import ar.com.faro.v5.delivery.api.OccurrenceRef
import ar.com.faro.v5.delivery.api.SourceRef
import ar.com.faro.v5.evidence.CommercialCurrencyCode
import ar.com.faro.v5.evidence.CommercialEvidenceBackupSnapshot
import ar.com.faro.v5.evidence.CommercialEvidenceId
import ar.com.faro.v5.evidence.CommercialEvidenceRecord
import ar.com.faro.v5.evidence.CommercialEvidenceSourceKind
import ar.com.faro.v5.evidence.CommercialMerchant
import ar.com.faro.v5.evidence.CommercialPriceObservation
import ar.com.faro.v5.evidence.CommercialProductKey
import ar.com.faro.v5.evidence.EvidenceContentTrust
import ar.com.faro.v5.evidence.WebEvidenceBackupSnapshot
import ar.com.faro.v5.evidence.WebEvidenceId
import ar.com.faro.v5.evidence.WebEvidenceRecord
import ar.com.faro.v5.evidence.WebFetchMethod
import ar.com.faro.v5.evidence.WebProviderUsage
import ar.com.faro.v5.evidence.WebSearchEvidenceRecord
import ar.com.faro.v5.evidence.WebSearchHit
import ar.com.faro.v5.kernel.contracts.EgressSensitivity
import ar.com.faro.v5.kernel.contracts.IdempotencyKey
import ar.com.faro.v5.kernel.contracts.PayloadFingerprint
import ar.com.faro.v5.store.auxiliary.AuxiliaryBackupSnapshot
import ar.com.faro.v5.store.reality.RealityBackupSnapshot
import ar.com.faro.v5.store.reality.RealityCommittedReceiptBackup
import ar.com.faro.v5.store.reality.RealityMigrationAccount
import ar.com.faro.v5.store.reality.RealityMigrationEntry
import ar.com.faro.v5.store.reality.RealitySemanticMigrationData
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/**
 * Canonical plaintext codec for the semantic A5 backup set.
 *
 * This representation is never active Truth. A6 encrypts/authenticates these
 * bytes before any external transfer. All counts and byte lengths are bounded
 * before allocation.
 */
object SemanticBackupSetCodec {
    private const val MAGIC = 0x46425331 // FBS1
    private const val VERSION = 1
    const val MAX_PLAINTEXT_BYTES: Int = 64 * 1024 * 1024
    private const val MAX_STRING_BYTES = 64 * 1024
    private const val MAX_RECORDS = 100_000
    private const val MAX_SECTIONS = 16
    private const val MAX_HITS = 20

    fun encode(backupSet: SemanticBackupSet): ByteArray =
        encodeWithin(backupSet, MAX_PLAINTEXT_BYTES)

    /** Early write admission for the future five-store FBS2 container. V1 wire bytes are unchanged. */
    fun encodeWithin(backupSet: SemanticBackupSet, maxBytes: Int): ByteArray {
        require(maxBytes in 1..MAX_PLAINTEXT_BYTES) { "invalid semantic backup byte budget" }
        require(SemanticBackupSetIntegrity.matches(backupSet)) {
            "semantic backup set must validate before encoding"
        }
        val bytes =
            BoundedByteArrayOutputStream(maxBytes).use { buffer ->
                DataOutputStream(buffer).use { output ->
                    output.writeInt(MAGIC)
                    output.writeInt(VERSION)
                    writeManifest(output, backupSet.manifest)
                    writeReality(output, backupSet.reality)
                    writeCommercial(output, backupSet.commercialEvidence)
                    writeWeb(output, backupSet.webEvidence)
                    writeAuxiliary(output, backupSet.auxiliary)
                }
                buffer.toByteArray()
            }
        require(bytes.isNotEmpty() && bytes.size <= maxBytes) {
            "semantic backup plaintext exceeds bound"
        }
        return bytes
    }

    fun decode(bytes: ByteArray): SemanticBackupSet {
        check(bytes.isNotEmpty() && bytes.size <= MAX_PLAINTEXT_BYTES) {
            "semantic backup plaintext outside bound"
        }
        return DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            check(input.readInt() == MAGIC) { "semantic backup magic mismatch" }
            check(input.readInt() == VERSION) { "unsupported semantic backup codec version" }
            val decoded =
                SemanticBackupSet(
                    manifest = readManifest(input),
                    reality = readReality(input),
                    commercialEvidence = readCommercial(input),
                    webEvidence = readWeb(input),
                    auxiliary = readAuxiliary(input),
                )
            check(input.available() == 0) { "semantic backup plaintext has trailing bytes" }
            check(SemanticBackupSetIntegrity.matches(decoded)) {
                "semantic backup manifest/payload mismatch"
            }
            decoded
        }
    }

    private fun writeManifest(output: DataOutputStream, manifest: BackupSetManifest) {
        output.writeInt(manifest.formatVersion)
        writeString(output, manifest.backupSetId.value)
        writeString(output, manifest.sourceGenerationId.value)
        writeString(output, manifest.productVersion)
        output.writeLong(manifest.createdAtEpochMillis)
        writeCount(output, manifest.sections.size, MAX_SECTIONS)
        manifest.sections.forEach { section ->
            writeString(output, section.sectionId)
            output.writeInt(section.schemaVersion)
            writeString(output, section.revisionToken)
            output.writeLong(section.recordCount)
            writeString(output, section.fingerprint)
        }
        output.writeBoolean(manifest.restoreReplayFenceRequired)
        writeString(output, manifest.setFingerprint)
    }

    private fun readManifest(input: DataInputStream): BackupSetManifest {
        val formatVersion = input.readInt()
        val backupSetId = BackupSetId(readString(input))
        val sourceGenerationId = StorageGenerationId(readString(input))
        val productVersion = readString(input)
        val createdAt = input.readLong()
        val sectionCount = readCount(input, MAX_SECTIONS)
        val sections =
            buildList(sectionCount) {
                repeat(sectionCount) {
                    add(
                        BackupSectionManifest(
                            sectionId = readString(input),
                            schemaVersion = input.readInt(),
                            revisionToken = readString(input),
                            recordCount = input.readLong(),
                            fingerprint = readString(input),
                        )
                    )
                }
            }
        return BackupSetManifest(
            formatVersion = formatVersion,
            backupSetId = backupSetId,
            sourceGenerationId = sourceGenerationId,
            productVersion = productVersion,
            createdAtEpochMillis = createdAt,
            sections = sections,
            classDecisions = BackupClassPolicy.decisions,
            restoreReplayFenceRequired = input.readBoolean(),
            setFingerprint = readString(input),
        )
    }

    private fun writeReality(output: DataOutputStream, snapshot: RealityBackupSnapshot) {
        output.writeInt(snapshot.schemaVersion)
        output.writeLong(snapshot.profileRevision)
        output.writeLong(snapshot.financeRevision)
        writeNullableString(output, snapshot.domain.declaredDisplayName)

        writeCount(output, snapshot.domain.accounts.size, MAX_RECORDS)
        snapshot.domain.accounts.forEach { account ->
            writeString(output, account.accountId)
            writeString(output, account.displayName)
            writeString(output, account.currencyCode)
            writeString(output, account.nature)
            output.writeLong(account.openingBalanceMinor)
        }

        writeCount(output, snapshot.domain.entries.size, MAX_RECORDS)
        snapshot.domain.entries.forEach { entry ->
            writeString(output, entry.entryId)
            writeString(output, entry.accountId)
            output.writeLong(entry.amountMinor)
            writeString(output, entry.kind)
            writeString(output, entry.concept)
            writeString(output, entry.category)
            output.writeLong(entry.effectiveAtEpochMillis)
            output.writeLong(entry.recordedAtEpochMillis)
            writeNullableString(output, entry.provenanceLabel)
        }

        writeCount(output, snapshot.committedReceipts.size, MAX_RECORDS)
        snapshot.committedReceipts.forEach { receipt ->
            writeString(output, receipt.receiptId)
            writeString(output, receipt.transactionId)
            writeString(output, receipt.planFingerprint)
        }
    }

    private fun readReality(input: DataInputStream): RealityBackupSnapshot {
        val schemaVersion = input.readInt()
        val profileRevision = input.readLong()
        val financeRevision = input.readLong()
        val displayName = readNullableString(input)

        val accountCount = readCount(input, MAX_RECORDS)
        val accounts =
            buildList(accountCount) {
                repeat(accountCount) {
                    add(
                        RealityMigrationAccount(
                            accountId = readString(input),
                            displayName = readString(input),
                            currencyCode = readString(input),
                            nature = readString(input),
                            openingBalanceMinor = input.readLong(),
                        )
                    )
                }
            }

        val entryCount = readCount(input, MAX_RECORDS)
        val entries =
            buildList(entryCount) {
                repeat(entryCount) {
                    add(
                        RealityMigrationEntry(
                            entryId = readString(input),
                            accountId = readString(input),
                            amountMinor = input.readLong(),
                            kind = readString(input),
                            concept = readString(input),
                            category = readString(input),
                            effectiveAtEpochMillis = input.readLong(),
                            recordedAtEpochMillis = input.readLong(),
                            provenanceLabel = readNullableString(input),
                        )
                    )
                }
            }

        val receiptCount = readCount(input, MAX_RECORDS)
        val receipts =
            buildList(receiptCount) {
                repeat(receiptCount) {
                    add(
                        RealityCommittedReceiptBackup(
                            receiptId = readString(input),
                            transactionId = readString(input),
                            planFingerprint = readString(input),
                        )
                    )
                }
            }

        return RealityBackupSnapshot(
            schemaVersion = schemaVersion,
            profileRevision = profileRevision,
            financeRevision = financeRevision,
            domain =
                RealitySemanticMigrationData(
                    declaredDisplayName = displayName,
                    accounts = accounts,
                    entries = entries,
                ),
            committedReceipts = receipts,
        )
    }

    private fun writeCommercial(
        output: DataOutputStream,
        snapshot: CommercialEvidenceBackupSnapshot,
    ) {
        output.writeInt(snapshot.schemaVersion)
        writeCount(output, snapshot.records.size, MAX_RECORDS)
        snapshot.records.forEach { record ->
            writeString(output, record.evidenceId.value)
            writeString(output, record.idempotencyKey.value)
            writeString(output, record.payloadFingerprint.value)
            writeString(output, record.observation.productKey.value)
            writeString(output, record.observation.merchant.value)
            writeString(output, record.observation.currencyCode.value)
            output.writeLong(record.observation.amountMinor)
            writeString(output, record.observation.sourceKind.name)
            writeString(output, record.observation.sourceRef)
            output.writeBoolean(record.observation.observedAtEpochMillis != null)
            record.observation.observedAtEpochMillis?.let(output::writeLong)
            output.writeLong(record.acquiredAtEpochMillis)
        }
    }

    private fun readCommercial(input: DataInputStream): CommercialEvidenceBackupSnapshot {
        val schemaVersion = input.readInt()
        val count = readCount(input, MAX_RECORDS)
        val records =
            buildList(count) {
                repeat(count) {
                    val evidenceId = CommercialEvidenceId(readString(input))
                    val idempotency = IdempotencyKey(readString(input))
                    val payloadFingerprint = PayloadFingerprint(readString(input))
                    val observation =
                        CommercialPriceObservation(
                            productKey = CommercialProductKey(readString(input)),
                            merchant = CommercialMerchant(readString(input)),
                            currencyCode = CommercialCurrencyCode(readString(input)),
                            amountMinor = input.readLong(),
                            sourceKind = CommercialEvidenceSourceKind.valueOf(readString(input)),
                            sourceRef = readString(input),
                            observedAtEpochMillis =
                                if (input.readBoolean()) input.readLong() else null,
                        )
                    add(
                        CommercialEvidenceRecord(
                            evidenceId = evidenceId,
                            idempotencyKey = idempotency,
                            payloadFingerprint = payloadFingerprint,
                            observation = observation,
                            acquiredAtEpochMillis = input.readLong(),
                        )
                    )
                }
            }
        return CommercialEvidenceBackupSnapshot(
            schemaVersion = schemaVersion,
            records = records,
        )
    }

    private fun writeWeb(output: DataOutputStream, snapshot: WebEvidenceBackupSnapshot) {
        output.writeInt(snapshot.schemaVersion)
        writeCount(output, snapshot.fetchRecords.size, MAX_RECORDS)
        snapshot.fetchRecords.forEach { record ->
            writeString(output, record.evidenceId.value)
            writeString(output, record.idempotencyKey.value)
            writeString(output, record.payloadFingerprint.value)
            writeString(output, record.requestedUrl)
            writeString(output, record.finalUrl)
            writeString(output, record.method.name)
            output.writeInt(record.statusCode)
            writeNullableString(output, record.mediaType)
            output.writeInt(record.redirectCount)
            output.writeLong(record.acquiredAtEpochMillis)
            writeNullableString(output, record.contentSha256Hex)
            output.writeBoolean(record.contentBytes != null)
            record.contentBytes?.let(output::writeInt)
            writeString(output, record.policyVersion)
            writeString(output, record.fetcherVersion)
            writeString(output, record.trust.name)
        }

        writeCount(output, snapshot.searchRecords.size, MAX_RECORDS)
        snapshot.searchRecords.forEach { record ->
            writeString(output, record.evidenceId.value)
            writeString(output, record.idempotencyKey.value)
            writeString(output, record.payloadFingerprint.value)
            writeString(output, record.query)
            output.writeInt(record.maxResults)
            writeString(output, record.sensitivity.name)
            writeString(output, record.providerId)
            writeNullableString(output, record.providerRequestId)
            output.writeBoolean(record.providerUsage != null)
            record.providerUsage?.let { usage ->
                writeString(output, usage.unitId)
                output.writeLong(usage.amount)
            }
            output.writeLong(record.acquiredAtEpochMillis)
            writeString(output, record.policyVersion)
            writeString(output, record.adapterVersion)
            writeCount(output, record.hits.size, MAX_HITS)
            record.hits.forEach { hit ->
                writeString(output, hit.title)
                writeString(output, hit.url)
                writeString(output, hit.snippet)
                output.writeBoolean(hit.score != null)
                hit.score?.let(output::writeDouble)
                writeString(output, hit.trust.name)
            }
            writeString(output, record.trust.name)
        }
    }

    private fun readWeb(input: DataInputStream): WebEvidenceBackupSnapshot {
        val schemaVersion = input.readInt()
        val fetchCount = readCount(input, MAX_RECORDS)
        val fetchRecords =
            buildList(fetchCount) {
                repeat(fetchCount) {
                    add(
                        WebEvidenceRecord(
                            evidenceId = WebEvidenceId(readString(input)),
                            idempotencyKey = IdempotencyKey(readString(input)),
                            payloadFingerprint = PayloadFingerprint(readString(input)),
                            requestedUrl = readString(input),
                            finalUrl = readString(input),
                            method = WebFetchMethod.valueOf(readString(input)),
                            statusCode = input.readInt(),
                            mediaType = readNullableString(input),
                            redirectCount = input.readInt(),
                            acquiredAtEpochMillis = input.readLong(),
                            contentSha256Hex = readNullableString(input),
                            contentBytes =
                                if (input.readBoolean()) input.readInt() else null,
                            policyVersion = readString(input),
                            fetcherVersion = readString(input),
                            trust = EvidenceContentTrust.valueOf(readString(input)),
                        )
                    )
                }
            }

        val searchCount = readCount(input, MAX_RECORDS)
        val searchRecords =
            buildList(searchCount) {
                repeat(searchCount) {
                    val evidenceId = WebEvidenceId(readString(input))
                    val idempotencyKey = IdempotencyKey(readString(input))
                    val payloadFingerprint = PayloadFingerprint(readString(input))
                    val query = readString(input)
                    val maxResults = input.readInt()
                    val sensitivity = EgressSensitivity.valueOf(readString(input))
                    val providerId = readString(input)
                    val providerRequestId = readNullableString(input)
                    val usage =
                        if (input.readBoolean()) {
                            WebProviderUsage(
                                unitId = readString(input),
                                amount = input.readLong(),
                            )
                        } else {
                            null
                        }
                    val acquiredAt = input.readLong()
                    val policyVersion = readString(input)
                    val adapterVersion = readString(input)
                    val hitCount = readCount(input, MAX_HITS)
                    val hits =
                        buildList(hitCount) {
                            repeat(hitCount) {
                                add(
                                    WebSearchHit(
                                        title = readString(input),
                                        url = readString(input),
                                        snippet = readString(input),
                                        score =
                                            if (input.readBoolean()) input.readDouble() else null,
                                        trust = EvidenceContentTrust.valueOf(readString(input)),
                                    )
                                )
                            }
                        }
                    add(
                        WebSearchEvidenceRecord(
                            evidenceId = evidenceId,
                            idempotencyKey = idempotencyKey,
                            payloadFingerprint = payloadFingerprint,
                            query = query,
                            maxResults = maxResults,
                            sensitivity = sensitivity,
                            providerId = providerId,
                            providerRequestId = providerRequestId,
                            providerUsage = usage,
                            acquiredAtEpochMillis = acquiredAt,
                            policyVersion = policyVersion,
                            adapterVersion = adapterVersion,
                            hits = hits,
                            trust = EvidenceContentTrust.valueOf(readString(input)),
                        )
                    )
                }
            }

        return WebEvidenceBackupSnapshot(
            schemaVersion = schemaVersion,
            fetchRecords = fetchRecords,
            searchRecords = searchRecords,
        )
    }

    private fun writeAuxiliary(output: DataOutputStream, snapshot: AuxiliaryBackupSnapshot) {
        output.writeInt(snapshot.schemaVersion)
        writeCount(output, snapshot.notices.size, MAX_RECORDS)
        snapshot.notices.forEach { notice ->
            writeString(output, notice.noticeId.value)
            writeString(output, notice.noticeKey.value)
            writeString(output, notice.sourceRef.value)
            writeString(output, notice.occurrenceRef.value)
            writeString(output, notice.materiality.name)
            writeString(output, notice.sensitivity.name)
            writeString(output, notice.summary.value)
            writeString(output, notice.destinationRef.value)
            writeString(output, notice.relevance.name)
            writeString(output, notice.attention.name)
            output.writeLong(notice.revision)
            writeNullableString(output, notice.resolutionReason?.name)
        }
    }

    private fun readAuxiliary(input: DataInputStream): AuxiliaryBackupSnapshot {
        val schemaVersion = input.readInt()
        val count = readCount(input, MAX_RECORDS)
        val notices =
            buildList(count) {
                repeat(count) {
                    add(
                        NoticeSnapshot(
                            noticeId = NoticeId.of(readString(input)),
                            noticeKey = NoticeKey.of(readString(input)),
                            sourceRef = SourceRef.of(readString(input)),
                            occurrenceRef = OccurrenceRef.of(readString(input)),
                            materiality = NoticeMateriality.valueOf(readString(input)),
                            sensitivity = NoticeSensitivity.valueOf(readString(input)),
                            summary = NoticeSummary.of(readString(input)),
                            destinationRef = DestinationRef.of(readString(input)),
                            relevance = NoticeRelevance.valueOf(readString(input)),
                            attention = NoticeAttention.valueOf(readString(input)),
                            revision = input.readLong(),
                            resolutionReason =
                                readNullableString(input)?.let(NoticeResolutionReason::valueOf),
                        )
                    )
                }
            }
        return AuxiliaryBackupSnapshot(
            schemaVersion = schemaVersion,
            notices = notices,
        )
    }

    private class BoundedByteArrayOutputStream(
        private val maxBytes: Int,
    ) : ByteArrayOutputStream() {
        override fun write(value: Int) {
            check(size() < maxBytes) { "semantic backup plaintext exceeds bound" }
            super.write(value)
        }

        override fun write(
            bytes: ByteArray,
            offset: Int,
            length: Int,
        ) {
            require(offset >= 0 && length >= 0 && offset + length <= bytes.size)
            check(length <= maxBytes - size()) {
                "semantic backup plaintext exceeds bound"
            }
            super.write(bytes, offset, length)
        }
    }

    private fun writeString(output: DataOutputStream, value: String) {
        val encoder =
            StandardCharsets.UTF_8
                .newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
        val encoded = encoder.encode(CharBuffer.wrap(value))
        val bytes = ByteArray(encoded.remaining())
        encoded.get(bytes)
        require(bytes.size <= MAX_STRING_BYTES) { "semantic backup string exceeds bound" }
        output.writeInt(bytes.size)
        output.write(bytes)
    }

    private fun readString(input: DataInputStream): String {
        val size = input.readInt()
        check(size in 0..MAX_STRING_BYTES) { "semantic backup string length outside bound" }
        check(input.available() >= size) { "semantic backup string truncated" }
        val bytes = ByteArray(size)
        input.readFully(bytes)
        val decoder =
            StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
        return decoder.decode(ByteBuffer.wrap(bytes)).toString()
    }

    private fun writeNullableString(output: DataOutputStream, value: String?) {
        output.writeBoolean(value != null)
        value?.let { writeString(output, it) }
    }

    private fun readNullableString(input: DataInputStream): String? =
        if (input.readBoolean()) readString(input) else null

    private fun writeCount(output: DataOutputStream, count: Int, max: Int) {
        require(count in 0..max) { "semantic backup collection count outside bound" }
        output.writeInt(count)
    }

    private fun readCount(input: DataInputStream, max: Int): Int {
        val count = input.readInt()
        check(count in 0..max) { "semantic backup collection count outside bound" }
        return count
    }
}
