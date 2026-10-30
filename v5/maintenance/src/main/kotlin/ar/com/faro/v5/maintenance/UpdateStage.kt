package ar.com.faro.v5.maintenance

import java.io.FileOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

fun interface UpdateArtifactDownloadPort {
    fun open(candidate: UpdateManifestCandidate): InputStream
}

fun interface UpdateArtifactCleanupPort {
    fun cleanup(artifactId: StagedUpdateArtifactId)
}

fun interface UpdateStagedArtifactReadPort {
    fun open(expected: ExpectedInstall): InputStream
}

object UpdateSigningLineagePolicy {
    fun requireCompatible(
        installed: UpdateSigningIdentity,
        artifact: UpdateSigningIdentity,
    ) {
        when {
            installed is UpdateSigningIdentity.Lineage &&
                artifact is UpdateSigningIdentity.Lineage -> {
                val installedHistory = installed.certificatesSha256
                val artifactHistory = artifact.certificatesSha256
                require(artifactHistory.size >= installedHistory.size) {
                    "update signer lineage is shorter than installed lineage"
                }
                require(artifactHistory.take(installedHistory.size) == installedHistory) {
                    "update signer lineage does not extend installed lineage"
                }
            }

            installed is UpdateSigningIdentity.MultipleSigners &&
                artifact is UpdateSigningIdentity.MultipleSigners -> {
                require(artifact.certificatesSha256 == installed.certificatesSha256) {
                    "update multiple-signer set does not match installed identity"
                }
            }

            else -> {
                throw IllegalArgumentException(
                    "update signing mode does not match installed identity",
                )
            }
        }
    }
}

class FileUpdateArtifactStagePort(
    stagingRoot: Path,
    private val download: UpdateArtifactDownloadPort,
) : UpdateArtifactStagePort, UpdateArtifactCleanupPort, UpdateStagedArtifactReadPort {
    private val configuredRoot = stagingRoot.toAbsolutePath().normalize()

    override fun stage(candidate: UpdateManifestCandidate): StagedUpdateArtifactId {
        require(candidate.declaredSizeBytes <= UpdateManifestFormat.MAX_ARTIFACT_BYTES) {
            "update artifact exceeds staging bound"
        }

        val artifactId =
            StagedUpdateArtifactId(
                "sha256:${candidate.artifactSha256.value}",
            )
        val finalPath = artifactPath(artifactId)
        val partialPath = partialPath(artifactId)

        Files.deleteIfExists(partialPath)
        Files.deleteIfExists(finalPath)

        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var total = 0L

            download.open(candidate).buffered().use { input ->
                FileOutputStream(partialPath.toFile(), false).use { fileOutput ->
                    val output = fileOutput.buffered()
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break

                        total += read.toLong()
                        require(total <= candidate.declaredSizeBytes) {
                            "update download exceeds declared size"
                        }
                        require(total <= UpdateManifestFormat.MAX_ARTIFACT_BYTES) {
                            "update download exceeds staging bound"
                        }

                        digest.update(buffer, 0, read)
                        output.write(buffer, 0, read)
                    }
                    output.flush()
                    fileOutput.fd.sync()
                }
            }

            require(total == candidate.declaredSizeBytes) {
                "update download is truncated"
            }

            val actualDigest =
                UpdateSha256(
                    digest.digest().joinToString("") { byte ->
                        "%02x".format(byte.toInt() and 0xff)
                    },
                )
            require(actualDigest == candidate.artifactSha256) {
                "update artifact digest mismatch"
            }

            moveIntoPlace(partialPath, finalPath)
            require(Files.isRegularFile(finalPath)) {
                "staged update artifact missing after finalize"
            }
            require(Files.size(finalPath) == candidate.declaredSizeBytes) {
                "staged update artifact size changed after finalize"
            }

            return artifactId
        } catch (failure: Throwable) {
            Files.deleteIfExists(partialPath)
            Files.deleteIfExists(finalPath)
            throw failure
        }
    }

    override fun cleanup(artifactId: StagedUpdateArtifactId) {
        Files.deleteIfExists(partialPath(artifactId))
        Files.deleteIfExists(artifactPath(artifactId))
    }

    override fun open(expected: ExpectedInstall): InputStream {
        val path = artifactPath(expected.staged.artifactId)
        require(Files.isRegularFile(path)) {
            "staged update artifact is missing"
        }
        require(Files.size(path) == expected.staged.artifactIdentity.byteCount) {
            "staged update artifact size changed before install"
        }
        return Files.newInputStream(path)
    }

    fun pathFor(artifactId: StagedUpdateArtifactId): Path = artifactPath(artifactId)

    private fun artifactPath(artifactId: StagedUpdateArtifactId): Path {
        val root = verifiedRoot()
        val path = root.resolve("${safeFileStem(artifactId)}.apk").normalize()
        require(path.parent == root) {
            "update staging path escaped private root"
        }
        return path
    }

    private fun partialPath(artifactId: StagedUpdateArtifactId): Path {
        val root = verifiedRoot()
        val path = root.resolve("${safeFileStem(artifactId)}.part").normalize()
        require(path.parent == root) {
            "update staging partial path escaped private root"
        }
        return path
    }

    private fun verifiedRoot(): Path {
        Files.createDirectories(configuredRoot)
        require(Files.isDirectory(configuredRoot)) {
            "update staging root is not a directory"
        }
        require(!Files.isSymbolicLink(configuredRoot)) {
            "update staging root must not be a symbolic link"
        }
        return configuredRoot.toRealPath()
    }

    private fun safeFileStem(artifactId: StagedUpdateArtifactId): String {
        val digest =
            MessageDigest.getInstance("SHA-256")
                .digest(artifactId.value.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
    }

    private fun moveIntoPlace(
        source: Path,
        destination: Path,
    ) {
        try {
            Files.move(
                source,
                destination,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(
                source,
                destination,
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }
}

class UpdateStageUseCase(
    private val staging: UpdateArtifactStagePort,
    private val cleanup: UpdateArtifactCleanupPort,
    private val inspection: UpdateArtifactInspectionPort,
    private val installedIdentity: InstalledUpdateIdentityPort,
    private val expectedChannel: String,
) {
    init {
        require(expectedChannel.isNotBlank()) {
            "expected update channel must be explicit"
        }
        require(expectedChannel == expectedChannel.trim()) {
            "expected update channel must be canonical"
        }
        require(expectedChannel.length <= 128) {
            "expected update channel is too long"
        }
    }

    fun stage(candidate: UpdateManifestCandidate): VerifiedStagedUpdate {
        val installed = installedIdentity.current()
        val eligibility =
            UpdateCheckPolicy.evaluate(
                installed = installed,
                candidate = candidate,
                expectedChannel = expectedChannel,
            )
        require(eligibility is UpdateEligibility.Available) {
            "update candidate is not eligible for staging"
        }

        var artifactId: StagedUpdateArtifactId? = null
        try {
            artifactId = staging.stage(candidate)
            val artifactIdentity = inspection.inspect(artifactId)
            val verified =
                VerifiedStagedUpdate(
                    artifactId = artifactId,
                    candidate = candidate,
                    artifactIdentity = artifactIdentity,
                )
            UpdateSigningLineagePolicy.requireCompatible(
                installed = installed.signingIdentity,
                artifact = verified.artifactIdentity.signingIdentity,
            )
            return verified
        } catch (failure: Throwable) {
            artifactId?.let { id ->
                runCatching { cleanup.cleanup(id) }
                    .exceptionOrNull()
                    ?.let(failure::addSuppressed)
            }
            throw failure
        }
    }
}
