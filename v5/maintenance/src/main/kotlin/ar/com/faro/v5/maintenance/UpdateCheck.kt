package ar.com.faro.v5.maintenance

import java.io.StringReader
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Properties

object UpdateManifestFormat {
    const val SUPPORTED_SCHEMA: Int = 1
    const val MAX_MANIFEST_BYTES: Int = 64 * 1024
    const val MAX_ARTIFACT_BYTES: Long = 250L * 1024L * 1024L

    private val requiredKeys =
        setOf(
            "schema",
            "channel",
            "packageName",
            "versionCode",
            "versionName",
            "minimumSourceVersionCode",
            "apkUrl",
            "sha256",
            "sizeBytes",
        )

    private val optionalKeys =
        setOf(
            "signingCertificateSha256",
            "notes",
        )

    fun parse(
        sourceIdentity: String,
        propertiesText: String,
    ): UpdateManifestCandidate {
        requireHttps(sourceIdentity, "update manifest source")
        require(
            propertiesText.toByteArray(StandardCharsets.UTF_8).size <= MAX_MANIFEST_BYTES
        ) {
            "update manifest exceeds size limit"
        }

        val observedKeys = validateCanonicalLines(propertiesText)
        require(observedKeys.containsAll(requiredKeys)) {
            val missing = requiredKeys.minus(observedKeys).sorted().joinToString(",")
            "missing update manifest fields: $missing"
        }

        val properties =
            Properties().apply {
                load(StringReader(propertiesText))
            }

        val schema = requiredLong(properties, "schema").toIntExact("schema")
        require(schema == SUPPORTED_SCHEMA) {
            "unsupported update manifest schema"
        }

        val artifactUri = required(properties, "apkUrl")
        requireHttps(artifactUri, "update artifact URL")

        val declaredSize = requiredLong(properties, "sizeBytes")
        require(declaredSize in 1..MAX_ARTIFACT_BYTES) {
            "update artifact size outside allowed bound"
        }

        if (properties.containsKey("signingCertificateSha256")) {
            UpdateSha256(required(properties, "signingCertificateSha256").lowercase())
        }

        return UpdateManifestCandidate(
            sourceIdentity = sourceIdentity,
            channel = required(properties, "channel"),
            packageName = required(properties, "packageName"),
            versionCode = requiredLong(properties, "versionCode"),
            versionName = required(properties, "versionName"),
            minimumSourceVersionCode =
                requiredLong(properties, "minimumSourceVersionCode"),
            artifactUri = artifactUri,
            artifactSha256 = UpdateSha256(required(properties, "sha256").lowercase()),
            declaredSizeBytes = declaredSize,
        )
    }

    private fun validateCanonicalLines(propertiesText: String): Set<String> {
        val observed = linkedSetOf<String>()
        propertiesText.lineSequence().forEachIndexed { index, raw ->
            if (raw.isBlank() || raw.startsWith("#") || raw.startsWith("!")) {
                return@forEachIndexed
            }
            require(raw == raw.trim()) {
                "update manifest line " + (index + 1) + " is not canonical"
            }
            require(!raw.endsWithUnescapedBackslash()) {
                "update manifest continuations are not allowed"
            }

            val separator = raw.indexOf('=')
            require(separator > 0) {
                "update manifest line " + (index + 1) + " must use key=value"
            }
            val key = raw.substring(0, separator)
            require(key.matches(Regex("[A-Za-z][A-Za-z0-9]*"))) {
                "invalid update manifest key"
            }
            require(key in requiredKeys || key in optionalKeys) {
                "unknown update manifest field $key"
            }
            require(observed.add(key)) {
                "duplicate update manifest field $key"
            }
        }
        return observed
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

    private fun String.endsWithUnescapedBackslash(): Boolean {
        var count = 0
        for (index in indices.reversed()) {
            if (this[index] != '\\') break
            count += 1
        }
        return count % 2 == 1
    }

    private fun requireHttps(
        value: String,
        label: String,
    ): URI {
        require(value == value.trim()) {
            "$label must be canonical"
        }
        val uri =
            runCatching { URI(value) }
                .getOrElse { throw IllegalArgumentException("invalid $label") }
        require(
            uri.scheme.equals("https", ignoreCase = true) &&
                !uri.host.isNullOrBlank() &&
                uri.userInfo == null &&
                uri.fragment == null &&
                !containsCredentialQuery(uri)
        ) {
            "$label must use credential-free HTTPS"
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
            val normalized =
                decoded.lowercase()
                    .replace("-", "")
                    .replace("_", "")
            CREDENTIAL_QUERY_TOKENS.any { token ->
                normalized == token || normalized.endsWith(token)
            }
        }
    }

    private fun required(
        properties: Properties,
        key: String,
    ): String =
        requireNotNull(properties.getProperty(key)) {
            "missing update manifest field $key"
        }.trim().also {
            require(it.isNotEmpty()) {
                "empty update manifest field $key"
            }
        }

    private fun requiredLong(
        properties: Properties,
        key: String,
    ): Long =
        required(properties, key).toLongOrNull()
            ?: throw IllegalArgumentException("invalid update manifest field $key")

    private fun Long.toIntExact(field: String): Int {
        require(this in Int.MIN_VALUE..Int.MAX_VALUE) {
            "update manifest field $field out of range"
        }
        return toInt()
    }
}

class UpdateCheckUseCase(
    private val source: UpdateManifestSourcePort,
    private val installedIdentity: InstalledUpdateIdentityPort,
    expectedChannel: String,
) {
    private val expectedChannel = expectedChannel.also {
        require(it.isNotBlank()) {
            "expected update channel must be explicit"
        }
        require(it == it.trim()) {
            "expected update channel must be canonical"
        }
        require(it.length <= 128) {
            "expected update channel is too long"
        }
    }

    fun check(): UpdateEligibility? {
        val candidate = source.check() ?: return null
        return UpdateCheckPolicy.evaluate(
            installed = installedIdentity.current(),
            candidate = candidate,
            expectedChannel = expectedChannel,
        )
    }
}
