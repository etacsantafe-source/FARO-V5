package ar.com.faro.v5.generation

import ar.com.faro.v5.evidence.CommercialEvidenceStore
import ar.com.faro.v5.evidence.WebEvidenceStore
import ar.com.faro.v5.kernel.contracts.AtomicApplyTransactionDecision
import ar.com.faro.v5.kernel.contracts.TransactionId
import ar.com.faro.v5.maintenance.BackupSetId
import ar.com.faro.v5.maintenance.MaintenanceOperationId
import ar.com.faro.v5.maintenance.MaterialWriteQuiescencePort
import ar.com.faro.v5.maintenance.SemanticBackupCaptureCoordinator
import ar.com.faro.v5.maintenance.StorageGenerationId
import ar.com.faro.v5.owner.objectives.api.*
import ar.com.faro.v5.owner.matters.api.*
import ar.com.faro.v5.store.auxiliary.AuxiliaryDeliveryStore
import ar.com.faro.v5.store.reality.RealityDomainBackupSnapshot
import ar.com.faro.v5.store.reality.RealityStore
import ar.com.faro.v5.workspace.WorkspaceBackupSnapshot
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class FaroV5Fbs2G4CodecTest {
    @TempDir lateinit var temp: Path

    private val lease = object : MaterialWriteQuiescencePort {
        override fun <T> withMaterialWritesQuiesced(
            operationId: MaintenanceOperationId, block: () -> T,
        ): T = block() // JVM fixture only, never a host admission
    }

    private fun original(): FaroV5FiveStoreSemanticSet {
        val dir = Files.createDirectories(temp.resolve("original"))
        return RealityStore(dir.resolve("reality.db").toString()).use { reality ->
            CommercialEvidenceStore(dir.resolve("commercial.db").toString()).use { commercial ->
                WebEvidenceStore(dir.resolve("web.db").toString()).use { web ->
                    AuxiliaryDeliveryStore.open(dir.resolve("auxiliary.db").toString()).use { auxiliary ->
                        val four = SemanticBackupCaptureCoordinator(
                            lease, reality.backup, commercial.backup, web.backup, auxiliary.backup,
                        ).capture(
                            MaintenanceOperationId("fbs2-g4-unit"),
                            BackupSetId("fbs2-g4-unit-backup"),
                            StorageGenerationId("generation-fbs2-g4-test"),
                            "5-unit-test", 123_456L,
                        )
                        FaroV5FiveStoreSemanticSet(
                            four, WorkspaceBackupSnapshot(
                                schemaVersion = WorkspaceBackupSnapshot.SCHEMA_VERSION,
                                revision = 0L,
                                activeConversationId = null,
                                conversations = emptyList(),
                                receipts = emptyList(),
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun domain(): RealityDomainBackupSnapshot =
        RealityStore(":memory:").use { reality ->
            reality.run(TransactionId("tx-fbs2-g4-domain")) { scope ->
                reality.objectivePersistence.upsert(
                    ObjectiveRecord(
                        ObjectiveId.of("goal-1"), ObjectiveTitle.of("Fondo de reserva"),
                        ObjectiveDetail.of("Reserva de emergencia"),
                        ObjectiveStatus.ACTIVE, 100L,
                    ), scope,
                )
                reality.objectivePersistence.upsert(
                    ObjectiveRecord(
                        ObjectiveId.of("goal-2"), ObjectiveTitle.of("Pagar obligaciones"),
                        ObjectiveDetail.of("Sin credenciales"), ObjectiveStatus.PAUSED, 200L,
                    ), scope,
                )
                reality.matterPersistence.upsert(
                    MatterRecord(
                        MatterId.of("matter-a"), MatterTitle.of("Factura"),
                        MatterDetail.of("Vence en abril"), MatterStatus.WAITING, 1_000L, 150L,
                    ), scope,
                )
                reality.matterPersistence.upsert(
                    MatterRecord(
                        MatterId.of("matter-b"), MatterTitle.of("Sin vencimiento"),
                        MatterDetail.of("Pendiente"), MatterStatus.OPEN, null, 250L,
                    ), scope,
                )
                AtomicApplyTransactionDecision.Commit(Unit)
            }
            reality.domainBackup.snapshot()
        }

    @Test fun fbs2V2RoundTripsGenuineG4OwnersWithUnicodeAndNullDue() {
        val set = original().copy(g4Domains = domain())
        val bytes = FaroV5FiveStoreSemanticCodec.encode(set)
        assertEquals(2, ByteBuffer.wrap(bytes).getInt(4))
        assertEquals(set, FaroV5FiveStoreSemanticCodec.decode(bytes))
        assertArrayEquals(bytes, FaroV5FiveStoreSemanticCodec.encode(set))
        assertEquals(2L, set.g4Domains!!.objectives.revision)
        assertNull(set.g4Domains!!.matters.matters.last().dueAtEpochMillis)
    }

    @Test fun v1EncodingIsByteStableAndNeverClaimsEmptyG4Owners() {
        val original = original()
        val before = FaroV5FiveStoreSemanticCodec.encode(original)
        assertEquals(1, ByteBuffer.wrap(before).getInt(4))
        assertNull(FaroV5FiveStoreSemanticCodec.decode(before).g4Domains)
        val withG4 = original.copy(g4Domains = domain())
        val after = FaroV5FiveStoreSemanticCodec.encode(withG4.copy(g4Domains = null))
        assertArrayEquals(before, after)
        assertEquals(2, ByteBuffer.wrap(FaroV5FiveStoreSemanticCodec.encode(withG4)).getInt(4))
    }

    @Test fun g4TypedCodecRejectsWrongSchemaOrderAndFalseRevision() {
        val good = domain()
        assertEquals(good, FaroV5G4DomainSemanticCodec.decode(
            FaroV5G4DomainSemanticCodec.encode(good),
        ))
        val wrong = listOf(
            good.copy(schemaVersion = 6),
            good.copy(objectives = good.objectives.copy(
                objectives = good.objectives.objectives.reversed(),
            )),
            good.copy(matters = good.matters.copy(revision = 1L)),
        )
        wrong.forEach { bad ->
            assertThrows(Exception::class.java) { FaroV5G4DomainSemanticCodec.encode(bad) }
        }
        val encoded = FaroV5G4DomainSemanticCodec.encode(good)
        val impossibleCount = encoded.copyOf().apply {
            ByteBuffer.wrap(this).putInt(20, Int.MAX_VALUE)
        }
        assertThrows(Exception::class.java) {
            FaroV5G4DomainSemanticCodec.decode(impossibleCount)
        }
        val marker = "Reserva de emergencia".toByteArray(Charsets.UTF_8)
        val offset = (0..encoded.size - marker.size).first { i ->
            marker.indices.all { j -> encoded[i + j] == marker[j] }
        }
        val invalidUtf8 = encoded.copyOf().apply { this[offset] = 0xff.toByte() }
        assertThrows(Exception::class.java) {
            FaroV5G4DomainSemanticCodec.decode(invalidUtf8)
        }
        assertThrows(Exception::class.java) {
            FaroV5G4DomainSemanticCodec.decode(encoded.copyOf(encoded.size - 1))
        }
        assertThrows(Exception::class.java) {
            FaroV5G4DomainSemanticCodec.decode(encoded + byteArrayOf(0))
        }
    }

    @Test fun fbs2V2ChecksThreeDigestsBoundsVersionsAndTrailingBytes() {
        val set = original().copy(g4Domains = domain())
        val valid = FaroV5FiveStoreSemanticCodec.encode(set)
        val variants = listOf(
            valid.copyOf().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() },
            valid.copyOf().apply { ByteBuffer.wrap(this).putInt(16, Int.MAX_VALUE) },
            valid.copyOf().apply { ByteBuffer.wrap(this).putInt(8, -1) },
            valid.copyOf().apply { ByteBuffer.wrap(this).putInt(4, 99) },
            valid.copyOf(valid.size - 1),
            valid + byteArrayOf(0),
        )
        for (corrupt in variants) {
            assertThrows(Exception::class.java) {
                FaroV5FiveStoreSemanticCodec.decode(corrupt)
            }
        }
    }

    @Test fun farobak2AeadAuthenticatesTypedG4AndRejectsTampering() {
        val set = original().copy(g4Domains = domain())
        val crypto = FaroV5FiveStorePortableBackupCrypto()
        val pass = "faro-private-g4-test-passphrase".toCharArray()
        val encrypted = crypto.seal(set, pass)
        val opened = assertInstanceOf(
            FiveStorePortableBackupOpenResult.Opened::class.java,
            crypto.open(encrypted, pass),
        )
        assertNotNull(opened.backupSet.g4Domains)
        assertEquals(set, opened.backupSet)
        val tampered = encrypted.copyOf()
        tampered[tampered.lastIndex] = (tampered.last().toInt() xor 1).toByte()
        assertTrue(crypto.open(tampered, pass) is FiveStorePortableBackupOpenResult.Rejected)
        assertTrue(crypto.open(encrypted, "another-invalid-passphrase".toCharArray())
            is FiveStorePortableBackupOpenResult.Rejected)
    }
}
