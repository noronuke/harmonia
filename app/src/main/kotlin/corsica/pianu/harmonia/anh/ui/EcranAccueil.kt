package corsica.pianu.harmonia.anh.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import corsica.pianu.harmonia.anh.MainActivity
import corsica.pianu.harmonia.anh.R
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable
fun EcranAccueil(a: MainActivity) {
    val portee = rememberCoroutineScope()
    var fichier by remember { mutableStateOf("—") }
    LaunchedEffect(a.ecranPage, a.pagePrete) {
        if (!a.pagePrete) return@LaunchedEffect
        val j = a.moteur.appeler("accueil") ?: return@LaunchedEffect
        try { fichier = JSONObject(j).getString("fichier") } catch (_: Exception) {}
    }
    fun page(f: String) { portee.launch { a.moteur.appeler(f) } }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.colonne().fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {

            Box(Modifier.size(width = 130.dp, height = 136.dp), contentAlignment = Alignment.Center) {

                if (DA.sombre) Image(painterResource(R.drawable.logo_harmonia_mono), contentDescription = null, modifier = Modifier.requiredSize(230.dp),
                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(DA.gold))
                else Image(painterResource(R.drawable.logo_harmonia), contentDescription = null, modifier = Modifier.requiredSize(230.dp))
            }
            Text("HARMONIA", style = DA.titreBouton.copy(color = DA.gold, fontWeight = FontWeight.Medium, letterSpacing = 8.sp, fontSize = 19.sp))
            Text("ACCORDEUR & TEMPÉRAMENTS", style = DA.section.copy(color = DA.logoSous), modifier = Modifier.padding(bottom = 16.dp))
            val pret = a.pagePrete
            BoutonCarte(Icones.horloge, "Reprendre l'accord précédent", fichier) { if (pret) page("reprendre") }
            BoutonCarte(Icones.nouveau, "Nouveau fichier d'accord") { if (pret) page("nouveau") }
            BoutonCarte(Icones.dossier, "Ouvrir un fichier d'accord") { if (pret) portee.launch { a.moteur.appeler("aller", "fileList") } }
            BoutonCarte(Icones.miseAuTon, "Mise au ton") { if (pret) page("miseAuTon") }
            BoutonCarte(Icones.roue, "Réglages", "Cadran, diapason, micro") { if (pret) portee.launch { a.moteur.appeler("aller", "reglages") } }
        }
    }
}
