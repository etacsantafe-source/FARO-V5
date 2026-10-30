package ar.com.faro.v5.kernel.runtime

import ar.com.faro.v5.kernel.contracts.AuthorityBoundaryId
import ar.com.faro.v5.kernel.contracts.DependencyKey
import ar.com.faro.v5.kernel.contracts.DependencyStamp
import ar.com.faro.v5.kernel.contracts.DependencyStampSet
import ar.com.faro.v5.kernel.contracts.DependencyStampValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DependencyStampSourceTest {
    private data class Input(val accountId: String)

    @Test
    fun `source captures only typed material dependencies chosen by boundary`() {
        val source = DependencyStampSource<Input> { input ->
            DependencyStampSet.of(
                DependencyStamp(
                    authorityBoundary = AuthorityBoundaryId("reality.finance"),
                    dependencyKey = DependencyKey("account:" + input.accountId),
                    observedValue = DependencyStampValue("revision:42"),
                )
            )
        }

        val captured = source.capture(Input("checking"))

        assertEquals(
            listOf(
                DependencyStamp(
                    authorityBoundary = AuthorityBoundaryId("reality.finance"),
                    dependencyKey = DependencyKey("account:checking"),
                    observedValue = DependencyStampValue("revision:42"),
                )
            ),
            captured.asCanonicalList(),
        )
    }
}
