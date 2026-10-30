package ar.com.faro.v5.maintenance

import ar.com.faro.v5.kernel.contracts.CanonicalHashBuilder
import ar.com.faro.v5.kernel.contracts.EffectId
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.UUID

@JvmInline
value class PortabilityArtifactHandle(val value: String) {
    init {
        require(value.matches(Regex("[0-9a-f]{64}"))) {
            "portability artifact handle must be lowercase SHA-256"
        }
    }
}

data class PreparedPortabilityArtifact(
    val handle: PortabilityArtifactHandle,
    val effectId: EffectId,
    val exportPlanFingerprint: ExportPlanFingerprint,
    val formatProfile: String,
    val suggestedFileName: String,
    val mimeType: String,
    val generatedAtEpochMillis: Long,
    val byteCount: Long,
    val sha256Hex: String,
) {
    init {
        require(formatProfile == PortabilityPreview.FORMAT_PROFILE)
        require(suggestedFileName.matches(Regex("[A-Za-z0-9._-]{1,160}")))
        require(mimeType == "application/json")
        require(generatedAtEpochMillis >= 0L)
        require(byteCount in 1..FilePortabilityArtifactStore.MAX_ARTIFACT_BYTES.toLong())
        require(sha256Hex.matches(Regex("[0-9a-f]{64}")))
    }
}

interface PortabilityArtifactStore {
    fun stage(
        effectId: EffectId,
        planFingerprint: ExportPlanFingerprint,
        generatedAtEpochMillis: Long,
        writer: (OutputStream) -> Unit,
    ): PreparedPortabilityArtifact

    fun verify(artifact: PreparedPortabilityArtifact): Boolean

    fun copyTo(
        artifact: PreparedPortabilityArtifact,
        output: OutputStream,
    ): Long

    fun delete(artifact: PreparedPortabilityArtifact): Boolean
}

class FilePortabilityArtifactStore(
    private val root: Path,
    private val maxArtifactBytes: Long = MAX_ARTIFACT_BYTES.toLong(),
) : PortabilityArtifactStore {
    private val lock = Any()

    init {
        require(maxArtifactBytes in 1..MAX_ARTIFACT_BYTES.toLong()) {
            "portability staging bound is invalid"
        }
        Files.createDirectories(root)
        require(Files.isDirectory(root)) { "portability staging root must be a directory" }
    }

    override fun stage(
        effectId: EffectId,
        planFingerprint: ExportPlanFingerprint,
        generatedAtEpochMillis: Long,
        writer: (OutputStream) -> Unit,
    ): PreparedPortabilityArtifact = synchronized(lock) {
        require(generatedAtEpochMillis >= 0L)
        val handle =
            PortabilityArtifactHandle(
                CanonicalHashBuilder
                    .domain("faro-portability-artifact-v1")
                    .string("effectId", effectId.value)
                    .string("planFingerprint", planFingerprint.value)
                    .sha256Hex()
            )
        val finalPath = pathFor(handle)
        val tempPath =
            root.resolve(handle.value + ".tmp-" + UUID.randomUUID().toString().replace("-", ""))

        var byteCount = 0L
        var sha256 = ""
        try {
            Files.deleteIfExists(finalPath)
            Files.newOutputStream(
                tempPath,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE,
            ).use { raw ->
                val bounded = BoundedDigestOutputStream(raw, maxArtifactBytes)
                writer(bounded)
                bounded.flush()
                byteCount = bounded.byteCount
                sha256 = bounded.sha256Hex()
            }
            check(byteCount > 0L) { "portability artifact must not be empty" }
            Files.move(
                tempPath,
                finalPath,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (failure: Exception) {
            runCatching { Files.deleteIfExists(tempPath) }
            throw failure
        }

        PreparedPortabilityArtifact(
            handle = handle,
            effectId = effectId,
            exportPlanFingerprint = planFingerprint,
            formatProfile = PortabilityPreview.FORMAT_PROFILE,
            suggestedFileName =
                "faro-portable-data-" + generatedAtEpochMillis + ".json",
            mimeType = "application/json",
            generatedAtEpochMillis = generatedAtEpochMillis,
            byteCount = byteCount,
            sha256Hex = sha256,
        )
    }

    override fun verify(artifact: PreparedPortabilityArtifact): Boolean =
        synchronized(lock) {
            verifyLocked(artifact)
        }

    private fun verifyLocked(artifact: PreparedPortabilityArtifact): Boolean {
        val path = pathFor(artifact.handle)
        if (!Files.isRegularFile(path)) return false
        if (runCatching { Files.size(path) }.getOrNull() != artifact.byteCount) return false

        return runCatching {
            val digest = MessageDigest.getInstance("SHA-256")
            var count = 0L
            Files.newInputStream(path).use { input ->
                val buffer = ByteArray(COPY_BUFFER_BYTES)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    count = Math.addExact(count, read.toLong())
                    if (count > maxArtifactBytes) return@runCatching false
                    digest.update(buffer, 0, read)
                }
            }
            count == artifact.byteCount &&
                digest.digest().toHex() == artifact.sha256Hex
        }.getOrDefault(false)
    }

    override fun copyTo(
        artifact: PreparedPortabilityArtifact,
        output: OutputStream,
    ): Long = synchronized(lock) {
        check(verifyLocked(artifact)) { "portability staged artifact failed pre-copy verification" }
        val path = pathFor(artifact.handle)
        var copied = 0L
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(COPY_BUFFER_BYTES)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                copied = Math.addExact(copied, read.toLong())
                check(copied <= artifact.byteCount) {
                    "portability staged artifact grew during copy"
                }
                output.write(buffer, 0, read)
            }
        }
        check(copied == artifact.byteCount) {
            "portability staged artifact length changed during copy"
        }
        output.flush()
        return copied
    }

    override fun delete(artifact: PreparedPortabilityArtifact): Boolean =
        synchronized(lock) {
            Files.deleteIfExists(pathFor(artifact.handle))
        }

    private fun pathFor(handle: PortabilityArtifactHandle): Path =
        root.resolve(handle.value + ".json")

    private class BoundedDigestOutputStream(
        private val delegate: OutputStream,
        private val maxBytes: Long,
    ) : OutputStream() {
        private val digest = MessageDigest.getInstance("SHA-256")
        var byteCount: Long = 0L
            private set

        override fun write(value: Int) {
            ensureCapacity(1)
            delegate.write(value)
            digest.update(value.toByte())
            byteCount += 1L
        }

        override fun write(
            bytes: ByteArray,
            offset: Int,
            length: Int,
        ) {
            require(offset >= 0 && length >= 0 && offset + length <= bytes.size)
            ensureCapacity(length)
            delegate.write(bytes, offset, length)
            digest.update(bytes, offset, length)
            byteCount += length.toLong()
        }

        override fun flush() = delegate.flush()

        fun sha256Hex(): String = digest.digest().toHex()

        private fun ensureCapacity(additional: Int) {
            check(additional.toLong() <= maxBytes - byteCount) {
                "portability artifact exceeds bound"
            }
        }

    }

    companion object {
        const val MAX_ARTIFACT_BYTES = 128 * 1024 * 1024
        private const val COPY_BUFFER_BYTES = 64 * 1024
    }
}

private fun ByteArray.toHex(): String =
    joinToString("") { "%02x".format(it) }
