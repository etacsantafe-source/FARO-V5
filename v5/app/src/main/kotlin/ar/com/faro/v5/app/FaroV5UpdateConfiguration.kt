package ar.com.faro.v5.app

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class FaroV5UpdateConfiguration(
    val manifestUrl: String,
    val channel: String,
    allowedHosts: Set<String>,
) {
    val allowedHosts: Set<String> =
        allowedHosts.map { it.lowercase() }.toSet()

    init {
        require(channel.isNotBlank() && channel == channel.trim() && channel.length <= 128) {
            "update channel must be canonical and bounded"
        }
        require(this.allowedHosts.isNotEmpty()) {
            "update host allowlist must not be empty"
        }
        require(
            this.allowedHosts.all { host ->
                host.isNotBlank() &&
                    host == host.trim() &&
                    host.length <= 253 &&
                    HOST.matches(host) &&
                    !host.all { it.isDigit() || it == '.' } &&
                    !host.contains(':')
            }
        ) {
            "update host allowlist contains an invalid DNS name"
        }

        val manifest = requireTrustedHttpsUrl(manifestUrl, rejectCredentialQuery = true)
        require(manifest.host.lowercase() in this.allowedHosts) {
            "update manifest host is not allowlisted"
        }
    }

    fun requireAllowedUrl(
        rawUrl: String,
        rejectCredentialQuery: Boolean,
    ): URI {
        val uri = requireTrustedHttpsUrl(rawUrl, rejectCredentialQuery)
        require(uri.host.lowercase() in allowedHosts) {
            "update URL host is not allowlisted"
        }
        return uri
    }

    private fun requireTrustedHttpsUrl(
        rawUrl: String,
        rejectCredentialQuery: Boolean,
    ): URI {
        require(rawUrl.isNotBlank() && rawUrl == rawUrl.trim() && rawUrl.length <= 4096) {
            "update URL must be canonical and bounded"
        }
        val uri =
            runCatching { URI(rawUrl) }
                .getOrElse { throw IllegalArgumentException("invalid update URL") }
        require(
            uri.scheme.equals("https", ignoreCase = true) &&
                !uri.host.isNullOrBlank() &&
                uri.userInfo == null &&
                uri.fragment == null
        ) {
            "update URL must use HTTPS without user-info or fragment"
        }
        if (rejectCredentialQuery) {
            require(!containsCredentialQuery(uri)) {
                "update URL must not persist credential-like query parameters"
            }
        }
        return uri
    }

    private fun containsCredentialQuery(uri: URI): Boolean {
        val rawQuery = uri.rawQuery ?: return false
        return rawQuery.split('&').any { pair ->
            val rawName = pair.substringBefore('=')
            val decoded =
                runCatching {
                    URLDecoder.decode(rawName, StandardCharsets.UTF_8)
                }.getOrElse {
                    return@any true
                }
            val name =
                decoded.lowercase()
                    .replace("-", "")
                    .replace("_", "")
            CREDENTIAL_QUERY_TOKENS.any { token ->
                name == token || name.endsWith(token)
            }
        }
    }

    private companion object {
        val HOST = Regex("[a-z0-9](?:[a-z0-9.-]{0,251}[a-z0-9])?")
        val CREDENTIAL_QUERY_TOKENS =
            setOf(
                "accesskeyid",
                "accesstoken",
                "apikey",
                "auth",
                "authorization",
                "authtoken",
                "bearertoken",
                "credential",
                "jwt",
                "keypairid",
                "password",
                "passwd",
                "secret",
                "sessionid",
                "signature",
                "sig",
                "token",
            )
    }
}
