package ar.com.faro.v5.generation

import ar.com.faro.v5.owner.matters.api.MatterDetail
import ar.com.faro.v5.owner.matters.api.MatterId
import ar.com.faro.v5.owner.matters.api.MatterRecord
import ar.com.faro.v5.owner.matters.api.MatterSnapshot
import ar.com.faro.v5.owner.matters.api.MatterStatus
import ar.com.faro.v5.owner.matters.api.MatterTitle
import ar.com.faro.v5.owner.objectives.api.ObjectiveDetail
import ar.com.faro.v5.owner.objectives.api.ObjectiveId
import ar.com.faro.v5.owner.objectives.api.ObjectiveRecord
import ar.com.faro.v5.owner.objectives.api.ObjectiveSnapshot
import ar.com.faro.v5.owner.objectives.api.ObjectiveStatus
import ar.com.faro.v5.owner.objectives.api.ObjectiveTitle
import ar.com.faro.v5.store.reality.RealityDomainBackupSnapshot
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/**
 * Private canonical G4 section for FBS2 VERSION 2, not a standalone export.
 * No source-generation authority, sharing consent, or restore permission here.
 */
object FaroV5G4DomainSemanticCodec {
    private const val MAGIC = 0x47344231 // G4B1
    private const val VERSION = 1
    private const val REALITY_SCHEMA = 7
    private const val MAX_ROWS = 100_000
    private const val MAX_FIELD_BYTES = 16_384
    const val MAX_BYTES = 4 * 1024 * 1024

    fun encode(snapshot: RealityDomainBackupSnapshot): ByteArray {
        validate(snapshot)
        return BoundedBytes(MAX_BYTES).use { buffer ->
            DataOutputStream(buffer).use { output ->
                output.writeInt(MAGIC)
                output.writeInt(VERSION)
                output.writeInt(snapshot.schemaVersion)
                output.writeLong(snapshot.objectives.revision)
                output.writeInt(snapshot.objectives.objectives.size)
                snapshot.objectives.objectives.forEach { item ->
                    putText(output, item.id.value)
                    putText(output, item.title.value)
                    putText(output, item.detail.value)
                    putText(output, item.status.name)
                    output.writeLong(item.updatedAtEpochMillis)
                }
                output.writeLong(snapshot.matters.revision)
                output.writeInt(snapshot.matters.matters.size)
                snapshot.matters.matters.forEach { item ->
                    putText(output, item.id.value)
                    putText(output, item.title.value)
                    putText(output, item.detail.value)
                    putText(output, item.status.name)
                    output.writeBoolean(item.dueAtEpochMillis != null)
                    item.dueAtEpochMillis?.let(output::writeLong)
                    output.writeLong(item.updatedAtEpochMillis)
                }
            }
            buffer.toByteArray()
        }
    }

    fun decode(bytes: ByteArray): RealityDomainBackupSnapshot {
        check(bytes.size in 1..MAX_BYTES) { "G4 section outside bounded size" }
        return DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            check(input.readInt() == MAGIC) { "invalid G4 section magic" }
            check(input.readInt() == VERSION) { "unsupported G4 section version" }
            val schema = input.readInt()
            check(schema == REALITY_SCHEMA) { "unsupported G4 Reality schema" }
            val objectiveRevision = input.readLong()
            val objectiveCount = getCount(input)
            val objectives = buildList(objectiveCount) {
                repeat(objectiveCount) {
                    add(ObjectiveRecord(
                        id = ObjectiveId.of(getText(input)),
                        title = ObjectiveTitle.of(getText(input)),
                        detail = ObjectiveDetail.of(getText(input)),
                        status = ObjectiveStatus.valueOf(getText(input)),
                        updatedAtEpochMillis = input.readLong(),
                    ))
                }
            }
            val matterRevision = input.readLong()
            val matterCount = getCount(input)
            val matters = buildList(matterCount) {
                repeat(matterCount) {
                    add(MatterRecord(
                        id = MatterId.of(getText(input)),
                        title = MatterTitle.of(getText(input)),
                        detail = MatterDetail.of(getText(input)),
                        status = MatterStatus.valueOf(getText(input)),
                        dueAtEpochMillis = when (input.readUnsignedByte()) {
                            0 -> null
                            1 -> input.readLong()
                            else -> error("invalid G4 due-date presence flag")
                        },
                        updatedAtEpochMillis = input.readLong(),
                    ))
                }
            }
            check(input.available() == 0) { "trailing G4 bytes" }
            RealityDomainBackupSnapshot(
                schemaVersion = schema,
                objectives = ObjectiveSnapshot(objectiveRevision, objectives),
                matters = MatterSnapshot(matterRevision, matters),
            ).also(::validate)
        }
    }

    private fun validate(value: RealityDomainBackupSnapshot) {
        check(value.schemaVersion == REALITY_SCHEMA) { "G4 Reality schema mismatch" }
        check(value.objectives.objectives.size <= MAX_ROWS &&
            value.matters.matters.size <= MAX_ROWS) { "G4 record count exceeds bound" }
        check(value.objectives.revision >= value.objectives.objectives.size.toLong() &&
            value.matters.revision >= value.matters.matters.size.toLong()
        ) { "G4 owner revision smaller than record count" }
        check(strictOrder(value.objectives.objectives.map { it.id.value }) &&
            strictOrder(value.matters.matters.map { it.id.value })
        ) { "G4 records are not in canonical SQLite BINARY order" }
    }

    private fun strictOrder(values: List<String>): Boolean =
        values.zipWithNext().all { (first, second) ->
            val a = first.toByteArray(Charsets.UTF_8)
            val b = second.toByteArray(Charsets.UTF_8)
            var i = 0
            val n = minOf(a.size, b.size)
            while (i < n && a[i] == b[i]) i++
            if (i == n) a.size < b.size
            else (a[i].toInt() and 255) < (b[i].toInt() and 255)
        }

    private fun putText(output: DataOutputStream, text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        check(bytes.size <= MAX_FIELD_BYTES) { "G4 field exceeds bound" }
        output.writeInt(bytes.size)
        output.write(bytes)
    }

    private fun getText(input: DataInputStream): String {
        val size = input.readInt()
        check(size in 0..MAX_FIELD_BYTES && size <= input.available()) {
            "invalid G4 field length"
        }
        val bytes = ByteArray(size)
        input.readFully(bytes)
        return Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString()
    }

    private fun getCount(input: DataInputStream): Int =
        input.readInt().also { count ->
            check(count in 0..MAX_ROWS && count <= input.available()) {
                "invalid G4 row count"
            }
        }

    private class BoundedBytes(private val limit: Int) : ByteArrayOutputStream() {
        override fun write(b: Int) {
            check(size() < limit) { "G4 section exceeds byte bound" }
            super.write(b)
        }
        override fun write(b: ByteArray, off: Int, len: Int) {
            check(len >= 0 && size().toLong() + len <= limit.toLong()) {
                "G4 section exceeds byte bound"
            }
            super.write(b, off, len)
        }
    }
}
