package ar.com.faro.v5.kernel.contracts

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class CandidateTest {
    private data class DraftProposal(
        val description: String,
        val amountText: String?,
    )

    @Test
    fun `candidate can hold an incomplete typed proposal without executable identity`() {
        val candidate = Candidate(
            proposal = DraftProposal(
                description = "registrar gasto",
                amountText = null,
            ),
        )

        assertEquals("registrar gasto", candidate.proposal.description)
        assertEquals(null, candidate.proposal.amountText)
    }

    @Test
    fun `candidate value follows proposal value only`() {
        val first = Candidate(DraftProposal("registrar gasto", null))
        val changed = Candidate(DraftProposal("registrar gasto", "1000"))

        assertNotEquals(first, changed)
    }
}
