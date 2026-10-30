package ar.com.faro.v5.surface

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

/**
 * S1.A1 compile-only Compose smoke.
 *
 * This is deliberately not wired to the launcher. It proves the V5 Surface
 * toolchain and module boundary before any shell/navigation behavior exists.
 */
@Composable
fun FaroV5SurfaceToolchainSmoke() {
    MaterialTheme {
        Surface {
            Text("FARO")
        }
    }
}
