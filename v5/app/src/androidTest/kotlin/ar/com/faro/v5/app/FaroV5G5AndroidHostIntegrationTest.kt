package ar.com.faro.v5.app

import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ar.com.faro.v5.kernel.contracts.AtomicApplyTransactionDecision
import ar.com.faro.v5.kernel.contracts.TransactionId
import ar.com.faro.v5.maintenance.FileStorageGenerationLayout
import ar.com.faro.v5.maintenance.GenerationStoreFiles
import ar.com.faro.v5.maintenance.StorageGenerationId
import ar.com.faro.v5.owner.finance.api.*
import ar.com.faro.v5.owner.objectives.api.*
import ar.com.faro.v5.owner.matters.api.*
import ar.com.faro.v5.store.reality.RealityStore
import ar.com.faro.v5.surface.FaroV5SearchCoverage
import ar.com.faro.v5.surface.FaroV5SearchOpenResult
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * G5 Android OS-only evidence, not certification of the user's phone.
 * Instrumented APK runs under the isolated v2CoreDev UID, with a disposable
 * cache-root five-store fixture. Never touches the normal DEV root.
 */
@RunWith(AndroidJUnit4::class)
class FaroV5G5AndroidHostIntegrationTest {
    @Test
    fun fourRealOwnersSearchNavigateAndRevokeOnAndroidWithoutEgress() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("ar.com.faro.alpha.v2coredev", context.packageName)
        val info = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS or
                PackageManager.GET_ACTIVITIES or
                PackageManager.GET_SERVICES or
                PackageManager.GET_RECEIVERS or
                PackageManager.GET_PROVIDERS,
        )
        assertTrue("DEV must request zero permissions", info.requestedPermissions.isNullOrEmpty())
        assertTrue("DEV has no providers", info.providers.isNullOrEmpty())
        assertTrue("DEV has no services", info.services.isNullOrEmpty())
        assertTrue("DEV has no receivers", info.receivers.isNullOrEmpty())
        val activities = info.activities.orEmpty()
        assertEquals("DEV exposes only its own launcher", 1, activities.size)
        assertEquals(FaroV5V2CoreDevActivity::class.java.name, activities.single().name)
        assertTrue("Only intended launcher may be exported", activities.single().exported)

        val root = Files.createTempDirectory(context.cacheDir.toPath(), "faro-g5-android-")
        // This test is intentionally not placed under noBackupFilesDir:
        // no production or interactive DEV Workspace may be modified.
        assertFalse(root.startsWith(context.noBackupFilesDir.toPath()))
        try {
            FaroV5V2CoreDevAuthority(root).open().use { }
            val staged = FileStorageGenerationLayout(root).requireStaged(
                StorageGenerationId("v2-core-dev-workspace-1"),
            )
            val realityFile = staged.directory.resolve(GenerationStoreFiles.REALITY)
            RealityStore(realityFile.toString()).use { reality ->
                reality.run(TransactionId("g5-android-only-fixture")) { scope ->
                    val account = FinanceAccountId.of("g5-android-disposable-account")
                    reality.financePersistence.createAccount(
                        FinanceAccountCreate(
                            account,
                            FinanceAccountDisplayName.of("Cuenta de ensayo Android"),
                            FinanceCurrencyCode.of("ARS"),
                            FinanceAccountNature.BANK,
                        ), scope,
                    )
                    reality.financePersistence.recordEntry(
                        FinanceEntryRecord(
                            entryId = FinanceEntryId.of("g5-android-disposable-entry"),
                            accountId = account,
                            amountMinor = 123456L,
                            kind = FinanceEntryKind.EXPENSE,
                            concept = FinanceEntryConcept.of("G5Android Finanzas"),
                            category = FinanceEntryCategory.of("Ensayo"),
                            effectiveAtEpochMillis = 1000L,
                            recordedAtEpochMillis = 2000L,
                        ), scope,
                    )
                    reality.objectivePersistence.upsert(
                        ObjectiveRecord(
                            ObjectiveId.of("g5-android-disposable-objective"),
                            ObjectiveTitle.of("G5Android Objetivos"),
                            ObjectiveDetail.of("Objetivo de ensayo Android"),
                            ObjectiveStatus.ACTIVE,
                            3000L,
                        ), scope,
                    )
                    reality.matterPersistence.upsert(
                        MatterRecord(
                            MatterId.of("g5-android-disposable-matter"),
                            MatterTitle.of("G5Android Asuntos"),
                            MatterDetail.of("Asunto de ensayo Android"),
                            MatterStatus.WAITING,
                            4000L,
                            3500L,
                        ), scope,
                    )
                    AtomicApplyTransactionDecision.Commit(Unit)
                }
                assertEquals(1, reality.financePersistence.read().entries.size)
                assertEquals(1, reality.objectivePersistence.read().objectives.size)
                assertEquals(1, reality.matterPersistence.read().matters.size)
            }

            val first = FaroV5V2CoreDevAuthority(root).open()
            val stalePort = first.searchReadPort
            val staleHit = try {
                val page = stalePort.search("G5Android")
                assertEquals(FaroV5SearchCoverage.COMPLETE, page.coverage)
                assertEquals(3, page.hits.size)
                assertEquals(
                    setOf(
                        FaroV5FinanceSearchReadSource.NAMESPACE,
                        FaroV5ObjectivesSearchReadSource.NAMESPACE,
                        FaroV5MattersSearchReadSource.NAMESPACE,
                    ),
                    page.hits.map { it.namespace }.toSet(),
                )
                page.hits.forEach { hit ->
                    assertEquals(FaroV5SearchOpenResult.CONFIRMED, stalePort.openAuthorized(hit))
                    val target = checkNotNull(stalePort.takeConfirmedDomainTarget())
                    assertEquals(hit.canonicalSource, target.canonicalSource)
                    assertEquals(hit.title, target.title)
                    assertNull("one-shot target", stalePort.takeConfirmedDomainTarget())
                    assertFalse("no raw financial amount", target.summary.contains("123456"))
                    assertFalse("no raw account ID", target.summary.contains("g5-android-disposable-account"))
                }
                val miss = stalePort.search("g5-android-no-hits-987654")
                assertEquals(FaroV5SearchCoverage.COMPLETE, miss.coverage)
                assertTrue(miss.hits.isEmpty())
                stalePort.search("G5Android").hits.first()
            } finally {
                first.close()
            }
            assertEquals(FaroV5SearchOpenResult.UNAVAILABLE, stalePort.openAuthorized(staleHit))
            FaroV5V2CoreDevAuthority(root).open().use { second ->
                assertEquals(FaroV5SearchOpenResult.UNAVAILABLE, stalePort.openAuthorized(staleHit))
                val fresh = second.searchReadPort.search("G5Android")
                assertEquals(FaroV5SearchCoverage.COMPLETE, fresh.coverage)
                assertEquals(3, fresh.hits.size)
                assertEquals(
                    FaroV5SearchOpenResult.CONFIRMED,
                    second.searchReadPort.openAuthorized(fresh.hits.first()),
                )
                assertNotNull(second.searchReadPort.takeConfirmedDomainTarget())
            }
        } finally {
            deleteFixtureTree(root)
        }
    }

    private fun deleteFixtureTree(root: Path) {
        if (!Files.exists(root)) return
        Files.walk(root).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
        }
    }
}
