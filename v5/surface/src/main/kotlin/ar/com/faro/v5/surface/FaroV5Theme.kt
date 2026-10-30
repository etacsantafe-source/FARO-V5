package ar.com.faro.v5.surface

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

internal object FaroV5Palette {
    val Navy = Color(0xFF0B1324)
    val Gold = Color(0xFFF6C85F)
    val ColdWhite = Color(0xFFF7F9FC)
    val BlueGray = Color(0xFFD9E0EA)
    val Blue = Color(0xFF335C81)
    val BlueSoft = Color(0xFFDCEBFA)
    val DarkBlueSoft = Color(0xFF233B55)

    val LightSecondary = Color(0xFF7A5B00)
    val LightSecondaryOnContainer = Color(0xFF261B00)
    val LightTertiaryOnContainer = Color(0xFF0B2A45)
    val LightSurfaceVariant = Color(0xFFE9EDF3)
    val LightOnSurfaceVariant = Color(0xFF465267)
    val LightOutline = Color(0xFF747F92)
    val LightOutlineVariant = Color(0xFFC7CFDB)
    val LightError = Color(0xFFB3261E)
    val LightErrorContainer = Color(0xFFFFDAD6)
    val LightOnErrorContainer = Color(0xFF410002)
    val LightInverseSurface = Color(0xFF2E3545)
    val LightInverseOnSurface = Color(0xFFF2F4F8)

    val DarkPrimaryContainer = Color(0xFF34445F)
    val DarkSecondaryContainer = Color(0xFF283449)
    val DarkTertiary = Color(0xFFA8C8EA)
    val DarkOnTertiary = Color(0xFF0A3353)
    val DarkOnTertiaryContainer = Color(0xFFD6E9FF)
    val DarkSurface = Color(0xFF111B2E)
    val DarkSurfaceVariant = Color(0xFF263247)
    val DarkOutline = Color(0xFF8E99AA)
    val DarkOutlineVariant = Color(0xFF3F4B5F)
    val DarkError = Color(0xFFFFB4AB)
    val DarkOnError = Color(0xFF690005)
    val DarkErrorContainer = Color(0xFF93000A)
    val DarkInverseSurface = Color(0xFFE7EAF1)

    val LightSuccessContainer = Color(0xFFD6F5E6)
    val LightOnSuccessContainer = Color(0xFF123524)
    val LightWarningContainer = Color(0xFFFFE8B3)
    val LightOnWarningContainer = Color(0xFF3D2A00)

    val DarkSuccessContainer = Color(0xFF123B28)
    val DarkOnSuccessContainer = Color(0xFFC7F5D8)
    val DarkWarningContainer = Color(0xFF4D3900)
    val DarkOnWarningContainer = Color(0xFFFFE08A)
}

@Immutable
data class FaroV5SemanticColors(
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
)

internal val FaroV5LightSemanticColors =
    FaroV5SemanticColors(
        successContainer = FaroV5Palette.LightSuccessContainer,
        onSuccessContainer = FaroV5Palette.LightOnSuccessContainer,
        warningContainer = FaroV5Palette.LightWarningContainer,
        onWarningContainer = FaroV5Palette.LightOnWarningContainer,
    )

internal val FaroV5DarkSemanticColors =
    FaroV5SemanticColors(
        successContainer = FaroV5Palette.DarkSuccessContainer,
        onSuccessContainer = FaroV5Palette.DarkOnSuccessContainer,
        warningContainer = FaroV5Palette.DarkWarningContainer,
        onWarningContainer = FaroV5Palette.DarkOnWarningContainer,
    )

val LocalFaroV5SemanticColors =
    staticCompositionLocalOf { FaroV5LightSemanticColors }

internal val FaroV5LightColorScheme =
    lightColorScheme(
        primary = FaroV5Palette.Navy,
        onPrimary = Color.White,
        primaryContainer = FaroV5Palette.BlueGray,
        onPrimaryContainer = FaroV5Palette.Navy,
        secondary = FaroV5Palette.LightSecondary,
        onSecondary = Color.White,
        secondaryContainer = FaroV5Palette.Gold,
        onSecondaryContainer = FaroV5Palette.LightSecondaryOnContainer,
        tertiary = FaroV5Palette.Blue,
        onTertiary = Color.White,
        tertiaryContainer = FaroV5Palette.BlueSoft,
        onTertiaryContainer = FaroV5Palette.LightTertiaryOnContainer,
        background = FaroV5Palette.ColdWhite,
        onBackground = FaroV5Palette.Navy,
        surface = Color.White,
        onSurface = FaroV5Palette.Navy,
        surfaceVariant = FaroV5Palette.LightSurfaceVariant,
        onSurfaceVariant = FaroV5Palette.LightOnSurfaceVariant,
        outline = FaroV5Palette.LightOutline,
        outlineVariant = FaroV5Palette.LightOutlineVariant,
        error = FaroV5Palette.LightError,
        onError = Color.White,
        errorContainer = FaroV5Palette.LightErrorContainer,
        onErrorContainer = FaroV5Palette.LightOnErrorContainer,
        inverseSurface = FaroV5Palette.LightInverseSurface,
        inverseOnSurface = FaroV5Palette.LightInverseOnSurface,
        inversePrimary = FaroV5Palette.Gold,
    )

internal val FaroV5DarkColorScheme =
    darkColorScheme(
        primary = FaroV5Palette.Gold,
        onPrimary = FaroV5Palette.LightSecondaryOnContainer,
        primaryContainer = FaroV5Palette.DarkPrimaryContainer,
        onPrimaryContainer = FaroV5Palette.ColdWhite,
        secondary = FaroV5Palette.BlueGray,
        onSecondary = FaroV5Palette.Navy,
        secondaryContainer = FaroV5Palette.DarkSecondaryContainer,
        onSecondaryContainer = FaroV5Palette.ColdWhite,
        tertiary = FaroV5Palette.DarkTertiary,
        onTertiary = FaroV5Palette.DarkOnTertiary,
        tertiaryContainer = FaroV5Palette.DarkBlueSoft,
        onTertiaryContainer = FaroV5Palette.DarkOnTertiaryContainer,
        background = FaroV5Palette.Navy,
        onBackground = FaroV5Palette.ColdWhite,
        surface = FaroV5Palette.DarkSurface,
        onSurface = FaroV5Palette.ColdWhite,
        surfaceVariant = FaroV5Palette.DarkSurfaceVariant,
        onSurfaceVariant = FaroV5Palette.BlueGray,
        outline = FaroV5Palette.DarkOutline,
        outlineVariant = FaroV5Palette.DarkOutlineVariant,
        error = FaroV5Palette.DarkError,
        onError = FaroV5Palette.DarkOnError,
        errorContainer = FaroV5Palette.DarkErrorContainer,
        onErrorContainer = FaroV5Palette.LightErrorContainer,
        inverseSurface = FaroV5Palette.DarkInverseSurface,
        inverseOnSurface = FaroV5Palette.Navy,
        inversePrimary = FaroV5Palette.Blue,
    )

private val DefaultTypography = Typography()

private val FaroV5Typography =
    Typography(
        headlineMedium = DefaultTypography.headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = DefaultTypography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = DefaultTypography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = DefaultTypography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = DefaultTypography.titleSmall.copy(fontWeight = FontWeight.Medium),
        labelLarge = DefaultTypography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        labelMedium = DefaultTypography.labelMedium.copy(fontWeight = FontWeight.Medium),
    )

private val FaroV5Shapes =
    Shapes(
        extraSmall = RoundedCornerShape(FaroV5Corner.compact),
        small = RoundedCornerShape(FaroV5Corner.control),
        medium = RoundedCornerShape(FaroV5Corner.card),
        large = RoundedCornerShape(FaroV5Corner.bubble),
        extraLarge = RoundedCornerShape(FaroV5Corner.sheet),
    )

@Composable
fun FaroV5Theme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalMinimumInteractiveComponentSize provides FaroV5Minimums.interactiveTarget,
        LocalFaroV5SemanticColors provides
            if (darkTheme) FaroV5DarkSemanticColors else FaroV5LightSemanticColors,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) FaroV5DarkColorScheme else FaroV5LightColorScheme,
            typography = FaroV5Typography,
            shapes = FaroV5Shapes,
            content = content,
        )
    }
}
