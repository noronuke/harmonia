package corsica.pianu.harmonia.anh.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import corsica.pianu.harmonia.anh.MainActivity
import kotlinx.coroutines.launch
import org.json.JSONArray

@Composable
fun Coquille(a: MainActivity) {
    val ecran = a.ecranPage
    BackHandler { a.retour() }
    Box(Modifier.fillMaxSize().background(DA.bg).windowInsetsPadding(WindowInsets.safeDrawing)) {
        AndroidView(factory = { a.moteur.vue }, modifier = Modifier.fillMaxSize())
        if (ecran in MainActivity.NATIFS || !a.pagePrete) {

            Box(Modifier.fillMaxSize().background(DA.bg).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}) {
                when (ecran) {
                    "fileList" -> EcranFichiers(a)
                    "reglages" -> EcranReglages(a)
                    else -> EcranAccueil(a)
                }
            }
        }
        MenuNatif(a)
    }
}

@Composable
fun EnTete(a: MainActivity, titre: String) {
    Row(Modifier.fillMaxWidth().padding(start = 6.dp, end = 16.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(24.dp)).clickable { a.retour() }.semantics { contentDescription = "Retour" }, contentAlignment = Alignment.Center) {
            Icon(Icones.retour, null, tint = DA.ink, modifier = Modifier.size(24.dp))
        }
        Text(titre, style = DA.titreEcran, modifier = Modifier.padding(start = 4.dp))
    }
}

@Composable
fun Section(texte: String) {
    Text(texte.uppercase(), style = DA.section, modifier = Modifier.padding(start = 4.dp, top = 20.dp, bottom = 8.dp))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BoutonCarte(icone: ImageVector?, titre: String, sous: String? = null, modifier: Modifier = Modifier, droite: (@Composable () -> Unit)? = null,
                appuiLong: (() -> Unit)? = null, onClick: () -> Unit) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(DA.radius)).background(DA.panel)
            .border(1.dp, DA.line, RoundedCornerShape(DA.radius))
            .combinedClickable(onLongClick = appuiLong, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (icone != null) Icon(icone, null, tint = DA.gold, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f)) {
            Text(titre, style = DA.titreBouton)
            if (!sous.isNullOrBlank()) Text(sous, style = DA.sous, modifier = Modifier.padding(top = 2.dp))
        }
        if (droite != null) droite() else Icon(Icones.chevron, null, tint = DA.ink3, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun MenuNatif(a: MainActivity) {
    val portee = rememberCoroutineScope()
    var entrees by remember { mutableStateOf<List<Pair<String, Int>>>(emptyList()) }
    LaunchedEffect(a.menuOuvert) {
        if (!a.menuOuvert) return@LaunchedEffect
        val j = a.moteur.appeler("menu") ?: return@LaunchedEffect
        val t = try { JSONArray(j) } catch (_: Exception) { return@LaunchedEffect }
        entrees = (0 until t.length()).map { i -> val o = t.getJSONObject(i); o.getString("texte") to (if (o.getString("t") == "titre") -1 else o.getInt("n")) }
    }
    AnimatedVisibility(a.menuOuvert, enter = fadeIn(), exit = fadeOut()) {
        Box(Modifier.fillMaxSize().background(Color(0x5C1F2A24)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { a.menuOuvert = false })
    }
    AnimatedVisibility(a.menuOuvert, enter = slideInHorizontally { -it }, exit = slideOutHorizontally { -it }) {
        Column(
            Modifier.fillMaxHeight().width(290.dp).background(DA.panel).border(1.dp, DA.line)
                .verticalScroll(rememberScrollState()).padding(vertical = 18.dp)
        ) {
            Text("HARMONIA", style = DA.titreBouton.copy(color = DA.gold, letterSpacing = 6.sp, fontWeight = FontWeight.Medium, fontSize = 19.sp),
                modifier = Modifier.padding(start = 22.dp, bottom = 6.dp))
            for ((texte, n) in entrees) {
                if (n < 0) Text(texte.uppercase(), style = DA.section, modifier = Modifier.padding(start = 22.dp, top = 16.dp, bottom = 4.dp))
                else Text(texte, style = DA.titreBouton.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.fillMaxWidth().clickable {
                        a.menuOuvert = false
                        portee.launch { a.moteur.appeler("menuChoisir", n) }
                    }.padding(horizontal = 22.dp, vertical = 10.dp))
            }
            Spacer(Modifier.size(12.dp))
        }
    }
}

fun Modifier.colonne(): Modifier = this.widthIn(max = 420.dp)
