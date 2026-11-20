package ar.com.faro.v5.generation

import ar.com.faro.v5.owner.household.api.HouseholdNeed
import ar.com.faro.v5.owner.household.api.HouseholdNeedId
import ar.com.faro.v5.owner.household.api.HouseholdNeedName
import ar.com.faro.v5.owner.household.api.HouseholdNeedPriority
import ar.com.faro.v5.owner.household.api.HouseholdNeedQuantity
import ar.com.faro.v5.owner.household.api.HouseholdNeedStatus
import ar.com.faro.v5.owner.household.api.HouseholdNeedUnit
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.time.LocalDate

internal data class Household10BackupSection(
    val revision: Long,
    val needs: List<HouseholdNeed>,
)

/** Canonical semantic section for the Household state and needs in Reality10. */
internal object FaroV5Household10BackupSectionCodec {
    private const val MAGIC = 0x48313053 // H10S
    private const val FORMAT = 1
    private const val SCHEMA = 10
    private const val HEADER_BYTES = 4 * Int.SIZE_BYTES + Long.SIZE_BYTES
    private const val MAX_NEEDS = 8_192
    private const val MAX_BYTES = 4 * 1024 * 1024
    private const val ID_BYTES = 128 * 3
    private const val NAME_BYTES = 160 * 3
    private const val ENUM_BYTES = 16
    private val dueDayRange = LocalDate.of(1, 1, 1).toEpochDay()..LocalDate.of(9999, 12, 31).toEpochDay()

    fun encode(section: Household10BackupSection): ByteArray {
        require(section.needs.size <= MAX_NEEDS) { "Household10 section has too many needs" }
        val needs = section.needs.toList()
        requireCanonical(section.revision, needs)

        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { output ->
            output.writeInt(MAGIC)
            output.writeInt(FORMAT)
            output.writeInt(SCHEMA)
            output.writeLong(section.revision)
            output.writeInt(needs.size)
            for (need in needs) {
                output.writeNeed(need)
                require(bytes.size() <= MAX_BYTES) { "Household10 section exceeds its byte limit" }
            }
        }
        return bytes.toByteArray()
    }

    fun decode(bytes: ByteArray): Household10BackupSection {
        require(bytes.size in HEADER_BYTES..MAX_BYTES) { "Household10 section size is invalid" }
        try {
            return DataInputStream(ByteArrayInputStream(bytes)).use { input ->
                require(input.readInt() == MAGIC && input.readInt() == FORMAT && input.readInt() == SCHEMA) {
                    "Household10 section header is invalid"
                }
                val revision = input.readLong()
                val count = input.readInt()
                require(revision >= 0L && count in 0..MAX_NEEDS) {
                    "Household10 section revision or row count is invalid"
                }
                val needs = List(count) { input.readNeed() }
                require(input.available() == 0) { "Household10 section has trailing bytes" }
                requireCanonical(revision, needs)
                Household10BackupSection(revision, needs)
            }
        } catch (failure: EOFException) {
            throw IllegalArgumentException("Household10 section is truncated", failure)
        }
    }

    private fun requireCanonical(revision: Long, needs: List<HouseholdNeed>) {
        require(revision >= 0L && needs.size <= MAX_NEEDS)
        var previous: String? = null
        for (need in needs) {
            require(previous == null || previous < need.id.value) {
                "Household10 section requires strictly ordered unique identities"
            }
            require(need.revision <= revision) { "Household10 row is newer than its snapshot" }
            previous = need.id.value
        }
    }

    private fun DataOutputStream.writeNeed(need: HouseholdNeed) {
        writeText(need.id.value, ID_BYTES)
        writeText(need.name.value, NAME_BYTES)
        writeInt(need.quantity.thousandths.toInt())
        writeText(need.quantity.unit.name, ENUM_BYTES)
        writeText(need.priority.name, ENUM_BYTES)
        writeText(need.status.name, ENUM_BYTES)
        writeLong(need.revision)
        val due = need.dueDate
        writeByte(if (due == null) 0 else 1)
        if (due != null) writeLong(due.toEpochDay())
    }

    private fun DataInputStream.readNeed(): HouseholdNeed {
        val id = HouseholdNeedId(readText(ID_BYTES))
        val name = HouseholdNeedName(readText(NAME_BYTES))
        val quantity = HouseholdNeedQuantity(readInt().toLong(), HouseholdNeedUnit.valueOf(readText(ENUM_BYTES)))
        val priority = HouseholdNeedPriority.valueOf(readText(ENUM_BYTES))
        val status = HouseholdNeedStatus.valueOf(readText(ENUM_BYTES))
        val revision = readLong()
        val due = when (readUnsignedByte()) {
            0 -> null
            1 -> {
                val day = readLong()
                require(day in dueDayRange) { "Household10 due day is invalid" }
                LocalDate.ofEpochDay(day)
            }
            else -> throw IllegalArgumentException("Household10 due day marker is invalid")
        }
        return HouseholdNeed(id, name, quantity, priority, status, revision, due)
    }

    private fun DataOutputStream.writeText(value: String, limit: Int) {
        val field = value.toByteArray(Charsets.UTF_8)
        require(field.size in 1..limit && field.toString(Charsets.UTF_8) == value) {
            "Household10 text cannot be encoded as bounded UTF-8"
        }
        writeShort(field.size)
        write(field)
    }

    private fun DataInputStream.readText(limit: Int): String {
        val count = readUnsignedShort()
        require(count in 1..limit && count <= available()) { "Household10 text length is invalid" }
        val field = ByteArray(count).also(::readFully)
        val value = field.toString(Charsets.UTF_8)
        require(value.toByteArray(Charsets.UTF_8).contentEquals(field)) { "Household10 UTF-8 is invalid" }
        return value
    }
}
