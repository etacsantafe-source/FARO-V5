package ar.com.faro.v5.surface

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FaroV5ThemeTokensTest {
    @Test
    fun minimumInteractiveTargetIs48Dp() {
        assertEquals(48.dp, FaroV5Minimums.interactiveTarget)
    }

    @Test
    fun coreTextPairsMeetNormalTextContrast() {
        val pairs =
            listOf(
                "light primary" to (FaroV5LightColorScheme.primary to FaroV5LightColorScheme.onPrimary),
                "light primary container" to
                    (FaroV5LightColorScheme.primaryContainer to FaroV5LightColorScheme.onPrimaryContainer),
                "light secondary" to (FaroV5LightColorScheme.secondary to FaroV5LightColorScheme.onSecondary),
                "light secondary container" to
                    (FaroV5LightColorScheme.secondaryContainer to FaroV5LightColorScheme.onSecondaryContainer),
                "light tertiary" to (FaroV5LightColorScheme.tertiary to FaroV5LightColorScheme.onTertiary),
                "light tertiary container" to
                    (FaroV5LightColorScheme.tertiaryContainer to FaroV5LightColorScheme.onTertiaryContainer),
                "light background" to
                    (FaroV5LightColorScheme.background to FaroV5LightColorScheme.onBackground),
                "light surface" to (FaroV5LightColorScheme.surface to FaroV5LightColorScheme.onSurface),
                "light surface variant" to
                    (FaroV5LightColorScheme.surfaceVariant to FaroV5LightColorScheme.onSurfaceVariant),
                "light error" to (FaroV5LightColorScheme.error to FaroV5LightColorScheme.onError),
                "light error container" to
                    (FaroV5LightColorScheme.errorContainer to FaroV5LightColorScheme.onErrorContainer),
                "light inverse surface" to
                    (FaroV5LightColorScheme.inverseSurface to FaroV5LightColorScheme.inverseOnSurface),
                "light inverse action" to
                    (FaroV5LightColorScheme.inverseSurface to FaroV5LightColorScheme.inversePrimary),
                "dark primary" to (FaroV5DarkColorScheme.primary to FaroV5DarkColorScheme.onPrimary),
                "dark primary container" to
                    (FaroV5DarkColorScheme.primaryContainer to FaroV5DarkColorScheme.onPrimaryContainer),
                "dark secondary" to (FaroV5DarkColorScheme.secondary to FaroV5DarkColorScheme.onSecondary),
                "dark secondary container" to
                    (FaroV5DarkColorScheme.secondaryContainer to FaroV5DarkColorScheme.onSecondaryContainer),
                "dark tertiary" to (FaroV5DarkColorScheme.tertiary to FaroV5DarkColorScheme.onTertiary),
                "dark tertiary container" to
                    (FaroV5DarkColorScheme.tertiaryContainer to FaroV5DarkColorScheme.onTertiaryContainer),
                "dark background" to
                    (FaroV5DarkColorScheme.background to FaroV5DarkColorScheme.onBackground),
                "dark surface" to (FaroV5DarkColorScheme.surface to FaroV5DarkColorScheme.onSurface),
                "dark surface variant" to
                    (FaroV5DarkColorScheme.surfaceVariant to FaroV5DarkColorScheme.onSurfaceVariant),
                "dark error" to (FaroV5DarkColorScheme.error to FaroV5DarkColorScheme.onError),
                "dark error container" to
                    (FaroV5DarkColorScheme.errorContainer to FaroV5DarkColorScheme.onErrorContainer),
                "dark inverse surface" to
                    (FaroV5DarkColorScheme.inverseSurface to FaroV5DarkColorScheme.inverseOnSurface),
                "dark inverse action" to
                    (FaroV5DarkColorScheme.inverseSurface to FaroV5DarkColorScheme.inversePrimary),
            )

        pairs.forEach { (name, colors) ->
            assertContrast(name, colors.first, colors.second)
        }
    }

    @Test
    fun semanticStatusPairsMeetNormalTextContrast() {
        listOf(
            "light success container" to
                (FaroV5LightSemanticColors.successContainer to FaroV5LightSemanticColors.onSuccessContainer),
            "light warning container" to
                (FaroV5LightSemanticColors.warningContainer to FaroV5LightSemanticColors.onWarningContainer),
            "dark success container" to
                (FaroV5DarkSemanticColors.successContainer to FaroV5DarkSemanticColors.onSuccessContainer),
            "dark warning container" to
                (FaroV5DarkSemanticColors.warningContainer to FaroV5DarkSemanticColors.onWarningContainer),
        ).forEach { (name, colors) ->
            assertContrast(name, colors.first, colors.second)
        }
    }

    private fun assertContrast(
        name: String,
        background: Color,
        foreground: Color,
    ) {
        val contrast = contrastRatio(background, foreground)
        assertTrue(
            contrast >= 4.5f,
            "$name contrast $contrast is below 4.5:1",
        )
    }

    private fun contrastRatio(
        first: Color,
        second: Color,
    ): Float {
        val firstLuminance = first.luminance()
        val secondLuminance = second.luminance()
        val lighter = maxOf(firstLuminance, secondLuminance)
        val darker = minOf(firstLuminance, secondLuminance)
        return (lighter + 0.05f) / (darker + 0.05f)
    }
}
