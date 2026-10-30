package ar.com.faro.v5.maintenance

import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.io.path.createTempDirectory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UpdateStageTest {
    @Test
    fun `file staging bounds bytes verifies digest and confines artifact to private root`() {
        val bytes = "faro-v5-update-fixture".toByteArray()
        val candidate = candidate(bytes)
        val root = createTempDirectory("faro-v5-update-stage")
        val stage =
            FileUpdateArtifactStagePort(root) {
                ByteArrayInputStream(bytes)
            }

        val id = stage.stage(candidate)
        val path = stage.pathFor(id)

        assertEquals(root.toRealPath(), path.parent)
        assertTrue(Files.isRegularFile(path))
        assertEquals(bytes.size.toLong(), Files.size(path))
        assertFalse(
            Files.exists(
                path.resolveSibling(
                    path.fileName.toString().removeSuffix(".apk") + ".part",
                ),
            ),
        )
    }

    @Test
    fun `staging deletes partial and final artifact when stream exceeds or misses declared bytes`() {
        val root = createTempDirectory("faro-v5-update-stage-bounds")
        val exact = "12345678".toByteArray()

        val oversizedCandidate = candidate(exact, declaredSize = exact.size.toLong() - 1L)
        val oversized =
            FileUpdateArtifactStagePort(root) {
                ByteArrayInputStream(exact)
            }
        assertThrows(IllegalArgumentException::class.java) {
            oversized.stage(oversizedCandidate)
        }
        assertEquals(0L, Files.list(root).use { it.count() })

        val truncatedCandidate = candidate(exact, declaredSize = exact.size.toLong() + 1L)
        val truncated =
            FileUpdateArtifactStagePort(root) {
                ByteArrayInputStream(exact)
            }
        assertThrows(IllegalArgumentException::class.java) {
            truncated.stage(truncatedCandidate)
        }
        assertEquals(0L, Files.list(root).use { it.count() })
    }

    @Test
    fun `staging rejects digest mismatch and leaves no artifact behind`() {
        val root = createTempDirectory("faro-v5-update-stage-digest")
        val bytes = "expected".toByteArray()
        val candidate =
            candidate(bytes).copy(
                artifactSha256 = digest("different".toByteArray()),
            )
        val stage =
            FileUpdateArtifactStagePort(root) {
                ByteArrayInputStream(bytes)
            }

        assertThrows(IllegalArgumentException::class.java) {
            stage.stage(candidate)
        }
        assertEquals(0L, Files.list(root).use { it.count() })
    }

    @Test
    fun `stage use case accepts signer rotation only when artifact lineage extends installed lineage`() {
        val bytes = "rotated-signer".toByteArray()
        val candidate = candidate(bytes)
        val root = createTempDirectory("faro-v5-update-stage-lineage")
        val fileStage =
            FileUpdateArtifactStagePort(root) {
                ByteArrayInputStream(bytes)
            }
        val installed =
            InstalledUpdateIdentity(
                packageName = candidate.packageName,
                versionCode = candidate.versionCode - 1L,
                signingIdentity =
                    UpdateSigningIdentity.Lineage(
                        listOf(cert("11"), cert("22")),
                    ),
            )
        val useCase =
            UpdateStageUseCase(
                staging = fileStage,
                cleanup = fileStage,
                inspection =
                    inspection(
                        candidate,
                        UpdateSigningIdentity.Lineage(
                            listOf(cert("11"), cert("22"), cert("33")),
                        ),
                    ),
                installedIdentity = InstalledUpdateIdentityPort { installed },
                expectedChannel = candidate.channel,
            )

        val verified = useCase.stage(candidate)

        assertEquals(candidate.versionCode, verified.artifactIdentity.versionCode)
        assertTrue(Files.exists(fileStage.pathFor(verified.artifactId)))
    }

    @Test
    fun `stage use case rejects divergent lineage and cleans staged artifact`() {
        val bytes = "bad-lineage".toByteArray()
        val candidate = candidate(bytes)
        val root = createTempDirectory("faro-v5-update-stage-bad-lineage")
        val fileStage =
            FileUpdateArtifactStagePort(root) {
                ByteArrayInputStream(bytes)
            }
        val installed =
            InstalledUpdateIdentity(
                packageName = candidate.packageName,
                versionCode = candidate.versionCode - 1L,
                signingIdentity =
                    UpdateSigningIdentity.Lineage(
                        listOf(cert("11"), cert("22")),
                    ),
            )
        val useCase =
            UpdateStageUseCase(
                staging = fileStage,
                cleanup = fileStage,
                inspection =
                    inspection(
                        candidate,
                        UpdateSigningIdentity.Lineage(
                            listOf(cert("11"), cert("99")),
                        ),
                    ),
                installedIdentity = InstalledUpdateIdentityPort { installed },
                expectedChannel = candidate.channel,
            )

        assertThrows(IllegalArgumentException::class.java) {
            useCase.stage(candidate)
        }
        assertEquals(0L, Files.list(root).use { it.count() })
    }

    @Test
    fun `multiple signer installs require the exact signer set`() {
        val installed =
            UpdateSigningIdentity.MultipleSigners(
                setOf(cert("11"), cert("22")),
            )
        UpdateSigningLineagePolicy.requireCompatible(
            installed = installed,
            artifact =
                UpdateSigningIdentity.MultipleSigners(
                    setOf(cert("22"), cert("11")),
                ),
        )

        assertThrows(IllegalArgumentException::class.java) {
            UpdateSigningLineagePolicy.requireCompatible(
                installed = installed,
                artifact =
                    UpdateSigningIdentity.MultipleSigners(
                        setOf(cert("11"), cert("33")),
                    ),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            UpdateSigningLineagePolicy.requireCompatible(
                installed = installed,
                artifact =
                    UpdateSigningIdentity.Lineage(
                        listOf(cert("11"), cert("22")),
                    ),
            )
        }
    }

    @Test
    fun `artifact identity mismatch fails closed and cleans staged file`() {
        val bytes = "wrong-package".toByteArray()
        val candidate = candidate(bytes)
        val root = createTempDirectory("faro-v5-update-stage-identity")
        val fileStage =
            FileUpdateArtifactStagePort(root) {
                ByteArrayInputStream(bytes)
            }
        val installed =
            InstalledUpdateIdentity(
                packageName = candidate.packageName,
                versionCode = candidate.versionCode - 1L,
                signingIdentity = UpdateSigningIdentity.Lineage(listOf(cert("11"))),
            )
        val wrongIdentity =
            UpdateArtifactIdentity(
                packageName = "ar.com.other.app",
                versionCode = candidate.versionCode,
                versionName = candidate.versionName,
                signingIdentity = UpdateSigningIdentity.Lineage(listOf(cert("11"))),
                byteCount = candidate.declaredSizeBytes,
                sha256 = candidate.artifactSha256,
            )
        val useCase =
            UpdateStageUseCase(
                staging = fileStage,
                cleanup = fileStage,
                inspection = UpdateArtifactInspectionPort { wrongIdentity },
                installedIdentity = InstalledUpdateIdentityPort { installed },
                expectedChannel = candidate.channel,
            )

        assertThrows(IllegalArgumentException::class.java) {
            useCase.stage(candidate)
        }
        assertEquals(0L, Files.list(root).use { it.count() })
    }

    private fun candidate(
        bytes: ByteArray,
        declaredSize: Long = bytes.size.toLong(),
    ): UpdateManifestCandidate =
        UpdateManifestCandidate(
            sourceIdentity = "https://updates.example.invalid/manifest.properties",
            channel = "dev",
            packageName = "ar.com.faro.v5.dev",
            versionCode = 42L,
            versionName = "5.0.42",
            minimumSourceVersionCode = 1L,
            artifactUri = "https://updates.example.invalid/faro-42.apk",
            artifactSha256 = digest(bytes),
            declaredSizeBytes = declaredSize,
        )

    private fun inspection(
        candidate: UpdateManifestCandidate,
        signingIdentity: UpdateSigningIdentity,
    ): UpdateArtifactInspectionPort =
        UpdateArtifactInspectionPort {
            UpdateArtifactIdentity(
                packageName = candidate.packageName,
                versionCode = candidate.versionCode,
                versionName = candidate.versionName,
                signingIdentity = signingIdentity,
                byteCount = candidate.declaredSizeBytes,
                sha256 = candidate.artifactSha256,
            )
        }

    private fun digest(bytes: ByteArray): UpdateSha256 {
        val value =
            MessageDigest.getInstance("SHA-256")
                .digest(bytes)
                .joinToString("") { byte ->
                    "%02x".format(byte.toInt() and 0xff)
                }
        return UpdateSha256(value)
    }

    private fun cert(prefix: String): UpdateSha256 =
        UpdateSha256(prefix.repeat(64 / prefix.length))
}
