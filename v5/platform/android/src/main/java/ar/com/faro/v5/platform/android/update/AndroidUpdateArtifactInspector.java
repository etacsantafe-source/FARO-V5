package ar.com.faro.v5.platform.android.update;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.os.Build;
import ar.com.faro.v5.maintenance.UpdateArtifactIdentity;
import ar.com.faro.v5.maintenance.UpdateInstallPlatformInterop;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Inspects one app-private staged APK without granting install authority. */
public final class AndroidUpdateArtifactInspector {
    private static final int BUFFER_BYTES = 64 * 1024;

    private final PackageManager packageManager;

    public AndroidUpdateArtifactInspector(Context context) {
        Context applicationContext =
                Objects.requireNonNull(context, "context").getApplicationContext();
        this.packageManager = applicationContext.getPackageManager();
    }

    public UpdateArtifactIdentity inspect(File apkFile) {
        Objects.requireNonNull(apkFile, "apkFile");
        File canonical;
        try {
            canonical = apkFile.getCanonicalFile();
        } catch (IOException failure) {
            throw new IllegalArgumentException("staged APK path is invalid", failure);
        }
        if (!canonical.isFile()) {
            throw new IllegalArgumentException("staged APK is missing");
        }

        PackageInfo info;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            info =
                    packageManager.getPackageArchiveInfo(
                            canonical.getAbsolutePath(),
                            PackageManager.PackageInfoFlags.of(
                                    PackageManager.GET_SIGNING_CERTIFICATES));
        } else {
            info =
                    packageManager.getPackageArchiveInfo(
                            canonical.getAbsolutePath(),
                            PackageManager.GET_SIGNING_CERTIFICATES);
        }
        if (info == null) {
            throw new IllegalArgumentException("staged APK package metadata is unreadable");
        }
        if (info.packageName == null || info.packageName.trim().isEmpty()) {
            throw new IllegalArgumentException("staged APK package identity is missing");
        }
        if (info.versionName == null || info.versionName.trim().isEmpty()) {
            throw new IllegalArgumentException("staged APK version name is missing");
        }

        SigningInfo signingInfo = info.signingInfo;
        if (signingInfo == null) {
            throw new IllegalArgumentException("staged APK signing identity is missing");
        }
        boolean multipleSigners = signingInfo.hasMultipleSigners();
        Signature[] signatures =
                multipleSigners
                        ? signingInfo.getApkContentsSigners()
                        : signingInfo.getSigningCertificateHistory();
        if (signatures == null || signatures.length == 0) {
            throw new IllegalArgumentException("staged APK signing certificates are missing");
        }

        List<String> signerDigests = new ArrayList<>(signatures.length);
        for (Signature signature : signatures) {
            signerDigests.add(sha256Hex(signature.toByteArray()));
        }

        long byteCount = canonical.length();
        if (byteCount <= 0L) {
            throw new IllegalArgumentException("staged APK is empty");
        }

        return UpdateInstallPlatformInterop.artifactIdentity(
                info.packageName,
                info.getLongVersionCode(),
                info.versionName,
                multipleSigners,
                signerDigests,
                byteCount,
                digestFile(canonical));
    }

    private static String digestFile(File file) {
        MessageDigest digest = sha256();
        byte[] buffer = new byte[BUFFER_BYTES];
        try (FileInputStream input = new FileInputStream(file)) {
            while (true) {
                int read = input.read(buffer);
                if (read < 0) {
                    break;
                }
                if (read > 0) {
                    digest.update(buffer, 0, read);
                }
            }
        } catch (IOException failure) {
            throw new IllegalArgumentException("staged APK digest could not be read", failure);
        } finally {
            java.util.Arrays.fill(buffer, (byte) 0);
        }
        return toHex(digest.digest());
    }

    private static String sha256Hex(byte[] bytes) {
        return toHex(sha256().digest(bytes));
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(String.format("%02x", value & 0xff));
        }
        return result.toString();
    }
}
