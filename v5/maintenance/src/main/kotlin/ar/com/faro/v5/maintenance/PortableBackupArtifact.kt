package ar.com.faro.v5.maintenance

import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.UUID

@JvmInline
value class PortableBackupArtifactHandle(val value: String) {
    init {
        require(value.matches(Regex("[0-9a-f]{64}"))) {
            "portable backup artifact handle must be lowercase SHA-256"
        }
    }
}

data class StagedPortableBackupArchive(
    val handle: PortableBackupArtifactHandle,
    val stagedAtEpochMillis: Long,
    val byteCount: Long,
    val sha256Hex: String,
) {
    init {
        require(stagedAtEpochMillis >= 0L)
        require(byteCount in 1..PortableBackupArchiveCrypto.MAX_ARCHIVE_BYTES.toLong())
        require(sha256Hex.matches(Regex("[0-9a-f]{64}")))
        require(handle.value == sha256Hex) {
            "portable backup staging handle must equal content digest"
        }
    }

    val suggestedFileName: String
        get() = "faro-backup-$stagedAtEpochMillis.farobak"

    val mimeType: String
        get() = "application/octet-stream"
}

interface PortableBackupArchiveStore {
    fun stageBytes(
        archive: ByteArray,
        stagedAtEpochMillis: Long,
    ): StagedPortableBackupArchive

    fun stageFrom(
        input: InputStream,
        stagedAtEpochMillis: Long,
    ): StagedPortableBackupArchive

    fun verify(artifact: StagedPortableBackupArchive): Boolean

    fun copyTo(
        artifact: StagedPortableBackupArchive,
        output: OutputStream,
    ): Long

    fun readBytes(artifact: StagedPortableBackupArchive): ByteArray

    fun delete(artifact: StagedPortableBackupArchive): Boolean
}

class FilePortableBackupArchiveStore(
    private val root: Path,
    private val maxArchiveBytes: Long =
        PortableBackupArchiveCrypto.MAX_ARCHIVE_BYTES.toLong(),
) : PortableBackupArchiveStore {
    private val lock = Any()

    init {
        require(
            maxArchiveBytes in
                1..PortableBackupArchiveCrypto.MAX_ARCHIVE_BYTES.toLong()
        ) {
            "portable backup staging bound is invalid"
        }
        Files.createDirectories(root)
        check(Files.isDirectory(root)) {
            "portable backup staging root must be a directory"
        }
        cleanupInterruptedTemps()
    }

    override fun stageBytes(
        archive: ByteArray,
        stagedAtEpochMillis: Long,
    ): StagedPortableBackupArchive {
        require(archive.isNotEmpty() && archive.size.toLong() <= maxArchiveBytes) {
            "portable backup archive outside staging bound"
        }
        return stageFrom(archive.inputStream(), stagedAtEpochMillis)
    }

    override fun stageFrom(
        input: InputStream,
        stagedAtEpochMillis: Long,
    ): StagedPortableBackupArchive =
        synchronized(lock) {
            require(stagedAtEpochMillis >= 0L)
            val temp =
                root.resolve(
                    "incoming.tmp-" +
                        UUID.randomUUID().toString().replace("-", "")
                )
            val digest = MessageDigest.getInstance("SHA-256")
            var count = 0L

            try {
                Files.newOutputStream(
                    temp,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE,
                ).use { output ->
                    val buffer = ByteArray(COPY_BUFFER_BYTES)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        count = Math.addExact(count, read.toLong())
                        check(count <= maxArchiveBytes) {
                            "portable backup archive exceeds staging bound"
                        }
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                    }
                    output.flush()
                }
                check(count > 0L) {
                    "portable backup archive must not be empty"
                }

                val sha256 = digest.digest().backupHex()
                val handle = PortableBackupArtifactHandle(sha256)
                val finalPath = pathFor(handle)
                Files.move(
                    temp,
                    finalPath,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
                StagedPortableBackupArchive(
                    handle = handle,
                    stagedAtEpochMillis = stagedAtEpochMillis,
                    byteCount = count,
                    sha256Hex = sha256,
                )
            } catch (failure: Exception) {
                runCatching { Files.deleteIfExists(temp) }
                throw failure
            }
        }

    override fun verify(
        artifact: StagedPortableBackupArchive,
    ): Boolean =
        synchronized(lock) {
            verifyLocked(artifact)
        }

    override fun copyTo(
        artifact: StagedPortableBackupArchive,
        output: OutputStream,
    ): Long =
        synchronized(lock) {
            check(verifyLocked(artifact)) {
                "portable backup staged artifact failed pre-copy verification"
            }
            var copied = 0L
            Files.newInputStream(pathFor(artifact.handle)).use { input ->
                val buffer = ByteArray(COPY_BUFFER_BYTES)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    copied = Math.addExact(copied, read.toLong())
                    check(copied <= artifact.byteCount) {
                        "portable backup staged artifact grew during copy"
                    }
                    output.write(buffer, 0, read)
                }
            }
            check(copied == artifact.byteCount) {
                "portable backup staged artifact length changed during copy"
            }
            output.flush()
            copied
        }

    override fun readBytes(
        artifact: StagedPortableBackupArchive,
    ): ByteArray =
        synchronized(lock) {
            check(verifyLocked(artifact)) {
                "portable backup staged artifact failed pre-open verification"
            }
            val bytes = Files.readAllBytes(pathFor(artifact.handle))
            check(bytes.size.toLong() == artifact.byteCount) {
                "portable backup staged artifact length changed during read"
            }
            bytes
        }

    override fun delete(
        artifact: StagedPortableBackupArchive,
    ): Boolean =
        synchronized(lock) {
            Files.deleteIfExists(pathFor(artifact.handle))
        }

    private fun verifyLocked(
        artifact: StagedPortableBackupArchive,
    ): Boolean {
        val path = pathFor(artifact.handle)
        if (!Files.isRegularFile(path)) return false
        if (runCatching { Files.size(path) }.getOrNull() != artifact.byteCount) {
            return false
        }

        return runCatching {
            val digest = MessageDigest.getInstance("SHA-256")
            var count = 0L
            Files.newInputStream(path).use { input ->
                val buffer = ByteArray(COPY_BUFFER_BYTES)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    count = Math.addExact(count, read.toLong())
                    if (count > maxArchiveBytes) {
                        return@runCatching false
                    }
                    digest.update(buffer, 0, read)
                }
            }
            count == artifact.byteCount &&
                digest.digest().backupHex() == artifact.sha256Hex
        }.getOrDefault(false)
    }

    private fun cleanupInterruptedTemps() {
        Files.newDirectoryStream(root, "*.tmp-*").use { entries ->
            entries.forEach { path ->
                runCatching { Files.deleteIfExists(path) }
            }
        }
    }

    private fun pathFor(handle: PortableBackupArtifactHandle): Path =
        root.resolve(handle.value + ".farobak")

    private companion object {
        const val COPY_BUFFER_BYTES = 64 * 1024
    }
}

class PreparedBackupExternalWriteSource(
    val effectIdentity: String,
    val artifact: StagedPortableBackupArchive,
    private val store: PortableBackupArchiveStore,
) {
    init {
        require(effectIdentity.isNotBlank() && effectIdentity.length <= 256)
        require(store.verify(artifact)) {
            "portable backup artifact must verify before external write"
        }
    }

    val artifactHandleHex: String
        get() = artifact.handle.value

    val suggestedFileName: String
        get() = artifact.suggestedFileName

    val mimeType: String
        get() = artifact.mimeType

    val byteCount: Long
        get() = artifact.byteCount

    val sha256Hex: String
        get() = artifact.sha256Hex

    fun copyTo(output: OutputStream): Long =
        store.copyTo(artifact, output)
}

private fun ByteArray.backupHex(): String =
    joinToString("") { "%02x".format(it) }
