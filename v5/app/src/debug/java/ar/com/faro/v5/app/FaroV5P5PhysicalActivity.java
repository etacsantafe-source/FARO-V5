package ar.com.faro.v5.app;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

/** Debug-only foreground host used by the P5 physical self-update fixture. */
public final class FaroV5P5PhysicalActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        TextView view = new TextView(this);
        view.setText("FARO V5 P5 physical update fixture");
        view.setContentDescription("FARO V5 P5 physical update fixture");
        setContentView(view);
    }
}
