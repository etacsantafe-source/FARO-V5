package ar.com.faro.v5.maintenance

import java.nio.file.Files
import java.nio.file.Path

class FileBootstrapJournal(
    root: Path,
) : BootstrapJournalPort {
    private val directory =
        root.toAbsolutePath().normalize().resolve("bootstrap").resolve("journal")
    private val records = DurableRecordDirectory(directory)

    override fun read(operationId: MaintenanceOperationId): BootstrapJournalEntry? =
        readPersisted(operationId)?.entry

    override fun pending(): List<BootstrapJournalEntry> {
        if (!Files.exists(directory)) return emptyList()
        val prefixes =
            Files.newDirectoryStream(directory).use { entries ->
                entries.mapNotNull { path ->
                    JOURNAL_FILE.matchEntire(path.fileName.toString())
                        ?.groupValues
                        ?.get(1)
                }.toSet()
            }

        return prefixes.map { prefix ->
            val durable =
                checkNotNull(records.readLatest(prefix)) {
                    "bootstrap journal disappeared during pending scan"
                }
            val persisted = BootstrapJournalCodec.decode(durable.bytes)
            check(persisted.sequence == durable.sequence) {
                "bootstrap journal sequence mismatch during pending scan"
            }
            check(prefix(persisted.entry.operationId) == prefix) {
                "bootstrap journal path identity mismatch"
            }
            persisted.entry
        }
            .filterNot { entry ->
                (entry.phase == BootstrapPhase.COMPLETED && entry.materialWritesAccepted) ||
                    entry.phase in setOf(
                        BootstrapPhase.ABORTED_PRE_SWITCH,
                        BootstrapPhase.ROLLBACK_VERIFIED,
                    )
            }
            .sortedBy { it.operationId.value }
    }

    override fun create(entry: BootstrapJournalEntry) {
        val prefix = prefix(entry.operationId)
        check(records.readLatest(prefix) == null) {
            "bootstrap journal operation already exists"
        }
        records.publish(
            prefix = prefix,
            sequence = 1L,
            bytes = BootstrapJournalCodec.encode(1L, entry),
        )
    }

    override fun compareAndSet(
        expected: BootstrapJournalEntry,
        updated: BootstrapJournalEntry,
    ): Boolean {
        requireLegalMutation(expected, updated)

        val current = readPersisted(expected.operationId) ?: return false
        if (current.entry != expected) {
            return false
        }

        val nextSequence = Math.addExact(current.sequence, 1L)
        records.publish(
            prefix = prefix(expected.operationId),
            sequence = nextSequence,
            bytes = BootstrapJournalCodec.encode(nextSequence, updated),
        )
        return true
    }

    private fun readPersisted(
        operationId: MaintenanceOperationId,
    ): PersistedBootstrapJournal? {
        val durable = records.readLatest(prefix(operationId)) ?: return null
        val persisted = BootstrapJournalCodec.decode(durable.bytes)
        check(persisted.sequence == durable.sequence) {
            "bootstrap journal sequence mismatch"
        }
        check(persisted.entry.operationId == operationId) {
            "bootstrap journal operation identity mismatch"
        }
        return persisted
    }

    private fun requireLegalMutation(
        expected: BootstrapJournalEntry,
        updated: BootstrapJournalEntry,
    ) {
        require(updated.operationId == expected.operationId) {
            "bootstrap operation identity is immutable"
        }
        require(updated.kind == expected.kind) {
            "bootstrap operation kind is immutable"
        }
        require(updated.previousGenerationId == expected.previousGenerationId) {
            "bootstrap previous generation is immutable"
        }
        require(updated.targetGenerationId == expected.targetGenerationId) {
            "bootstrap target generation is immutable"
        }

        if (updated.phase != expected.phase) {
            require(updated.materialWritesAccepted == expected.materialWritesAccepted) {
                "phase transition cannot change material-write fence"
            }
            val legal = BootstrapTransitionPolicy.transition(expected, updated.phase)
            require(legal == updated) {
                "bootstrap journal phase mutation is not canonical"
            }
        } else {
            val legal = BootstrapTransitionPolicy.acceptMaterialWrites(expected)
            require(legal == updated) {
                "same-phase journal mutation must be the material-write fence"
            }
        }
    }

    private fun prefix(operationId: MaintenanceOperationId): String =
        "journal-" + MaintenancePathIdentity.sha256Hex(operationId.value)

    private companion object {
        val JOURNAL_FILE =
            Regex("^(journal-[0-9a-f]{64})-[0-9]{20}\\.bin$")
    }
}
