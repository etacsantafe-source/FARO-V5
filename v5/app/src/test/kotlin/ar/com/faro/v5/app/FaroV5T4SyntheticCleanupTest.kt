package ar.com.faro.v5.app

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class FaroV5T4SyntheticCleanupTest {
    @TempDir lateinit var privateRoot: Path

    @Test fun removesOnlyOrphanedSyntheticAttemptDirectories() {
        val interrupted = Files.createDirectory(privateRoot.resolve("t4-saf-integral-crashed"))
        Files.createDirectories(interrupted.resolve("nested"))
        Files.write(interrupted.resolve("nested/encrypted"), byteArrayOf(1, 2, 3))
        val unrelated = Files.createDirectory(privateRoot.resolve("other-session"))
        Files.write(unrelated.resolve("keep"), byteArrayOf(9))
        val regular = Files.write(privateRoot.resolve("t4-saf-integral-file-not-directory"), byteArrayOf(8))
        assertEquals(1, FaroV5T4SyntheticCleanup.deleteOrphans(privateRoot))
        assertFalse(Files.exists(interrupted))
        assertTrue(Files.exists(unrelated.resolve("keep")))
        assertTrue(Files.exists(regular))
    }

    @Test fun cleanupIsIdempotentAndMissingRootIsSafe() {
        assertEquals(0, FaroV5T4SyntheticCleanup.deleteOrphans(privateRoot))
        assertEquals(0, FaroV5T4SyntheticCleanup.deleteOrphans(privateRoot.resolve("missing")))
        val old = Files.createDirectory(privateRoot.resolve("t4-saf-integral-old"))
        assertEquals(1, FaroV5T4SyntheticCleanup.deleteOrphans(privateRoot))
        assertFalse(Files.exists(old))
        assertEquals(0, FaroV5T4SyntheticCleanup.deleteOrphans(privateRoot))
    }

    @Test fun symlinksCannotEscapePrivateRoot() {
        val outside = Files.createDirectory(privateRoot.resolve("do-not-delete"))
        Files.write(outside.resolve("important"), byteArrayOf(42))
        val old = Files.createDirectory(privateRoot.resolve("t4-saf-integral-with-link"))
        Files.createSymbolicLink(old.resolve("outside-link"), outside)
        Files.createSymbolicLink(privateRoot.resolve("t4-saf-integral-external-link"), outside)
        assertEquals(1, FaroV5T4SyntheticCleanup.deleteOrphans(privateRoot))
        assertTrue(Files.exists(outside.resolve("important")))
        assertTrue(Files.isSymbolicLink(privateRoot.resolve("t4-saf-integral-external-link")))
    }
}
