package ar.com.faro.v5.app

import ar.com.faro.v5.kernel.contracts.AtomicApplyTransactionDecision
import ar.com.faro.v5.kernel.contracts.TransactionId
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.StorageGenerationId
import ar.com.faro.v5.owner.matters.api.*
import ar.com.faro.v5.owner.reminders.api.*
import ar.com.faro.v5.owner.monthlyplan.api.*
import ar.com.faro.v5.store.reality.RealityPlanningBackupSnapshot
import ar.com.faro.v5.store.reality.RealityStore
import java.nio.file.Path
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** New combination tests: REAL SQLite schema8 plus fake Android facts; no G15/shipping. */
class FaroV5N031ThreeOwnerDueHostBindingT2Test {
    @TempDir lateinit var root: Path
    private val now = Instant.parse("2026-11-08T15:00:00Z")
    private val zone = ZoneId.of("America/Argentina/Cordoba")

    private fun planned() = RealityPlanningBackupSnapshot(
        schemaVersion = 8,
        reminders = ReminderSnapshot(
            revision = 2L,
            rules = listOf(ReminderRule(
                id = ReminderRuleId.of("rule-n031"), title = "Confidential",
                zoneId = zone.id,
                anchorLocalDate = LocalDate.of(2026, 11, 9),
                anchorLocalTime = LocalTime.of(8, 0),
                recurrence = ReminderRecurrence.ONCE,
                enabled = true, updatedAtEpochMillis = 20L,
            )),
            occurrences = listOf(ReminderOccurrence(
                id = ReminderOccurrenceId.of("occ-n031"),
                ruleId = ReminderRuleId.of("rule-n031"),
                dueAtEpochMillis = Instant.parse("2026-11-09T11:00:00Z").toEpochMilli(),
                status = ReminderOccurrenceStatus.PENDING, updatedAtEpochMillis = 21L,
            )),
        ),
        monthlyPlan = MonthlyPlanSnapshot(
            revision = 1L,
            items = listOf(MonthlyPlanItem(
                id = MonthlyPlanItemId.of("bill-n031"), title = "Sensitive",
                period = YearMonth.of(2026, 11),
                dueDate = LocalDate.of(2026, 11, 10),
                status = MonthlyPlanItemStatus.PLANNED,
                expectedAmountMinor = 14000L,
                currencyCode = "ARS", updatedAtEpochMillis = 22L,
            )),
        ),
    )

    private inner class Case(label: String, schema8: Boolean = true) : AutoCloseable {
        val reality = RealityStore(root.resolve(label + "-reality.db").toString(),
            planningStage8 = schema8)
        val pointer = FileActiveGenerationPointer(root.resolve(label + "-pointer"))
        val generation = StorageGenerationId("gen-" + label)
        val pinned = requireNotNull(pointer.compareAndSet(null, generation))
        var hostEpoch: String? = "host-1"
        var ready = true
        var foreground = true
        val appId = "ar.com.faro.alpha.v2coredev"
        var facts = AndroidSandboxProcessFacts(
            processUid = 10220, applicationUid = 10220,
            processIsApplicationUid = true, processIsIsolated = false,
            processIsSdkSandbox = false, applicationId = appId,
        )
        val sandbox = FaroV5AndroidSandboxPrincipal(
            expectedApplicationId = appId,
            pinned = pinned, activePointer = pointer,
            expectedHostEpoch = "host-1", hostEpochOrNull = { hostEpoch },
            facts = AndroidSandboxFactsReadPort { facts },
        )
        val access = FaroV5LocalInstallReadAuthority(
            sandbox = sandbox, activePointer = pointer,
            hostReady = { ready }, foregroundReady = { foreground },
        )
        val principal = requireNotNull(access.technicalPrincipalOrNull())

        fun seed() {
            reality.planningBackupRestore.restoreOrVerify(planned())
            reality.run(TransactionId("tx-" + generation.value)) { scope ->
                reality.matterPersistence.upsert(MatterRecord(
                    id = MatterId.of("matter-n031"),
                    title = MatterTitle.of("Private matter"),
                    detail = MatterDetail.of("Never display detail"),
                    status = MatterStatus.OPEN,
                    dueAtEpochMillis = Instant.parse("2026-11-08T12:00:00Z").toEpochMilli(),
                    updatedAtEpochMillis = 30L,
                ), scope)
                AtomicApplyTransactionDecision.Commit(Unit)
            }
        }

        fun grant(vararg domains: FaroV5LocalReadDomain): Boolean =
            access.grantAfterTrustedForegroundReadAction(domains.toSet())

        fun host() = FaroV5N031ThreeOwnerDueHostBinding(
            generationId = generation.value,
            pinned = pinned, activePointer = pointer, reality = reality,
            principalId = principal, expectedHostEpoch = "host-1",
            hostEpochOrNull = { hostEpoch }, hostReady = { ready },
            readAuthority = access,
        )

        override fun close() {
            access.close()
            reality.close()
        }
    }

    private val all = arrayOf(
        FaroV5LocalReadDomain.MATTER_DUE,
        FaroV5LocalReadDomain.REMINDER_OCCURRENCE,
        FaroV5LocalReadDomain.MONTHLY_PLAN_ITEM,
    )

    @Test fun withoutExplicitGrantNoneOfThreeOwnersIsReadable() {
        Case("default-deny").use { f ->
            f.seed()
            f.host().use { host ->
                assertNull(host.readAuthorized(now, zone))
                assertEquals(1, f.reality.matterPersistence.read().matters.size)
            }
        }
    }

    @Test fun missingAnyOneDomainDeniesWholePageNotInventedZero() {
        for (missing in all) {
            Case("missing-" + missing.name).use { f ->
                f.seed()
                assertTrue(f.grant(*all.filter { it != missing }.toTypedArray()))
                f.host().use { assertNull(it.readAuthorized(now, zone)) }
            }
        }
    }

    @Test fun threeRealSqliteSourcesHaveDistinctProvenanceAndRevisions() {
        Case("three-real").use { f ->
            f.seed()
            val beforeMatter = f.reality.matterPersistence.read()
            val beforePlanning = f.reality.planningBackup.snapshot()
            assertTrue(f.grant(*all))
            f.host().use { host ->
                val page = requireNotNull(host.readAuthorized(now, zone))
                assertEquals(listOf("matter-n031", "occ-n031", "bill-n031"),
                    page.items.map { it.opaqueId })
                assertEquals(listOf(FaroV5N031DueSource.MATTER_DUE,
                    FaroV5N031DueSource.REMINDER_OCCURRENCE,
                    FaroV5N031DueSource.MONTHLY_PLAN_ITEM), page.items.map { it.source })
                assertEquals(beforeMatter.revision, page.matterRevision)
                assertEquals(2L, page.reminderRevision)
                assertEquals(1L, page.monthlyPlanRevision)
                assertEquals(3, page.canonicalSources.size)
                assertNull(page.items.last().dueAtEpochMillis)
                assertTrue(page.completeWithinStoredDues)
                assertFalse(page.globalAttentionComplete)
                assertFalse(page.recurrenceExpanded)
                assertFalse(page.crossOwnerAtomicSnapshot)
                assertFalse(page.priorityOrSnoozeInferred)
                assertTrue(page.items.none { it.opaqueId.contains("Private") })
            }
            assertEquals(beforeMatter, f.reality.matterPersistence.read())
            assertEquals(beforePlanning, f.reality.planningBackup.snapshot())
            assertTrue(f.reality.maintenance.inspect().integrityHealthy)
        }
    }

    @Test fun boundedPageDoesNotBecomeGlobalCount() {
        Case("bounded").use { f ->
            f.seed()
            assertTrue(f.grant(*all))
            f.host().use { host ->
                val page = requireNotNull(host.readAuthorized(now, zone, limit = 1))
                assertEquals(listOf("matter-n031"), page.items.map { it.opaqueId })
                assertFalse(page.completeWithinStoredDues)
                assertFalse(page.globalAttentionComplete)
                assertThrows(IllegalArgumentException::class.java) {
                    host.readAuthorized(now, zone, limit = 0)
                }
            }
        }
    }

    @Test fun eachIndependentRevocationMakesEntirePageUnknown() {
        Case("revoke").use { f ->
            f.seed()
            assertTrue(f.grant(*all))
            f.host().use { host ->
                assertNotNull(host.readAuthorized(now, zone))
                for (domain in all) {
                    f.access.revoke(domain)
                    assertNull(host.readAuthorized(now, zone))
                    assertTrue(f.grant(domain))
                    assertNotNull(host.readAuthorized(now, zone))
                }
            }
        }
    }

    @Test fun pointerAbaRevokesOriginalHostPermanently() {
        Case("aba").use { f ->
            f.seed()
            assertTrue(f.grant(*all))
            f.host().use { host ->
                assertNotNull(host.readAuthorized(now, zone))
                val other = StorageGenerationId("other-gen-n031")
                assertNotNull(f.pointer.compareAndSet(f.generation, other))
                assertNotNull(f.pointer.compareAndSet(other, f.generation))
                assertNull(host.readAuthorized(now, zone))
                assertFalse(f.grant(*all))
            }
        }
    }

    @Test fun backgroundAndHostEpochRevokeWithoutRestoringGrants() {
        Case("lifecycle").use { f ->
            f.seed()
            assertTrue(f.grant(*all))
            f.host().use { host ->
                assertNotNull(host.readAuthorized(now, zone))
                f.foreground = false
                assertNull(host.readAuthorized(now, zone))
                f.foreground = true
                assertNull(host.readAuthorized(now, zone))
                f.hostEpoch = "host-2"
                assertNull(host.readAuthorized(now, zone))
                f.hostEpoch = "host-1"
                assertNull(host.readAuthorized(now, zone))
            }
        }
    }

    @Test fun legacySchemaAndClosedOwnerRemainDenied() {
        Case("schema7", schema8 = false).use { f ->
            assertThrows(IllegalStateException::class.java) { f.host() }
            assertEquals(7, f.reality.maintenance.inspect().schemaVersion)
        }
        Case("closed").use { f ->
            f.seed()
            assertTrue(f.grant(*all))
            val host = f.host()
            assertNotNull(host.readAuthorized(now, zone))
            host.close()
            assertNull(host.readAuthorized(now, zone))
            f.reality.close()
            assertNull(host.readAuthorized(now, zone))
        }
    }
}
