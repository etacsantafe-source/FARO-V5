package ar.com.faro.v5.generation

import java.nio.ByteBuffer
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class FaroV5Household11ReceiptBackupSectionCodecTest {
    private val codec = FaroV5Household11ReceiptBackupSectionCodec

    @Test
    fun emptyRevisionZeroRoundTripsWithoutInventingStock() {
        val empty = Household11ReceiptBackupSection(0, emptyList())
        val bytes = codec.encode(empty)
        assertEquals(empty, codec.decode(bytes))
        assertArrayEquals(bytes, codec.encode(codec.decode(bytes)))
    }

    @Test
    fun fullRowAndCivilDayBoundariesRoundTrip() {
        val first = row("r1", day = LocalDate.of(1, 1, 1).toEpochDay(), recordedAt = 0)
        val last = row("r2", intent = "i2", day = LocalDate.of(9999, 12, 31).toEpochDay(),
            recordedAt = Long.MAX_VALUE, revision = 2L)
        val section = Household11ReceiptBackupSection(2L, listOf(first, last))
        assertEquals(section, codec.decode(codec.encode(section)))
    }

    @Test
    fun distinctReceiptsFromOnePurchaseRemainSeparateAndDeterministic() {
        val a = row("a", intent = "first", quantity = 7_000)
        val b = row("b", intent = "second", quantity = 3_000, revision = 2L)
        val section = Household11ReceiptBackupSection(2L, listOf(a, b))
        val first = codec.encode(section)
        assertEquals(section, codec.decode(first))
        assertArrayEquals(first, codec.encode(section))
        assertEquals(2, codec.decode(first).receipts.size)
    }

    @Test
    fun unsortedReceiptsCannotSilentlyReorderArchive() {
        val section = Household11ReceiptBackupSection(2L, listOf(
            row("b", intent = "i2"),
            row("a", intent = "i1"),
        ))
        assertThrows(IllegalArgumentException::class.java) { codec.encode(section) }
    }

    @Test
    fun duplicateReceiptIdIsRejected() {
        val section = Household11ReceiptBackupSection(2L, listOf(
            row("a", intent = "i1"),
            row("a", intent = "i2"),
        ))
        assertThrows(IllegalArgumentException::class.java) { codec.encode(section) }
    }

    @Test
    fun duplicateIntentWithDifferentReceiptIdsIsRejected() {
        val section = Household11ReceiptBackupSection(2L, listOf(
            row("a", intent = "shared"),
            row("b", intent = "shared"),
        ))
        assertThrows(IllegalArgumentException::class.java) { codec.encode(section) }
    }

    @Test
    fun invalidGlobalOrRowRevisionsFailClosed() {
        val invalid = listOf(
            Household11ReceiptBackupSection(-1L, emptyList()),
            Household11ReceiptBackupSection(0L, listOf(row("a"))),
            Household11ReceiptBackupSection(3L, listOf(row("a", revision = 0L))),
            Household11ReceiptBackupSection(3L, listOf(row("a", revision = -1L))),
            Household11ReceiptBackupSection(3L, listOf(row("a", revision = 4L))),
        )
        for (section in invalid) {
            assertThrows(IllegalArgumentException::class.java) { codec.encode(section) }
        }
    }

    @Test
    fun quantityAndUnitMustMatchTypedSqlConstraints() {
        val invalid = listOf(
            row("a").copy(quantityThousandths = 0),
            row("a").copy(quantityThousandths = -3),
            row("a").copy(unit = "UNSPECIFIED"),
        )
        for (row in invalid) {
            assertThrows(IllegalArgumentException::class.java) {
                codec.encode(Household11ReceiptBackupSection(2L, listOf(row)))
            }
        }
    }

    @Test
    fun blankOverlongReferencesAndOutOfRangeDatesAreRejected() {
        val invalid = listOf(
            row(" "),
            row("a").copy(intentKey = ""),
            row("a").copy(purchaseRef = " ".repeat(4)),
            row("a").copy(stockItemRef = "x".repeat(129)),
            row("a").copy(receivedEpochDay = LocalDate.of(1, 1, 1).toEpochDay() - 1),
            row("a").copy(receivedEpochDay = LocalDate.of(9999, 12, 31).toEpochDay() + 1),
            row("a").copy(recordedAtMs = -1L),
        )
        for (row in invalid) {
            assertThrows(IllegalArgumentException::class.java) {
                codec.encode(Household11ReceiptBackupSection(2L, listOf(row)))
            }
        }
    }

    @Test
    fun corruptedMagicFormatOrSchemaNeverDecodes() {
        val bytes = codec.encode(Household11ReceiptBackupSection(1L, listOf(row("a"))))
        for ((offset, value) in listOf(0 to 0, 4 to 99, 8 to 10)) {
            val forged = bytes.clone()
            ByteBuffer.wrap(forged).putInt(offset, value)
            assertThrows(IllegalArgumentException::class.java) { codec.decode(forged) }
        }
    }

    @Test
    fun truncatedBytesAndTrailingJunkNeverDecode() {
        val bytes = codec.encode(Household11ReceiptBackupSection(1L, listOf(row("a"))))
        for (size in listOf(0, 1, 23, 24, bytes.size / 2, bytes.size - 1)) {
            assertThrows(IllegalArgumentException::class.java) {
                codec.decode(bytes.copyOf(size))
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            codec.decode(bytes + byteArrayOf(0x11))
        }
    }

    @Test
    fun hostileCountLengthsUtf8AndSizeBudgetsFailClosed() {
        val bytes = codec.encode(Household11ReceiptBackupSection(1L, listOf(row("a"))))
        val count = bytes.clone()
        ByteBuffer.wrap(count).putInt(20, Int.MAX_VALUE)
        assertThrows(IllegalArgumentException::class.java) { codec.decode(count) }

        val length = bytes.clone()
        length[24] = 0x7f.toByte()
        length[25] = 0x7f.toByte()
        assertThrows(IllegalArgumentException::class.java) { codec.decode(length) }

        val utf8 = bytes.clone()
        utf8[26] = 0xff.toByte()
        assertThrows(IllegalArgumentException::class.java) { codec.decode(utf8) }
        assertThrows(IllegalArgumentException::class.java) {
            codec.decode(ByteArray(4 * 1024 * 1024 + 1))
        }

        val bigRows = (0 until 8_192).map { n ->
            val suffix = n.toString().padStart(4, '0')
            row(
                "r" + suffix + "r".repeat(123),
                intent = "i" + suffix + "i".repeat(123),
            ).copy(
                purchaseRef = "p" + suffix + "p".repeat(123),
                stockItemRef = "s" + suffix + "s".repeat(123),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            codec.encode(Household11ReceiptBackupSection(1L, bigRows))
        }
    }

    private fun row(
        id: String,
        intent: String = "intent-$id",
        quantity: Int = 1_000,
        day: Long = 0L,
        recordedAt: Long = 1L,
        revision: Long = 1L,
    ) = Household11ReceiptBackupRow(
        receiptId = id,
        intentKey = intent,
        purchaseRef = "purchase-01",
        stockItemRef = "yerba",
        quantityThousandths = quantity,
        unit = "UNIT",
        receivedEpochDay = day,
        recordedAtMs = recordedAt,
        revision = revision,
    )
}
