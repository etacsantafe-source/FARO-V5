package ar.com.faro.v5.store.auxiliary

import ar.com.faro.v5.delivery.api.NoticeSnapshot

data class AuxiliaryBackupSnapshot(
    val schemaVersion: Int,
    val notices: List<NoticeSnapshot>,
) {
    init {
        require(schemaVersion >= 0)
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
