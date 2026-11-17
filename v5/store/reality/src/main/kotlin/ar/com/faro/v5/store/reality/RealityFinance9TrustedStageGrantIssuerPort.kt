package ar.com.faro.v5.store.reality

/**
 * M1C1B1C DEV-STAGED9 only. A caller MUST have completed actual
 * AndroidKeyStore/BiometricPrompt authentication and an APPROVED canonical
 * review before invoking this port. The DTO itself is NOT an attestation.
 *
 * This factory does NOT exist on the productive Android graph. No UI/Chat
 * code may obtain an issuer or use a forged data object to authorize Apply.
 */
data class Finance9TrustedStageGrantIssueRequest(
    val principalHash: String,
    val generationId: String,
    val pointerSequence: Long,
    val operationId: String,
    val objectScope: String,
    val fingerprintHex: String,
    val intentKey: String,
    val grantId: String,
    val issuedAtMs: Long,
    val expiresAtMs: Long,
) {
    init {
        require(Regex("[0-9a-f]{64}").matches(principalHash))
        require(Regex("[0-9a-f]{64}").matches(fingerprintHex))
        require(pointerSequence > 0L)
        require(issuedAtMs >= 0L && expiresAtMs > issuedAtMs &&
            expiresAtMs - issuedAtMs <= 30_000L)
        for (value in listOf(generationId, operationId, objectScope, intentKey, grantId)) {
            require(value.length in 1..128 && value == value.trim() &&
                value.none { it.code < 32 || it.code == 127 })
        }
    }
}

/**
 * Narrow SQL issuer for an EXTERNALLY verified STAGED9 host only.
 * The Reality9 owner checks schema/fences and serializes the epoch + grant;
 * no public DB handle, biometric API, real-user authorization, or Apply.
 * Policy and grant are separate BEGIN IMMEDIATE transactions: if the grant
 * insertion fails, it issues NO grant and may have advanced policy epoch,
 * conservatively invalidating any older unused grants for the same scope.
 */
fun interface RealityFinance9TrustedStageGrantIssuerPort {
    fun issueAfterExternallyVerifiedStageAdmission(
        request: Finance9TrustedStageGrantIssueRequest,
    ): Finance9TrustedStageGrant?
}
