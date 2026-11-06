package ar.com.faro.v5.generation

import ar.com.faro.v5.kernel.contracts.AtomicApplyTransactionDecision
import ar.com.faro.v5.kernel.contracts.TransactionId
import ar.com.faro.v5.maintenance.BackupSetId
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.FileRestoreArchiveIdentity
import ar.com.faro.v5.maintenance.MaintenanceOperationId
import ar.com.faro.v5.maintenance.MaterialWriteQuiescencePort
import ar.com.faro.v5.maintenance.StagedStorageGeneration
import ar.com.faro.v5.maintenance.StorageGenerationDescriptor
import ar.com.faro.v5.maintenance.StorageGenerationId
import ar.com.faro.v5.owner.reminders.api.*
import ar.com.faro.v5.owner.monthlyplan.api.*
import ar.com.faro.v5.owner.objectives.api.*
import ar.com.faro.v5.owner.matters.api.*
import ar.com.faro.v5.store.reality.RealityPlanningBackupSnapshot
import java.nio.ByteBuffer
import java.nio.file.Path
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** Pure isolated STAGED V2 fixtures, no Android host, no live five-owner gate. */
class FaroV5Fbs2V3PlanningStageT2Test {
    @TempDir lateinit var root: Path
    private val password = "planning-v3-internal-stage-fixture".toCharArray()

    private class Lease : MaterialWriteQuiescencePort {
        var calls=0
        override fun <T> withMaterialWritesQuiesced(
            operationId: MaintenanceOperationId, block: () -> T,
        ): T {
            calls++
            return block() // test fixture, never production quiescence
        }
    }

    private data class Fresh(
        val stage: StagedStorageGeneration,
        val graph: FaroV5GenerationStoreGraph,
    ) : AutoCloseable {
        override fun close()=graph.close()
    }

    private fun fresh(label: String): Fresh {
        val descriptor=StorageGenerationDescriptor(
            StorageGenerationId("plan8-"+label),"plan-stage-only-test",222L,
        )
        val stage=FaroV5WorkspaceV2StageOwner(root).stageFreshAndVerify(descriptor)
        return Fresh(stage,FaroV5GenerationStoreGraph.openValidatedV2PlanningStage8(stage))
    }

    private fun planning(): RealityPlanningBackupSnapshot {
        val rule=ReminderRuleId.of("rule-1")
        return RealityPlanningBackupSnapshot(
            8,
            ReminderSnapshot(2L,listOf(ReminderRule(
                rule,"Renovar seguro","America/Argentina/Cordoba",
                LocalDate.of(2026,11,30),LocalTime.of(9,30),
                ReminderRecurrence.MONTHLY,true,"matter-1",100L,
            )),listOf(ReminderOccurrence(
                ReminderOccurrenceId.of("occ-1"),rule,88_000L,
                ReminderOccurrenceStatus.PENDING,null,101L,
            ))),
            MonthlyPlanSnapshot(1L,listOf(MonthlyPlanItem(
                MonthlyPlanItemId.of("bill-1"),"Seguro",
                YearMonth.of(2026,11),LocalDate.of(2026,12,1),
                MonthlyPlanItemStatus.PLANNED,199_900L,"ARS",null,102L,
            ))),
        )
    }

    private fun seed(source: Fresh): RealityPlanningBackupSnapshot {
        source.graph.reality.run(TransactionId("seed-"+source.stage.descriptor.generationId.value)) { scope ->
            source.graph.reality.objectivePersistence.upsert(
                ObjectiveRecord(ObjectiveId.of("obj-1"),ObjectiveTitle.of("Previsión"),
                    ObjectiveDetail.of("Fondo"),ObjectiveStatus.ACTIVE,100L),scope,
            )
            source.graph.reality.matterPersistence.upsert(
                MatterRecord(MatterId.of("matter-1"),MatterTitle.of("Seguro"),
                    MatterDetail.of("Renovación"),MatterStatus.WAITING,88_000L,100L),scope,
            )
            AtomicApplyTransactionDecision.Commit(Unit)
        }
        return planning().also { source.graph.reality.planningBackupRestore.restoreOrVerify(it) }
    }

    private fun capture(source: Fresh): Pair<FaroV5FiveStoreSemanticSet, ByteArray> {
        val set=FaroV5FiveStoreBackupCapture(source.graph,Lease()).capture(
            MaintenanceOperationId("capture-plan8"),BackupSetId("backup-plan8"),
            "5-plan8-stage-only",990L,
        )
        return set to FaroV5FiveStorePortableBackupCrypto().seal(set,password)
    }

    private fun restore(target: Fresh, archive: ByteArray,
        op: MaintenanceOperationId=MaintenanceOperationId("restore-plan8"),
        after: (FiveStoreStagedRestoreSection)->Unit = {},
    ): FiveStoreStagedRestoreResult =
        FaroV5FiveStoreStagedRestore(
            root,target.stage,target.graph,Lease(),after,
            FileRestoreArchiveIdentity(root),
        ).restoreAuthenticatedPlanningStageOnly(op,archive,password)

    @Test fun realSchemaEightPlanningAlwaysCapturedAsVersionThree() {
        fresh("capture").use { source ->
            val original=seed(source)
            val (set,encrypted)=capture(source)
            assertEquals(8,set.planningDomains?.schemaVersion)
            assertEquals(original.reminders,set.planningDomains?.reminders)
            assertEquals(original.monthlyPlan,set.planningDomains?.monthlyPlan)
            assertEquals(7,set.g4Domains?.schemaVersion)
            val opened=FaroV5FiveStorePortableBackupCrypto().open(encrypted,password)
            assertTrue(opened is FiveStorePortableBackupOpenResult.Opened)
            assertEquals(set,(opened as FiveStorePortableBackupOpenResult.Opened).backupSet)
            assertEquals(3,ByteBuffer.wrap(FaroV5FiveStoreSemanticCodec.encode(set)).getInt(4))
            assertThrows(IllegalStateException::class.java) {
                FaroV5GenerationStoreGraph.openValidatedV2(source.stage)
            }
        }
    }

    @Test fun restoresFiveStoresAndFourRealityOwnersWithExactReplay() {
        fresh("source").use { source ->
            val plan=seed(source)
            val (set,archive)=capture(source)
            fresh("target").use { target ->
                assertTrue(restore(target,archive) is FiveStoreStagedRestoreResult.StagedVerified)
                assertEquals(set.fourStore.reality,target.graph.reality.fbs2Backup.snapshot().legacy)
                assertEquals(set.g4Domains,target.graph.reality.fbs2Backup.snapshot().g4Domains)
                assertEquals(plan,target.graph.reality.fbs2Backup.snapshot().planningDomains)
                assertEquals(set.workspace,target.graph.workspace!!.backup.snapshot())
                assertEquals(set.fourStore.auxiliary,target.graph.auxiliary.backup.snapshot())
                assertEquals(set.fourStore.webEvidence,target.graph.webEvidence.backup.snapshot())
                assertEquals(set.fourStore.commercialEvidence,
                    target.graph.commercialEvidence.backup.snapshot())
                assertTrue(restore(target,archive) is FiveStoreStagedRestoreResult.StagedVerified)
                assertNull(FileActiveGenerationPointer(root).read())
            }
        }
    }

    @Test fun publicAndG4OnlyEntryPointsRejectPlanningBeforePinAndWrites() {
        fresh("src-deny").use { source ->
            seed(source)
            val (set,archive)=capture(source)
            fresh("dst-deny").use { target ->
                val before=target.graph.reality.fbs2Backup.snapshot()
                val identity=FileRestoreArchiveIdentity(root)
                val restore=FaroV5FiveStoreStagedRestore(
                    root,target.stage,target.graph,Lease(),archiveIdentity=identity,
                )
                val id=MaintenanceOperationId("same-operation-no-pin")
                val denied=FiveStoreStagedRestoreResult.Blocked(
                    FiveStoreStagedRestoreBlock.UNSUPPORTED_PLANNING_ARCHIVE)
                assertEquals(denied,restore.restoreAuthenticated(id,archive,password))
                assertEquals(denied,restore.restoreAuthenticatedG4StageOnly(id,archive,password))
                assertEquals(before,target.graph.reality.fbs2Backup.snapshot())
                val legacy=FaroV5FiveStorePortableBackupCrypto().seal(
                    set.copy(g4Domains=null,planningDomains=null),password,
                )
                assertEquals(denied,
                    restore.restoreAuthenticatedPlanningStageOnly(id,legacy,password))
                // Nothing got pinned by any rejected route: exact v3 still works.
                assertTrue(restore.restoreAuthenticatedPlanningStageOnly(id,archive,password)
                    is FiveStoreStagedRestoreResult.StagedVerified)
            }
        }
    }

    @Test fun crashAfterRealityIsIdempotentlyRecoveredUnderSamePin() {
        fresh("src-crash").use { source ->
            val plan=seed(source)
            val (_,archive)=capture(source)
            fresh("dst-crash").use { target ->
                val id=MaintenanceOperationId("crash-after-reality8")
                assertEquals(FiveStoreStagedRestoreResult.Blocked(
                    FiveStoreStagedRestoreBlock.STAGED_RESTORE_FAILED),
                    restore(target,archive,id) {
                        if(it==FiveStoreStagedRestoreSection.REALITY)
                            error("test-only crash")
                    })
                assertEquals(plan,target.graph.reality.planningBackup.snapshot())
                assertTrue(restore(target,archive,id) is FiveStoreStagedRestoreResult.StagedVerified)
                assertNull(FileActiveGenerationPointer(root).read())
            }
        }
    }

    @Test fun foreignPlanningIsNeverOverwrittenOrQuietlyRestored() {
        fresh("src-foreign").use { source ->
            seed(source)
            val (_,archive)=capture(source)
            fresh("dst-foreign").use { target ->
                val foreign=RealityPlanningBackupSnapshot(
                    8, ReminderSnapshot(0L,emptyList()),MonthlyPlanSnapshot(1L,emptyList()))
                target.graph.reality.planningBackupRestore.restoreOrVerify(foreign)
                val before=target.graph.reality.fbs2Backup.snapshot()
                assertEquals(FiveStoreStagedRestoreResult.Blocked(
                    FiveStoreStagedRestoreBlock.STAGED_RESTORE_FAILED),
                    restore(target,archive))
                assertEquals(before,target.graph.reality.fbs2Backup.snapshot())
                assertNull(FileActiveGenerationPointer(root).read())
            }
        }
    }

    @Test fun tamperingSubstitutionAndSelfTargetStayFailClosed() {
        fresh("src-safety").use { source ->
            seed(source)
            val (set,archive)=capture(source)
            fresh("dst-safety").use { target ->
                val broken=archive.copyOf()
                broken[broken.lastIndex]=(broken.last().toInt() xor 1).toByte()
                assertEquals(FiveStoreStagedRestoreResult.Blocked(
                    FiveStoreStagedRestoreBlock.INVALID_ARCHIVE,
                    FiveStorePortableBackupOpenFailure.AUTHENTICATION_FAILED),
                    restore(target,broken,MaintenanceOperationId("tamper-v3")))
                assertEquals(FiveStoreStagedRestoreResult.Blocked(
                    FiveStoreStagedRestoreBlock.SOURCE_TARGET_IDENTITY_CONFLICT),
                    restore(source,archive,MaintenanceOperationId("self-v3")))
                val id=MaintenanceOperationId("identity-v3")
                assertTrue(restore(target,archive,id) is FiveStoreStagedRestoreResult.StagedVerified)
                val substitute=FaroV5FiveStorePortableBackupCrypto().seal(set,password)
                assertFalse(substitute.contentEquals(archive))
                assertEquals(FiveStoreStagedRestoreResult.Blocked(
                    FiveStoreStagedRestoreBlock.ARCHIVE_IDENTITY_CONFLICT),
                    restore(target,substitute,id))
            }
        }
    }
}
