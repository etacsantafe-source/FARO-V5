package ar.com.faro.v5.kernel.contracts

import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.function.Supplier

/**
 * Single-process admission gate for material writes versus maintenance quiescence.
 *
 * Normal material writes acquire the shared/read side. Maintenance acquires the exclusive/write
 * side, which blocks new writes and waits for already-admitted writes to finish.
 *
 * This gate does not replace owner/store transaction locks and never becomes domain authority.
 */
interface MaterialWriteGate {
    fun <T> withMaterialWrite(block: () -> T): T

    fun <T> withQuiescedMaterialWrites(block: () -> T): T

    fun <T> supplyMaterialWrite(supplier: Supplier<T>): T =
        withMaterialWrite { supplier.get() }
}

class SingleProcessMaterialWriteGate : MaterialWriteGate {
    private val lock = ReentrantReadWriteLock(true)

    @Volatile
    private var materialWriteAdmissionOpen = true

    override fun <T> withMaterialWrite(block: () -> T): T {
        val admission = lock.readLock()
        admission.lock()
        return try {
            // Only the thread holding the maintenance-exclusive lease may write while
            // normal admission is closed. Outsiders remain blocked and then rejected.
            check(materialWriteAdmissionOpen || lock.isWriteLockedByCurrentThread()) {
                "material writes are suspended until maintenance recovery completes"
            }
            block()
        } finally {
            admission.unlock()
        }
    }

    override fun <T> withQuiescedMaterialWrites(block: () -> T): T {
        val quiescence = lock.writeLock()
        quiescence.lock()
        return try {
            block()
        } finally {
            quiescence.unlock()
        }
    }

    fun closeMaterialWriteAdmission() {
        val quiescence = lock.writeLock()
        quiescence.lock()
        try {
            materialWriteAdmissionOpen = false
        } finally {
            quiescence.unlock()
        }
    }

    fun openMaterialWriteAdmission() {
        val quiescence = lock.writeLock()
        quiescence.lock()
        try {
            materialWriteAdmissionOpen = true
        } finally {
            quiescence.unlock()
        }
    }

    fun isMaterialWriteAdmissionOpen(): Boolean =
        materialWriteAdmissionOpen
}
