package ar.com.faro.v5.app

/**
 * Pure, testable one-shot post-picker authorization.
 * Choosing a content URI is NOT permission to write/restore.
 * A process recreated before the result may accept a new result, but it can
 * never resurrect an old authorization: only a new checkbox press can grant it.
 */
internal class FaroV5T4PostPickerGate {
    private var selectedOperation: String? = null

    fun pickerReturned(operation: String, isContentUri: Boolean) {
        require(operation == "EXPORT" || operation == "RESTORE")
        selectedOperation = if (isContentUri) operation else null
    }

    fun authorizeAfterReturn(operation: String, explicitFreshConsent: Boolean): Boolean {
        val allowed = selectedOperation == operation && explicitFreshConsent
        if (allowed) selectedOperation = null
        return allowed
    }

    fun reset() {
        selectedOperation = null
    }
}
