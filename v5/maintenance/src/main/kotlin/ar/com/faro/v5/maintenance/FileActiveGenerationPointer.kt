package ar.com.faro.v5.maintenance

import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArraySet

data class ActiveGenerationPointer(
    val sequence: Long,
    val generationId: StorageGenerationId,
) {
    init {
        require(sequence > 0L) { "active generation pointer sequence must be positive" }
    }
}

interface ActiveGenerationPointerPort {
    fun read(): ActiveGenerationPointer?

    fun compareAndSet(
        expectedGenerationId: StorageGenerationId?,
        targetGenerationId: StorageGenerationId,
    ): ActiveGenerationPointer?
}

class FileActiveGenerationPointer(
    root: Path,
) : ActiveGenerationPointerPort {
    /** Real pointer owner: process-local observers after durable publication only. */
    private val changeListeners = CopyOnWriteArraySet<Runnable>()

    fun addChangeListener(listener: Runnable) { changeListeners.add(listener) }
    fun removeChangeListener(listener: Runnable) { changeListeners.remove(listener) }

    private val records =
        DurableRecordDirectory(
            root.toAbsolutePath().normalize().resolve("bootstrap").resolve("active")
        )

    override fun read(): ActiveGenerationPointer? {
        val durable = records.readLatest(PREFIX) ?: return null
        val pointer = ActiveGenerationPointerCodec.decode(durable.bytes)
        check(pointer.sequence == durable.sequence) {
            "active generation pointer sequence mismatch"
        }
        return pointer
    }

    override fun compareAndSet(
        expectedGenerationId: StorageGenerationId?,
        targetGenerationId: StorageGenerationId,
    ): ActiveGenerationPointer? {
        require(targetGenerationId != expectedGenerationId) {
            "active generation switch must change generation"
        }

        val current = read()
        if (current?.generationId != expectedGenerationId) {
            return null
        }

        val nextSequence = Math.addExact(current?.sequence ?: 0L, 1L)
        val next = ActiveGenerationPointer(
            sequence = nextSequence,
            generationId = targetGenerationId,
        )
        records.publish(
            prefix = PREFIX,
            sequence = nextSequence,
            bytes = ActiveGenerationPointerCodec.encode(next),
        )
        // A rejected CAS never notifies; callbacks cannot roll back durable publication.
        for (listener in changeListeners) {
            try {
                listener.run()
            } catch (_: Exception) {
                // Observers revalidate authority on every read.
            }
        }
        return next
    }

    private companion object {
        const val PREFIX = "active"
    }
}
