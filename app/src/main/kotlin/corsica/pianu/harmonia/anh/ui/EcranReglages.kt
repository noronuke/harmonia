package corsica.pianu.harmonia.anh.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import corsica.pianu.harmonia.anh.MainActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

@Composable
fun EcranReglages(a: MainActivity) {
    val portee = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var relire by remember { mutableIntStateOf(0) }
    suspend fun charger() {
        val j = a.moteur.appeler("reglages") ?: return
        try { val t = JSONArray(j); items = (0 until t.length()).map { t.getJSONObject(it) } } catch (_: Exception) {}
    }
    LaunchedEffect(relire) { charger() }
    LaunchedEffect(Unit) { while (true) { delay(1000); charger() } }
    fun regler(n: Int, v: String) { portee.launch { a.moteur.appeler("regler", n, v); charger() } }

    Column(Modifier.fillMaxSize()) {
        EnTete(a, "Réglages")
        LazyColumn(
            Modifier.fillMaxWidth().weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            itemsIndexed(items, key = { i, o -> i.toString() + o.optString("t") + o.optString("titre") + o.optString("texte") }) { _, o ->
                Box(Modifier.colonne().fillMaxWidth()) { Reglage(o, ::regler) }
            }
        }
    }
}

@Composable
private fun Titre(titre: String, aide: String) {
    var ouverte by remember { mutableStateOf(false) }
    Column(Modifier.padding(top = 12.dp, bottom = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(titre, style = DA.corps, modifier = Modifier.weight(1f, fill = false))
            if (aide.isNotBlank()) Box(Modifier.padding(start = 6.dp).size(32.dp).clip(RoundedCornerShape(16.dp)).clickable { ouverte = !ouverte }
                .semantics { contentDescription = "Explication" }, contentAlignment = Alignment.Center) {
                Icon(Icones.info, null, tint = if (ouverte) DA.gold else DA.ink3, modifier = Modifier.size(18.dp))
            }
        }
        if (ouverte && aide.isNotBlank()) Text(aide, style = DA.sous, modifier = Modifier.padding(top = 4.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Reglage(o: JSONObject, regler: (Int, String) -> Unit) {
    val titre = o.optString("titre")
    val aide = o.optString("aide")
    when (o.optString("t")) {
        "section" -> Section(o.optString("texte"))
        "note" -> Text(o.optString("texte"), style = DA.sous, modifier = Modifier.padding(vertical = 6.dp))
        "puces" -> Column {
            Titre(titre, aide)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val opts = o.getJSONArray("options")
                for (i in 0 until opts.length()) {
                    val p = opts.getJSONObject(i)
                    Puce(p.optString("texte"), p.optBoolean("on")) { regler(p.getInt("n"), "") }
                }
                o.optJSONObject("libre")?.let { l -> ChampNombre(titre + "·libre", l.optString("valeur"), "", Modifier.width(110.dp)) { regler(l.getInt("n"), it) } }
            }
        }
        "liste" -> Column {
            Titre(titre, aide)
            Liste(o) { regler(o.getInt("n"), it) }
        }
        "curseur" -> Column {
            Titre(titre, aide)
            val min = o.optDouble("min", 0.0).toFloat(); val max = o.optDouble("max", 1.0).toFloat(); val pas = o.optDouble("pas", 0.0).toFloat()
            var v by remember(o.optDouble("valeur")) { mutableFloatStateOf(o.optDouble("valeur", 0.0).toFloat()) }
            Slider(value = v, onValueChange = { v = it }, valueRange = min..max,
                steps = if (pas > 0) (((max - min) / pas).roundToInt() - 1).coerceAtLeast(0) else 0,
                onValueChangeFinished = { regler(o.getInt("n"), (Math.round(v / pas) * pas).toString()) })
        }
        "nombre" -> Column {
            Titre(titre, aide)
            ChampNombre(titre, o.optString("valeur"), o.optString("unite"), Modifier.width(160.dp)) { regler(o.getInt("n"), it) }
        }
        "ligne" -> Row(
            Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(DA.radiusS)).background(DA.panel)
                .border(1.dp, DA.line, RoundedCornerShape(DA.radiusS))
                .let { m -> if (o.optInt("n", -1) >= 0) m.clickable { regler(o.getInt("n"), "") } else m }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(titre, style = DA.corps, modifier = Modifier.weight(1f))
            Text(o.optString("valeur"), style = DA.chiffres)
            if (o.optInt("n", -1) >= 0) Icon(Icones.chevron, null, tint = DA.ink3, modifier = Modifier.padding(start = 6.dp).size(18.dp))
        }
        "bouton" -> OutlinedButton(onClick = { regler(o.getInt("n"), "") }, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) { Text(titre, style = DA.corps) }
    }
}

@Composable
private fun Puce(texte: String, on: Boolean, onClick: () -> Unit) {
    Text(texte, style = DA.corps.copy(color = if (on) DA.gold else DA.ink2),
        modifier = Modifier.clip(RoundedCornerShape(DA.radiusS)).background(if (on) DA.tintRose else DA.panel)
            .border(if (on) 1.5.dp else 1.dp, if (on) DA.gold else DA.line, RoundedCornerShape(DA.radiusS))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 9.dp))
}

@Composable
private fun Liste(o: JSONObject, choisir: (String) -> Unit) {
    var ouvert by remember { mutableStateOf(false) }
    val opts = o.getJSONArray("options")
    val valeur = o.optString("valeur")
    var libelle = ""
    for (i in 0 until opts.length()) if (opts.getJSONObject(i).optString("v") == valeur) libelle = opts.getJSONObject(i).optString("texte")
    Box {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(DA.radiusS)).background(DA.panel).border(1.dp, DA.line, RoundedCornerShape(DA.radiusS))
                .clickable { ouvert = true }.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(libelle, style = DA.corps, modifier = Modifier.weight(1f))
            Icon(Icones.chevron, null, tint = DA.ink2, modifier = Modifier.padding(start = 8.dp).size(18.dp).rotate(90f))
        }
        DropdownMenu(expanded = ouvert, onDismissRequest = { ouvert = false }) {
            for (i in 0 until opts.length()) {
                val p = opts.getJSONObject(i)
                DropdownMenuItem(text = { Text(p.optString("texte"), style = DA.corps.copy(color = if (p.optString("v") == valeur) DA.gold else DA.ink)) },
                    onClick = { ouvert = false; choisir(p.optString("v")) })
            }
        }
    }
}

@Composable
private fun ChampNombre(cle: String, valeur: String, unite: String, modifier: Modifier, envoyer: (String) -> Unit) {
    var texte by remember(cle) { mutableStateOf(valeur.replace('.', ',')) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = texte,
            onValueChange = { t ->
                texte = t
                val v = t.trim().replace(',', '.')
                if (v.toDoubleOrNull() != null) envoyer(v)
            },
            singleLine = true,
            textStyle = DA.corps,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = modifier
        )
        if (unite.isNotBlank()) Text(unite, style = DA.chiffres, modifier = Modifier.padding(start = 8.dp))
    }
}
