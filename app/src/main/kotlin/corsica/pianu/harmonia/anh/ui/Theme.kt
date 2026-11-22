package corsica.pianu.harmonia.anh.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import corsica.pianu.harmonia.anh.R

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
object DA {
    val bg = Color(0xFFF2EFE6)
    val panel = Color(0xFFFAF8F2)
    val panel2 = Color(0xFFEBE7DA)
    val line = Color(0xFFDFDCCF)
    val line2 = Color(0xFFC9C5B6)
    val gold = Color(0xFF7D2E3A)
    val gold2 = Color(0xFF6B2432)
    val green = Color(0xFF2F7A3C)
    val red = Color(0xFFA8323F)
    val ink = Color(0xFF1F2A24)
    val ink2 = Color(0xFF4A554D)
    val ink3 = Color(0xFF7A8279)
    val tintRose = Color(0xFFEFE3E0)
    val radius = 18.dp
    val radiusS = 11.dp

    val serif = FontFamily(
        Font(R.font.cormorant, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
        Font(R.font.cormorant, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
        Font(R.font.cormorant, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700)))
    )
    val sans = FontFamily(
        Font(R.font.quicksand, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
        Font(R.font.quicksand, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
        Font(R.font.quicksand, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700)))
    )
    val mono = FontFamily(Font(R.font.plexmono, FontWeight.Normal), Font(R.font.plexmono_medium, FontWeight.Medium))

    val titreBouton = TextStyle(fontFamily = serif, fontWeight = FontWeight.Bold, fontSize = 19.sp, lineHeight = 23.sp, color = ink, fontFeatureSettings = "lnum")
    val sous = TextStyle(fontFamily = sans, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 17.5.sp, color = ink2)
    val section = TextStyle(fontFamily = mono, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 1.54.sp, color = gold2)
    val titreEcran = TextStyle(fontFamily = serif, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 28.sp, color = ink, fontFeatureSettings = "lnum")
    val corps = TextStyle(fontFamily = sans, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 21.sp, color = ink)
    val chiffres = TextStyle(fontFamily = mono, fontSize = 12.5.sp, lineHeight = 16.sp, color = ink2)
}

@Composable
fun ThemeHarmonia(contenu: @Composable () -> Unit) {
    val couleurs = lightColorScheme(
        primary = DA.gold, onPrimary = Color.White, primaryContainer = DA.tintRose, onPrimaryContainer = DA.gold2,
        secondary = DA.gold, onSecondary = Color.White, secondaryContainer = DA.tintRose, onSecondaryContainer = DA.gold2,
        background = DA.bg, onBackground = DA.ink, surface = DA.panel, onSurface = DA.ink,
        surfaceVariant = DA.panel2, onSurfaceVariant = DA.ink2, outline = DA.line2, outlineVariant = DA.line,
        surfaceContainer = DA.panel, surfaceContainerHigh = DA.panel, surfaceContainerHighest = DA.panel,
        surfaceContainerLow = DA.panel, surfaceContainerLowest = DA.panel, error = DA.red
    )
    val t = Typography(
        bodyLarge = DA.corps, bodyMedium = DA.sous, labelLarge = DA.corps.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp),
        titleLarge = DA.titreEcran, titleMedium = DA.titreBouton, labelSmall = DA.section
    )
    MaterialTheme(colorScheme = couleurs, typography = t, content = contenu)
}

object Icones {
    private fun trait(nom: String, vararg chemins: String): ImageVector {
        val b = ImageVector.Builder(name = nom, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        for (c in chemins) b.addPath(
            pathData = addPathNodes(c), fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        )
        return b.build()
    }

    private fun cercle(x: Float, y: Float, r: Float) = "M${x - r} ${y}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0"

    val horloge = trait("horloge", "M12 8v4l2.5 2.5", cercle(12f, 12f, 9f))
    val nouveau = trait("nouveau", "M14 3H6a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9z", "M14 3v6h6", "M12 12v6M9 15h6")
    val dossier = trait("dossier", "M3 7v11a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V9a2 2 0 0 0-2-2h-8L9 5H5a2 2 0 0 0-2 2z")
    val miseAuTon = trait("miseAuTon", "M10.59 7.76L7.76 10.59a2 2 0 0 0 0 2.83l2.83 2.83a2 2 0 0 0 2.83 0l2.83-2.83a2 2 0 0 0 0-2.83l-2.83-2.83a2 2 0 0 0-2.83 0z", "M12 3.5a8.5 8.5 0 1 1-8.1 5.9", "M1.2 10.8l2.7-1.4 1.35 2.7")
    val roue = trait("roue", cercle(12f, 12f, 3f), "M19 12a7 7 0 0 0-.1-1.2l2-1.5-2-3.5-2.4 1a7 7 0 0 0-2-1.2L14 3h-4l-.5 2.6a7 7 0 0 0-2 1.2l-2.4-1-2 3.5 2 1.5A7 7 0 0 0 5 12c0 .4 0 .8.1 1.2l-2 1.5 2 3.5 2.4-1a7 7 0 0 0 2 1.2L10 21h4l.5-2.6a7 7 0 0 0 2-1.2l2.4 1 2-3.5-2-1.5c.1-.4.1-.8.1-1.2z")
    val chevron = trait("chevron", "M9 6l6 6-6 6")
    val retour = trait("retour", "M15 6l-6 6 6 6")
    val croix = trait("croix", "M6 6l12 12M18 6L6 18")
    val coche = trait("coche", "M5 12.5l4.5 4.5L19 7.5")
    val importer = trait("importer", "M12 15V3", "M7 8l5-5 5 5", "M4 15v4a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-4")
    val exporter = trait("exporter", "M12 3v12", "M7 10l5 5 5-5", "M4 15v4a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-4")
    val fusion = trait("fusion", "M6 4v5a5 5 0 0 0 5 5h7", "M18 4v5a5 5 0 0 1-5 5", "M15 11l3 3-3 3")
    val info = trait("info", cercle(12f, 12f, 9f), "M12 11v6", "M12 7.5v.01")
}
