package ar.com.faro.v5.maintenance

import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class PortabilityExternalWriteT2Test {
    @Test
    fun `write start survives reopen and becomes unknown without replay`(
        @TempDir tempDir: Path,
    ) {
        val source = source("effect/process-death", "portable-payload")
        val firstStore = FilePortabilityExternalAttemptStore(tempDir)
        val first = PortabilityExternalEffectCoordinator(firstStore)

        first.begin(source)
        first.markWriteStarted(source)

        val reopenedStore = FilePortabilityExternalAttemptStore(tempDir)
        val reopened = PortabilityExternalEffectCoordinator(reopenedStore)
        val receipts = reopened.reconcileInterruptedWrites()

        assertEquals(1, receipts.size)
        assertEquals(PortabilityExternalStatus.EFFECT_UNKNOWN, receipts.single().status)
        assertEquals(
            "process_interrupted_after_write_start",
            receipts.single().reason,
        )
        assertTrue(reopenedStore.pending().isEmpty())

        val terminal = reopenedStore.read(source.effectIdentity)!!
        assertEquals(PortabilityExternalAttemptPhase.TERMINAL, terminal.phase)
        assertEquals(PortabilityExternalStatus.EFFECT_UNKNOWN, terminal.terminalStatus)
    }

    @Test
    fun `picker cancel is durable no-write cancellation`(
        @TempDir tempDir: Path,
    ) {
        val source = source("effect/picker-cancel", "payload")
        val store = FilePortabilityExternalAttemptStore(tempDir)
        val coordinator = PortabilityExternalEffectCoordinator(store)

        coordinator.begin(source)
        val receipt = coordinator.pickerCancelled(source)

        assertEquals(PortabilityExternalStatus.CANCELLED, receipt.status)
        assertEquals("picker_cancelled", receipt.reason)
        assertEquals(
            PortabilityExternalAttemptPhase.TERMINAL,
            store.read(source.effectIdentity)!!.phase,
        )
        assertEquals(0, source.copyCount)
    }

    @Test
    fun `started write cannot be entered again and blindly replayed`(
        @TempDir tempDir: Path,
    ) {
        val source = source("effect/no-blind-replay", "payload")
        val coordinator =
            PortabilityExternalEffectCoordinator(
                FilePortabilityExternalAttemptStore(tempDir)
            )

        coordinator.begin(source)
        coordinator.markWriteStarted(source)

        assertThrows(IllegalStateException::class.java) {
            coordinator.markWriteStarted(source)
        }
    }

    @Test
    fun `verified success requires durable write-start marker`(
        @TempDir tempDir: Path,
    ) {
        val source = source("effect/success", "payload")
        val store = FilePortabilityExternalAttemptStore(tempDir)
        val coordinator = PortabilityExternalEffectCoordinator(store)

        coordinator.begin(source)
        assertThrows(IllegalStateException::class.java) {
            coordinator.completeVerifiedSuccess(source)
        }

        coordinator.markWriteStarted(source)
        val receipt = coordinator.completeVerifiedSuccess(source)

        assertEquals(PortabilityExternalStatus.SUCCESS, receipt.status)
        assertEquals(null, receipt.reason)
        assertEquals(source.byteCount, receipt.byteCount)
        assertEquals(source.sha256Hex, receipt.sha256Hex)
    }

    @Test
    fun `same effect identity cannot be rebound to another artifact`(
        @TempDir tempDir: Path,
    ) {
        val first = source("effect/rebind", "payload-one")
        val second = source("effect/rebind", "payload-two")
        val coordinator =
            PortabilityExternalEffectCoordinator(
                FilePortabilityExternalAttemptStore(tempDir)
            )

        coordinator.begin(first)

        assertThrows(IllegalStateException::class.java) {
            coordinator.begin(second)
        }
    }

    @Test
    fun `journal paths hash effect identity and persist no external uri`(
        @TempDir tempDir: Path,
    ) {
        val source = source("effect/opaque-sensitive-id", "payload")
        val coordinator =
            PortabilityExternalEffectCoordinator(
                FilePortabilityExternalAttemptStore(tempDir)
            )

        coordinator.begin(source)

        val journalDir =
            tempDir.resolve("portability").resolve("external-attempts")
        val names =
            Files.list(journalDir).use { stream ->
                stream.map { it.fileName.toString() }.toList()
            }
        assertEquals(1, names.size)
        assertTrue(names.single().matches(Regex("portability-[0-9a-f]{64}-[0-9]{20}\\.bin")))
        assertFalse(names.single().contains("opaque-sensitive-id"))

        val recordBytes =
            Files.list(journalDir).use { stream ->
                Files.readAllBytes(stream.findFirst().orElseThrow())
            }
        val recordText = recordBytes.toString(Charsets.ISO_8859_1)
        assertFalse(recordText.contains("content://"))
        assertFalse(recordText.contains("document/"))
    }

    private fun source(
        effectIdentity: String,
        payload: String,
    ): FakeSource =
        FakeSource(
            effectIdentity = effectIdentity,
            payload = payload.toByteArray(Charsets.UTF_8),
        )

    private class FakeSource(
        override val effectIdentity: String,
        private val payload: ByteArray,
    ) : PortabilityExternalWriteSource {
        override val exportPlanFingerprintHex: String = "a".repeat(64)
        override val formatProfile: String = PortabilityPreview.FORMAT_PROFILE
        override val artifactHandleHex: String =
            sha256(("handle:" + effectIdentity).toByteArray(Charsets.UTF_8))
        override val suggestedFileName: String = "faro-portable-data.json"
        override val mimeType: String = "application/json"
        override val generatedAtEpochMillis: Long = 100L
        override val byteCount: Long = payload.size.toLong()
        override val sha256Hex: String = sha256(payload)
        var copyCount: Int = 0
            private set

        override fun copyTo(output: OutputStream): Long {
            copyCount += 1
            output.write(payload)
            output.flush()
            return payload.size.toLong()
        }
    }

    private companion object {
        fun sha256(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256")
                .digest(bytes)
                .joinToString("") { "%02x".format(it) }
    }
}
