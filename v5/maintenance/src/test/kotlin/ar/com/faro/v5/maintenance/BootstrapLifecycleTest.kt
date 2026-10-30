package ar.com.faro.v5.maintenance

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BootstrapLifecycleTest {
    private val previous = StorageGenerationId("generation-old")
    private val target = StorageGenerationId("generation-new")

    @Test
    fun `identities reject blank or non canonical values`() {
        assertThrows(IllegalArgumentException::class.java) {
            StorageGenerationId(" ")
        }
        assertThrows(IllegalArgumentException::class.java) {
            StorageGenerationId(" generation-new")
        }
        assertThrows(IllegalArgumentException::class.java) {
            MaintenanceOperationId("")
        }
    }

    @Test
    fun `bootstrap source and target must differ`() {
        assertThrows(IllegalArgumentException::class.java) {
            entry(
                previousGenerationId = target,
                targetGenerationId = target,
            )
        }
    }

    @Test
    fun `happy path cannot skip validation or switch preparation`() {
        val preparing = entry()

        assertThrows(IllegalArgumentException::class.java) {
            BootstrapTransitionPolicy.transition(
                preparing,
                BootstrapPhase.SWITCHED,
            )
        }

        val staged =
            BootstrapTransitionPolicy.transition(
                preparing,
                BootstrapPhase.STAGED,
            )
        val validated =
            BootstrapTransitionPolicy.transition(
                staged,
                BootstrapPhase.VALIDATED,
            )
        val switchPrepared =
            BootstrapTransitionPolicy.transition(
                validated,
                BootstrapPhase.SWITCH_PREPARED,
            )
        val switched =
            BootstrapTransitionPolicy.transition(
                switchPrepared,
                BootstrapPhase.SWITCHED,
            )

        assertTrue(
            BootstrapTransitionPolicy.previousGenerationMayBeReactivated(switched)
        )
    }

    @Test
    fun `abort is pre switch only`() {
        val aborted =
            BootstrapTransitionPolicy.transition(
                entry(),
                BootstrapPhase.ABORTED_PRE_SWITCH,
            )

        assertThrows(IllegalArgumentException::class.java) {
            BootstrapTransitionPolicy.transition(
                aborted,
                BootstrapPhase.STAGED,
            )
        }

        val switched = switchedEntry()
        assertThrows(IllegalArgumentException::class.java) {
            BootstrapTransitionPolicy.transition(
                switched,
                BootstrapPhase.ABORTED_PRE_SWITCH,
            )
        }
    }

    @Test
    fun `material writes permanently forbid previous generation reactivation`() {
        val switched = switchedEntry()
        assertTrue(
            BootstrapTransitionPolicy.previousGenerationMayBeReactivated(switched)
        )

        val live =
            BootstrapTransitionPolicy.acceptMaterialWrites(switched)

        assertFalse(
            BootstrapTransitionPolicy.previousGenerationMayBeReactivated(live)
        )
    }

    @Test
    fun `verified rollback is terminal and cannot accept target writes`() {
        val switched = switchedEntry()
        val rollbackPrepared =
            BootstrapTransitionPolicy.transition(
                switched,
                BootstrapPhase.ROLLBACK_PREPARED,
            )
        val rolledBack =
            BootstrapTransitionPolicy.transition(
                rollbackPrepared,
                BootstrapPhase.ROLLED_BACK_POST_SWITCH,
            )
        val verified =
            BootstrapTransitionPolicy.transition(
                rolledBack,
                BootstrapPhase.ROLLBACK_VERIFIED,
            )

        assertFalse(
            BootstrapTransitionPolicy.previousGenerationMayBeReactivated(verified)
        )
        assertThrows(IllegalArgumentException::class.java) {
            BootstrapTransitionPolicy.acceptMaterialWrites(verified)
        }
        assertThrows(IllegalArgumentException::class.java) {
            BootstrapTransitionPolicy.transition(
                verified,
                BootstrapPhase.COMPLETED,
            )
        }
    }

    @Test
    fun `material writes are rejected before switch`() {
        assertThrows(IllegalArgumentException::class.java) {
            BootstrapTransitionPolicy.acceptMaterialWrites(entry())
        }
    }

    private fun switchedEntry(): BootstrapJournalEntry {
        val staged =
            BootstrapTransitionPolicy.transition(
                entry(),
                BootstrapPhase.STAGED,
            )
        val validated =
            BootstrapTransitionPolicy.transition(
                staged,
                BootstrapPhase.VALIDATED,
            )
        val prepared =
            BootstrapTransitionPolicy.transition(
                validated,
                BootstrapPhase.SWITCH_PREPARED,
            )
        return BootstrapTransitionPolicy.transition(
            prepared,
            BootstrapPhase.SWITCHED,
        )
    }

    private fun entry(
        previousGenerationId: StorageGenerationId? = previous,
        targetGenerationId: StorageGenerationId = target,
    ): BootstrapJournalEntry =
        BootstrapJournalEntry(
            operationId = MaintenanceOperationId("maintenance-op-1"),
            kind = MaintenanceOperationKind.MIGRATION,
            previousGenerationId = previousGenerationId,
            targetGenerationId = targetGenerationId,
            phase = BootstrapPhase.PREPARING,
        )
}
