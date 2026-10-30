package ar.com.faro.v5.maintenance

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UpdateCheckTest {
    @Test
    fun `strict manifest parses V4-compatible fields into V5 candidate`() {
        val candidate =
            UpdateManifestFormat.parse(
                sourceIdentity = "https://updates.example.invalid/manifest.properties",
                propertiesText = manifest(),
            )

        assertEquals("stable", candidate.channel)
        assertEquals("ar.com.faro.v5", candidate.packageName)
        assertEquals(8L, candidate.versionCode)
        assertEquals(7L, candidate.minimumSourceVersionCode)
        assertEquals(
            "https://updates.example.invalid/faro-v5.apk?ref=stable",
            candidate.artifactUri,
        )
        assertEquals(4096L, candidate.declaredSizeBytes)
    }

    @Test
    fun `manifest rejects duplicates unknown fields and continuations`() {
        assertThrows(IllegalArgumentException::class.java) {
            UpdateManifestFormat.parse(
                "https://updates.example.invalid/manifest.properties",
                manifest() + "channel=beta\n",
            )
        }

        assertThrows(IllegalArgumentException::class.java) {
            UpdateManifestFormat.parse(
                "https://updates.example.invalid/manifest.properties",
                manifest() + "surprise=value\n",
            )
        }

        assertThrows(IllegalArgumentException::class.java) {
            UpdateManifestFormat.parse(
                "https://updates.example.invalid/manifest.properties",
                manifest().replace(
                    "versionName=5.0.0",
                    "versionName=5.0.0\\\ncontinued=value",
                ),
            )
        }
    }

    @Test
    fun `manifest and artifact sources must stay canonical HTTPS without user info`() {
        assertThrows(IllegalArgumentException::class.java) {
            UpdateManifestFormat.parse(
                "http://updates.example.invalid/manifest.properties",
                manifest(),
            )
        }

        assertThrows(IllegalArgumentException::class.java) {
            UpdateManifestFormat.parse(
                "https://token@updates.example.invalid/manifest.properties",
                manifest(),
            )
        }

        assertThrows(IllegalArgumentException::class.java) {
            UpdateManifestFormat.parse(
                "https://updates.example.invalid/manifest.properties",
                manifest(
                    apkUrl = "http://updates.example.invalid/faro-v5.apk",
                ),
            )
        }

        assertThrows(IllegalArgumentException::class.java) {
            UpdateManifestFormat.parse(
                "https://updates.example.invalid/manifest.properties?token=secret",
                manifest(),
            )
        }

        assertThrows(IllegalArgumentException::class.java) {
            UpdateManifestFormat.parse(
                "https://updates.example.invalid/manifest.properties?%74oken=secret",
                manifest(),
            )
        }

        assertThrows(IllegalArgumentException::class.java) {
            UpdateManifestFormat.parse(
                "https://updates.example.invalid/manifest.properties#fragment",
                manifest(),
            )
        }

        assertThrows(IllegalArgumentException::class.java) {
            UpdateManifestFormat.parse(
                "https://updates.example.invalid/manifest.properties",
                manifest(
                    apkUrl = "https://updates.example.invalid/faro-v5.apk?sig=secret",
                ),
            )
        }

        assertThrows(IllegalArgumentException::class.java) {
            UpdateManifestFormat.parse(
                "https://updates.example.invalid/manifest.properties",
                manifest(
                    apkUrl = "https://updates.example.invalid/faro-v5.apk?%73ig=secret",
                ),
            )
        }
    }

    @Test
    fun `manifest fails closed on schema size digest and missing required field`() {
        assertThrows(IllegalArgumentException::class.java) {
            UpdateManifestFormat.parse(
                "https://updates.example.invalid/manifest.properties",
                manifest(schema = 2),
            )
        }

        assertThrows(IllegalArgumentException::class.java) {
            UpdateManifestFormat.parse(
                "https://updates.example.invalid/manifest.properties",
                manifest(sizeBytes = UpdateManifestFormat.MAX_ARTIFACT_BYTES + 1),
            )
        }

        assertThrows(IllegalArgumentException::class.java) {
            UpdateManifestFormat.parse(
                "https://updates.example.invalid/manifest.properties",
                manifest(sha256 = "not-a-digest"),
            )
        }

        assertThrows(IllegalArgumentException::class.java) {
            UpdateManifestFormat.parse(
                "https://updates.example.invalid/manifest.properties",
                manifest().lineSequence()
                    .filterNot { it.startsWith("packageName=") }
                    .joinToString("\n"),
            )
        }

        assertThrows(IllegalArgumentException::class.java) {
            UpdateManifestFormat.parse(
                "https://updates.example.invalid/manifest.properties",
                manifest() + "notes=" + "x".repeat(UpdateManifestFormat.MAX_MANIFEST_BYTES),
            )
        }

        assertThrows(IllegalArgumentException::class.java) {
            UpdateManifestFormat.parse(
                "https://updates.example.invalid/manifest.properties",
                manifest(signerSha256 = ""),
            )
        }
    }

    @Test
    fun `legacy signer metadata may be validated but never becomes check authority`() {
        val candidate =
            UpdateManifestFormat.parse(
                "https://updates.example.invalid/manifest.properties",
                manifest(
                    signerSha256 = "b".repeat(64),
                    notes = "release-note",
                ),
            )

        assertEquals("ar.com.faro.v5", candidate.packageName)
        assertTrue(candidate.toString().contains("ar.com.faro.v5"))
    }

    @Test
    fun `update check is read only and returns policy result without staging`() {
        var sourceCalls = 0
        var installedCalls = 0
        val candidate =
            UpdateManifestFormat.parse(
                "https://updates.example.invalid/manifest.properties",
                manifest(),
            )
        val useCase =
            UpdateCheckUseCase(
                source =
                    UpdateManifestSourcePort {
                        sourceCalls += 1
                        candidate
                    },
                installedIdentity =
                    InstalledUpdateIdentityPort {
                        installedCalls += 1
                        installed(versionCode = 7L)
                    },
                expectedChannel = "stable",
            )

        val result = useCase.check()

        assertEquals(UpdateEligibility.Available(candidate), result)
        assertEquals(1, sourceCalls)
        assertEquals(1, installedCalls)
    }

    @Test
    fun `update check preserves no candidate up to date and rejection semantics`() {
        val noCandidate =
            UpdateCheckUseCase(
                source = UpdateManifestSourcePort { null },
                installedIdentity =
                    InstalledUpdateIdentityPort {
                        error("installed identity must not be read without a candidate")
                    },
                expectedChannel = "stable",
            )
        assertEquals(null, noCandidate.check())

        val upToDateCandidate =
            UpdateManifestFormat.parse(
                "https://updates.example.invalid/manifest.properties",
                manifest(versionCode = 7L, minimumSourceVersionCode = 1L),
            )
        assertEquals(
            UpdateEligibility.UpToDate,
            UpdateCheckUseCase(
                source = UpdateManifestSourcePort { upToDateCandidate },
                installedIdentity = InstalledUpdateIdentityPort { installed(7L) },
                expectedChannel = "stable",
            ).check(),
        )

        val wrongChannel =
            UpdateManifestFormat.parse(
                "https://updates.example.invalid/manifest.properties",
                manifest(channel = "beta"),
            )
        assertEquals(
            UpdateEligibility.Rejected(UpdateEligibilityRejection.CHANNEL_MISMATCH),
            UpdateCheckUseCase(
                source = UpdateManifestSourcePort { wrongChannel },
                installedIdentity = InstalledUpdateIdentityPort { installed(7L) },
                expectedChannel = "stable",
            ).check(),
        )
    }

    private fun installed(versionCode: Long): InstalledUpdateIdentity =
        InstalledUpdateIdentity(
            packageName = "ar.com.faro.v5",
            versionCode = versionCode,
            signingIdentity =
                UpdateSigningIdentity.Lineage(
                    listOf(UpdateSha256("1".repeat(64)))
                ),
        )

    private fun manifest(
        schema: Int = 1,
        channel: String = "stable",
        versionCode: Long = 8L,
        minimumSourceVersionCode: Long = 7L,
        apkUrl: String = "https://updates.example.invalid/faro-v5.apk?ref=stable",
        sha256: String = "a".repeat(64),
        sizeBytes: Long = 4096L,
        signerSha256: String? = null,
        notes: String? = null,
    ): String =
        buildList {
            add("schema=$schema")
            add("channel=$channel")
            add("packageName=ar.com.faro.v5")
            add("versionCode=$versionCode")
            add("versionName=5.0.0")
            add("minimumSourceVersionCode=$minimumSourceVersionCode")
            add("apkUrl=$apkUrl")
            add("sha256=$sha256")
            add("sizeBytes=$sizeBytes")
            signerSha256?.let { add("signingCertificateSha256=$it") }
            notes?.let { add("notes=$it") }
        }.joinToString("\n", postfix = "\n")
}
