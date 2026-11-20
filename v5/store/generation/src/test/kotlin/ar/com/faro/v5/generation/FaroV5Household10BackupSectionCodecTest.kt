package ar.com.faro.v5.generation

import ar.com.faro.v5.owner.household.api.HouseholdNeed
import ar.com.faro.v5.owner.household.api.HouseholdNeedId
import ar.com.faro.v5.owner.household.api.HouseholdNeedName
import ar.com.faro.v5.owner.household.api.HouseholdNeedPriority
import ar.com.faro.v5.owner.household.api.HouseholdNeedQuantity
import ar.com.faro.v5.owner.household.api.HouseholdNeedStatus
import ar.com.faro.v5.owner.household.api.HouseholdNeedUnit
import java.nio.ByteBuffer
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class FaroV5Household10BackupSectionCodecTest {
    private val codec = FaroV5Household10BackupSectionCodec
    private val fixture = Household10BackupSection(8, listOf(need("a"), need("z")))

    @Test
    fun roundTripsTypedNeedsAndReemitsIdenticalBytes() {
        val maximum = need("ñ").copy(
            name = HouseholdNeedName("Yerba orgánica 🍃"),
            quantity = HouseholdNeedQuantity(Int.MAX_VALUE.toLong(), HouseholdNeedUnit.KILOGRAM),
            priority = HouseholdNeedPriority.HIGH,
            status = HouseholdNeedStatus.COMPLETED,
            revision = 8,
            dueDate = LocalDate.of(9999, 12, 31),
        )
        val original = fixture.copy(needs = fixture.needs + maximum)
        val bytes = codec.encode(original)
        val decoded = codec.decode(bytes)

        assertEquals(original, decoded)
        assertArrayEquals(bytes, codec.encode(decoded))
    }

    @Test
    fun emptySectionPreservesNonzeroStateRevision() {
        val empty = Household10BackupSection(81, emptyList())
        assertEquals(empty, codec.decode(codec.encode(empty)))
    }

    @Test
    fun refusesDuplicateUnorderedAndFutureRowRevisions() {
        for (rows in listOf(
            listOf(need("a"), need("a")),
            listOf(need("z"), need("a")),
            listOf(need("a").copy(revision = 9)),
        )) {
            assertThrows(IllegalArgumentException::class.java) { codec.encode(fixture.copy(needs = rows)) }
        }

        val bytes = codec.encode(fixture)
        // Changing the aggregate to zero leaves existing row revisions ahead of it.
        ByteBuffer.wrap(bytes).putLong(12, 0L)
        assertThrows(IllegalArgumentException::class.java) { codec.decode(bytes) }
    }

    @Test
    fun rejectsInvalidMagicFormatSchemaAndRowCount() {
        for ((offset, replacement) in listOf(0 to 0, 4 to 4, 8 to 9, 20 to 8193)) {
            val bytes = codec.encode(fixture)
            ByteBuffer.wrap(bytes).putInt(offset, replacement)
            assertThrows(IllegalArgumentException::class.java) { codec.decode(bytes) }
        }
    }

    @Test
    fun rejectsMalformedUtfEightAndUnrepresentableUtfSixteen() {
        val malformed = codec.encode(fixture)
        // Header (24 bytes), ID length (2 bytes), then the first ID byte.
        malformed[26] = 0xff.toByte()
        assertThrows(IllegalArgumentException::class.java) { codec.decode(malformed) }

        val loneSurrogate = need("a").copy(name = HouseholdNeedName("Yerba \uD800"))
        assertThrows(IllegalArgumentException::class.java) {
            codec.encode(fixture.copy(needs = listOf(loneSurrogate)))
        }
    }

    @Test
    fun rejectsTruncationTrailingBytesAndInvalidDateMarker() {
        val bytes = codec.encode(fixture)
        val invalidMarker = bytes.copyOf().also { it[it.lastIndex] = 2 }
        for (invalid in listOf(bytes.copyOf(8), bytes.copyOf(bytes.size - 1), bytes + 0.toByte(), invalidMarker)) {
            assertThrows(IllegalArgumentException::class.java) { codec.decode(invalid) }
        }
    }

    @Test
    fun rejectsUnknownEnumInvalidQuantityAndOutOfRangeDate() {
        val quantity = codec.encode(fixture)
        // Header + ID length and content + name length and content.
        ByteBuffer.wrap(quantity).putInt(24 + 2 + 1 + 2 + "Yerba".length, 0)

        val unit = codec.encode(fixture)
        val unitOffset = 24 + 2 + 1 + 2 + "Yerba".length + Int.SIZE_BYTES + 2
        "VOID".toByteArray(Charsets.US_ASCII).copyInto(unit, unitOffset)

        val dated = Household10BackupSection(8, listOf(need("a").copy(dueDate = LocalDate.of(1, 1, 1))))
        val day = codec.encode(dated)
        ByteBuffer.wrap(day).putLong(day.size - Long.SIZE_BYTES, 2932897L)

        for (invalid in listOf(quantity, unit, day)) {
            assertThrows(IllegalArgumentException::class.java) { codec.decode(invalid) }
        }
    }

    @Test
    fun failsClosedAtCountAndByteBudgets() {
        val tooMany = List(8193) { need("a") }
        assertThrows(IllegalArgumentException::class.java) {
            codec.encode(Household10BackupSection(8, tooMany))
        }
        val oversized = List(8192) { index ->
            need("需".repeat(124) + index.toString().padStart(4, '0')).copy(
                name = HouseholdNeedName("食".repeat(160)),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            codec.encode(Household10BackupSection(8, oversized))
        }
        assertThrows(IllegalArgumentException::class.java) {
            codec.decode(ByteArray(4 * 1024 * 1024 + 1))
        }
    }

    private fun need(id: String) = HouseholdNeed(
        id = HouseholdNeedId(id),
        name = HouseholdNeedName("Yerba"),
        quantity = HouseholdNeedQuantity(1, HouseholdNeedUnit.UNIT),
        priority = HouseholdNeedPriority.NORMAL,
        status = HouseholdNeedStatus.OPEN,
        revision = 1,
    )
}
