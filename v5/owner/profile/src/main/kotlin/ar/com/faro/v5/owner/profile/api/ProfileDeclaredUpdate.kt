package ar.com.faro.v5.owner.profile.api

import ar.com.faro.v5.kernel.contracts.CanonicalPayload

data class ProfileDeclaredUpdate(
    val displayName: DeclaredDisplayName,
)

object ProfileDeclaredUpdatePayload {
    const val FORMAT_ID: String = "profile.update_declared.v1"

    fun encodeBytes(update: ProfileDeclaredUpdate): ByteArray =
        update.displayName.value.toByteArray(Charsets.UTF_8)

    fun payload(update: ProfileDeclaredUpdate): CanonicalPayload =
        CanonicalPayload.fromEncoded(FORMAT_ID, encodeBytes(update))

    fun decode(payload: CanonicalPayload): ProfileDeclaredUpdate? {
        if (payload.formatId != FORMAT_ID) return null
        return runCatching {
            val raw = String(payload.copyBytes(), Charsets.UTF_8)
            ProfileDeclaredUpdate(DeclaredDisplayName.of(raw))
        }.getOrNull()
    }
}
