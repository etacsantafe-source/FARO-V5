package ar.com.faro.v5.platform.android.cert;

import android.os.Bundle;
import android.widget.TextView;
import androidx.fragment.app.FragmentActivity;

/** Debug-only Activity host for P6 biometric and screen-privacy certification. */
public final class FaroV5P6CertificationActivity extends FragmentActivity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        TextView view = new TextView(this);
        view.setText("FARO V5 P6 certification fixture");
        view.setContentDescription("FARO V5 P6 certification fixture");
        setContentView(view);
    }
}
