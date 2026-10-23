package sk.matej.spajza.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = TerracottaLight,
    onPrimary = OnTerracottaLight,
    primaryContainer = TerracottaContainerLight,
    onPrimaryContainer = OnTerracottaContainerLight,
    secondary = MustardLight,
    onSecondary = OnMustardLight,
    secondaryContainer = MustardContainerLight,
    onSecondaryContainer = OnMustardContainerLight,
    tertiary = SageLight,
    onTertiary = OnSageLight,
    tertiaryContainer = SageContainerLight,
    onTertiaryContainer = OnSageContainerLight,
    background = CreamBackground,
    onBackground = OnCream,
    surface = CreamBackground,
    onSurface = OnCream,
    surfaceVariant = CreamVariant,
    onSurfaceVariant = OnCreamVariant,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
    surfaceContainerLowest = SurfaceContainerLowestLight,
    surfaceContainerLow = SurfaceContainerLowLight,
    surfaceContainer = SurfaceContainerLight,
    surfaceContainerHigh = SurfaceContainerHighLight,
    surfaceContainerHighest = SurfaceContainerHighestLight,
    error = ErrorLight,
    errorContainer = ErrorContainerLight,
)

private val DarkColors = darkColorScheme(
    primary = TerracottaDark,
    onPrimary = OnTerracottaDark,
    primaryContainer = TerracottaContainerDark,
    onPrimaryContainer = OnTerracottaContainerDark,
    secondary = MustardDark,
    onSecondary = OnMustardDark,
    secondaryContainer = MustardContainerDark,
    onSecondaryContainer = OnMustardContainerDark,
    tertiary = SageDark,
    onTertiary = OnSageDark,
    tertiaryContainer = SageContainerDark,
    onTertiaryContainer = OnSageContainerDark,
    background = RoastBackground,
    onBackground = OnRoast,
    surface = RoastBackground,
    onSurface = OnRoast,
    surfaceVariant = RoastVariant,
    onSurfaceVariant = OnRoastVariant,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
    surfaceContainerLowest = SurfaceContainerLowestDark,
    surfaceContainerLow = SurfaceContainerLowDark,
    surfaceContainer = SurfaceContainerDark,
    surfaceContainerHigh = SurfaceContainerHighDark,
    surfaceContainerHighest = SurfaceContainerHighestDark,
    error = ErrorDark,
    errorContainer = ErrorContainerDark,
)

/** The app theme. Dynamic color is off on purpose so the warm food palette always shows. */
@Composable
fun SpajzaTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = SpajzaTypography,
        content = content,
    )
}
