package ar.com.faro.v5.owner.reminders.spi

import ar.com.faro.v5.kernel.contracts.AuthorityBoundaryId
import ar.com.faro.v5.kernel.contracts.DependencyKey
import ar.com.faro.v5.kernel.contracts.DependencyStamp
import ar.com.faro.v5.kernel.contracts.DependencyStampValue
import ar.com.faro.v5.kernel.contracts.RealityTransactionScope
import ar.com.faro.v5.owner.reminders.api.ReminderRule
import ar.com.faro.v5.owner.reminders.api.ReminderSnapshot
import ar.com.faro.v5.owner.reminders.api.ReminderSnapshotReadApi

/** A schema8 Reality owner SPI, not a Chat or catalog operation binding. */
interface ReminderPersistencePort : ReminderSnapshotReadApi {
    fun createRule(
        rule: ReminderRule,
        expectedOwnerRevision: Long,
        transactionScope: RealityTransactionScope,
    ): ReminderSnapshot
}

/** Owner-wide revision stamp (rules and occurrences); never a user ACL grant. */
object ReminderOwnerDependency {
    val AUTHORITY = AuthorityBoundaryId("reality.reminders")
    val STATE_KEY = DependencyKey("reminders:state")

    fun stamp(snapshot: ReminderSnapshot): DependencyStamp =
        DependencyStamp(
            authorityBoundary = AUTHORITY,
            dependencyKey = STATE_KEY,
            observedValue = DependencyStampValue("revision:" + snapshot.revision),
        )
}
