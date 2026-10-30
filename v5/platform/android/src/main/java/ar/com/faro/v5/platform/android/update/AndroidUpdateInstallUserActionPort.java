package ar.com.faro.v5.platform.android.update;

import android.content.Intent;

/**
 * Process-local UI boundary for PackageInstaller STATUS_PENDING_USER_ACTION.
 *
 * <p>The implementation may launch immediately only while FARO is foreground; otherwise it should
 * post a user-initiated notification. False means the user action could not be surfaced safely.
 */
public interface AndroidUpdateInstallUserActionPort {
    boolean handle(
            String expectedInstallId,
            String platformSessionIdentity,
            Intent confirmationIntent);
}
