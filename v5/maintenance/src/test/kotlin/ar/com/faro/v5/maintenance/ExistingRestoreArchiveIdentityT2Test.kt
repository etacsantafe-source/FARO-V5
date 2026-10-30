package ar.com.faro.v5.maintenance

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** Native/JVM T2: validation may VERIFY an existing archive pin but never create one. */
class ExistingRestoreArchiveIdentityT2Test {
    @TempDir lateinit var root: Path
    private val op = MaintenanceOperationId("existing-pin-restore-op")
    private val target = StorageGenerationId("existing-pin-v2-target")
    private val archive = byteArrayOf(8, 3, 1, 4, 2)

    private fun identityFiles(): Long =
        Files.list(root.resolve("bootstrap/restore-archive-identities")).use { entries ->
            entries.filter { it.fileName.toString().endsWith(".bin") }.count()
        }

    @Test fun missingPinnedIdentityCannotBeInventedByValidation() {
        val verifier: ExistingRestoreArchiveIdentityVerificationPort =
            FileRestoreArchiveIdentity(root)
        assertThrows(IllegalStateException::class.java) {
            verifier.requireExistingPinnedArchive(op, target, archive)
        }
        assertEquals(0L, identityFiles())
        FileRestoreArchiveIdentity(root).pinOrVerify(op, target, archive)
        verifier.requireExistingPinnedArchive(op, target, archive)
        assertEquals(1L, identityFiles())
    }

    @Test fun exactIdentitySurvivesReopenAndRefusesSubstitutionWithoutPublishing() {
        FileRestoreArchiveIdentity(root).pinOrVerify(op, target, archive)
        val verifier: ExistingRestoreArchiveIdentityVerificationPort =
            FileRestoreArchiveIdentity(root)
        verifier.requireExistingPinnedArchive(op, target, archive)
        assertThrows(IllegalStateException::class.java) {
            verifier.requireExistingPinnedArchive(op, target, byteArrayOf(8, 3, 1, 4, 3))
        }
        assertThrows(IllegalStateException::class.java) {
            verifier.requireExistingPinnedArchive(
                op, StorageGenerationId("another-target"), archive
            )
        }
        assertThrows(IllegalStateException::class.java) {
            verifier.requireExistingPinnedArchive(
                MaintenanceOperationId("another-operation"), target, archive
            )
        }
        assertEquals(1L, identityFiles())
    }

    @Test fun tamperedDurableIdentityFailsClosedWithoutRepin() {
        FileRestoreArchiveIdentity(root).pinOrVerify(op, target, archive)
        val record = Files.list(root.resolve("bootstrap/restore-archive-identities")).use {
            it.filter { p -> p.fileName.toString().endsWith(".bin") }
                .findFirst().orElseThrow()
        }
        val modified = Files.readAllBytes(record)
        modified[modified.lastIndex] = (modified.last().toInt() xor 1).toByte()
        Files.write(record, modified)
        assertThrows(IllegalStateException::class.java) {
            FileRestoreArchiveIdentity(root).requireExistingPinnedArchive(op, target, archive)
        }
        assertEquals(1L, identityFiles())
    }
}
