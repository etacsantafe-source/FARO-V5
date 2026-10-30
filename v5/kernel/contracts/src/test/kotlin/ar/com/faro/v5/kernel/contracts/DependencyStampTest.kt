package ar.com.faro.v5.kernel.contracts

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class DependencyStampTest {
    @Test
    fun `dependency stamp identities reject blank values`() {
        assertThrows(IllegalArgumentException::class.java) { AuthorityBoundaryId(" ") }
        assertThrows(IllegalArgumentException::class.java) { DependencyKey("") }
        assertThrows(IllegalArgumentException::class.java) { DependencyStampValue("\t") }
    }

    @Test
    fun `dependency stamp keeps boundary key and observed value explicit`() {
        val stamp = DependencyStamp(
            authorityBoundary = AuthorityBoundaryId("reality.finance"),
            dependencyKey = DependencyKey("account:checking"),
            observedValue = DependencyStampValue("revision:42"),
        )

        assertEquals(AuthorityBoundaryId("reality.finance"), stamp.authorityBoundary)
        assertEquals(DependencyKey("account:checking"), stamp.dependencyKey)
        assertEquals(DependencyStampValue("revision:42"), stamp.observedValue)
    }

    @Test
    fun `same key in another authority boundary is a different dependency`() {
        val reality = DependencyStamp(
            authorityBoundary = AuthorityBoundaryId("reality"),
            dependencyKey = DependencyKey("item:7"),
            observedValue = DependencyStampValue("3"),
        )
        val workspace = DependencyStamp(
            authorityBoundary = AuthorityBoundaryId("workspace"),
            dependencyKey = DependencyKey("item:7"),
            observedValue = DependencyStampValue("3"),
        )

        assertNotEquals(reality, workspace)
    }

    @Test
    fun `dependency stamp set rejects duplicate authority and key`() {
        val first = DependencyStamp(
            authorityBoundary = AuthorityBoundaryId("reality"),
            dependencyKey = DependencyKey("account:1"),
            observedValue = DependencyStampValue("41"),
        )
        val second = DependencyStamp(
            authorityBoundary = AuthorityBoundaryId("reality"),
            dependencyKey = DependencyKey("account:1"),
            observedValue = DependencyStampValue("42"),
        )

        assertThrows(IllegalArgumentException::class.java) {
            DependencyStampSet.of(first, second)
        }
    }

    @Test
    fun `dependency stamp set equality ignores input order`() {
        val a = DependencyStamp(
            authorityBoundary = AuthorityBoundaryId("reality"),
            dependencyKey = DependencyKey("account:1"),
            observedValue = DependencyStampValue("41"),
        )
        val b = DependencyStamp(
            authorityBoundary = AuthorityBoundaryId("workspace"),
            dependencyKey = DependencyKey("conversation:7"),
            observedValue = DependencyStampValue("3"),
        )

        assertEquals(
            DependencyStampSet.of(a, b),
            DependencyStampSet.of(b, a),
        )
        assertEquals(setOf(a, b), DependencyStampSet.of(a, b).asSet())
    }

    @Test
    fun `canonical list sorts by authority boundary then dependency key`() {
        val z = DependencyStamp(
            authorityBoundary = AuthorityBoundaryId("workspace"),
            dependencyKey = DependencyKey("conversation:7"),
            observedValue = DependencyStampValue("3"),
        )
        val b = DependencyStamp(
            authorityBoundary = AuthorityBoundaryId("reality"),
            dependencyKey = DependencyKey("account:2"),
            observedValue = DependencyStampValue("8"),
        )
        val a = DependencyStamp(
            authorityBoundary = AuthorityBoundaryId("reality"),
            dependencyKey = DependencyKey("account:1"),
            observedValue = DependencyStampValue("7"),
        )

        assertEquals(
            listOf(a, b, z),
            DependencyStampSet.of(z, b, a).asCanonicalList(),
        )
    }

}
