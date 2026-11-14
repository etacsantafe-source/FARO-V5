package ar.com.faro.v5.app

import android.app.Application
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.maintenance.*
import ar.com.faro.v5.owner.matters.api.*
import ar.com.faro.v5.owner.reminders.api.*
import ar.com.faro.v5.owner.monthlyplan.api.*
import ar.com.faro.v5.store.reality.RealityPlanningBackupSnapshot
import ar.com.faro.v5.store.reality.RealityStore
import java.time.*
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** New host boundary: real Application, real SQLite three sources, real lifecycle and durable ABA. */
@RunWith(AndroidJUnit4::class)
class FaroV5S3FinalA4OpeningPointerTest {
    @Test fun realForegroundThreeOwnerReadRevokesAndStaleSessionCannotRegrantAfterAba() {
        check(android.os.Build.VERSION.SDK_INT == 35)
        check(android.os.Build.FINGERPRINT.contains("emulator") || android.os.Build.MODEL.contains("sdk", true))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("ar.com.faro.alpha.v2coredev", context.packageName)
        val app = context.applicationContext as Application
        val root = context.noBackupFilesDir.toPath().resolve("faro-v2-core-dev-root")
        check(!root.toFile().exists()) { "Fresh emulator root required; never reset existing data" }
        val zone = ZoneId.of("America/Argentina/Cordoba")
        val now = Instant.now()
        val tomorrow = now.atZone(zone).toLocalDate().plusDays(1)
        try {
            val authority = FaroV5V2CoreDevAuthority(root)
            authority.open().close()
            authority.upgradePlanning8AfterExplicitForegroundRequest { true }.close()
            val pointer = FileActiveGenerationPointer(root)
            val pinned = checkNotNull(pointer.read())
            val staged = FileStorageGenerationLayout(root).requireStaged(pinned.generationId)
            // Only this disposable emulator test seeds the canonical owner, before host Session opens.
            RealityStore(staged.directory.resolve(GenerationStoreFiles.REALITY).toString(), planningStage8 = true).use { r ->
                r.planningBackupRestore.restoreOrVerify(RealityPlanningBackupSnapshot(8,
                    ReminderSnapshot(2L, listOf(ReminderRule(ReminderRuleId.of("a4-rule"), "private rule",
                        zone.id, tomorrow, LocalTime.of(8, 0), ReminderRecurrence.ONCE, true, updatedAtEpochMillis = 20L)),
                        listOf(ReminderOccurrence(ReminderOccurrenceId.of("a4-occ"), ReminderRuleId.of("a4-rule"),
                            tomorrow.atTime(8, 0).atZone(zone).toInstant().toEpochMilli(), ReminderOccurrenceStatus.PENDING, updatedAtEpochMillis = 21L))),
                    MonthlyPlanSnapshot(1L, listOf(MonthlyPlanItem(MonthlyPlanItemId.of("a4-bill"), "private bill",
                        YearMonth.from(tomorrow), tomorrow, MonthlyPlanItemStatus.PLANNED, 14000L, "ARS", updatedAtEpochMillis = 22L)))))
                r.run(TransactionId("a4-positive-fixture")) { scope ->
                    r.matterPersistence.upsert(MatterRecord(MatterId.of("a4-matter"), MatterTitle.of("private matter"),
                        MatterDetail.of("never expose"), MatterStatus.OPEN,
                        tomorrow.atStartOfDay(zone).toInstant().toEpochMilli(), 30L), scope)
                    AtomicApplyTransactionDecision.Commit(Unit)
                }
            }
            ActivityScenario.launch(FaroV5V2CoreDevActivity::class.java).use { scenario ->
                val nativeLifecycle = AtomicReference<Lifecycle>()
                val nativeSession = AtomicReference<FaroV5V2CoreDevAuthority.Session>()
                val resumed = { nativeLifecycle.get()?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true }
                scenario.onActivity { a ->
                    assertEquals(Lifecycle.State.RESUMED, a.lifecycle.currentState)
                    nativeLifecycle.set(a.lifecycle)
                    fun find(view: View): Button? {
                        if (view is Button && view.text.toString() == "Abrir Chat local de desarrollo") return view
                        if (view is ViewGroup) for (i in 0 until view.childCount) {
                            find(view.getChildAt(i))?.let { return it }
                        }
                        return null
                    }
                    val open = checkNotNull(find(a.window.decorView))
                    assertTrue(open.isEnabled)
                    assertTrue(open.performClick())
                }
                val deadline = SystemClock.elapsedRealtime() + 60_000L
                while (nativeSession.get() == null && SystemClock.elapsedRealtime() < deadline) {
                    scenario.onActivity { a ->
                        nativeSession.set(a.javaClass.getDeclaredField("session").apply { isAccessible = true }
                            .get(a) as FaroV5V2CoreDevAuthority.Session?)
                    }
                    if (nativeSession.get() == null) SystemClock.sleep(50L)
                }
                val session = checkNotNull(nativeSession.get()) { "Native Activity never published its Session" }
                assertTrue(resumed())
                run {
                    val history = checkNotNull(session.openActivityAfterTrustedForegroundRequest(app) { resumed() })
                    val dues = checkNotNull(session.openPendingAfterTrustedForegroundRequest(app) { resumed() })
                    assertNotNull(runBlocking { history.readActivity() })
                    val positive = checkNotNull(dues.readAuthorized(now, zone))
                    assertEquals(setOf(FaroV5N031DueSource.MATTER_DUE, FaroV5N031DueSource.REMINDER_OCCURRENCE,
                        FaroV5N031DueSource.MONTHLY_PLAN_ITEM), positive.items.map { it.source }.toSet())
                    assertEquals(3, positive.items.size)
                    assertFalse(positive.globalAttentionComplete)
                    scenario.moveToState(Lifecycle.State.STARTED)
                    scenario.onActivity { a ->
                        assertEquals(Lifecycle.State.STARTED, a.lifecycle.currentState)
                        nativeLifecycle.set(a.lifecycle)
                    }
                    assertFalse(resumed())
                    assertNull(runBlocking { history.readActivity() })
                    assertNull(dues.readAuthorized(now, zone))
                    scenario.moveToState(Lifecycle.State.RESUMED)
                    scenario.onActivity { a ->
                        assertEquals(Lifecycle.State.RESUMED, a.lifecycle.currentState)
                        nativeLifecycle.set(a.lifecycle)
                    }
                    assertTrue(resumed())
                    assertNull("Old history authority cannot resurrect", runBlocking { history.readActivity() })
                    assertNull("Old due authority cannot resurrect", dues.readAuthorized(now, zone))
                    val freshHistory = checkNotNull(session.openActivityAfterTrustedForegroundRequest(app) { resumed() })
                    val freshDues = checkNotNull(session.openPendingAfterTrustedForegroundRequest(app) { resumed() })
                    assertNotNull(runBlocking { freshHistory.readActivity() })
                    val freshPositive = checkNotNull(freshDues.readAuthorized(now, zone))
                    assertEquals(positive.items.map { it.source }.toSet(), freshPositive.items.map { it.source }.toSet())
                    assertEquals(3, freshPositive.items.size)
                    assertFalse(freshPositive.globalAttentionComplete)
                    session.revokeActivityReadingOnPause()
                    session.revokePendingReadingOnPause()
                    assertNull(runBlocking { freshHistory.readActivity() })
                    assertNull(freshDues.readAuthorized(now, zone))
                    val b = StorageGenerationId("a4-other-generation")
                    assertNotNull(pointer.compareAndSet(pinned.generationId, b))
                    val restored = checkNotNull(pointer.compareAndSet(b, pinned.generationId))
                    assertTrue(restored.sequence > pinned.sequence)
                    assertNull("Old Session cannot issue a NEW history grant after ABA",
                        session.openActivityAfterTrustedForegroundRequest(app) { resumed() })
                    assertNull("Old Session cannot issue a NEW due grant after ABA",
                        session.openPendingAfterTrustedForegroundRequest(app) { resumed() })
                }
            }
            assertFalse(FaroV5HostCertification.V2_ACTIVE_ENABLED)
        } finally { root.toFile().deleteRecursively() }
    }
}
