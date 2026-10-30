package ar.com.faro.v5.kernel.contracts

enum class EgressSensitivity {
    PUBLIC,
    PRIVATE,
    RESTRICTED,
    SECRET,
    UNCLASSIFIED,
}

enum class DataEgressTarget {
    REMOTE_MODEL,
    ON_DEVICE_MODEL,
}

data class DataEgressPolicyConfig(
    val remoteAllowed: Set<EgressSensitivity>,
    val onDeviceAllowed: Set<EgressSensitivity>,
) {
    init {
        val forbidden = setOf(
            EgressSensitivity.SECRET,
            EgressSensitivity.UNCLASSIFIED,
        )
        require(remoteAllowed.intersect(forbidden).isEmpty()) {
            "remote egress cannot allow SECRET or UNCLASSIFIED data"
        }
        require(onDeviceAllowed.intersect(forbidden).isEmpty()) {
            "on-device model egress cannot allow SECRET or UNCLASSIFIED data"
        }
    }

    fun allowedFor(target: DataEgressTarget): Set<EgressSensitivity> =
        when (target) {
            DataEgressTarget.REMOTE_MODEL -> remoteAllowed
            DataEgressTarget.ON_DEVICE_MODEL -> onDeviceAllowed
        }
}

sealed interface DataEgressDecision {
    data object Allowed : DataEgressDecision

    data class Blocked(
        val fragmentIds: List<ContextFragmentId>,
    ) : DataEgressDecision {
        init {
            require(fragmentIds.isNotEmpty()) {
                "blocked egress must identify at least one fragment"
            }
            require(fragmentIds.size == fragmentIds.distinct().size) {
                "blocked egress fragment ids must be unique"
            }
        }
    }
}

fun interface DataEgressPolicy {
    fun evaluate(
        pack: ContextPack,
        target: DataEgressTarget,
    ): DataEgressDecision
}
