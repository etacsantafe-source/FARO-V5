package ar.com.faro.v5.app

import ar.com.faro.v5.surface.FaroV5SearchCoverage
import ar.com.faro.v5.surface.FaroV5SearchHit
import ar.com.faro.v5.surface.FaroV5SearchOpenResult
import ar.com.faro.v5.surface.FaroV5SearchPage
import ar.com.faro.v5.surface.FaroV5SearchReadPort
import java.util.function.BooleanSupplier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

/** Host-only wiring: a presentation read must NEVER create a new grant. */
class FaroV5SearchHostPortAdmissionTest {
    private val searchPort = object : FaroV5SearchReadPort {
        override val invalidations: Flow<Unit> = emptyFlow()
        override suspend fun search(query: String) =
            FaroV5SearchPage(FaroV5SearchCoverage.UNAVAILABLE, emptyList(), true)
        override suspend fun openAuthorized(hit: FaroV5SearchHit) =
            FaroV5SearchOpenResult.UNAVAILABLE
    }

    private fun owner(allowed: () -> Boolean = { true }) =
        FaroV5ActiveWorkspaceSearchGrantOwner("g2-host", BooleanSupplier { allowed() })

    private fun admitted(
        owner: FaroV5ActiveWorkspaceSearchGrantOwner?,
        port: FaroV5SearchReadPort? = searchPort,
        recovery: Boolean = true,
        ready: Boolean = true,
        certified: Boolean = true,
    ) = FaroV5SearchHostPortAdmission.admittedPort(
        port, owner, recovery, ready, certified,
    )

    @Test fun validHostBindsOnlyTheActualPort() {
        val grant = owner()
        grant.grantAfterVerifiedHostOpen()
        assertSame(searchPort, admitted(grant))
        grant.close()
    }

    @Test fun v2ProductionFlagOffLeavesSearchUnbound() {
        val grant = owner()
        grant.grantAfterVerifiedHostOpen()
        assertNull(admitted(grant, certified = false))
        grant.close()
    }

    @Test fun incompleteRecoveryDeniesEvenWithExistingGrant() {
        val grant = owner()
        grant.grantAfterVerifiedHostOpen()
        assertNull(admitted(grant, recovery = false))
        grant.close()
    }

    @Test fun notReadyDeniesEvenWithExistingGrant() {
        val grant = owner()
        grant.grantAfterVerifiedHostOpen()
        assertNull(admitted(grant, ready = false))
        grant.close()
    }

    @Test fun missingSearchPortDenies() {
        val grant = owner()
        grant.grantAfterVerifiedHostOpen()
        assertNull(admitted(grant, port = null))
        grant.close()
    }

    @Test fun missingOwnerDenies() {
        assertNull(admitted(null))
    }

    @Test fun ungrantedOwnerCannotMintPermissionFromUi() {
        val grant = owner()
        assertNull(admitted(grant))
        assertNull(grant.epochOrNull())
        grant.close()
    }

    @Test fun revokedOwnerDeniesOldHitAndExplicitRegrantChangesEpoch() {
        val grant = owner()
        val first = grant.grantAfterVerifiedHostOpen()
        assertSame(searchPort, admitted(grant))
        grant.revoke()
        assertNull(admitted(grant))
        val second = grant.renewAfterVerifiedRecovery()
        assertNotEquals(first, second)
        assertSame(searchPort, admitted(grant))
        grant.close()
    }

    @Test fun admittedHostLossCannotResurrectOldEpoch() {
        var allowed = true
        val grant = owner { allowed }
        grant.grantAfterVerifiedHostOpen()
        allowed = false
        assertNull(admitted(grant))
        allowed = true
        assertNull(admitted(grant))
        assertNull(grant.epochOrNull())
        grant.close()
    }

    @Test fun closedGrantNeverResurrects() {
        val grant = owner()
        grant.grantAfterVerifiedHostOpen()
        grant.close()
        assertNull(admitted(grant))
        assertEquals(null, grant.epochOrNull())
    }
}
