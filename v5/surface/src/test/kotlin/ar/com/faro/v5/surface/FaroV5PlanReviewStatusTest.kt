package ar.com.faro.v5.surface

import ar.com.faro.v5.kernel.contracts.ConversationPlanReviewState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class FaroV5PlanReviewStatusTest {
    @Test fun pendingNeverClaimsExecution() {
        assertEquals("Pendiente de revisión", faroV5ReviewStateLabel(ConversationPlanReviewState.PENDING))
    }

    @Test fun approvalClearlySeparateFromApply() {
        assertEquals("Confirmado. Todavía no se aplicó.",
            faroV5ReviewStateLabel(ConversationPlanReviewState.APPROVED))
    }

    @Test fun staleProcessingAfterRestartDoesNotClaimLiveApply() {
        assertEquals("Estado de aplicación pendiente de verificar",
            faroV5ReviewStateLabel(ConversationPlanReviewState.APPLYING))
        assertEquals("Aplicando cambios…",
            faroV5ReviewStateLabel(ConversationPlanReviewState.APPLYING, true))
    }

    @Test fun unknownAndRejectedAreNotSuccess() {
        assertFalse(faroV5ReviewStateLabel(ConversationPlanReviewState.RECOVERING_UNKNOWN).contains("Aplicado"))
        assertEquals("No se aplicaron los cambios",
            faroV5ReviewStateLabel(ConversationPlanReviewState.REJECTED))
    }

    @Test fun committedOnlyLabelAfterCanonicalReceipt() {
        assertEquals("Aplicado y registrado",
            faroV5ReviewStateLabel(ConversationPlanReviewState.COMMITTED))
    }
}
