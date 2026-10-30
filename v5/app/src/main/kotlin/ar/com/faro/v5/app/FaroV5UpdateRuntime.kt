package ar.com.faro.v5.app

import android.content.Context
import ar.com.faro.v5.maintenance.ExpectedInstall
import ar.com.faro.v5.maintenance.ExpectedInstallId
import ar.com.faro.v5.maintenance.FileUpdateArtifactStagePort
import ar.com.faro.v5.maintenance.FileUpdateControlStore
import ar.com.faro.v5.maintenance.StagedUpdateArtifactId
import ar.com.faro.v5.maintenance.UpdateArtifactDownloadPort
import ar.com.faro.v5.maintenance.UpdateArtifactInspectionPort
import ar.com.faro.v5.maintenance.UpdateCheckPolicy
import ar.com.faro.v5.maintenance.UpdateCheckUseCase
import ar.com.faro.v5.maintenance.UpdateControlEntry
import ar.com.faro.v5.maintenance.UpdateEligibility
import ar.com.faro.v5.maintenance.UpdateInstallCallbackCoordinator
import ar.com.faro.v5.maintenance.UpdateInstallControlCoordinator
import ar.com.faro.v5.maintenance.UpdateInstallSessionObservation
import ar.com.faro.v5.maintenance.UpdateOperationId
import ar.com.faro.v5.maintenance.UpdateReconcileCoordinator
import ar.com.faro.v5.maintenance.UpdateSigningLineagePolicy
import ar.com.faro.v5.maintenance.UpdateStageUseCase
import ar.com.faro.v5.maintenance.VerifiedStagedUpdate
import ar.com.faro.v5.platform.android.update.AndroidInstalledUpdateIdentityPort
import ar.com.faro.v5.platform.android.update.AndroidPackageInstallerSessionInspectionPort
import ar.com.faro.v5.platform.android.update.AndroidPackageInstallerUpdateInstallPort
import ar.com.faro.v5.platform.android.update.AndroidUpdateArtifactInspector
import ar.com.faro.v5.platform.android.update.AndroidUpdateForegroundStatePort
import ar.com.faro.v5.platform.android.update.AndroidUpdateInstallReadiness
import ar.com.faro.v5.platform.android.update.AndroidUpdateInstallStatusSender
import ar.com.faro.v5.platform.android.update.AndroidUpdateInstallUserActionRouter
import ar.com.faro.v5.platform.android.update.FaroUpdateInstallCallbackExecutionBridge
import ar.com.faro.v5.platform.android.update.FaroUpdateInstallUserActionExecutionBridge
import java.io.File
import java.nio.file.Path
import java.util.UUID

/**
 * Terminal P5 composition.
 *
 * Startup only reconciles durable local state. Network check/stage are lazy and require an explicit
 * update configuration supplied by the caller.
 */
class FaroV5UpdateRuntime(
    context: Context,
    storageRoot: Path,
    private val smallIconResId: Int,
) {
    private val appContext = context.applicationContext
    private val stagingRoot =
        File(appContext.noBackupFilesDir, "faro-v5-update-staging").toPath()
    private val store = FileUpdateControlStore(storageRoot)
    private val installedIdentity = AndroidInstalledUpdateIdentityPort(appContext)
    private val sessionInspection = AndroidPackageInstallerSessionInspectionPort(appContext)
    private val cleanupPort =
        FileUpdateArtifactStagePort(
            stagingRoot = stagingRoot,
            download =
                UpdateArtifactDownloadPort {
                    error("update recovery cleanup must never start a network download")
                },
        )
    private val callbackCoordinator = UpdateInstallCallbackCoordinator(store)
    private val reconcileCoordinator =
        UpdateReconcileCoordinator(
            store = store,
            installedIdentity = installedIdentity,
            sessionInspection = sessionInspection,
            sessionCleanup = sessionInspection,
            artifactCleanup = cleanupPort,
        )
    private val readiness = AndroidUpdateInstallReadiness(appContext)

    @Volatile
    private var userActionRouter: AndroidUpdateInstallUserActionRouter? = null

    fun installProcessBridges(
        foregroundState: AndroidUpdateForegroundStatePort,
    ) {
        FaroUpdateInstallCallbackExecutionBridge.install(callbackCoordinator)
        val router =
            AndroidUpdateInstallUserActionRouter(
                appContext,
                readiness,
                foregroundState,
                smallIconResId,
            )
        userActionRouter = router
        FaroUpdateInstallUserActionExecutionBridge.install(router)
    }

    fun reconcilePending(): List<UpdateControlEntry> =
        reconcileCoordinator.reconcilePending()

    fun readiness(): AndroidUpdateInstallReadiness.BlockReason =
        readiness.check()

    fun check(
        configuration: FaroV5UpdateConfiguration,
    ): UpdateEligibility? {
        val transport = FaroV5UpdateHttpTransport(configuration)
        return UpdateCheckUseCase(
            source = transport,
            installedIdentity = installedIdentity,
            expectedChannel = configuration.channel,
        ).check()
    }

    fun stage(
        configuration: FaroV5UpdateConfiguration,
        candidate: ar.com.faro.v5.maintenance.UpdateManifestCandidate,
    ): VerifiedStagedUpdate {
        val transport = FaroV5UpdateHttpTransport(configuration)
        val staging =
            FileUpdateArtifactStagePort(
                stagingRoot = stagingRoot,
                download = transport,
            )
        val apkInspector = AndroidUpdateArtifactInspector(appContext)
        val inspection =
            UpdateArtifactInspectionPort { artifactId: StagedUpdateArtifactId ->
                apkInspector.inspect(staging.pathFor(artifactId).toFile())
            }

        return UpdateStageUseCase(
            staging = staging,
            cleanup = staging,
            inspection = inspection,
            installedIdentity = installedIdentity,
            expectedChannel = configuration.channel,
        ).stage(candidate)
    }

    @Synchronized
    fun install(
        configuration: FaroV5UpdateConfiguration,
        staged: VerifiedStagedUpdate,
    ): FaroV5UpdateInstallResult {
        val blocked = readiness.check()
        if (blocked != AndroidUpdateInstallReadiness.BlockReason.NONE) {
            return FaroV5UpdateInstallResult.Blocked(blocked)
        }

        reconcileCoordinator.reconcilePending()
        if (store.pending().isNotEmpty()) {
            return FaroV5UpdateInstallResult.ExistingPendingOperation
        }

        val installedNow = installedIdentity.current()
        val eligibility =
            UpdateCheckPolicy.evaluate(
                installed = installedNow,
                candidate = staged.candidate,
                expectedChannel = configuration.channel,
            )
        require(eligibility is UpdateEligibility.Available) {
            "staged update is no longer eligible for install"
        }
        UpdateSigningLineagePolicy.requireCompatible(
            installed = installedNow.signingIdentity,
            artifact = staged.artifactIdentity.signingIdentity,
        )

        val expected =
            ExpectedInstall(
                expectedInstallId =
                    ExpectedInstallId(
                        "expected-" + UUID.randomUUID().toString(),
                    ),
                operationId =
                    UpdateOperationId(
                        "update-" + UUID.randomUUID().toString(),
                    ),
                installedIdentityAtPreparation = installedNow,
                staged = staged,
            )

        val stagedReader =
            FileUpdateArtifactStagePort(
                stagingRoot = stagingRoot,
                download =
                    UpdateArtifactDownloadPort {
                        error("install phase must read only the verified staged artifact")
                    },
            )
        val installer =
            AndroidPackageInstallerUpdateInstallPort(
                appContext,
                stagedReader,
                UpdateInstallControlCoordinator(store),
                AndroidUpdateInstallStatusSender(appContext),
            )
        val observation = installer.commit(expected)
        return FaroV5UpdateInstallResult.Attempted(
            expectedInstallId = expected.expectedInstallId.value,
            observation = observation,
        )
    }
}

sealed interface FaroV5UpdateInstallResult {
    data class Blocked(
        val reason: AndroidUpdateInstallReadiness.BlockReason,
    ) : FaroV5UpdateInstallResult

    data object ExistingPendingOperation : FaroV5UpdateInstallResult

    data class Attempted(
        val expectedInstallId: String,
        val observation: UpdateInstallSessionObservation,
    ) : FaroV5UpdateInstallResult
}
