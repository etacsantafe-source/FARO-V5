package ar.com.faro.v5.maintenance

import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest

internal object MaintenancePathIdentity {
    fun sha256Hex(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { byte ->
                (byte.toInt() and 0xff).toString(16).padStart(2, '0')
            }
}

internal data class DurableRecord(
    val sequence: Long,
    val bytes: ByteArray,
)

internal class DurableRecordDirectory(
    directory: Path,
    private val maxRecordBytes: Int = MAX_RECORD_BYTES,
) {
    private val directory: Path = directory.toAbsolutePath().normalize()

    init {
        require(maxRecordBytes in 1..MAX_RECORD_BYTES) {
            "invalid durable record byte bound"
        }
        Files.createDirectories(this.directory)
        check(!Files.isSymbolicLink(this.directory)) {
            "durable record directory must not be a symbolic link"
        }
        check(Files.isDirectory(this.directory)) {
            "durable record path must be a directory"
        }
    }

    fun readLatest(prefix: String): DurableRecord? {
        validatePrefix(prefix)

        var latestSequence: Long? = null
        var latestPath: Path? = null

        Files.newDirectoryStream(directory).use { entries ->
            for (path in entries) {
                val fileName = path.fileName.toString()
                if (!fileName.startsWith("$prefix-") || !fileName.endsWith(RECORD_SUFFIX)) {
                    continue
                }

                val sequenceText =
                    fileName.removePrefix("$prefix-").removeSuffix(RECORD_SUFFIX)
                check(
                    sequenceText.length == SEQUENCE_WIDTH &&
                        sequenceText.all(Char::isDigit)
                ) {
                    "malformed durable record filename: $fileName"
                }

                val sequence = sequenceText.toLong()
                check(sequence > 0L) {
                    "durable record sequence must be positive"
                }
                val currentLatest = latestSequence
                if (currentLatest == null || sequence > currentLatest) {
                    latestSequence = sequence
                    latestPath = path
                }
            }
        }

        val sequence = latestSequence ?: return null
        val path = checkNotNull(latestPath)

        check(!Files.isSymbolicLink(path)) {
            "durable record must not be a symbolic link"
        }
        check(Files.isRegularFile(path)) {
            "durable record must be a regular file"
        }

        val size = Files.size(path)
        check(size in 1..maxRecordBytes.toLong()) {
            "durable record size outside bound: $size"
        }

        return DurableRecord(
            sequence = sequence,
            bytes = Files.readAllBytes(path),
        )
    }

    fun publish(
        prefix: String,
        sequence: Long,
        bytes: ByteArray,
    ) {
        validatePrefix(prefix)
        require(sequence > 0L) { "durable record sequence must be positive" }
        require(bytes.isNotEmpty() && bytes.size <= maxRecordBytes) {
            "durable record bytes outside bound"
        }

        val finalPath =
            directory.resolve(
                "$prefix-${sequence.toString().padStart(SEQUENCE_WIDTH, '0')}$RECORD_SUFFIX"
            )
        check(!Files.exists(finalPath)) {
            "durable record already exists for sequence $sequence"
        }

        val temporary = Files.createTempFile(directory, TEMP_PREFIX, ".tmp")
        try {
            FileChannel.open(
                temporary,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING,
            ).use { channel ->
                val buffer = ByteBuffer.wrap(bytes)
                while (buffer.hasRemaining()) {
                    channel.write(buffer)
                }
                channel.force(true)
            }

            Files.move(
                temporary,
                finalPath,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (failure: Throwable) {
            runCatching { Files.deleteIfExists(temporary) }
            throw failure
        }
    }

    private fun validatePrefix(prefix: String) {
        require(prefix.matches(Regex("[a-z0-9-]{1,96}"))) {
            "durable record prefix is not canonical"
        }
    }

    private companion object {
        const val MAX_RECORD_BYTES = 64 * 1024
        const val SEQUENCE_WIDTH = 20
        const val RECORD_SUFFIX = ".bin"
        const val TEMP_PREFIX = ".pending-"
    }
}
