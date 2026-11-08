package ar.com.faro.v5.owner.monthlyplan.spi

import ar.com.faro.v5.kernel.contracts.AuthorityBoundaryId
import ar.com.faro.v5.kernel.contracts.DependencyKey
import ar.com.faro.v5.kernel.contracts.DependencyStamp
import ar.com.faro.v5.kernel.contracts.DependencyStampValue
import ar.com.faro.v5.kernel.contracts.RealityTransactionScope
import ar.com.faro.v5.owner.monthlyplan.api.MonthlyPlanItem
import ar.com.faro.v5.owner.monthlyplan.api.MonthlyPlanSnapshot
import ar.com.faro.v5.owner.monthlyplan.api.MonthlyPlanSnapshotReadApi

/** A schema8 Reality owner SPI; never a Finance payment or Chat binding. */
interface MonthlyPlanPersistencePort : MonthlyPlanSnapshotReadApi {
    fun createItem(
        item: MonthlyPlanItem,
        expectedOwnerRevision: Long,
        transactionScope: RealityTransactionScope,
    ): MonthlyPlanSnapshot
}

/** Owner-wide local revision, not a global generation/revocation fence. */
object MonthlyPlanOwnerDependency {
    val AUTHORITY = AuthorityBoundaryId("reality.monthly_plan")
    val STATE_KEY = DependencyKey("monthly_plan:state")

    fun stamp(snapshot: MonthlyPlanSnapshot): DependencyStamp =
        DependencyStamp(
            authorityBoundary = AUTHORITY,
            dependencyKey = STATE_KEY,
            observedValue = DependencyStampValue("revision:" + snapshot.revision),
        )
}

/** Internal compensation port, not a Finance payment or user-facing cancellation. */
interface MonthlyPlanCancellationPersistencePort : MonthlyPlanSnapshotReadApi {
    fun cancelItem(
        original: MonthlyPlanItem,
        cancelledAtEpochMillis: Long,
        expectedOwnerRevision: Long,
        transactionScope: RealityTransactionScope,
    ): MonthlyPlanSnapshot
}
