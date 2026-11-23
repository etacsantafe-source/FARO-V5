package ar.com.faro.v5.app

import android.content.pm.ApplicationInfo
import android.widget.Button
import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.util.UUID

/**
 * F2C1B1A — real isolated Android DEV process/foreground/pointer anchored
 * HUMAN READ CONSENT, scoped separately to one Household10 need and one
 * Evidence2 product. Consent is NOT a Household/Commercial owner grant.
 *
 * No source, prices, SQLite, Apply, UI selection, payment, or D4 view is
 * opened or mounted. F2C1B1B must obtain and recheck both real owner ACLs
 * and an F2C0 leased read before it may consume either consent bit.
 */
internal class FaroV5W04CommercialDevReadConsentIssuer private constructor(
    private val activity: FaroV5W04CommercialDevActivity,
    private val pointer: FileActiveGenerationPointer,
    private val pinned: ActiveGenerationPointer,
    private val needId: String,
    private val productKey: String,
    private val admittedEpoch: String,
    private val currentEpoch: () -> String?,
    private val ownerReady: () -> Boolean,
    private val sandbox: FaroV5AndroidSandboxPrincipal,
) : AutoCloseable {
    private val monitor = Any()
    private val firstSandboxEvidence = sandbox.currentEvidenceOrNull()
    private var revoked = firstSandboxEvidence == null
    private var householdConsent: String? = null
    private var commercialConsent: String? = null
    private var householdButton: Button? = null
    private var commercialButton: Button? = null

    /**
     * Bind ONLY trusted Activity-owned, already attached and visible buttons.
     * A Boolean from Intent/model/prefs is never accepted as consent. The host
     * must show these ONLY after presenting an authenticated local selection.
     */
    fun bindTrustedLocalConsentButtons(household: Button, commercial: Button): Boolean =
        synchronized(monitor) {
            if (household === commercial || householdButton != null || commercialButton != null ||
                !validButton(household) || !validButton(commercial) || !currentLocked()
            ) return@synchronized false

            householdButton = household
            commercialButton = commercial
            household.setOnClickListener { view ->
                synchronized(monitor) {
                    if (view === householdButton && validButton(household)) {
                        grantLocked(householdScope = true)
                    }
                }
            }
            commercial.setOnClickListener { view ->
                synchronized(monitor) {
                    if (view === commercialButton && validButton(commercial)) {
                        grantLocked(householdScope = false)
                    }
                }
            }
            true
        }

    /** The two scopes are independently granted and revoked. */
    fun householdConsented(candidateNeedId: String): Boolean = synchronized(monitor) {
        if (!currentLocked()) return@synchronized false
        val approved = candidateNeedId == needId && householdConsent != null
        currentLocked() && approved
    }

    fun commercialConsented(candidateProductKey: String): Boolean = synchronized(monitor) {
        if (!currentLocked()) return@synchronized false
        val approved = candidateProductKey == productKey && commercialConsent != null
        currentLocked() && approved
    }

    fun revokeHouseholdConsent() = synchronized(monitor) { householdConsent = null }
    fun revokeCommercialConsent() = synchronized(monitor) { commercialConsent = null }
    fun isRevoked(): Boolean = synchronized(monitor) { revoked }

    private fun validButton(button: Button) =
        button.context === activity && button.isAttachedToWindow && button.isShown &&
            activity.isForegroundForTrustedRead()

    private fun grantLocked(householdScope: Boolean): Boolean {
        if (!currentLocked()) return false
        val nonce = UUID.randomUUID().toString()
        if (householdScope) householdConsent = nonce else commercialConsent = nonce
        if (!currentLocked()) return false
        return true
    }

    /**
     * Any false observation poisons this issuer irreversibly. A->B->A of
     * generation or foreground never resurrects the old consent token.
     */
    private fun currentLocked(): Boolean {
        if (revoked) return false
        val accepted = firstSandboxEvidence
        val valid = runCatching {
            accepted != null &&
                activity.isForegroundForTrustedRead() &&
                !activity.isFinishing && !activity.isDestroyed &&
                pointer.read() == pinned && currentEpoch() == admittedEpoch &&
                ownerReady() && sandbox.currentEvidenceOrNull() == accepted &&
                pointer.read() == pinned && currentEpoch() == admittedEpoch &&
                ownerReady() && activity.isForegroundForTrustedRead()
        }.getOrDefault(false)
        if (!valid) revokeLocked()
        return valid
    }

    private fun revokeLocked() {
        if (revoked && householdButton == null && commercialButton == null) return
        revoked = true
        householdConsent = null
        commercialConsent = null
        householdButton?.setOnClickListener(null)
        commercialButton?.setOnClickListener(null)
        householdButton = null
        commercialButton = null
        sandbox.close()
    }

    override fun close() = synchronized(monitor) { revokeLocked() }

    companion object {
        private const val DEV_PACKAGE = "ar.com.faro.alpha.w04commercialdev"

        /**
         * Trusted host only; the root must physically belong to this DEV app's
         * private filesDir. It must be the SAME root later used by F2C0.
         * Does not choose, activate or reopen any generation/database.
         */
        fun openForTrustedDevHost(
            activity: FaroV5W04CommercialDevActivity,
            privateRoot: Path,
            pinned: ActiveGenerationPointer,
            selectedNeedId: String,
            selectedProductKey: String,
            hostEpoch: String,
            currentHostEpoch: () -> String?,
            ownerReady: () -> Boolean,
        ): FaroV5W04CommercialDevReadConsentIssuer? = runCatching {
            check(activity.packageName == DEV_PACKAGE)
            check(activity.application is FaroV5W04CommercialDevApplication)
            check((activity.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0)
            check(activity.isForegroundForTrustedRead() && !activity.isFinishing &&
                !activity.isDestroyed)
            check(selectedNeedId.length in 1..128 &&
                selectedNeedId.none { it.isWhitespace() || it.isISOControl() })
            check(selectedProductKey.length in 1..160 &&
                selectedProductKey.isNotBlank() &&
                selectedProductKey == selectedProductKey.trim() &&
                selectedProductKey.none(Char::isISOControl))
            check(hostEpoch.length in 1..160 && hostEpoch == hostEpoch.trim() &&
                hostEpoch.none(Char::isISOControl))

            val filesDir = activity.application.filesDir.toPath().toRealPath()
            val requested = privateRoot.toAbsolutePath().normalize()
            check(Files.isDirectory(requested, NOFOLLOW_LINKS) &&
                !Files.isSymbolicLink(requested))
            val canonical = requested.toRealPath()
            check(canonical != filesDir && canonical.startsWith(filesDir))
            val pointer = FileActiveGenerationPointer(canonical)
            check(pointer.read() == pinned)
            val sandbox = FaroV5AndroidSandboxPrincipal(
                DEV_PACKAGE, pinned, pointer, hostEpoch, currentHostEpoch,
                AndroidPlatformSandboxFactsReader(activity.application),
            )
            val issuer = FaroV5W04CommercialDevReadConsentIssuer(
                activity, pointer, pinned, selectedNeedId, selectedProductKey,
                hostEpoch, currentHostEpoch, ownerReady, sandbox,
            )
            if (issuer.isRevoked() || !issuer.currentLocked()) {
                issuer.close()
                null
            } else issuer
        }.getOrNull()
    }
}
