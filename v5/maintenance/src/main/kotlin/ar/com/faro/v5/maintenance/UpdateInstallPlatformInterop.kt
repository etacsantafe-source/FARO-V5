package ar.com.faro.v5.maintenance

data class UpdateInstallPlatformPayload(
    val expectedInstallId: String,
    val packageName: String,
    val artifactByteCount: Long,
    val artifactSha256Hex: String,
)

object UpdateInstallPlatformInterop {
    @JvmStatic
    fun installedIdentity(
        packageName: String,
        versionCode: Long,
        multipleSigners: Boolean,
        signerSha256: List<String>,
    ): InstalledUpdateIdentity {
        require(signerSha256.isNotEmpty()) {
            "installed signing identity must contain at least one certificate"
        }
        val certificates = signerSha256.map(::UpdateSha256)
        val signingIdentity =
            if (multipleSigners) {
                UpdateSigningIdentity.MultipleSigners(certificates.toSet())
            } else {
                UpdateSigningIdentity.Lineage(certificates)
            }
        return InstalledUpdateIdentity(
            packageName = packageName,
            versionCode = versionCode,
            signingIdentity = signingIdentity,
        )
    }

    @JvmStatic
    fun artifactIdentity(
        packageName: String,
        versionCode: Long,
        versionName: String,
        multipleSigners: Boolean,
        signerSha256: List<String>,
        byteCount: Long,
        artifactSha256Hex: String,
    ): UpdateArtifactIdentity {
        require(signerSha256.isNotEmpty()) {
            "artifact signing identity must contain at least one certificate"
        }
        val certificates = signerSha256.map(::UpdateSha256)
        val signingIdentity =
            if (multipleSigners) {
                UpdateSigningIdentity.MultipleSigners(certificates.toSet())
            } else {
                UpdateSigningIdentity.Lineage(certificates)
            }
        return UpdateArtifactIdentity(
            packageName = packageName,
            versionCode = versionCode,
            versionName = versionName,
            signingIdentity = signingIdentity,
            byteCount = byteCount,
            sha256 = UpdateSha256(artifactSha256Hex),
        )
    }

    @JvmStatic
    fun sessionObservation(
        request: UpdateInstallSessionInspectionRequest,
        kind: UpdateInstallObservationKind,
        failure: UpdateFailure?,
    ): UpdateInstallSessionObservation =
        UpdateInstallSessionObservation(
            expectedInstallId = ExpectedInstallId(request.expectedInstallId),
            platformSessionIdentity = request.platformSessionIdentity,
            kind = kind,
            failure = failure,
        )

    @JvmStatic
    fun payload(expected: ExpectedInstall): UpdateInstallPlatformPayload =
        UpdateInstallPlatformPayload(
            expectedInstallId = expected.expectedInstallId.value,
            packageName = expected.packageName,
            artifactByteCount = expected.staged.artifactIdentity.byteCount,
            artifactSha256Hex = expected.staged.artifactIdentity.sha256.value,
        )

    @JvmStatic
    fun observation(
        expected: ExpectedInstall,
        platformSessionIdentity: String?,
        kind: UpdateInstallObservationKind,
        failure: UpdateFailure?,
    ): UpdateInstallSessionObservation =
        UpdateInstallSessionObservation(
            expectedInstallId = expected.expectedInstallId,
            platformSessionIdentity = platformSessionIdentity,
            kind = kind,
            failure = failure,
        )
}
