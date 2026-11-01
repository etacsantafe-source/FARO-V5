package ar.com.faro.v5.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FaroV5T4PostPickerGateTest {
    @Test fun pickerResultDoesNotAuthorizeAnExternalWrite() {
        val gate = FaroV5T4PostPickerGate()
        gate.pickerReturned("EXPORT", true)
        assertFalse(gate.authorizeAfterReturn("EXPORT", false))
        assertTrue(gate.authorizeAfterReturn("EXPORT", true))
        assertFalse("consent is one shot", gate.authorizeAfterReturn("EXPORT", true))
    }

    @Test fun stalePrePickerConsentCannotAuthorizeAfterProcessRestart() {
        val oldProcess = FaroV5T4PostPickerGate()
        oldProcess.pickerReturned("EXPORT", true)
        assertTrue(oldProcess.authorizeAfterReturn("EXPORT", true))
        val recreated = FaroV5T4PostPickerGate()
        assertFalse(recreated.authorizeAfterReturn("EXPORT", true))
        // Android delivers the result to the corresponding registered launcher.
        recreated.pickerReturned("EXPORT", true)
        assertFalse(recreated.authorizeAfterReturn("EXPORT", false))
        assertTrue(recreated.authorizeAfterReturn("EXPORT", true))
    }

    @Test fun cancellationWrongSchemeOrWrongOperationCannotRestore() {
        val gate = FaroV5T4PostPickerGate()
        gate.pickerReturned("RESTORE", false)
        assertFalse(gate.authorizeAfterReturn("RESTORE", true))
        gate.pickerReturned("RESTORE", true)
        assertFalse(gate.authorizeAfterReturn("EXPORT", true))
        assertTrue(gate.authorizeAfterReturn("RESTORE", true))
        assertFalse(gate.authorizeAfterReturn("RESTORE", true))
    }

    @Test fun selectingAgainInvalidatesPreviouslySelectedOperation() {
        val gate = FaroV5T4PostPickerGate()
        gate.pickerReturned("EXPORT", true)
        gate.reset()
        assertFalse(gate.authorizeAfterReturn("EXPORT", true))
        gate.pickerReturned("RESTORE", true)
        assertFalse(gate.authorizeAfterReturn("EXPORT", true))
        assertTrue(gate.authorizeAfterReturn("RESTORE", true))
    }
}
