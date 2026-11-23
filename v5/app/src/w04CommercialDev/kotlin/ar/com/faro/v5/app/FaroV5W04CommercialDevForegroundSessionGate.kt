package ar.com.faro.v5.app

import ar.com.faro.v5.generation.FaroV5Household10Evidence2DevSelectionReadPort

/**
 * F2C1A — an isolated DEV foreground lifetime fence around a previously trusted
 * F2C0 selected read port. It DOES NOT issue grants, locate databases, choose
 * observations, synthesize prices, or produce a D4 display.
 *
 * Until F2C1B provides a real issuer and host, the launcher installs no source.
 */
internal class FaroV5W04CommercialDevForegroundSessionGate(
    private val admittedEpoch: String,
    private val currentEpoch: () -> String?,
    private val isForeground: () -> Boolean,
    private val ownerAdmission: () -> Boolean,
    private val householdGrant: (String) -> Boolean,
    private val commercialGrant: (String) -> Boolean,
    private val source: SelectedReadSource,
) : AutoCloseable {
    internal interface SelectedReadSource : AutoCloseable {
        fun readSelected(
            needId: String,
            productKey: String,
            explicitlyChosen: () -> Boolean,
        ): FaroV5Household10Evidence2DevSelectionReadPort.SelectedRead?
    }

    private val monitor = Any()
    @Volatile private var revoked = false

    fun readSelected(
        needId: String,
        productKey: String,
        explicitlyChosen: () -> Boolean,
    ): FaroV5Household10Evidence2DevSelectionReadPort.SelectedRead? = synchronized(monitor) {
        // Lack of an explicit choice is not an authorization attempt.
        if (revoked) return@synchronized null
        if (!runCatching { explicitlyChosen() }.getOrDefault(false)) return@synchronized null
        if (!admitted(needId, productKey, explicitlyChosen)) {
            revokeLocked()
            return@synchronized null
        }
        val reading = runCatching {
            source.readSelected(needId, productKey) {
                admitted(needId, productKey, explicitlyChosen)
            }
        }
        if (reading.isFailure || !admitted(needId, productKey, explicitlyChosen)) {
            // Once a stale epoch, grant or foreground is observed, even ABA of
            // that value cannot resurrect this closed session.
            revokeLocked()
            return@synchronized null
        }
        reading.getOrNull()
    }

    private fun admitted(
        needId: String,
        productKey: String,
        explicitlyChosen: () -> Boolean,
    ) = runCatching {
        !revoked && admittedEpoch.isNotBlank() && admittedEpoch.length <= 160 &&
            needId.length in 1..128 && productKey.length in 1..160 &&
            needId.none { it.isWhitespace() || it.isISOControl() } &&
            productKey.none { it.isISOControl() } &&
            currentEpoch() == admittedEpoch &&
            isForeground() && ownerAdmission() && explicitlyChosen() &&
            householdGrant(needId) && commercialGrant(productKey)
    }.getOrDefault(false)

    fun isRevoked(): Boolean = revoked

    fun revoke() = synchronized(monitor) { revokeLocked() }

    private fun revokeLocked() {
        if (!revoked) {
            revoked = true
            // Fail closed even if the underlying lease close throws.
            runCatching { source.close() }
        }
    }

    override fun close() = revoke()

    companion object {
        /** The sole production-source adapter for a certified, selected F2C0 port. */
        fun fromCertifiedDevPort(
            port: FaroV5Household10Evidence2DevSelectionReadPort,
            admittedEpoch: String,
            currentEpoch: () -> String?,
            isForeground: () -> Boolean,
            ownerAdmission: () -> Boolean,
            householdGrant: (String) -> Boolean,
            commercialGrant: (String) -> Boolean,
        ) = FaroV5W04CommercialDevForegroundSessionGate(
            admittedEpoch, currentEpoch, isForeground, ownerAdmission,
            householdGrant, commercialGrant,
            object : SelectedReadSource {
                override fun readSelected(
                    needId: String,
                    productKey: String,
                    explicitlyChosen: () -> Boolean,
                ) = port.readSelected(needId, productKey, explicitlyChosen)
                override fun close() = port.close()
            },
        )
    }
}
