package ar.com.faro.v5.kernel.contracts

fun interface OperationContractLookupPort {
    fun current(operationId: OperationId): OperationContractDescriptor?
}

interface PreparedPlanView {
    val applyPlan: ApplyPlan
    val planFingerprint: PlanFingerprint
}

fun interface DependencyStampLookupPort {
    fun current(
        authorityBoundary: AuthorityBoundaryId,
        dependencyKey: DependencyKey,
    ): DependencyStampValue?
}
