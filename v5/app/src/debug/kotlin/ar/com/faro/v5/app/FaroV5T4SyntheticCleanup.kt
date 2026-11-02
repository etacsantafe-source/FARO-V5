package ar.com.faro.v5.app

import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

/**
 * Debug-only orphan cleanup. Must be called once, before any T4 worker starts
 * in a fresh process. Operates solely under the app's private noBackupFilesDir.
 * Never traverses a symlink and never deletes the user's SAF document.
 */
internal object FaroV5T4SyntheticCleanup {
    private const val PREFIX = "t4-saf-integral-"

    fun deleteOrphans(privateNoBackupRoot: Path): Int {
        val root = privateNoBackupRoot.toAbsolutePath().normalize()
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) return 0
        var deleted = 0
        Files.newDirectoryStream(root, "$PREFIX*").use { matches ->
            for (candidate in matches) {
                if (!Files.isDirectory(candidate, LinkOption.NOFOLLOW_LINKS) ||
                    Files.isSymbolicLink(candidate) ||
                    candidate.parent.toAbsolutePath().normalize() != root ||
                    !candidate.fileName.toString().startsWith(PREFIX)
                ) continue
                Files.walkFileTree(candidate, object : SimpleFileVisitor<Path>() {
                    override fun visitFile(
                        file: Path, attrs: BasicFileAttributes,
                    ): FileVisitResult {
                        Files.delete(file)
                        return FileVisitResult.CONTINUE
                    }

                    override fun postVisitDirectory(
                        dir: Path, exc: IOException?,
                    ): FileVisitResult {
                        if (exc != null) throw exc
                        Files.delete(dir)
                        return FileVisitResult.CONTINUE
                    }
                })
                deleted++
            }
        }
        return deleted
    }
}
