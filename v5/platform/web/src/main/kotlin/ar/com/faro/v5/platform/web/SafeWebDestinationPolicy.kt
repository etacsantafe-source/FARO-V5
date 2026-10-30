package ar.com.faro.v5.platform.web

import java.net.InetAddress
import java.net.UnknownHostException
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal object PublicInternetAddressPolicy {
    fun allows(address: InetAddress): Boolean {
        val bytes = address.address
        return when (bytes.size) {
            4 -> allowsIpv4(bytes)
            16 -> allowsIpv6(bytes)
            else -> false
        }
    }

    private fun allowsIpv4(bytes: ByteArray): Boolean =
        IPV4_BLOCKED.none { it.matches(bytes) }

    private fun allowsIpv6(bytes: ByteArray): Boolean {
        // Global unicast baseline only. This excludes loopback, unspecified,
        // ULA, link-local, multicast and current non-global special blocks.
        if (!prefix("2000::", 3).matches(bytes)) {
            return false
        }
        return IPV6_BLOCKED.none { it.matches(bytes) }
    }

    private val IPV4_BLOCKED =
        listOf(
            prefix("0.0.0.0", 8),
            prefix("10.0.0.0", 8),
            prefix("100.64.0.0", 10),
            prefix("127.0.0.0", 8),
            prefix("169.254.0.0", 16),
            prefix("172.16.0.0", 12),
            prefix("192.0.0.0", 24),
            prefix("192.0.2.0", 24),
            prefix("192.88.99.0", 24),
            prefix("192.168.0.0", 16),
            prefix("198.18.0.0", 15),
            prefix("198.51.100.0", 24),
            prefix("203.0.113.0", 24),
            prefix("224.0.0.0", 4),
            prefix("240.0.0.0", 4),
        )

    private val IPV6_BLOCKED =
        listOf(
            // IETF protocol assignments: conservative default-deny.
            prefix("2001::", 23),
            prefix("2001:db8::", 32),
            // IPv4-embedding transition range.
            prefix("2002::", 16),
            // RFC 9637 documentation range.
            prefix("3fff::", 20),
        )

    private fun prefix(
        literal: String,
        bits: Int,
    ): IpPrefix =
        IpPrefix(
            bytes = InetAddress.getByName(literal).address,
            bits = bits,
        )

    private data class IpPrefix(
        val bytes: ByteArray,
        val bits: Int,
    ) {
        init {
            require(bits in 0..bytes.size * 8)
        }

        fun matches(candidate: ByteArray): Boolean {
            if (candidate.size != bytes.size) {
                return false
            }
            val fullBytes = bits / 8
            val remainder = bits % 8
            for (index in 0 until fullBytes) {
                if (candidate[index] != bytes[index]) {
                    return false
                }
            }
            if (remainder == 0) {
                return true
            }
            val mask = (0xff shl (8 - remainder)) and 0xff
            return (candidate[fullBytes].toInt() and mask) ==
                (bytes[fullBytes].toInt() and mask)
        }
    }
}

class PolicyEnforcingDns(
    private val delegate: Dns = Dns.SYSTEM,
) : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val resolved =
            try {
                delegate.lookup(hostname)
            } catch (failure: UnknownHostException) {
                throw failure
            } catch (failure: RuntimeException) {
                throw UnknownHostException("DNS lookup failed").also {
                    it.initCause(failure)
                }
            }

        if (resolved.isEmpty()) {
            throw UnknownHostException("DNS lookup returned no addresses")
        }
        if (resolved.any { !PublicInternetAddressPolicy.allows(it) }) {
            throw BlockedDestinationException()
        }
        return resolved.toList()
    }
}

internal class BlockedDestinationException :
    UnknownHostException("destination rejected by public-network policy")

internal object HttpsTargetPolicy {
    fun parse(url: String): HttpUrl? {
        val parsed = url.toHttpUrlOrNull() ?: return null
        if (parsed.scheme != "https") {
            return null
        }
        if (parsed.host.isBlank()) {
            return null
        }
        if (parsed.username.isNotEmpty() || parsed.password.isNotEmpty()) {
            return null
        }
        if (parsed.fragment != null) {
            return null
        }
        if (containsCredentialQuery(parsed)) {
            return null
        }
        val literalAddress = numericAddressOrNull(parsed.host)
        if (
            literalAddress != null &&
            !PublicInternetAddressPolicy.allows(literalAddress)
        ) {
            return null
        }
        return parsed
    }

    private fun containsCredentialQuery(url: HttpUrl): Boolean =
        url.queryParameterNames.any { name ->
            val normalized =
                name.lowercase()
                    .replace("-", "")
                    .replace("_", "")
            CREDENTIAL_QUERY_TOKENS.any { token ->
                normalized == token ||
                    normalized.endsWith(token)
            }
        }

    private fun numericAddressOrNull(host: String): InetAddress? {
        val looksNumeric =
            host.contains(':') ||
                host.all { it.isDigit() || it == '.' }
        if (!looksNumeric) {
            return null
        }
        return try {
            InetAddress.getByName(host)
        } catch (_: Exception) {
            null
        }
    }

    fun resolveRedirect(
        current: HttpUrl,
        location: String,
        redirectCount: Int,
        maxRedirects: Int,
    ): HttpUrl? {
        if (redirectCount >= maxRedirects) {
            return null
        }
        val resolved = current.resolve(location) ?: return null
        return parse(resolved.toString())
    }
    private val CREDENTIAL_QUERY_TOKENS =
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
