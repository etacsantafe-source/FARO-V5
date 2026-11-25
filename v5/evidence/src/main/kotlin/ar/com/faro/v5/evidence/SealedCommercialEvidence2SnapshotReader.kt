package ar.com.faro.v5.evidence

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.driver.bundled.SQLITE_OPEN_NOFOLLOW
import androidx.sqlite.driver.bundled.SQLITE_OPEN_READONLY
import androidx.sqlite.driver.bundled.SQLITE_OPEN_URI
import ar.com.faro.v5.kernel.contracts.IdempotencyKey
import ar.com.faro.v5.kernel.contracts.PayloadFingerprint
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path

/**
 * W05 C2B1A: immutable STAGED Evidence2 only. Full typed semantic readback,
 * including the distinction between a missing terms row and explicit UNKNOWN.
 * Never opens a writable Evidence owner, migrates a schema or creates SQLite WAL.
 */
object SealedCommercialEvidence2SnapshotReader {
    fun read(path: Path): CommercialEvidenceStagedTypedBackupSnapshot {
        val file = path.toAbsolutePath().normalize()
        fun requireSealed() {
            val parent = checkNotNull(file.parent)
            check(Files.isDirectory(parent, NOFOLLOW_LINKS) &&
                parent.toRealPath() == parent &&
                Files.isRegularFile(file, NOFOLLOW_LINKS) &&
                !Files.isSymbolicLink(file) &&
                (Files.getAttribute(file, "unix:nlink", NOFOLLOW_LINKS) as Number).toLong() == 1L) {
                "Evidence2 semantic source is not a canonical sealed regular file"
            }
            for (suffix in listOf("-wal", "-shm", "-journal")) {
                check(Files.notExists(file.resolveSibling(file.fileName.toString() + suffix),
                    NOFOLLOW_LINKS)) { "Evidence2 semantic source contains SQLite sidecar" }
            }
            val header = Files.newInputStream(file, NOFOLLOW_LINKS).use { it.readNBytes(20) }
            check(header.size == 20 && header[18] == 2.toByte() &&
                header[19] == 2.toByte()) { "Evidence2 semantic source must be sealed WAL format" }
        }

        requireSealed()
        val snapshot = BundledSQLiteDriver().open(
            file.toUri().toString() + "?mode=ro&immutable=1",
            SQLITE_OPEN_READONLY or SQLITE_OPEN_NOFOLLOW or SQLITE_OPEN_URI,
        ).use { db ->
            check(db.prepare("PRAGMA user_version").use {
                it.step() && it.getLong(0) == 2L && !it.step()
            }) { "Evidence2 semantic reader requires exact schema2" }
            check(db.prepare(
                "SELECT 1 FROM sqlite_master WHERE type='trigger' LIMIT 1"
            ).use { !it.step() }) { "Evidence2 semantic source contains trigger" }
            check(db.prepare("PRAGMA quick_check").use {
                it.step() && it.getText(0) == "ok" && !it.step()
            }) { "Evidence2 semantic source integrity failure" }
            check(db.prepare("PRAGMA foreign_key_check").use { !it.step() }) {
                "Evidence2 semantic source foreign key failure"
            }
            val count = db.prepare("SELECT COUNT(*) FROM commercial_evidence").use {
                check(it.step())
                it.getLong(0)
            }
            check(count in 0L..CommercialEvidenceStagedTypedBackupSnapshot.MAX_RECORDS.toLong()) {
                "Evidence2 semantic source exceeds bounded record count"
            }
            val rows = mutableListOf<CommercialEvidenceStagedTypedBackupRow>()
            db.prepare(
                """
                SELECT e.evidence_id, e.idempotency_key, e.payload_fingerprint,
                       e.product_key, e.merchant, e.currency_code, e.amount_minor,
                       e.source_kind, e.source_ref, e.observed_at_epoch_millis,
                       e.acquired_at_epoch_millis,
                       t.terms_fingerprint, t.locality_kind, t.locality_code,
                       t.issued_at_epoch_millis, t.valid_from_epoch_millis,
                       t.valid_until_epoch_millis, t.presentation_quantity_thousandths,
                       t.presentation_unit, t.pack_count, t.payment_method,
                       t.eligibility, t.conditions, t.promotion_kind,
                       t.promotion_value, t.promotion_source_ref
                FROM commercial_evidence AS e
                LEFT JOIN commercial_evidence_quote_terms AS t
                  ON t.evidence_id = e.evidence_id
                ORDER BY e.evidence_id
                """.trimIndent()
            ).use { st ->
                while (st.step()) {
                    check(rows.size < CommercialEvidenceStagedTypedBackupSnapshot.MAX_RECORDS)
                    val record = CommercialEvidenceRecord(
                        evidenceId = CommercialEvidenceId(st.getText(0)),
                        idempotencyKey = IdempotencyKey(st.getText(1)),
                        payloadFingerprint = PayloadFingerprint(st.getText(2)),
                        observation = CommercialPriceObservation(
                            productKey = CommercialProductKey(st.getText(3)),
                            merchant = CommercialMerchant(st.getText(4)),
                            currencyCode = CommercialCurrencyCode(st.getText(5)),
                            amountMinor = st.getLong(6),
                            sourceKind = CommercialEvidenceSourceKind.valueOf(st.getText(7)),
                            sourceRef = st.getText(8),
                            observedAtEpochMillis =
                                if (st.isNull(9)) null else st.getLong(9),
                        ),
                        acquiredAtEpochMillis = st.getLong(10),
                    )
                    val terms = if (st.isNull(11)) null else CommercialEvidenceTypedTerms(
                        localityKind = CommercialLocalityKind.valueOf(st.getText(12)),
                        localityCode = if (st.isNull(13)) null else st.getText(13),
                        issuedAtEpochMillis = if (st.isNull(14)) null else st.getLong(14),
                        validFromEpochMillis = if (st.isNull(15)) null else st.getLong(15),
                        validUntilEpochMillis = if (st.isNull(16)) null else st.getLong(16),
                        presentationQuantityThousandths =
                            if (st.isNull(17)) null else st.getLong(17),
                        presentationUnit = if (st.isNull(18)) null else
                            CommercialPresentationUnit.valueOf(st.getText(18)),
                        packCount = if (st.isNull(19)) null else
                            st.getLong(19).also { check(it in 1L..Int.MAX_VALUE.toLong()) }.toInt(),
                        paymentMethod = CommercialPaymentMethod.valueOf(st.getText(20)),
                        eligibility = CommercialEligibility.valueOf(st.getText(21)),
                        conditions = if (st.isNull(22)) null else st.getText(22),
                        promotionKind = CommercialPromotionKind.valueOf(st.getText(23)),
                        promotionValue = if (st.isNull(24)) null else st.getLong(24),
                        promotionSourceRef = if (st.isNull(25)) null else st.getText(25),
                    )
                    if (terms != null) {
                        check(st.getText(11) == terms.fingerprint()) {
                            "Evidence2 semantic terms fingerprint mismatch"
                        }
                    }
                    rows += CommercialEvidenceStagedTypedBackupRow(record, terms)
                }
            }
            check(rows.size.toLong() == count) {
                "Evidence2 semantic reader row count drift"
            }
            CommercialEvidenceStagedTypedBackupSnapshot(schemaVersion = 2, rows = rows)
        }
        requireSealed()
        return snapshot
    }
}
