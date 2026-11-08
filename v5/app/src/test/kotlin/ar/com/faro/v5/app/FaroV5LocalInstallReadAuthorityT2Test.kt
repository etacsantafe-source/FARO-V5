package ar.com.faro.v5.app

import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.StorageGenerationId
import java.nio.file.Path
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** JVM-only facts simulator; not Application, shipping, user identity or G15 evidence. */
class FaroV5LocalInstallReadAuthorityT2Test {
    @TempDir lateinit var root: Path

    private inner class Case(label: String) {
        val pointer = FileActiveGenerationPointer(root.resolve("ptr-" + label))
        val generation = StorageGenerationId("gen-" + label)
        val pinned = requireNotNull(pointer.compareAndSet(null, generation))
        val appId = "ar.com.faro.alpha.v2coredev"
        var hostEpoch: String? = "host-A"
        var host = true
        var foreground = true
        var facts = AndroidSandboxProcessFacts(
            processUid = 10200, applicationUid = 10200,
            processIsApplicationUid = true, processIsIsolated = false,
            processIsSdkSandbox = false, applicationId = appId,
        )
        fun owner(): FaroV5LocalInstallReadAuthority {
            val probe = FaroV5AndroidSandboxPrincipal(
                expectedApplicationId = appId,
                pinned = pinned,
                activePointer = pointer,
                expectedHostEpoch = "host-A",
                hostEpochOrNull = { hostEpoch },
                facts = AndroidSandboxFactsReadPort { facts },
            )
            return FaroV5LocalInstallReadAuthority(
                sandbox = probe, activePointer = pointer,
                hostReady = { host }, foregroundReady = { foreground },
            )
        }
        fun principal(owner: FaroV5LocalInstallReadAuthority) =
            requireNotNull(owner.technicalPrincipalOrNull())
    }

    @Test fun noExplicitActionMeansNoReadFromAnyDomain() {
        val f = Case("default-deny")
        f.owner().use { authority ->
            val p = f.principal(authority)
            for (source in FaroV5LocalReadDomain.entries) {
                assertNull(authority.epochOrNull(p, source, f.pinned))
            }
            assertFalse(authority.grantAfterTrustedForegroundReadAction(emptySet()))
        }
    }

    @Test fun historyGrantDoesNotAuthorizePlanningOrMatter() {
        val f = Case("scope")
        f.owner().use { authority ->
            val p = f.principal(authority)
            assertTrue(authority.grantAfterTrustedForegroundReadAction(setOf(
                FaroV5LocalReadDomain.REALITY_RECEIPT,
                FaroV5LocalReadDomain.WORKSPACE_MESSAGE,
            )))
            assertNotNull(authority.historyReadPolicy().epochOrNull(
                p, FaroV5ActivityDomain.REALITY_RECEIPT, f.pinned))
            assertNotNull(authority.historyReadPolicy().epochOrNull(
                p, FaroV5ActivityDomain.WORKSPACE_MESSAGE, f.pinned))
            assertNull(authority.planningDueReadPolicy().epochOrNull(
                p, FaroV5PlanningDueDomain.REMINDER_OCCURRENCE, f.pinned))
            assertNull(authority.matterDueEpochOrNull(p, f.pinned))
        }
    }

    @Test fun dueOwnersAndMatterHaveIndependentEpochs() {
        val f = Case("due")
        f.owner().use { authority ->
            val p = f.principal(authority)
            assertTrue(authority.grantAfterTrustedForegroundReadAction(
                setOf(FaroV5LocalReadDomain.MATTER_DUE,
                    FaroV5LocalReadDomain.REMINDER_OCCURRENCE,
                    FaroV5LocalReadDomain.MONTHLY_PLAN_ITEM)))
            val m = requireNotNull(authority.matterDueEpochOrNull(p, f.pinned))
            val r = requireNotNull(authority.planningDueReadPolicy().epochOrNull(
                p, FaroV5PlanningDueDomain.REMINDER_OCCURRENCE, f.pinned))
            val b = requireNotNull(authority.planningDueReadPolicy().epochOrNull(
                p, FaroV5PlanningDueDomain.MONTHLY_PLAN_ITEM, f.pinned))
            assertEquals(3, setOf(m, r, b).size)
            assertNull(authority.historyReadPolicy().epochOrNull(
                p, FaroV5ActivityDomain.REALITY_RECEIPT, f.pinned))
        }
    }

    @Test fun wrongPrincipalOrPinnedPointerAlwaysDenies() {
        val f = Case("wrong")
        f.owner().use { authority ->
            val p = f.principal(authority)
            assertTrue(authority.grantAfterTrustedForegroundReadAction(
                setOf(FaroV5LocalReadDomain.REALITY_RECEIPT)))
            assertNull(authority.epochOrNull("local-install:someone-else",
                FaroV5LocalReadDomain.REALITY_RECEIPT, f.pinned))
            val wrong = f.pinned.copy(sequence = f.pinned.sequence + 1)
            assertNull(authority.epochOrNull(p, FaroV5LocalReadDomain.REALITY_RECEIPT, wrong))
            assertNotNull(authority.epochOrNull(p, FaroV5LocalReadDomain.REALITY_RECEIPT, f.pinned))
        }
    }

    @Test fun revokeOneDoesNotGrantAnyOtherAndNewActionRotatesEpoch() {
        val f = Case("revoke")
        f.owner().use { authority ->
            val p = f.principal(authority)
            val both = setOf(FaroV5LocalReadDomain.REALITY_RECEIPT,
                FaroV5LocalReadDomain.WORKSPACE_MESSAGE)
            assertTrue(authority.grantAfterTrustedForegroundReadAction(both))
            val before = authority.epochOrNull(p, FaroV5LocalReadDomain.REALITY_RECEIPT, f.pinned)
            val workspace = authority.epochOrNull(p, FaroV5LocalReadDomain.WORKSPACE_MESSAGE, f.pinned)
            authority.revoke(FaroV5LocalReadDomain.REALITY_RECEIPT)
            assertNull(authority.epochOrNull(p, FaroV5LocalReadDomain.REALITY_RECEIPT, f.pinned))
            assertEquals(workspace, authority.epochOrNull(
                p, FaroV5LocalReadDomain.WORKSPACE_MESSAGE, f.pinned))
            assertTrue(authority.grantAfterTrustedForegroundReadAction(
                setOf(FaroV5LocalReadDomain.REALITY_RECEIPT)))
            assertNotEquals(before, authority.epochOrNull(
                p, FaroV5LocalReadDomain.REALITY_RECEIPT, f.pinned))
        }
    }

    @Test fun backgroundDropsAllOldGrantsEvenAfterReturning() {
        val f = Case("background")
        f.owner().use { authority ->
            val p = f.principal(authority)
            assertTrue(authority.grantAfterTrustedForegroundReadAction(
                setOf(FaroV5LocalReadDomain.REALITY_RECEIPT)))
            val old = authority.epochOrNull(p, FaroV5LocalReadDomain.REALITY_RECEIPT, f.pinned)
            f.foreground = false
            assertNull(authority.epochOrNull(p, FaroV5LocalReadDomain.REALITY_RECEIPT, f.pinned))
            assertFalse(authority.grantAfterTrustedForegroundReadAction(
                setOf(FaroV5LocalReadDomain.REALITY_RECEIPT)))
            f.foreground = true
            assertNull(authority.epochOrNull(p, FaroV5LocalReadDomain.REALITY_RECEIPT, f.pinned))
            assertTrue(authority.grantAfterTrustedForegroundReadAction(
                setOf(FaroV5LocalReadDomain.REALITY_RECEIPT)))
            assertNotEquals(old, authority.epochOrNull(
                p, FaroV5LocalReadDomain.REALITY_RECEIPT, f.pinned))
        }
    }

    @Test fun hostFailurePermanentlyRevokesEvenIfHostReturns() {
        val f = Case("host-death")
        f.owner().use { authority ->
            val p = f.principal(authority)
            authority.grantAfterTrustedForegroundReadAction(
                setOf(FaroV5LocalReadDomain.MATTER_DUE))
            f.host = false
            assertNull(authority.matterDueEpochOrNull(p, f.pinned))
            f.host = true
            assertFalse(authority.grantAfterTrustedForegroundReadAction(
                setOf(FaroV5LocalReadDomain.MATTER_DUE)))
        }
    }

    @Test fun pointerAbaAndHostEpochChangeNeverRevive() {
        val f = Case("aba")
        f.owner().use { authority ->
            val p = f.principal(authority)
            authority.grantAfterTrustedForegroundReadAction(
                setOf(FaroV5LocalReadDomain.MONTHLY_PLAN_ITEM))
            val another = StorageGenerationId("other-gen-aba")
            assertNotNull(f.pointer.compareAndSet(f.generation, another))
            assertNotNull(f.pointer.compareAndSet(another, f.generation))
            assertNull(authority.epochOrNull(p, FaroV5LocalReadDomain.MONTHLY_PLAN_ITEM, f.pinned))
            assertFalse(authority.grantAfterTrustedForegroundReadAction(
                setOf(FaroV5LocalReadDomain.MONTHLY_PLAN_ITEM)))
        }
        // A->B->A permanently revokes f's original pinned session.
        // Host lifecycle is an independent hypothesis and needs a fresh root.
        val hostCase = Case("host-epoch")
        hostCase.owner().use { fresh ->
            assertNotNull(hostCase.principal(fresh))
            hostCase.hostEpoch = "host-B"
            assertNull(fresh.technicalPrincipalOrNull())
            hostCase.hostEpoch = "host-A"
            assertNull(fresh.technicalPrincipalOrNull())
        }
    }

    @Test fun alteredSandboxFactsOrCloseDoesNotReissueAuthority() {
        val f = Case("sandbox")
        val authority = f.owner()
        val p = f.principal(authority)
        authority.grantAfterTrustedForegroundReadAction(
            setOf(FaroV5LocalReadDomain.WORKSPACE_MESSAGE))
        f.facts = f.facts.copy(applicationUid = f.facts.applicationUid + 1)
        assertNull(authority.epochOrNull(p, FaroV5LocalReadDomain.WORKSPACE_MESSAGE, f.pinned))
        f.facts = f.facts.copy(applicationUid = f.facts.processUid)
        assertFalse(authority.grantAfterTrustedForegroundReadAction(
            setOf(FaroV5LocalReadDomain.WORKSPACE_MESSAGE)))
        authority.close()
        assertNull(authority.technicalPrincipalOrNull())
    }

    @Test fun grantAndRevocationEmitInvalidationsForVisibleCollectors() = runBlocking {
        val f = Case("flow")
        f.owner().use { authority ->
            val updates = mutableListOf<Unit>()
            val collector = launch(start = CoroutineStart.UNDISPATCHED) {
                withTimeout(3000) { authority.invalidations.take(2).toList(updates) }
            }
            assertTrue(authority.grantAfterTrustedForegroundReadAction(
                setOf(FaroV5LocalReadDomain.REALITY_RECEIPT)))
            authority.revoke(FaroV5LocalReadDomain.REALITY_RECEIPT)
            collector.join()
            assertEquals(2, updates.size)
        }
    }
}
