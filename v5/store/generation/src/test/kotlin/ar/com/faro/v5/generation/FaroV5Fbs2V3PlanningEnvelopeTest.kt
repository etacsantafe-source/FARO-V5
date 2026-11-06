package ar.com.faro.v5.generation

import ar.com.faro.v5.maintenance.BackupSetId
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.FileRestoreArchiveIdentity
import ar.com.faro.v5.maintenance.MaintenanceOperationId
import ar.com.faro.v5.maintenance.MaterialWriteQuiescencePort
import ar.com.faro.v5.maintenance.StagedStorageGeneration
import ar.com.faro.v5.maintenance.StorageGenerationDescriptor
import ar.com.faro.v5.maintenance.StorageGenerationId
import ar.com.faro.v5.owner.monthlyplan.api.*
import ar.com.faro.v5.owner.reminders.api.*
import java.nio.ByteBuffer
import java.nio.file.Path
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** Semantic-only v3: not a real owner backup or public restore grant. */
class FaroV5Fbs2V3PlanningEnvelopeTest {
    @TempDir lateinit var root: Path
    private val passphrase = "fbs2-planning-v3-private-test".toCharArray()
    private val lease = object : MaterialWriteQuiescencePort {
        override fun <T> withMaterialWritesQuiesced(
            operationId: MaintenanceOperationId, block: () -> T,
        ): T = block() // JVM fixture only. NOT production global quiescence.
    }

    private data class Fresh(
        val stage: StagedStorageGeneration,
        val graph: FaroV5GenerationStoreGraph,
    ) : AutoCloseable {
        override fun close() = graph.close()
    }

    private fun fresh(name: String): Fresh {
        val descriptor = StorageGenerationDescriptor(
            StorageGenerationId("fbs2-v3-" + name), "v3-semantic-test-only", 123L,
        )
        val stage = FaroV5WorkspaceV2StageOwner(root).stageFreshAndVerify(descriptor)
        return Fresh(stage, FaroV5GenerationStoreGraph.openValidatedV2(stage))
    }

    private fun base(source: Fresh): FaroV5FiveStoreSemanticSet =
        FaroV5FiveStoreBackupCapture(source.graph, lease).capture(
            MaintenanceOperationId("v3-test-capture"),
            BackupSetId("v3-test-backup"),
            "5.0-v3-fixture", 456L,
        )

    private fun planning(): PlanningDomainSectionSnapshot {
        val rid = ReminderRuleId.of("rule-1")
        return PlanningDomainSectionSnapshot(
            8, ReminderSnapshot(1L, listOf(ReminderRule(
                rid, "Factura", "America/Argentina/Cordoba",
                LocalDate.of(2026,11,30), LocalTime.of(9,0),
                ReminderRecurrence.MONTHLY, true, null, 123L,
            )), listOf(ReminderOccurrence(
                ReminderOccurrenceId.of("occ-1"), rid, 5_000L,
                ReminderOccurrenceStatus.PENDING, null, 123L,
            ))),
            MonthlyPlanSnapshot(1L, listOf(MonthlyPlanItem(
                MonthlyPlanItemId.of("bill-1"), "Alquiler",
                YearMonth.of(2026,11), LocalDate.of(2026,12,5),
                MonthlyPlanItemStatus.PLANNED, 100_000L, "ARS", null, 123L,
            ))),
        )
    }

    private fun v3(source: Fresh): FaroV5FiveStoreSemanticSet =
        base(source).copy(
            g4Domains = source.graph.reality.domainBackup.snapshot(),
            planningDomains = planning(),
        )

    @Test fun v3CarriesAllFourSectionsAndIsCanonicalAndDeterministic() {
        fresh("canonical").use { source ->
            val s = v3(source)
            val raw = FaroV5FiveStoreSemanticCodec.encode(s)
            assertEquals(3, ByteBuffer.wrap(raw).getInt(4))
            assertEquals(s, FaroV5FiveStoreSemanticCodec.decode(raw))
            assertArrayEquals(raw, FaroV5FiveStoreSemanticCodec.encode(s))
            assertEquals(MonthlyPlanItemStatus.PLANNED,
                FaroV5FiveStoreSemanticCodec.decode(raw).planningDomains!!
                    .monthlyPlan.items.single().status)
        }
    }

    @Test fun legacyV1AndG4V2AreNotReinterpretedAsPlanningBackups() {
        fresh("legacy").use { source ->
            val legacy = base(source)
            val first = FaroV5FiveStoreSemanticCodec.encode(legacy)
            assertEquals(1, ByteBuffer.wrap(first).getInt(4))
            assertNull(FaroV5FiveStoreSemanticCodec.decode(first).planningDomains)
            assertArrayEquals(first,
                FaroV5FiveStoreSemanticCodec.encode(legacy.copy(planningDomains=null)))
            val g4 = legacy.copy(g4Domains=source.graph.reality.domainBackup.snapshot())
            val second = FaroV5FiveStoreSemanticCodec.encode(g4)
            assertEquals(2, ByteBuffer.wrap(second).getInt(4))
            assertEquals(g4, FaroV5FiveStoreSemanticCodec.decode(second))
            assertNull(FaroV5FiveStoreSemanticCodec.decode(second).planningDomains)
        }
    }

    @Test fun noV3WithoutExplicitG4AndCorrectPlanningSchema() {
        fresh("mandatory").use { source ->
            val base = base(source)
            assertThrows(Exception::class.java) {
                FaroV5FiveStoreSemanticCodec.encode(base.copy(planningDomains=planning()))
            }
            assertThrows(Exception::class.java) {
                FaroV5FiveStoreSemanticCodec.encode(base.copy(
                    g4Domains=source.graph.reality.domainBackup.snapshot(),
                    planningDomains=planning().copy(schemaVersion=7),
                ))
            }
        }
    }

    @Test fun digestHeaderLengthVersionTruncationAndTrailingAreFailClosed() {
        fresh("corrupt").use { source ->
            val raw=FaroV5FiveStoreSemanticCodec.encode(v3(source))
            val variants=listOf(
                raw.copyOf().apply { this[lastIndex]=(this[lastIndex].toInt() xor 1).toByte() },
                raw.copyOf().apply { ByteBuffer.wrap(this).putInt(20,Int.MAX_VALUE) },
                raw.copyOf().apply { ByteBuffer.wrap(this).putInt(8,-1) },
                raw.copyOf().apply { ByteBuffer.wrap(this).putInt(4,99) },
                raw.copyOf(raw.size-1),
                raw+byteArrayOf(1),
            )
            variants.forEach { bad ->
                assertThrows(Exception::class.java) {
                    FaroV5FiveStoreSemanticCodec.decode(bad)
                }
            }
        }
    }

    @Test fun farobak2AeadAuthenticatesAllFourV3Sections() {
        fresh("aead").use { source ->
            val expected=v3(source)
            val crypto=FaroV5FiveStorePortableBackupCrypto()
            val encrypted=crypto.seal(expected,passphrase)
            val opened=assertInstanceOf(FiveStorePortableBackupOpenResult.Opened::class.java,
                crypto.open(encrypted,passphrase))
            assertEquals(expected,opened.backupSet)
            val damaged=encrypted.copyOf()
            damaged[damaged.lastIndex]=(damaged.last().toInt() xor 1).toByte()
            assertTrue(crypto.open(damaged,passphrase)
                is FiveStorePortableBackupOpenResult.Rejected)
        }
    }

    @Test fun publicAndInternalG4StageRejectV3BeforeWritesOrArchivePin() {
        fresh("source").use { source ->
            val base=base(source)
            val v3=base.copy(
                g4Domains=source.graph.reality.domainBackup.snapshot(),
                planningDomains=planning(),
            )
            val crypto=FaroV5FiveStorePortableBackupCrypto()
            val encrypted=crypto.seal(v3,passphrase)
            fresh("target").use { target ->
                val before=target.graph.reality.fbs2Backup.snapshot()
                val workspaceBefore=target.graph.workspace!!.backup.snapshot()
                val restore=FaroV5FiveStoreStagedRestore(
                    root, target.stage, target.graph, lease,
                    archiveIdentity=FileRestoreArchiveIdentity(root),
                )
                val id=MaintenanceOperationId("v3-denied-no-pin")
                val denied=FiveStoreStagedRestoreResult.Blocked(
                    FiveStoreStagedRestoreBlock.UNSUPPORTED_PLANNING_ARCHIVE)
                assertEquals(denied,restore.restoreAuthenticated(id,encrypted,passphrase))
                assertEquals(denied,restore.restoreAuthenticatedG4StageOnly(id,encrypted,passphrase))
                assertEquals(before,target.graph.reality.fbs2Backup.snapshot())
                assertEquals(workspaceBefore,target.graph.workspace!!.backup.snapshot())
                assertNull(FileActiveGenerationPointer(root).read())
                // Identical operation can restore a safe legacy v1 archive:
                // v3 rejection must not have pinned identity or staged writes.
                val v1Encrypted=crypto.seal(base,passphrase)
                assertTrue(restore.restoreAuthenticated(id,v1Encrypted,passphrase)
                    is FiveStoreStagedRestoreResult.StagedVerified)
            }
        }
    }
}
