package ar.com.faro.v5.generation

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** Canonical Finance9 DATA only; never export ACL, grants or Keystore material. */
data class Finance9TableRows(val table: String, val rows: List<List<String?>>)

data class Finance9SectionSnapshot(
    val schemaVersion: Int,
    val tables: List<Finance9TableRows>,
) {
    init { FaroV5Finance9BackupCodec.validate(this) }

    companion object {
        fun empty(): Finance9SectionSnapshot =
            Finance9SectionSnapshot(9, FaroV5Finance9BackupCodec.tableNames().map {
                Finance9TableRows(it, emptyList())
            })
    }
}

/**
 * F9S1: bounded canonical semantic snapshot for the fifth FBS2v4 section.
 * No SQLite writes here. FBS2 section digests are not authenticity proofs;
 * only FAROBAK2's authenticated envelope supplies authenticity.
 */
object FaroV5Finance9BackupCodec {
    private const val MAGIC = 0x46395331 // F9S1
    private const val FORMAT_VERSION = 1
    const val SCHEMA_VERSION = 9
    const val MAX_BYTES = 4 * 1024 * 1024
    private const val MAX_ROWS = 20_000
    private const val MAX_FIELD_BYTES = 4_096

    private data class Spec(
        val name: String,
        val columns: Int,
        val keyIndices: List<Int>,
        val nullable: Set<Int>,
        val numeric: Set<Int>,
    )

    /** Exactly eight data tables. No finance_acl_state/policy/apply_grant. */
    private val specs: List<Spec> = listOf(
        Spec("finance_opening_confirmation", 5, listOf(0), setOf(1, 3), setOf(1, 3)),
        Spec("finance_transfer", 9, listOf(0), emptySet(), setOf(6, 7, 8)),
        Spec("finance_debt", 8, listOf(0), emptySet(), setOf(4, 5, 6, 7)),
        Spec("finance_schedule", 10, listOf(0), setOf(1, 5), setOf(3, 5, 7, 8, 9)),
        Spec("finance_plan_item", 11, listOf(0), setOf(1, 2, 3), setOf(2, 5, 8, 10)),
        Spec("finance_plan_item_payment", 3, listOf(0, 1), emptySet(), setOf(2)),
        Spec("finance_budget_limit", 7, listOf(0, 1, 2), emptySet(), setOf(3, 4, 5)),
        Spec("finance_schema_migration_audit", 5, listOf(0), emptySet(), setOf(1, 2, 4)),
    )

    fun tableNames(): List<String> = specs.map { it.name }

    fun validate(snapshot: Finance9SectionSnapshot) {
        require(snapshot.schemaVersion == SCHEMA_VERSION) {
            "Finance9 section schema must be exactly 9"
        }
        require(snapshot.tables.map { it.table } == tableNames()) {
            "Finance9 section must contain all eight data tables in canonical order"
        }
        var rowCount = 0
        for (index in specs.indices) {
            val spec = specs[index]
            val table = snapshot.tables[index]
            rowCount += table.rows.size
            require(rowCount <= MAX_ROWS) { "Finance9 row budget exceeded" }
            table.rows.forEach { row ->
                require(row.size == spec.columns) {
                    "Finance9 wrong column count for " + spec.name
                }
                row.forEachIndexed { column, value ->
                    if (value == null) {
                        require(column in spec.nullable) {
                            "Finance9 unexpected NULL in " + spec.name
                        }
                    } else {
                        require(value.toByteArray(Charsets.UTF_8).size <= MAX_FIELD_BYTES &&
                            value.indexOf('\u0000') == -1
                        ) { "Finance9 field exceeds UTF8 budget" }
                        if (column in spec.numeric) {
                            val parsed = value.toLongOrNull()
                            require(parsed != null && parsed >= 0L && parsed.toString() == value) {
                                "Finance9 numeric is not canonical nonnegative Long"
                            }
                        }
                    }
                }
                require(spec.keyIndices.all { !row[it].isNullOrEmpty() }) {
                    "Finance9 primary key is missing"
                }
            }
            require(table.rows.zipWithNext().all { (left, right) ->
                compareKey(spec, left, right) < 0
            }) { "Finance9 rows require sorted unique unsigned UTF8 keys" }
        }
    }

    fun encode(snapshot: Finance9SectionSnapshot): ByteArray {
        validate(snapshot)
        val buffer = ByteArrayOutputStream()
        DataOutputStream(buffer).use { output ->
            output.writeInt(MAGIC)
            output.writeInt(FORMAT_VERSION)
            output.writeInt(SCHEMA_VERSION)
            output.writeInt(specs.size)
            for (table in snapshot.tables) {
                writeField(output, table.table)
                output.writeInt(table.rows.size)
                table.rows.forEach { row ->
                    row.forEach { value -> writeField(output, value) }
                    require(buffer.size() <= MAX_BYTES) {
                        "Finance9 section exceeds 4MiB"
                    }
                }
            }
            output.flush()
        }
        return buffer.toByteArray().also {
            require(it.size <= MAX_BYTES) { "Finance9 section exceeds 4MiB" }
        }
    }

    fun decode(bytes: ByteArray): Finance9SectionSnapshot {
        require(bytes.size in 16..MAX_BYTES) { "Finance9 section length invalid" }
        val parsed = DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            require(input.readInt() == MAGIC &&
                input.readInt() == FORMAT_VERSION &&
                input.readInt() == SCHEMA_VERSION
            ) { "Finance9 section magic/format/schema mismatch" }
            require(input.readInt() == specs.size) {
                "Finance9 table count mismatch"
            }
            var totalRows = 0
            val tables = specs.map { spec ->
                val name = checkNotNull(readField(input))
                require(name == spec.name) { "Finance9 table identity mismatch" }
                val n = input.readInt()
                require(n in 0..MAX_ROWS && totalRows + n <= MAX_ROWS) {
                    "Finance9 row count invalid"
                }
                totalRows += n
                Finance9TableRows(
                    name,
                    List(n) { List(spec.columns) { readField(input) } },
                )
            }
            require(input.available() == 0) { "Finance9 trailing bytes" }
            Finance9SectionSnapshot(SCHEMA_VERSION, tables)
        }
        require(encode(parsed).contentEquals(bytes)) {
            "Finance9 noncanonical representation"
        }
        return parsed
    }

    private fun writeField(out: DataOutputStream, value: String?) {
        if (value == null) {
            out.writeInt(-1)
            return
        }
        val bytes = value.toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_FIELD_BYTES) { "Finance9 field length exceeds budget" }
        out.writeInt(bytes.size)
        out.write(bytes)
    }

    private fun readField(input: DataInputStream): String? {
        val length = input.readInt()
        if (length == -1) return null
        require(length in 0..MAX_FIELD_BYTES && length <= input.available()) {
            "Finance9 invalid field byte length"
        }
        val raw = ByteArray(length)
        input.readFully(raw)
        return Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(raw)).toString().also {
                require(it.toByteArray(Charsets.UTF_8).contentEquals(raw)) {
                    "Finance9 noncanonical UTF8"
                }
            }
    }

    private fun compareKey(spec: Spec, a: List<String?>, b: List<String?>): Int {
        for (index in spec.keyIndices) {
            val left = checkNotNull(a[index]).toByteArray(Charsets.UTF_8)
            val right = checkNotNull(b[index]).toByteArray(Charsets.UTF_8)
            val length = minOf(left.size, right.size)
            for (position in 0 until length) {
                val comparison = (left[position].toInt() and 255)
                    .compareTo(right[position].toInt() and 255)
                if (comparison != 0) return comparison
            }
            val comparison = left.size.compareTo(right.size)
            if (comparison != 0) return comparison
        }
        return 0
    }
}
