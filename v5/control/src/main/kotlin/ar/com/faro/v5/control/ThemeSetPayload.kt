package ar.com.faro.v5.control

import ar.com.faro.v5.kernel.contracts.CanonicalPayload

object ThemeSetPayload {
    const val FORMAT_ID = "settings.theme.set.v1"

    fun payload(themeMode: PresentationThemeMode): CanonicalPayload =
        CanonicalPayload.fromEncoded(
            FORMAT_ID,
            themeMode.name.toByteArray(Charsets.UTF_8),
        )

    fun decode(payload: CanonicalPayload): PresentationThemeMode? {
        if (payload.formatId != FORMAT_ID) return null
        return runCatching {
            val raw = String(payload.copyBytes(), Charsets.UTF_8)
            require(raw.isNotBlank())
            require(raw == raw.trim())
            PresentationThemeMode.valueOf(raw)
        }.getOrNull()
    }
}
