package ar.com.faro.v5.kernel.contracts

@JvmInline
value class AuthorityBoundaryId(val value: String) {
    init { require(value.isNotBlank()) { "authority boundary id must not be blank" } }
}

@JvmInline
value class DependencyKey(val value: String) {
    init { require(value.isNotBlank()) { "dependency key must not be blank" } }
}

@JvmInline
value class DependencyStampValue(val value: String) {
    init { require(value.isNotBlank()) { "dependency stamp value must not be blank" } }
}

data class DependencyStamp(
    val authorityBoundary: AuthorityBoundaryId,
    val dependencyKey: DependencyKey,
    val observedValue: DependencyStampValue,
)


private data class DependencyAddress(
    val authorityBoundary: AuthorityBoundaryId,
    val dependencyKey: DependencyKey,
)

class DependencyStampSet private constructor(
    private val stampsByAddress: Map<DependencyAddress, DependencyStamp>,
) {
    fun asSet(): Set<DependencyStamp> = stampsByAddress.values.toSet()

    fun asCanonicalList(): List<DependencyStamp> =
        stampsByAddress.values.sortedWith(
            compareBy<DependencyStamp>(
                { it.authorityBoundary.value },
                { it.dependencyKey.value },
            )
        )

    override fun equals(other: Any?): Boolean =
        other is DependencyStampSet && stampsByAddress == other.stampsByAddress

    override fun hashCode(): Int = stampsByAddress.hashCode()

    override fun toString(): String = "DependencyStampSet(size=" + stampsByAddress.size + ")"

    companion object {
        fun empty(): DependencyStampSet = DependencyStampSet(emptyMap())

        fun of(vararg stamps: DependencyStamp): DependencyStampSet {
            val grouped = stamps.groupBy {
                DependencyAddress(
                    authorityBoundary = it.authorityBoundary,
                    dependencyKey = it.dependencyKey,
                )
            }
            require(grouped.values.none { it.size > 1 }) {
                "dependency stamp set cannot contain the same authority/key more than once"
            }

            return DependencyStampSet(
                grouped.mapValues { (_, entries) -> entries.single() },
            )
        }
    }
}
