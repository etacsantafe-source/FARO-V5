package ar.com.faro.v5.maintenance

import java.nio.file.Path

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
    /**
     * Every instance addressing this same durable root shares the process-local
     * observer registry. Runtime and bootstrap may hold distinct pointer objects.
     * Cross-process writes still require their own authority revalidation.
     */
    private val rootKey: Path = root.toAbsolutePath().normalize()

    fun addChangeListener(listener: Runnable) {
        synchronized(listenerGuard) {
            listenersByRoot.getOrPut(rootKey) { mutableSetOf() }.add(listener)
        }
    }

    fun removeChangeListener(listener: Runnable) {
        synchronized(listenerGuard) {
            listenersByRoot[rootKey]?.let { listeners ->
                listeners.remove(listener)
                if (listeners.isEmpty()) listenersByRoot.remove(rootKey)
            }
        }
    }

    private val records =
        DurableRecordDirectory(
            rootKey.resolve("bootstrap").resolve("active")
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
        // Only the owner that durably publishes may notify. Snapshot under the
        // registry lock and invoke outside it, allowing listeners to unsubscribe.
        val listeners = synchronized(listenerGuard) {
            listenersByRoot[rootKey]?.toList().orEmpty()
        }
        for (listener in listeners) {
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
        private val listenerGuard = Any()
        private val listenersByRoot = mutableMapOf<Path, MutableSet<Runnable>>()
    }
}
