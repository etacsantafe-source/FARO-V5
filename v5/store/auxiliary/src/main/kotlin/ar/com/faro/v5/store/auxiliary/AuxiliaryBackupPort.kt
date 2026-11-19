package ar.com.faro.v5.store.auxiliary

import ar.com.faro.v5.delivery.api.NoticeAttention
import ar.com.faro.v5.delivery.api.NoticeId
import ar.com.faro.v5.delivery.api.NoticeSnapshot
import ar.com.faro.v5.kernel.contracts.CanonicalHashBuilder

data class AuxiliaryBackupSnapshot(
    val schemaVersion: Int,
    val notices: List<NoticeSnapshot>,
    /** Null is legacy absence, never proof that Guardados was empty. */
    val bookmarks: BookmarkBackupSnapshot? = null,
) {
    init {
        require(schemaVersion >= 0)
        bookmarks?.validate()
        require(
            notices == notices.sortedWith(
                compareBy<NoticeSnapshot>({ it.noticeKey.value }, { it.occurrenceRef.value })
            )
        ) { "Auxiliary backup notices must be in canonical order" }
        require(notices.map { it.noticeId.value }.distinct().size == notices.size)
        require(
            notices.map { it.noticeKey.value to it.occurrenceRef.value }.distinct().size ==
                notices.size
        )
    }
}

interface AuxiliaryBackupPort {
    fun snapshot(): AuxiliaryBackupSnapshot

    /**
     * Restores retained Notice authority only. DeliveryIntent rows are Class D
     * and deliberately remain absent until post-restore reprojection.
     */
    fun restoreOrVerify(snapshot: AuxiliaryBackupSnapshot): AuxiliaryBackupSnapshot
}

/**
 * Auxiliary4 durable data. The base is the common Auxiliary3 projection used
 * in the legacy FBS2 block. An empty ledger is an explicit value in schema 4.
 */
data class AuxiliaryReceiptBackupSnapshot(
    val base: AuxiliaryBackupSnapshot,
    val receipts: List<S4W03NoticeAttentionReceiptV4>,
) {
    init {
        validate()
    }

    internal fun validate() {
        require(base.schemaVersion == 3 && base.bookmarks != null)
        require(receipts.zipWithNext().all { (left, right) ->
            left.requestKey < right.requestKey
        }) { "Receipt backup keys must be sorted and unique" }
        val notices = base.notices.associateBy { it.noticeId }
        val usedRevisions = mutableSetOf<Pair<NoticeId, Long>>()
        for (receipt in receipts) {
            val notice = requireNotNull(notices[receipt.noticeId]) {
                "Receipt backup must retain the referenced Notice"
            }
            require(notice.sourceRef.value.startsWith("reminder:") &&
                notice.noticeKey.value.startsWith("reminder:") &&
                notice.destinationRef.value == "faro:avisos.local")
            require(receipt.resultRevision in 2L..notice.revision &&
                usedRevisions.add(receipt.noticeId to receipt.resultRevision) &&
                (receipt.resultRevision != notice.revision || receipt.target == notice.attention))
            require(receipt.requestHash == noticeAttentionReceiptHash(
                receipt.noticeId, receipt.expectedRevision, receipt.target,
            )) { "Receipt backup fingerprint mismatch" }
        }
    }
}

internal fun noticeAttentionReceiptHash(
    noticeId: NoticeId,
    expectedRevision: Long,
    target: NoticeAttention,
): String = CanonicalHashBuilder.domain("faro-notice-attention-receipt-v4")
    .string("noticeId", noticeId.value)
    .string("expectedRevision", expectedRevision.toString())
    .string("targetAttention", target.name)
    .sha256Hex()
