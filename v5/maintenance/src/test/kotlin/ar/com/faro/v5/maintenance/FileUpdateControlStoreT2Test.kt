package ar.com.faro.v5.maintenance

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class FileUpdateControlStoreT2Test {
    @Test
    fun `expected install survives filesystem reopen and terminal install leaves pending`(
        @TempDir tempDir: Path,
    ) {
        val expectedInstall = expectedInstall("install-one")
        val store = FileUpdateControlStore(tempDir)
        store.create(expectedInstall)

        val prepared = checkNotNull(store.read(expectedInstall.expectedInstallId))
        assertEquals(UpdateLifecyclePhase.INSTALL_PREPARED, prepared.phase)

        val handoff =
            prepared.copy(
                phase = UpdateLifecyclePhase.OS_HANDOFF,
                platformSessionIdentity = "session-41",
            )
        assertTrue(store.compareAndSet(prepared, handoff))

        val reopened = FileUpdateControlStore(tempDir)
        assertEquals(handoff, reopened.read(expectedInstall.expectedInstallId))
        assertEquals(
            listOf(expectedInstall.expectedInstallId),
            reopened.pending().map { it.expectedInstallId },
        )

        val reconcile =
            handoff.copy(
                phase = UpdateLifecyclePhase.RECONCILE_REQUIRED,
            )
        assertTrue(reopened.compareAndSet(handoff, reconcile))

        val installed =
            reconcile.copy(
                phase = UpdateLifecyclePhase.INSTALLED,
            )
        assertTrue(reopened.compareAndSet(reconcile, installed))

        assertTrue(FileUpdateControlStore(tempDir).pending().isEmpty())
        assertEquals(
            UpdateLifecyclePhase.INSTALLED,
            FileUpdateControlStore(tempDir)
                .read(expectedInstall.expectedInstallId)
                ?.phase,
        )
    }

    @Test
    fun `stale compare and set cannot overwrite a newer durable phase`(
        @TempDir tempDir: Path,
    ) {
        val expectedInstall = expectedInstall("install-cas")
        val store = FileUpdateControlStore(tempDir)
        store.create(expectedInstall)
        val prepared = checkNotNull(store.read(expectedInstall.expectedInstallId))
        val handoff =
            prepared.copy(
                phase = UpdateLifecyclePhase.OS_HANDOFF,
                platformSessionIdentity = "session-cas",
            )

        assertTrue(store.compareAndSet(prepared, handoff))
        assertFalse(store.compareAndSet(prepared, handoff))
        assertEquals(
            UpdateLifecyclePhase.OS_HANDOFF,
            FileUpdateControlStore(tempDir)
                .read(expectedInstall.expectedInstallId)
                ?.phase,
        )
    }

    @Test
    fun `effect unknown remains pending across reopen and can only reenter reconcile`(
        @TempDir tempDir: Path,
    ) {
        val expectedInstall = expectedInstall("install-unknown")
        val store = FileUpdateControlStore(tempDir)
        store.create(expectedInstall)
        val prepared = checkNotNull(store.read(expectedInstall.expectedInstallId))
        val handoff =
            prepared.copy(
                phase = UpdateLifecyclePhase.OS_HANDOFF,
                platformSessionIdentity = "session-unknown",
            )
        assertTrue(store.compareAndSet(prepared, handoff))

        val unknown =
            handoff.copy(
                phase = UpdateLifecyclePhase.EFFECT_UNKNOWN,
                failure =
                    UpdateFailure(
                        kind = UpdateFailureKind.EFFECT_UNKNOWN,
                        reason = "callback missing after process replacement",
                    ),
            )
        assertTrue(store.compareAndSet(handoff, unknown))

        val reopened = FileUpdateControlStore(tempDir)
        assertEquals(listOf(unknown), reopened.pending())

        val reconcile =
            unknown.copy(
                phase = UpdateLifecyclePhase.RECONCILE_REQUIRED,
                failure = null,
            )
        assertTrue(reopened.compareAndSet(unknown, reconcile))
        assertEquals(
            UpdateLifecyclePhase.RECONCILE_REQUIRED,
            FileUpdateControlStore(tempDir)
                .read(expectedInstall.expectedInstallId)
                ?.phase,
        )
    }

    @Test
    fun `expected install payload and platform session are immutable`(
        @TempDir tempDir: Path,
    ) {
        val expectedInstall = expectedInstall("install-immutable")
        val store = FileUpdateControlStore(tempDir)
        store.create(expectedInstall)
        val prepared = checkNotNull(store.read(expectedInstall.expectedInstallId))

        val replacement =
            expectedInstall(
                id = "install-immutable",
                targetVersionCode = 3L,
                versionName = "3.0",
            )
        val mutatedPayload =
            UpdateControlEntry(
                expectedInstall = replacement,
                phase = UpdateLifecyclePhase.OS_HANDOFF,
                platformSessionIdentity = "session-a",
            )

        assertThrows(IllegalArgumentException::class.java) {
            store.compareAndSet(prepared, mutatedPayload)
        }

        val sessionBound =
            prepared.copy(
                platformSessionIdentity = "session-a",
            )
        assertTrue(store.compareAndSet(prepared, sessionBound))
        assertEquals(
            sessionBound,
            FileUpdateControlStore(tempDir).read(expectedInstall.expectedInstallId),
        )

        val handoff =
            sessionBound.copy(
                phase = UpdateLifecyclePhase.OS_HANDOFF,
            )
        assertTrue(store.compareAndSet(sessionBound, handoff))

        val changedSession =
            handoff.copy(
                phase = UpdateLifecyclePhase.RECONCILE_REQUIRED,
                platformSessionIdentity = "session-b",
            )
        assertThrows(IllegalArgumentException::class.java) {
            store.compareAndSet(handoff, changedSession)
        }
    }

    @Test
    fun `corrupted latest durable record fails closed`(
        @TempDir tempDir: Path,
    ) {
        val expectedInstall = expectedInstall("install-corrupt")
        FileUpdateControlStore(tempDir).create(expectedInstall)

        val controlDir = tempDir.resolve("update").resolve("control")
        val record =
            Files.list(controlDir).use { stream ->
                stream.filter { Files.isRegularFile(it) }.findFirst().orElseThrow()
            }
        val bytes = Files.readAllBytes(record)
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 0x01).toByte()
        Files.write(record, bytes)

        assertThrows(IllegalStateException::class.java) {
            FileUpdateControlStore(tempDir).read(expectedInstall.expectedInstallId)
        }
    }

    @Test
    fun `logical expected install id is never used as a filesystem path`(
        @TempDir tempDir: Path,
    ) {
        val expectedInstall = expectedInstall("../logical-install")
        FileUpdateControlStore(tempDir).create(expectedInstall)

        val names =
            Files.list(tempDir.resolve("update").resolve("control")).use { stream ->
                stream.map { it.fileName.toString() }.toList()
            }

        assertTrue(names.size == 1)
        assertTrue(names.single().startsWith("update-control-"))
        assertFalse(names.single().contains("logical-install"))
        assertFalse(names.single().contains(".."))
    }

    @Test
    fun `multiple signer identity round trips deterministically`(
        @TempDir tempDir: Path,
    ) {
        val expectedInstall =
            expectedInstall(
                id = "install-multisigner",
                signingIdentity =
                    UpdateSigningIdentity.MultipleSigners(
                        setOf(
                            sha('b'),
                            sha('a'),
                        )
                    ),
            )
        val store = FileUpdateControlStore(tempDir)
        store.create(expectedInstall)

        assertEquals(
            expectedInstall,
            FileUpdateControlStore(tempDir)
                .read(expectedInstall.expectedInstallId)
                ?.expectedInstall,
        )
    }

    private fun expectedInstall(
        id: String,
        targetVersionCode: Long = 2L,
        versionName: String = "2.0",
        signingIdentity: UpdateSigningIdentity =
            UpdateSigningIdentity.Lineage(
                listOf(
                    sha('1'),
                    sha('2'),
                )
            ),
    ): ExpectedInstall {
        val candidate =
            UpdateManifestCandidate(
                sourceIdentity = "https://updates.example.invalid/manifest-v1",
                channel = "stable",
                packageName = "ar.com.faro.v5",
                versionCode = targetVersionCode,
                versionName = versionName,
                minimumSourceVersionCode = 1L,
                artifactUri = "https://updates.example.invalid/faro-v5.apk",
                artifactSha256 = sha('a'),
                declaredSizeBytes = 4096L,
            )
        val artifactIdentity =
            UpdateArtifactIdentity(
                packageName = candidate.packageName,
                versionCode = candidate.versionCode,
                versionName = candidate.versionName,
                signingIdentity = signingIdentity,
                byteCount = candidate.declaredSizeBytes,
                sha256 = candidate.artifactSha256,
            )
        return ExpectedInstall(
            expectedInstallId = ExpectedInstallId(id),
            operationId = UpdateOperationId("operation-$id"),
            installedIdentityAtPreparation =
                InstalledUpdateIdentity(
                    packageName = candidate.packageName,
                    versionCode = 1L,
                    signingIdentity = signingIdentity,
                ),
            staged =
                VerifiedStagedUpdate(
                    artifactId = StagedUpdateArtifactId("artifact-$id"),
                    candidate = candidate,
                    artifactIdentity = artifactIdentity,
                ),
        )
    }

    private fun sha(char: Char): UpdateSha256 =
        UpdateSha256(char.toString().repeat(64))
}
