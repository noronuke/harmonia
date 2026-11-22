package corsica.pianu.harmonia.anh

import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import corsica.pianu.harmonia.anh.ui.Coquille
import corsica.pianu.harmonia.anh.ui.ThemeHarmonia
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity(), MoteurPage.Ecouteur {

    lateinit var moteur: MoteurPage
        private set

    var ecranPage by mutableStateOf("home")
        private set
    var menuOuvert by mutableStateOf(false)
    var pagePrete by mutableStateOf(false)
        private set

    var donnees by mutableIntStateOf(0)
        private set

    private val audio by lazy { getSystemService(AudioManager::class.java) }
    private val attente = Handler(Looper.getMainLooper())
    private var aEnregistrer: String? = null

    private val choisirEnregistrement = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val texte = aEnregistrer
        aEnregistrer = null
        if (uri == null || texte == null) return@registerForActivityResult
        try {
            contentResolver.openOutputStream(uri, "wt")?.use { it.write(texte.toByteArray(Charsets.UTF_8)) }
            Toast.makeText(this, "Fichier enregistré", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Enregistrement impossible", Toast.LENGTH_LONG).show()
        }
    }

    private val choisirImport = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        val texte = try { contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } } catch (_: Exception) { null }
        if (texte == null) { Toast.makeText(this, "Fichier illisible", Toast.LENGTH_LONG).show(); return@registerForActivityResult }
        lifecycleScope.launch { moteur.appeler("importer", texte); donnees++ }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val fond = android.graphics.Color.parseColor("#F2EFE6")
        enableEdgeToEdge(SystemBarStyle.light(fond, fond), SystemBarStyle.light(fond, fond))
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        moteur = MoteurPage(this, this)
        moteur.installer()
        setContent { ThemeHarmonia { Coquille(this) } }
    }

    override fun ecran(id: String) {
        val premier = !pagePrete
        ecranPage = id
        pagePrete = true
        if (id != "tuning") menuOuvert = false
        if (premier) sauvegarder()
    }

    override fun menu() { menuOuvert = true }

    override fun enregistrer(texte: String, nom: String) { demanderEnregistrement(texte, nom) }

    fun demanderEnregistrement(texte: String, nom: String) {
        aEnregistrer = texte
        try { choisirEnregistrement.launch(nom) } catch (e: Exception) { aEnregistrer = null }
    }

    fun demanderImport() {
        try { choisirImport.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")) } catch (_: Exception) {}
    }

    fun retour() {
        if (menuOuvert) { menuOuvert = false; return }
        lifecycleScope.launch {
            val r = moteur.appeler("retour")
            if (r == "dehors" || r == null) finish()
        }
    }

    private fun sauvegarder() {
        if (!Sauvegardes.aFaire(this)) return
        lifecycleScope.launch {
            val s = moteur.appeler("stockage")
            Sauvegardes.ecrire(this@MainActivity, s)
        }
    }

    private val surAppareil = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        AudioManager.OnCommunicationDeviceChangedListener { _ -> rendreLeSon() } else null
    private val surMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        AudioManager.OnModeChangedListener { _ -> rendreLeSon() } else null
    private var enTrainDeRendre = false
    private var microOuvert = false

    private fun rendreLeSon() {
        if (enTrainDeRendre || !microOuvert) return
        enTrainDeRendre = true
        try {
            if (audio.mode == AudioManager.MODE_IN_COMMUNICATION) audio.mode = AudioManager.MODE_NORMAL
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) audio.clearCommunicationDevice()
        } catch (_: Exception) {
        } finally {
            enTrainDeRendre = false
        }
    }

    fun rendreLeSonBientot() {
        attente.post {
            microOuvert = true
            attente.postDelayed({ rendreLeSon() }, 400)
            attente.postDelayed({ rendreLeSon() }, 1500)
        }
    }

    @Deprecated("Permissions demandées par la page (micro)")
    override fun onRequestPermissionsResult(code: Int, permissions: Array<String>, resultats: IntArray) {
        @Suppress("DEPRECATION")
        super.onRequestPermissionsResult(code, permissions, resultats)
        moteur.permissionRendue(code, resultats.isNotEmpty() && resultats[0] == android.content.pm.PackageManager.PERMISSION_GRANTED)
    }

    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audio.addOnCommunicationDeviceChangedListener(mainExecutor, surAppareil!!)
            audio.addOnModeChangedListener(mainExecutor, surMode!!)
        }
        rendreLeSon()
    }

    override fun onPause() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try { audio.removeOnCommunicationDeviceChangedListener(surAppareil!!) } catch (_: Exception) {}
            try { audio.removeOnModeChangedListener(surMode!!) } catch (_: Exception) {}
        }
        attente.removeCallbacksAndMessages(null)
        super.onPause()
    }

    override fun onStop() {
        moteur.micro.arreter()
        super.onStop()
    }

    override fun onDestroy() {
        moteur.detruire()
        super.onDestroy()
    }

    companion object {

        val NATIFS = setOf("home", "fileList", "reglages")

        init { Log.i(MoteurPage.JOURNAL, "Harmonia 2.0 : écrans natifs, moteur de la page") }
    }
}
