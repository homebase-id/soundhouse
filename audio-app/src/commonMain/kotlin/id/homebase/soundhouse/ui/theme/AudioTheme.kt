package id.homebase.soundhouse.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import id.homebase.core.ui.theme.HomebaseFonts
import id.homebase.core.ui.theme.HomebaseTheme

// VU-meter amber: the one audio-specific colour. It marks sound happening (playing, recording, progress);
// Homebase blue stays for actions.
private val AmberLight = Color(0xFFB86E00)
private val OnAmberLight = Color(0xFFFFFFFF)
private val AmberContainerLight = Color(0xFFFFDDB3)
private val OnAmberContainerLight = Color(0xFF2B1700)
private val AmberDark = Color(0xFFF2B65A)
private val OnAmberDark = Color(0xFF452B00)
private val AmberContainerDark = Color(0xFF633F00)
private val OnAmberContainerDark = Color(0xFFFFDDB3)

/**
 * Homebase colours with an amber tertiary role, set in the brand's type pairing (design manual:
 * Montserrat Alternates for headings, Montserrat for text). Time readouts use [tabular].
 */
@Composable
fun AudioTheme(
    darkTheme: Boolean,
    followsSystemTheme: Boolean = true,
    updatesSystemChrome: Boolean = true,
    content: @Composable () -> Unit,
) {
    HomebaseTheme(darkTheme = darkTheme, followsSystemTheme = followsSystemTheme, updatesSystemChrome = updatesSystemChrome) {
        val base = MaterialTheme.colorScheme
        val colors = if (darkTheme) {
            base.copy(tertiary = AmberDark, onTertiary = OnAmberDark, tertiaryContainer = AmberContainerDark, onTertiaryContainer = OnAmberContainerDark)
        } else {
            base.copy(tertiary = AmberLight, onTertiary = OnAmberLight, tertiaryContainer = AmberContainerLight, onTertiaryContainer = OnAmberContainerLight)
        }
        MaterialTheme(colorScheme = colors, typography = brandTypography(MaterialTheme.typography, HomebaseFonts.headline, HomebaseFonts.body)) {
            content()
        }
    }
}

private fun brandTypography(base: Typography, display: FontFamily, text: FontFamily): Typography {
    fun TextStyle.display(weight: FontWeight, tracking: Double = -0.01) =
        copy(fontFamily = display, fontWeight = weight, letterSpacing = tracking.em)
    fun TextStyle.text(weight: FontWeight = FontWeight.Normal) = copy(fontFamily = text, fontWeight = weight)
    return base.copy(
        displayLarge = base.displayLarge.display(FontWeight.Light, -0.02),
        displayMedium = base.displayMedium.display(FontWeight.Light, -0.02),
        displaySmall = base.displaySmall.display(FontWeight.Light, -0.02),
        headlineLarge = base.headlineLarge.display(FontWeight.Bold),
        headlineMedium = base.headlineMedium.display(FontWeight.Bold),
        headlineSmall = base.headlineSmall.display(FontWeight.Bold),
        titleLarge = base.titleLarge.display(FontWeight.Bold, 0.0),
        titleMedium = base.titleMedium.text(FontWeight.Normal),
        titleSmall = base.titleSmall.text(FontWeight.Normal),
        bodyLarge = base.bodyLarge.text(),
        bodyMedium = base.bodyMedium.text(),
        bodySmall = base.bodySmall.text(),
        labelLarge = base.labelLarge.text(),
        labelMedium = base.labelMedium.text(),
        labelSmall = base.labelSmall.text(),
    )
}

/** Fixed-width digits so a running clock doesn't jitter. */
fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "tnum")
