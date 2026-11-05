package ar.com.faro.v5.app

import ar.com.faro.v5.surface.FaroV5SearchReadPort

/**
 * G2 presentation admission: the genuine host provides BOTH a live Search port
 * and a separately issued G1 owner grant. Never issue, renew or persist ACL.
 * The adapter continues to revalidate every canonical read and hit open.
 */
object FaroV5SearchHostPortAdmission {
    @JvmStatic
    fun admittedPort(
        port: FaroV5SearchReadPort?,
        grantOwner: FaroV5ActiveWorkspaceSearchGrantOwner?,
        recoveryComplete: Boolean,
        localReady: Boolean,
        v2Certified: Boolean,
    ): FaroV5SearchReadPort? {
        if (!recoveryComplete || !localReady || !v2Certified ||
            port == null || grantOwner == null) return null
        return if (grantOwner.epochOrNull() != null) port else null
    }
}
