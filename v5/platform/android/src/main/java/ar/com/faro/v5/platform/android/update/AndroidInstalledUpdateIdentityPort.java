package ar.com.faro.v5.platform.android.update;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.os.Build;
import ar.com.faro.v5.maintenance.InstalledUpdateIdentity;
import ar.com.faro.v5.maintenance.InstalledUpdateIdentityPort;
import ar.com.faro.v5.maintenance.UpdateInstallPlatformInterop;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Reads FARO's currently installed package/version/signing identity for update reconciliation. */
public final class AndroidInstalledUpdateIdentityPort implements InstalledUpdateIdentityPort {
    private final PackageManager packageManager;
    private final String packageName;

    public AndroidInstalledUpdateIdentityPort(Context context) {
        Context applicationContext =
                Objects.requireNonNull(context, "context").getApplicationContext();
        this.packageManager = applicationContext.getPackageManager();
        this.packageName = applicationContext.getPackageName();
    }

    @Override
    public InstalledUpdateIdentity current() {
        final PackageInfo info;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                info =
                        packageManager.getPackageInfo(
                                packageName,
                                PackageManager.PackageInfoFlags.of(
                                        PackageManager.GET_SIGNING_CERTIFICATES));
            } else {
                info =
                        packageManager.getPackageInfo(
                                packageName,
                                PackageManager.GET_SIGNING_CERTIFICATES);
            }
        } catch (PackageManager.NameNotFoundException impossibleForOwnPackage) {
            throw new IllegalStateException(
                    "installed FARO package identity is unavailable",
                    impossibleForOwnPackage);
        }

        SigningInfo signingInfo = info.signingInfo;
        if (signingInfo == null) {
            throw new IllegalStateException("installed FARO signing identity is unavailable");
        }

        boolean multipleSigners = signingInfo.hasMultipleSigners();
        Signature[] signatures =
                multipleSigners
                        ? signingInfo.getApkContentsSigners()
                        : signingInfo.getSigningCertificateHistory();
        if (signatures == null || signatures.length == 0) {
            throw new IllegalStateException("installed FARO signing certificates are unavailable");
        }

        List<String> digests = new ArrayList<>(signatures.length);
        for (Signature signature : signatures) {
            digests.add(sha256Hex(signature.toByteArray()));
        }

        return UpdateInstallPlatformInterop.installedIdentity(
                packageName,
                info.getLongVersionCode(),
                multipleSigners,
                digests);
    }

    private static String sha256Hex(byte[] bytes) {
        final byte[] digest;
        try {
            digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }

        StringBuilder hex = new StringBuilder(digest.length * 2);
        for (byte value : digest) {
            hex.append(String.format("%02x", value & 0xff));
        }
        return hex.toString();
    }
}
