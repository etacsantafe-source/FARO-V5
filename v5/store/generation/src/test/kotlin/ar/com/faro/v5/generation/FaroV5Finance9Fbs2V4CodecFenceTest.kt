package ar.com.faro.v5.generation

import ar.com.faro.v5.maintenance.BackupSetId
import ar.com.faro.v5.maintenance.FileRestoreArchiveIdentity
import ar.com.faro.v5.maintenance.MaintenanceOperationId
import ar.com.faro.v5.maintenance.MaterialWriteQuiescencePort
import ar.com.faro.v5.maintenance.StagedStorageGeneration
import ar.com.faro.v5.maintenance.StorageGenerationDescriptor
import ar.com.faro.v5.maintenance.StorageGenerationId
import java.nio.ByteBuffer
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** M0C1 semantic codec and legacy restore DENY only. No real Finance9 restore. */
class FaroV5Finance9Fbs2V4CodecFenceTest {
    @TempDir lateinit var root: Path
    private val passphrase = "m0c1-test-only-password".toCharArray()
    private val lease = object : MaterialWriteQuiescencePort {
        override fun <T> withMaterialWritesQuiesced(
            operationId: MaintenanceOperationId,
            block: () -> T,
        ): T = block() // Test harness only, not a product lock.
    }

    private data class Stage(
        val staged: StagedStorageGeneration,
        val graph: FaroV5GenerationStoreGraph,
    ) : AutoCloseable {
        override fun close() = graph.close()
    }

    private fun stage(name: String): Stage {
        val descriptor = StorageGenerationDescriptor(
            StorageGenerationId("m0c1-" + name), "finance9-fbs2v4-fixture", 100L,
        )
        val staged = FaroV5WorkspaceV2StageOwner(root)
            .stageFreshPlanning8AndVerify(descriptor)
        return Stage(
            staged,
            FaroV5GenerationStoreGraph.openValidatedV2PlanningStage8(staged),
        )
    }

    private fun base(stage: Stage): FaroV5FiveStoreSemanticSet =
        FaroV5FiveStoreBackupCapture(stage.graph, lease).capture(
            MaintenanceOperationId("m0c1-capture"),
            BackupSetId("m0c1-set-" + stage.staged.descriptor.generationId.value),
            "5.0-dev", 123L,
        )

    private fun finance(): Finance9SectionSnapshot =
        Finance9SectionSnapshot.empty().let { empty ->
            empty.copy(tables = empty.tables.map {
                when (it.table) {
                    "finance_opening_confirmation" -> it.copy(rows = listOf(
                        listOf("account-a", null, "LEGACY_UNVERIFIED", null, "MIGRATED_FROM_8"),
                    ))
                    "finance_budget_limit" -> it.copy(rows = listOf(
                        listOf("2026-11", "Comida", "ARS", "200000", "1", "123", "HUMAN"),
                    ))
                    else -> it
                }
            })
        }

    @Test fun canonicalFinance9SectionHasEightTablesAndNoAclOrGrant() {
        val snapshot = finance()
        assertEquals(8, snapshot.tables.size)
        assertTrue(snapshot.tables.none {
            it.table.contains("acl") || it.table.contains("grant")
        })
        val bytes = FaroV5Finance9BackupCodec.encode(snapshot)
        assertEquals(snapshot, FaroV5Finance9BackupCodec.decode(bytes))
        assertArrayEquals(bytes, FaroV5Finance9BackupCodec.encode(snapshot))
    }

    @Test fun fbs2V4HasFiveMandatorySectionsAndRoundTrips() {
        stage("roundtrip").use { stage ->
            val snapshot = base(stage).copy(finance9 = finance())
            val bytes = FaroV5FiveStoreSemanticCodec.encode(snapshot)
            assertEquals(4, ByteBuffer.wrap(bytes).getInt(4))
            assertEquals(snapshot, FaroV5FiveStoreSemanticCodec.decode(bytes))
            assertArrayEquals(bytes, FaroV5FiveStoreSemanticCodec.encode(snapshot))
            assertThrows(Exception::class.java) {
                FaroV5FiveStoreSemanticCodec.encode(
                    snapshot.copy(planningDomains = null),
                )
            }
        }
    }

    @Test fun oldV1V2V3StillDecodeWithoutFinanceOrVersionReinterpretation() {
        stage("legacy").use { stage ->
            val v3 = base(stage)
            val v2 = v3.copy(planningDomains = null)
            val v1 = v2.copy(g4Domains = null)
            listOf(v1, v2, v3).forEachIndexed { index, legacy ->
                val bytes = FaroV5FiveStoreSemanticCodec.encode(legacy)
                assertEquals(index + 1, ByteBuffer.wrap(bytes).getInt(4))
                val decoded = FaroV5FiveStoreSemanticCodec.decode(bytes)
                assertNull(decoded.finance9)
                assertEquals(legacy, decoded)
                assertArrayEquals(bytes, FaroV5FiveStoreSemanticCodec.encode(decoded))
            }
        }
    }

    @Test fun unexpectedTableNumericDuplicatedKeyCorruptDigestAndTruncatedV4FailClosed() {
        val snapshot = finance()
        assertThrows(Exception::class.java) {
            snapshot.copy(tables = snapshot.tables.dropLast(1))
        }
        assertThrows(Exception::class.java) {
            snapshot.copy(tables = snapshot.tables.map {
                if (it.table == "finance_budget_limit")
                    it.copy(rows = listOf(
                        listOf("2026-11", "Food", "ARS", "1.25", "1", "123", "HUMAN"),
                    ))
                else it
            })
        }
        assertThrows(Exception::class.java) {
            snapshot.copy(tables = snapshot.tables.map {
                if (it.table == "finance_budget_limit")
                    it.copy(rows = it.rows + it.rows)
                else it
            })
        }
        val encoded = FaroV5Finance9BackupCodec.encode(snapshot)
        assertThrows(Exception::class.java) {
            FaroV5Finance9BackupCodec.decode(encoded + byteArrayOf(1))
        }
        stage("corrupt").use { stage ->
            val raw = FaroV5FiveStoreSemanticCodec.encode(
                base(stage).copy(finance9 = snapshot),
            )
            listOf(
                raw.copyOf().apply {
                    this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte()
                },
                raw.copyOf(raw.size - 1),
                raw.copyOf().apply { ByteBuffer.wrap(this).putInt(4, 5) },
                raw.copyOf().apply { ByteBuffer.wrap(this).putInt(24, Int.MAX_VALUE) },
            ).forEach { bad ->
                assertThrows(Exception::class.java) {
                    FaroV5FiveStoreSemanticCodec.decode(bad)
                }
            }
        }
    }

    @Test fun farobak2AeadAuthenticatesAllFiveSections() {
        stage("aead").use { stage ->
            val expected = base(stage).copy(finance9 = finance())
            val crypto = FaroV5FiveStorePortableBackupCrypto()
            val archive = crypto.seal(expected, passphrase)
            val opened = assertInstanceOf(
                FiveStorePortableBackupOpenResult.Opened::class.java,
                crypto.open(archive, passphrase),
            )
            assertEquals(expected, opened.backupSet)
            val damaged = archive.copyOf()
            damaged[damaged.lastIndex] = (damaged.last().toInt() xor 1).toByte()
            assertTrue(
                crypto.open(damaged, passphrase) is FiveStorePortableBackupOpenResult.Rejected,
            )
        }
    }

    @Test fun legacyRestorersRejectV4BeforeAnyWritesOrArchivePin() {
        stage("source").use { source ->
            val expected = base(source).copy(finance9 = finance())
            val archive = FaroV5FiveStorePortableBackupCrypto().seal(
                expected, passphrase,
            )
            stage("target").use { target ->
                val realityBefore = target.graph.reality.fbs2Backup.snapshot()
                val workspaceBefore = target.graph.workspace!!.backup.snapshot()
                val restorer = FaroV5FiveStoreStagedRestore(
                    root, target.staged, target.graph, lease,
                    archiveIdentity = FileRestoreArchiveIdentity(root),
                )
                val blocked = FiveStoreStagedRestoreResult.Blocked(
                    FiveStoreStagedRestoreBlock.UNSUPPORTED_FINANCE9_ARCHIVE,
                )
                val operation = MaintenanceOperationId("m0c1-deny-v4")
                assertEquals(
                    blocked, restorer.restoreAuthenticated(
                        operation, archive, passphrase,
                    ),
                )
                assertEquals(
                    blocked, restorer.restoreAuthenticatedG4StageOnly(
                        operation, archive, passphrase,
                    ),
                )
                assertEquals(
                    blocked, restorer.restoreAuthenticatedPlanningStageOnly(
                        operation, archive, passphrase,
                    ),
                )
                assertEquals(realityBefore, target.graph.reality.fbs2Backup.snapshot())
                assertEquals(workspaceBefore, target.graph.workspace!!.backup.snapshot())
            }
        }
    }
}
