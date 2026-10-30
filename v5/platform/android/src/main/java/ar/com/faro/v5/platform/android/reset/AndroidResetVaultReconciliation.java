package ar.com.faro.v5.platform.android.reset;

import android.content.Context;
import ar.com.faro.v5.maintenance.ResetPlan;
import ar.com.faro.v5.maintenance.ResetVaultReconciliationPort;
import ar.com.faro.v5.platform.android.security.AndroidKeystoreSecretStore;
import java.util.Objects;

/** Full-reset binding for device-local FARO V5 secret material only. */
public final class AndroidResetVaultReconciliation
        implements ResetVaultReconciliationPort {
    private final AndroidKeystoreSecretStore secretStore;

    public AndroidResetVaultReconciliation(Context context) {
        this.secretStore =
                new AndroidKeystoreSecretStore(
                        Objects.requireNonNull(context, "context").getApplicationContext());
    }

    @Override
    public void reconcileAfterReset(ResetPlan plan) {
        Objects.requireNonNull(plan, "plan");
        secretStore.deleteAllFaroSecrets();
    }
}
