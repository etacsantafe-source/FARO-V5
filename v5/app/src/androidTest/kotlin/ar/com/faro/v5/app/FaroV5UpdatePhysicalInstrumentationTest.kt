package ar.com.faro.v5.app

import android.os.Process
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.RequiresDevice
import androidx.test.platform.app.InstrumentationRegistry
import ar.com.faro.v5.maintenance.ExpectedInstallId
import ar.com.faro.v5.maintenance.FileUpdateArtifactStagePort
import ar.com.faro.v5.maintenance.FileUpdateControlStore
import ar.com.faro.v5.maintenance.StagedUpdateArtifactId
import ar.com.faro.v5.maintenance.UpdateArtifactDownloadPort
import ar.com.faro.v5.maintenance.UpdateArtifactInspectionPort
import ar.com.faro.v5.maintenance.UpdateLifecyclePhase
import ar.com.faro.v5.maintenance.UpdateManifestCandidate
import ar.com.faro.v5.maintenance.UpdateStageUseCase
import ar.com.faro.v5.platform.android.update.AndroidInstalledUpdateIdentityPort
import ar.com.faro.v5.platform.android.update.AndroidUpdateArtifactInspector
import ar.com.faro.v5.platform.android.update.AndroidUpdateInstallReadiness
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RequiresDevice
@RunWith(AndroidJUnit4::class)
class FaroV5UpdatePhysicalInstrumentationTest {
    @Test
    fun prepareCommitAndObservePendingUserAction() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val target = instrumentation.targetContext
        val application = target.applicationContext as FaroV5Application
        val root = compositionRoot(application)
        val runtime = root.updateRuntime

        assertEquals(
            AndroidUpdateInstallReadiness.BlockReason.NONE,
            runtime.readiness(),
        )

        val fixtureApk = File(target.filesDir, FIXTURE_APK)
        assertTrue("fixture update APK missing", fixtureApk.isFile)

        val installedPort = AndroidInstalledUpdateIdentityPort(target)
        val installed = installedPort.current()
        val inspector = AndroidUpdateArtifactInspector(target)
        val targetArtifact = inspector.inspect(fixtureApk)

        assertEquals(target.packageName, installed.packageName)
        assertEquals(installed.packageName, targetArtifact.packageName)
        assertTrue(
            "physical fixture must be a monotonic upgrade",
            targetArtifact.versionCode > installed.versionCode,
        )

        val candidate =
            UpdateManifestCandidate(
                sourceIdentity = "p5-physical-fixture-local",
                channel = CHANNEL,
                packageName = installed.packageName,
                versionCode = targetArtifact.versionCode,
                versionName = targetArtifact.versionName,
                minimumSourceVersionCode = installed.versionCode,
                artifactUri = "https://fixture.invalid/faro-v5-p5-update.apk",
                artifactSha256 = targetArtifact.sha256,
                declaredSizeBytes = targetArtifact.byteCount,
            )

        val stagingRoot =
            File(target.noBackupFilesDir, "faro-v5-update-staging").toPath()
        val stagePort =
            FileUpdateArtifactStagePort(
                stagingRoot = stagingRoot,
                download =
                    UpdateArtifactDownloadPort {
                        Files.newInputStream(fixtureApk.toPath())
                    },
            )
        val verified =
            UpdateStageUseCase(
                staging = stagePort,
                cleanup = stagePort,
                inspection =
                    UpdateArtifactInspectionPort { artifactId: StagedUpdateArtifactId ->
                        inspector.inspect(stagePort.pathFor(artifactId).toFile())
                    },
                installedIdentity = installedPort,
                expectedChannel = CHANNEL,
            ).stage(candidate)

        val configuration =
            FaroV5UpdateConfiguration(
                manifestUrl = "https://fixture.invalid/manifest.txt",
                channel = CHANNEL,
                allowedHosts = setOf("fixture.invalid"),
            )

        File(target.filesDir, OLD_PID).writeText(Process.myPid().toString())

        ActivityScenario.launch(FaroV5P5PhysicalActivity::class.java).use {
            instrumentation.waitForIdleSync()
            val result = runtime.install(configuration, verified)
            assertTrue(result is FaroV5UpdateInstallResult.Attempted)
            val attempted = result as FaroV5UpdateInstallResult.Attempted
            assertEquals(
                ar.com.faro.v5.maintenance.UpdateInstallObservationKind.SESSION_ACTIVE,
                attempted.observation.kind,
            )
            File(target.filesDir, EXPECTED_ID).writeText(attempted.expectedInstallId)

            val store =
                FileUpdateControlStore(
                    File(target.noBackupFilesDir, "faro-v5-storage").toPath(),
                )
            val pending =
                waitForPhase(
                    store = store,
                    expectedInstallId = ExpectedInstallId(attempted.expectedInstallId),
                    phase = UpdateLifecyclePhase.AWAITING_USER_ACTION,
                )
            File(target.filesDir, PENDING_EVIDENCE).writeText(
                "expected_install_id=${attempted.expectedInstallId}\n" +
                    "phase=${pending.phase}\n" +
                    "platform_session=${pending.platformSessionIdentity}\n" +
                    "source_version=${pending.expectedInstall.sourceVersionCode}\n" +
                    "target_version=${pending.expectedInstall.targetVersionCode}\n" +
                    "pid=${Process.myPid()}\n",
            )
        }
    }

    @Test
    fun verifyInstalledAfterProcessReplacement() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val expectedInstallId =
            File(target.filesDir, EXPECTED_ID).readText().trim()
        val oldPid = File(target.filesDir, OLD_PID).readText().trim().toInt()
        val pendingEvidence = File(target.filesDir, PENDING_EVIDENCE)
        assertTrue("pending-user-action evidence missing", pendingEvidence.isFile)
        assertTrue(
            pendingEvidence.readText().contains("phase=AWAITING_USER_ACTION"),
        )

        val store =
            FileUpdateControlStore(
                File(target.noBackupFilesDir, "faro-v5-storage").toPath(),
            )
        val entry =
            requireNotNull(store.read(ExpectedInstallId(expectedInstallId))) {
                "durable expected-install entry missing after update"
            }

        assertEquals(UpdateLifecyclePhase.INSTALLED, entry.phase)
        assertTrue(store.pending().isEmpty())

        val installed = AndroidInstalledUpdateIdentityPort(target).current()
        assertEquals(entry.expectedInstall.packageName, installed.packageName)
        assertEquals(entry.expectedInstall.targetVersionCode, installed.versionCode)
        assertNotEquals("process was not replaced", oldPid, Process.myPid())

        val staging =
            FileUpdateArtifactStagePort(
                stagingRoot = File(target.noBackupFilesDir, "faro-v5-update-staging").toPath(),
                download =
                    UpdateArtifactDownloadPort {
                        error("verification must never download")
                    },
            )
        assertFalse(
            "staged update artifact was not cleaned after terminal reconcile",
            Files.exists(staging.pathFor(entry.expectedInstall.staged.artifactId)),
        )
    }

    private fun waitForPhase(
        store: FileUpdateControlStore,
        expectedInstallId: ExpectedInstallId,
        phase: UpdateLifecyclePhase,
    ): ar.com.faro.v5.maintenance.UpdateControlEntry {
        val deadline = System.nanoTime() + 15_000_000_000L
        while (System.nanoTime() < deadline) {
            val current = store.read(expectedInstallId)
            if (current?.phase == phase) {
                return current
            }
            if (current?.phase == UpdateLifecyclePhase.EFFECT_UNKNOWN ||
                current?.phase == UpdateLifecyclePhase.FAILED ||
                current?.phase == UpdateLifecyclePhase.CANCELLED
            ) {
                error("update entered unexpected phase before user action: ${current.phase}")
            }
            Thread.sleep(50L)
        }
        error("timed out waiting for PENDING_USER_ACTION callback")
    }

    private fun compositionRoot(application: FaroV5Application): FaroV5CompositionRoot {
        val field = FaroV5Application::class.java.getDeclaredField("compositionRoot")
        field.isAccessible = true
        return field.get(application) as FaroV5CompositionRoot
    }

    private companion object {
        const val CHANNEL = "physical-fixture"
        const val FIXTURE_APK = "p5-update.apk"
        const val EXPECTED_ID = "p5-physical-expected-id.txt"
        const val OLD_PID = "p5-physical-old-pid.txt"
        const val PENDING_EVIDENCE = "p5-physical-pending.txt"
    }
}
