package ar.com.faro.v5.generation

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.MaintenanceOperationId
import ar.com.faro.v5.maintenance.MaterialWriteQuiescencePort
import ar.com.faro.v5.maintenance.StagedStorageGeneration
import ar.com.faro.v5.maintenance.StorageGenerationDescriptor
import ar.com.faro.v5.maintenance.StorageGenerationId
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** M0B only: verified staging topology, never an active Finance9 host. */
class FaroV5Finance9StagedMigrationTest {
    @TempDir lateinit var root: Path

    private val quiescence = object : MaterialWriteQuiescencePort {
        override fun <T> withMaterialWritesQuiesced(
            operationId: MaintenanceOperationId,
            block: () -> T,
        ): T = block()
    }

    private fun descriptor(id: String) = StorageGenerationDescriptor(
        StorageGenerationId(id), "w01-finance9-stage-test", 1000L,
    )

    private fun pointer(): ActiveGenerationPointer =
        checkNotNull(FileActiveGenerationPointer(root).compareAndSet(
            null, StorageGenerationId("historical-active"),
        ))

    private fun stage(id: String, planning8: Boolean = true): StagedStorageGeneration =
        FaroV5WorkspaceV2StageOwner(root).let { owner ->
            if (planning8) owner.stageFreshPlanning8AndVerify(descriptor(id))
            else owner.stageFreshAndVerify(descriptor(id))
        }

    private fun migrate(
        staged: StagedStorageGeneration,
        active: ActiveGenerationPointer,
        owner: FaroV5Finance9StagedMigration =
            FaroV5Finance9StagedMigration(root, quiescence),
    ) = owner.migrate(
        MaintenanceOperationId("w01-m0b-" + staged.descriptor.generationId.value),
        staged, active, "a".repeat(64), 1000L,
    )

    private fun version(staged: StagedStorageGeneration): Long =
        BundledSQLiteDriver().open(staged.directory.resolve("reality.db").toString()).use { db ->
            db.prepare("PRAGMA user_version").use { s ->
                check(s.step())
                s.getLong(0)
            }
        }

    @Test fun canonicalSealedEightUpgradesToNineWithoutSwitchingPointer() {
        val active = pointer()
        val s = stage("target-valid")
        val result = migrate(s, active)
        assertEquals(s.descriptor, result.descriptor)
        assertEquals(9L, version(s))
        assertEquals(active, FileActiveGenerationPointer(root).read())
    }

    @Test fun activeTargetIsRejectedBeforeAnyRealityWrite() {
        val active = pointer()
        val s = stage("target-now-active")
        val switched = checkNotNull(
            FileActiveGenerationPointer(root).compareAndSet(
                active.generationId, s.descriptor.generationId,
            )
        )
        assertThrows(IllegalStateException::class.java) { migrate(s, switched) }
        assertEquals(8L, version(s))
    }

    @Test fun forgedDescriptorOrNoncanonicalDirectoryIsRejected() {
        val active = pointer()
        val s = stage("target-canonical")
        val fake = s.copy(descriptor = descriptor("foreign-descriptor"))
        assertThrows(IllegalStateException::class.java) { migrate(fake, active) }
        val wrong = s.copy(directory = root.resolve("foreign-directory"))
        assertThrows(IllegalStateException::class.java) { migrate(wrong, active) }
        assertEquals(8L, version(s))
    }

    @Test fun sourceSevenOrUnknownSchemaIsRejectedBeforeMigration() {
        val active = pointer()
        val seven = stage("target-seven", planning8 = false)
        assertThrows(IllegalStateException::class.java) { migrate(seven, active) }
        assertEquals(7L, version(seven))
        val unknown = stage("target-unknown")
        BundledSQLiteDriver().open(unknown.directory.resolve("reality.db").toString()).use {
            it.execSQL("PRAGMA user_version=55")
        }
        assertThrows(IllegalStateException::class.java) { migrate(unknown, active) }
        assertEquals(55L, version(unknown))
    }

    @Test fun uncheckpointedSidecarIsRejectedBeforeMigration() {
        val active = pointer()
        val s = stage("target-wal")
        val wal = Files.writeString(s.directory.resolve("reality.db-wal"), "untrusted")
        assertThrows(IllegalStateException::class.java) { migrate(s, active) }
        assertTrue(Files.exists(wal))
        assertEquals(8L, version(s))
    }

    @Test fun pointerAbaDuringWindowLeavesUnactivatedNineQuarantined() {
        val active = pointer()
        val s = stage("target-aba")
        val injected = FaroV5Finance9StagedMigration(root, quiescence) {
            val changed = FileActiveGenerationPointer(root).compareAndSet(
                active.generationId, StorageGenerationId("different-active"),
            )
            checkNotNull(changed)
        }
        assertThrows(IllegalStateException::class.java) { migrate(s, active, injected) }
        assertEquals(9L, version(s))
        assertFalse(FileActiveGenerationPointer(root).read() == active)
    }

    @Test fun secondStageNineMigrationIsRejectedWithoutImplicitReapply() {
        val active = pointer()
        val s = stage("target-repeat")
        migrate(s, active)
        assertThrows(IllegalStateException::class.java) { migrate(s, active) }
        assertEquals(9L, version(s))
        assertEquals(active, FileActiveGenerationPointer(root).read())
    }
}
