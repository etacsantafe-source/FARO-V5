package ar.com.faro.v5.app

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ar.com.faro.v5.evidence.CommercialEvidenceStore
import ar.com.faro.v5.generation.FaroV5Finance9StagedMigration
import ar.com.faro.v5.generation.FaroV5GenerationStoreGraph
import ar.com.faro.v5.generation.FaroV5WorkspaceV2StageOwner
import ar.com.faro.v5.kernel.contracts.SingleProcessMaterialWriteGate
import ar.com.faro.v5.maintenance.*
import ar.com.faro.v5.store.reality.*
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.file.Files
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class S4W05StockReadAccessibilityAndroidTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun isolatedLauncherRendersReadOnlyStockAtTwoHundredPercentAndRedactsOnRecreation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("ar.com.faro.alpha.v2coredev", context.packageName)
        assertEquals(35, Build.VERSION.SDK_INT)
        assertEquals(2f, context.resources.configuration.fontScale, 0.01f)
        assertFalse(context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.GET_PERMISSIONS)
            .requestedPermissions.orEmpty().contains("android.permission.INTERNET"))
        val root = context.noBackupFilesDir.toPath().resolve("faro-v2-core-dev-root")
        assertFalse("CI fixture requires a fresh isolated app", Files.exists(root))
        prepareSelectedLedger(context)

        ActivityScenario.launch(FaroV5V2CoreDevActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                clickStockEntry(activity.window.decorView)
            }
            waitForStock("Yerba")
            compose.onNodeWithText("Stock", useUnmergedTree = true)
                .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
                .assertIsDisplayed()
            compose.onAllNodes(hasScrollAction())[0]
                .performScrollToNode(hasText("Disponible: 5 unidades"))
            compose.onNodeWithText("Disponible: 5 unidades", useUnmergedTree = true).assertIsDisplayed()
            compose.onAllNodes(hasScrollAction())[0]
                .performScrollToNode(hasText("Recepción confirmada"))
            compose.onNodeWithText("Recepción confirmada", useUnmergedTree = true).assertIsDisplayed()

            scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            compose.waitUntil(20_000) {
                var ready = false
                scenario.onActivity {
                    ready = it.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&
                        findStockEntry(it.window.decorView) != null
                }
                ready
            }
            compose.onNodeWithText("Disponible: 5 unidades", useUnmergedTree = true).assertDoesNotExist()
            scenario.onActivity { clickStockEntry(it.window.decorView) }
            waitForStock("Yerba")
            compose.onAllNodes(hasScrollAction())[0]
                .performScrollToNode(hasText("Disponible: 5 unidades"))
            compose.onNodeWithText("Disponible: 5 unidades", useUnmergedTree = true).assertIsDisplayed()

            scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
            compose.waitUntil(10_000) {
                compose.onAllNodes(hasText("Stock no disponible. Falta autorización de lectura."), useUnmergedTree = true)
                    .fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Disponible: 5 unidades", useUnmergedTree = true).assertDoesNotExist()
        }
    }

    private fun waitForStock(text: String) {
        compose.waitUntil(20_000) {
            compose.onAllNodes(hasText(text), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun clickStockEntry(view: View) {
        checkNotNull(findStockEntry(view)) { "Stock entry missing from isolated launcher" }.performClick()
    }

    private fun findStockEntry(view: View): Button? {
        if (view is Button && view.text == "Leer stock en SmartCompra (DEV)") return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findStockEntry(view.getChildAt(index))?.let { return it }
            }
        }
        return null
    }

    /** SQL15 fixture with the same package/UID principal and real completion reader format. */
    private fun prepareSelectedLedger(context: Context) {
        val root = context.noBackupFilesDir.toPath().resolve("faro-v2-core-dev-root")
        val pointer = FileActiveGenerationPointer(root)
        val gate = SingleProcessMaterialWriteGate()
        val original = checkNotNull(pointer.compareAndSet(null, StorageGenerationId("android-stock-origin")))
        val fresh = FaroV5WorkspaceV2StageOwner(root).stageFreshPlanning8AndVerify(
            StorageGenerationDescriptor(StorageGenerationId("android-stock-sql15"), "android-stock-test", 100),
        )
        val stage = FaroV5Finance9StagedMigration(root, MaterialWriteGateQuiescence(gate)).migrate(
            MaintenanceOperationId("android-stock-finance"), fresh, original, "a".repeat(64), 1_000,
        )
        FaroV5GenerationStoreGraph.openQuarantinedAuxiliaryReceiptV4Stage(stage, pointer).close()
        val file = stage.directory.resolve(GenerationStoreFiles.REALITY)
        RealityHousehold10StagedFilePort.migrateTrustedStagedNine(file)
        RealityHousehold11StagedFilePort.migrateTrustedStagedTen(file)
        RealityHousehold12StagedFilePort.migrateTrustedStagedEleven(file) {}
        RealityHousehold13StagedFilePort.migrateTrustedStagedTwelve(file)
        RealityHousehold14StagedFilePort.migrateTrustedStagedThirteen(file) {}
        RealityHousehold15StagedFilePort.migrateTrustedStagedFourteen(file) {}
        CommercialEvidenceStore(
            fileName = stage.directory.resolve(GenerationStoreFiles.COMMERCIAL_EVIDENCE).toString(),
            stagedTypedTermsV2 = true, stagedSemanticBackupV2 = true,
        ).close()
        val principal = FaroV5W05StockPanelPolicy.principal(context.packageName, context.applicationInfo.uid)
        SQLiteDatabase.openDatabase(file.toString(), null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL("INSERT INTO household_confirmed_purchase VALUES " +
                "('purchase','Yerba',10000,'UNIT',?,1,'MANUAL_EXPLICIT',1)", arrayOf(principal))
            db.execSQL("INSERT INTO household_stock_receipt VALUES " +
                "('receipt','i-receipt','purchase','Yerba',7000,'UNIT',0,20,1)")
            db.execSQL("UPDATE household_stock_receipt_state SET revision=1")
            db.execSQL("INSERT INTO household_stock_reservation_event VALUES " +
                "('reserve','i-reserve','RESERVE','reserve','Yerba','UNIT',2000,?,21,2)", arrayOf(principal))
        }
        val selected = checkNotNull(pointer.compareAndSet(original.generationId, stage.descriptor.generationId))
        val op = MaintenanceOperationId("android-stock-restore")
        FileBootstrapJournal(root).create(BootstrapJournalEntry(op, MaintenanceOperationKind.RESTORE,
            original.generationId, selected.generationId, BootstrapPhase.COMPLETED, true))
        FileGenerationReplayFence(root).install(MaintenanceOperationKind.RESTORE, selected.generationId)
        val stream = ByteArrayOutputStream()
        DataOutputStream(stream).use {
            it.writeUTF("FARO_SQL15_EVIDENCE2_CUTOVER_V1")
            it.writeUTF(op.value)
            it.writeLong(original.sequence)
            it.writeUTF(original.generationId.value)
            it.writeUTF(selected.generationId.value)
            it.writeUTF(original.generationId.value)
            repeat(4) { _ -> it.writeUTF("b".repeat(64)) }
            it.writeUTF("android-stock-precursor")
        }
        val ticket = stream.toByteArray()
        Files.write(root.resolve("bootstrap/sql15-evidence2-cutover.ticket"),
            ticket + MessageDigest.getInstance("SHA-256").digest(ticket))
    }
}
