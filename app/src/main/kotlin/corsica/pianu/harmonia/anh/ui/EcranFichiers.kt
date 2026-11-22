package corsica.pianu.harmonia.anh.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import corsica.pianu.harmonia.anh.MainActivity
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private data class Fichier(val id: String, val nom: String, val sous: String, val ouvert: Boolean, val details: String)
private data class Ligne(val id: String, val nom: String, val sous: String)
private data class Fusion(val garde: Ligne, val autres: List<Ligne>)

@Composable
fun EcranFichiers(a: MainActivity) {
    val portee = rememberCoroutineScope()
    var liste by remember { mutableStateOf<List<Fichier>?>(null) }
    var relire by remember { mutableIntStateOf(0) }
    var aSupprimer by remember { mutableStateOf<Fichier?>(null) }
    var fusions by remember { mutableStateOf<List<Fusion>?>(null) }

    LaunchedEffect(relire, a.donnees) {
        val j = a.moteur.appeler("fichiers") ?: return@LaunchedEffect
        liste = try {
            val t = JSONArray(j)
            (0 until t.length()).map { i ->
                val o = t.getJSONObject(i)
                val d = ArrayList<String>()
                val hz = o.optDouble("diapason", 0.0)
                if (hz > 0) d.add(String.format(java.util.Locale.FRANCE, "%.1f Hz", hz))
                o.optString("temperament").takeIf { it.isNotBlank() }?.let { d.add(it) }
                o.optString("style").takeIf { it.isNotBlank() }?.let { d.add(it) }
                val m = o.optInt("mesures", 0)
                if (m > 0) d.add("$m mesure" + (if (m > 1) "s" else ""))
                Fichier(o.getString("id"), o.getString("nom"), o.optString("sous"), o.optBoolean("ouvert"), d.joinToString(" · "))
            }
        } catch (_: Exception) { emptyList() }
    }

    Column(Modifier.fillMaxSize()) {
        EnTete(a, "Fichiers d'accord")
        LazyColumn(
            Modifier.fillMaxWidth().weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val l = liste
            if (l != null && l.isEmpty()) item {
                Text("Aucun fichier enregistré pour l'instant. Les fichiers se créent depuis l'accueil et s'enregistrent automatiquement.",
                    style = DA.sous, modifier = Modifier.colonne().padding(8.dp))
            }
            if (l != null) items(l, key = { it.id }) { f ->
                var menu by remember { mutableStateOf(false) }
                Box(Modifier.colonne()) {
                    BoutonCarte(null, f.nom, listOf(f.sous, f.details).filter { it.isNotBlank() }.joinToString("\n"),
                        droite = { if (f.ouvert) Icon(Icones.coche, "Fichier ouvert", tint = DA.gold, modifier = Modifier.size(20.dp)) else Icon(Icones.chevron, null, tint = DA.ink3, modifier = Modifier.size(18.dp)) },
                        appuiLong = { menu = true }
                    ) { portee.launch { a.moteur.appeler("ouvrir", f.id) } }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Ouvrir", style = DA.corps) }, onClick = { menu = false; portee.launch { a.moteur.appeler("ouvrir", f.id) } })
                        DropdownMenuItem(text = { Text("Exporter…", style = DA.corps) }, onClick = {
                            menu = false
                            portee.launch {
                                val j = a.moteur.appeler("exporter", f.id) ?: return@launch
                                try { val o = JSONObject(j); a.demanderEnregistrement(o.getString("texte"), o.getString("nom")) } catch (_: Exception) {}
                            }
                        })
                        DropdownMenuItem(text = { Text("Supprimer…", style = DA.corps.copy(color = DA.red)) }, onClick = {
                            menu = false
                            if (f.ouvert) Toast.makeText(a, "Impossible de supprimer le fichier ouvert", Toast.LENGTH_SHORT).show() else aSupprimer = f
                        })
                    }
                }
            }
            item { Section("Fichiers") }
            item {
                Column(Modifier.colonne(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BoutonCarte(Icones.nouveau, "Nouveau fichier d'accord") { portee.launch { a.moteur.appeler("nouveau") } }
                    BoutonCarte(Icones.importer, "Importer…", "Fichier d'accord ou bibliothèque (.json)") { a.demanderImport() }
                    BoutonCarte(Icones.exporter, "Exporter toute la bibliothèque", "Fichiers, tempéraments, styles et réglages") {
                        portee.launch {
                            val j = a.moteur.appeler("exporterTout") ?: return@launch
                            try { val o = JSONObject(j); a.demanderEnregistrement(o.getString("texte"), o.getString("nom")) } catch (_: Exception) {}
                        }
                    }
                    BoutonCarte(Icones.fusion, "Nettoyer les fichiers…", "Le même piano en plusieurs fichiers : fusionner") {
                        portee.launch {
                            val j = a.moteur.appeler("nettoyage") ?: return@launch
                            fusions = try {
                                val t = JSONArray(j)
                                fun ligne(o: JSONObject) = Ligne(o.getString("id"), o.getString("nom"), o.optString("sous"))
                                (0 until t.length()).map { i ->
                                    val o = t.getJSONObject(i); val au = o.getJSONArray("autres")
                                    Fusion(ligne(o.getJSONObject("garde")), (0 until au.length()).map { k -> ligne(au.getJSONObject(k)) })
                                }
                            } catch (_: Exception) { emptyList() }
                        }
                    }
                    Text("Appui long sur un fichier : l'exporter ou le supprimer.", style = DA.sous.copy(color = DA.ink3), textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 16.dp))
                }
            }
        }
    }

    aSupprimer?.let { f ->
        AlertDialog(
            onDismissRequest = { aSupprimer = null },
            title = { Text("Supprimer ce fichier ?", style = DA.titreBouton) },
            text = { Text(f.nom + (if (f.sous.isNotBlank()) "\n" + f.sous else "") + "\n\nSes mesures sont perdues.", style = DA.corps) },
            confirmButton = { TextButton(onClick = {
                aSupprimer = null
                portee.launch {
                    when (a.moteur.appeler("supprimer", f.id)) {
                        "ok" -> Toast.makeText(a, "Fichier supprimé", Toast.LENGTH_SHORT).show()
                        "ouvert" -> Toast.makeText(a, "Impossible de supprimer le fichier ouvert", Toast.LENGTH_SHORT).show()
                    }
                    relire++
                }
            }) { Text("Supprimer", color = DA.red) } },
            dismissButton = { TextButton(onClick = { aSupprimer = null }) { Text("Annuler") } }
        )
    }

    fusions?.let { fs ->
        val choix = remember(fs) { mutableStateMapOf<String, Boolean>().apply { fs.forEach { g -> g.autres.forEach { put(it.id, true) } } } }
        AlertDialog(
            onDismissRequest = { fusions = null },
            title = { Text(if (fs.isEmpty()) "Nettoyer les fichiers" else "Le même piano en plusieurs fichiers", style = DA.titreBouton) },
            text = {
                if (fs.isEmpty()) Text("Rien à fusionner : pas de doublon.", style = DA.corps)
                else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item { Text("Le premier est gardé et reprend ce qui manque chez les autres (champs vides, mesures d'inharmonicité, notes).", style = DA.sous) }
                    items(fs, key = { it.garde.id }) { g ->
                        Column {
                            Text("Gardé : " + g.garde.nom, style = DA.corps)
                            if (g.garde.sous.isNotBlank()) Text(g.garde.sous, style = DA.sous)
                            g.autres.forEach { o ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = choix[o.id] == true, onCheckedChange = { choix[o.id] = it })
                                    Column { Text(o.nom, style = DA.corps); if (o.sous.isNotBlank()) Text(o.sous, style = DA.sous) }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (fs.isEmpty()) TextButton(onClick = { fusions = null }) { Text("Fermer") }
                else TextButton(onClick = {
                    val o = JSONObject()
                    fs.forEach { g -> o.put(g.garde.id, JSONArray(g.autres.filter { choix[it.id] == true }.map { it.id })) }
                    fusions = null
                    portee.launch { a.moteur.appeler("nettoyer", o.toString()); relire++ }
                }) { Text("Appliquer") }
            },
            dismissButton = { if (fs.isNotEmpty()) TextButton(onClick = { fusions = null }) { Text("Annuler") } }
        )
    }
}
