package ar.com.faro.v5.app

/** Single shipping authority for permitting the five-owner V2 runtime.
 * False until the COMPLETE S2.A6 F1-F4 lifecycle and physical T4 pass.
 * Both the live graph opener and the pre-switch admission must use this exact value.
 */
object FaroV5HostCertification {
    const val V2_ACTIVE_ENABLED: Boolean = false
}
