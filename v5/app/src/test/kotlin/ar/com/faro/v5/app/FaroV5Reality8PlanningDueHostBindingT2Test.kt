package ar.com.faro.v5.app

import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.StorageGenerationId
import ar.com.faro.v5.owner.monthlyplan.api.*
import ar.com.faro.v5.owner.reminders.api.*
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

/**
 * Real Reality8 SQLite owner -> host -> existing N031 due projection.
 * ACL/principal are TEST fixtures. Not Android production authorization.
 */
class FaroV5Reality8PlanningDueHostBindingT2Test {
    @TempDir lateinit var root: Path
    private val zone = ZoneId.of("America/Argentina/Cordoba")
    private val now = Instant.parse("2026-11-08T15:00:00Z")

    private fun sample() = RealityPlanningBackupSnapshot(
        schemaVersion = 8,
        reminders = ReminderSnapshot(
            revision = 2L,
            rules = listOf(ReminderRule(
                id = ReminderRuleId.of("rule-1"), title = "Private reminder",
                zoneId = zone.id, anchorLocalDate = LocalDate.of(2026, 11, 9),
                anchorLocalTime = LocalTime.of(8, 0),
                recurrence = ReminderRecurrence.ONCE, enabled = true,
                updatedAtEpochMillis = 100L,
            )),
            occurrences = listOf(ReminderOccurrence(
                id = ReminderOccurrenceId.of("occ-1"),
                ruleId = ReminderRuleId.of("rule-1"),
                dueAtEpochMillis = Instant.parse("2026-11-09T11:00:00Z").toEpochMilli(),
                status = ReminderOccurrenceStatus.PENDING,
                updatedAtEpochMillis = 101L,
            )),
        ),
        monthlyPlan = MonthlyPlanSnapshot(
            revision = 1L,
            items = listOf(MonthlyPlanItem(
                id = MonthlyPlanItemId.of("rent-1"), title = "Private bill",
                period = YearMonth.of(2026, 11),
                dueDate = LocalDate.of(2026, 11, 10),
                status = MonthlyPlanItemStatus.PLANNED,
                expectedAmountMinor = 123_000L,
                currencyCode = "ARS",
                updatedAtEpochMillis = 102L,
            )),
        ),
    )

    private inner class Case(
        label: String, schema8: Boolean = true,
    ) : AutoCloseable {
        val file = root.resolve(label + "-reality.db").toString()
        val reality = RealityStore(file, planningStage8 = schema8)
        val pointer = FileActiveGenerationPointer(root.resolve(label + "-pointer"))
        val generation = StorageGenerationId("gen-" + label)
        val pinned = requireNotNull(pointer.compareAndSet(null, generation))
        var hostEpoch: String? = "host-1"
        var ready = true
        var reminderGrant: String? = "rem-1"
        var monthlyGrant: String? = "month-1"
        var intercept: (FaroV5PlanningDueDomain, String?) -> String? = { _, value -> value }

        val policy = Planning8TrustedDueReadPolicyPort { principal, domain, observed ->
            if (principal != "local-test-principal" || observed != pinned) null
            else intercept(domain, when (domain) {
                FaroV5PlanningDueDomain.REMINDER_OCCURRENCE -> reminderGrant
                FaroV5PlanningDueDomain.MONTHLY_PLAN_ITEM -> monthlyGrant
            })
        }

        fun seed() {
            reality.planningBackupRestore.restoreOrVerify(sample())
        }

        fun binding(
            policyOverride: Planning8TrustedDueReadPolicyPort? = policy,
            database: RealityStore = reality,
        ) = FaroV5Reality8PlanningDueHostBinding(
            generationId = generation.value, pinned = pinned,
            activePointer = pointer, reality = database,
            principalId = "local-test-principal",
            expectedHostEpoch = "host-1",
            hostEpochOrNull = { hostEpoch }, hostReady = { ready },
            readPolicy = policyOverride,
        )

        override fun close() {
            runCatching { reality.close() }
        }
    }

    @Test fun absentIndependentPolicyFailsClosedWithoutFakeZero() {
        Case("missing-policy").use { f ->
            f.seed()
            f.binding(null).use { binding ->
                assertNull(binding.readAuthorized(now, zone))
            }
            f.reminderGrant = null
            f.binding().use { binding ->
                assertNull(binding.readAuthorized(now, zone))
            }
            f.reminderGrant = "rem-1"
            f.monthlyGrant = null
            f.binding().use { binding ->
                assertNull(binding.readAuthorized(now, zone))
            }
            assertEquals(sample(), f.reality.planningBackup.snapshot())
        }
    }

    @Test fun realSqliteReadAcrossBothOwnersPreservesCanonicalDataAndRevisions() {
        Case("sqlite-real").use { f ->
            val stored = sample()
            f.seed()
            f.binding().use { binding ->
                val page = requireNotNull(binding.readAuthorized(now, zone))
                assertEquals(listOf("occ-1", "rent-1"), page.items.map { it.opaqueId })
                assertEquals(2L, page.reminderRevision)
                assertEquals(1L, page.monthlyPlanRevision)
                assertEquals("reality.reminder_occurrence", page.canonicalSources.first())
                assertFalse(page.globalAttentionComplete)
                assertTrue(page.completeWithinStoredDues)
                assertNull(page.items.last().dueAtEpochMillis)
            }
            assertEquals(stored, f.reality.planningBackup.snapshot())
            assertEquals("ok", f.reality.integrityCheck())
        }
    }

    @Test fun legacySchemaSevenRejectsHostReadWithoutMigratingOrReturningEmpty() {
        Case("schema-seven", schema8 = false).use { f ->
            assertEquals(7, f.reality.schemaVersion())
            assertThrows(IllegalStateException::class.java) { f.binding() }
            assertEquals(7, f.reality.schemaVersion())
        }
    }

    @Test fun durablePointerAbaAndHostLifecycleChangeBothDenyOldBinding() {
        Case("pointer-aba").use { f ->
            f.seed()
            f.binding().use { binding ->
                assertNotNull(binding.readAuthorized(now, zone))
                f.hostEpoch = "host-2"
                assertNull(binding.readAuthorized(now, zone))
                f.hostEpoch = "host-1"
                val other = StorageGenerationId("gen-other")
                assertNotNull(f.pointer.compareAndSet(f.generation, other))
                assertNotNull(f.pointer.compareAndSet(other, f.generation))
                assertNotEquals(f.pinned, f.pointer.read())
                assertNull(binding.readAuthorized(now, zone))
            }
        }
    }

    @Test fun independentOwnerPolicyEpochRevocationDuringSqliteReadDropsWholePage() {
        Case("revoke-mid-read").use { f ->
            f.seed()
            var reminderEpochChecks = 0
            f.intercept = { domain, epoch ->
                if (domain == FaroV5PlanningDueDomain.REMINDER_OCCURRENCE) {
                    reminderEpochChecks++
                    if (reminderEpochChecks >= 9) "rem-revoked" else epoch
                } else epoch
            }
            f.binding().use { binding ->
                assertNull(binding.readAuthorized(now, zone))
                assertTrue(reminderEpochChecks >= 9)
            }
            assertEquals(sample(), f.reality.planningBackup.snapshot())
        }
    }

    @Test fun closedHostOrClosedRealityHandleNeverLeaksPersistedData() {
        Case("close-owner").use { f ->
            f.seed()
            val binding = f.binding()
            assertNotNull(binding.readAuthorized(now, zone))
            binding.close()
            assertNull(binding.readAuthorized(now, zone))
            f.binding().use { reopenedBinding ->
                assertNotNull(reopenedBinding.readAuthorized(now, zone))
                f.reality.close()
                assertNull(reopenedBinding.readAuthorized(now, zone))
            }
        }
    }
}
