package corsica.pianu.harmonia.anh

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.FileProvider
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebMessagePortCompat
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.resume

class MoteurPage(private val activite: MainActivity, private val ecouteur: Ecouteur) {

    interface Ecouteur {
        fun ecran(id: String)
        fun menu()
        fun enregistrer(texte: String, nom: String)

        fun theme(choix: String)
    }

    val vue: WebView = WebView(activite)
    val micro = MicroNatif()
    private val attente = Handler(Looper.getMainLooper())
    private var requeteMicro: PermissionRequest? = null

    private val chargeur = WebViewAssetLoader.Builder()
        .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(activite))
        .build()

    @SuppressLint("SetJavaScriptEnabled")
    fun installer() {

        WebView.setWebContentsDebuggingEnabled(true)
        vue.settings.javaScriptEnabled = true
        vue.settings.domStorageEnabled = true
        vue.settings.mediaPlaybackRequiresUserGesture = false
        vue.settings.allowFileAccess = false
        vue.settings.allowContentAccess = false
        vue.addJavascriptInterface(micro, "HarmoniaMicro")
        vue.addJavascriptInterface(Partage(), "HarmoniaPartage")
        vue.addJavascriptInterface(Pont(), "HarmoniaNatif")
        vue.setBackgroundColor(Color.parseColor(if (activite.sombre()) "#131513" else "#F2EFE6"))
        vue.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(v: WebView, r: WebResourceRequest): WebResourceResponse? =
                if (r.url.host == HOTE && r.url.path == "/assets/harmonia.html") servir() else chargeur.shouldInterceptRequest(r.url)
        }
        vue.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(requete: PermissionRequest) {
                if (!requete.resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) {
                    requete.deny()
                    return
                }
                if (activite.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    requete.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE))
                    activite.rendreLeSonBientot()
                } else {
                    requeteMicro?.deny()
                    requeteMicro = requete
                    activite.runOnUiThread { activite.requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), DEMANDE_MICRO) }
                }
            }
        }

        vue.layoutParams = android.view.ViewGroup.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT)

        vue.addOnLayoutChangeListener(object : android.view.View.OnLayoutChangeListener {
            override fun onLayoutChange(v: android.view.View, l: Int, t: Int, r: Int, b: Int, ol: Int, ot: Int, or: Int, ob: Int) {
                if (r - l > 0 && b - t > 0) { vue.removeOnLayoutChangeListener(this); vue.post { vue.loadUrl(ADRESSE) } }
            }
        })
    }

    private fun servir(): WebResourceResponse? = try {
        val page = activite.assets.open("harmonia.html").use { it.readBytes().toString(Charsets.UTF_8) }
        val pont = activite.assets.open("natif.js").use { it.readBytes().toString(Charsets.UTF_8) }
        val i = page.lastIndexOf("</body>")
        val html = if (i < 0) page + "<script>" + pont + "</script>" else page.substring(0, i) + "<script>" + pont + "</script>" + page.substring(i)
        WebResourceResponse("text/html", "utf-8", ByteArrayInputStream(html.toByteArray(Charsets.UTF_8)))
    } catch (e: Exception) {
        Log.e(JOURNAL, "page introuvable", e); null
    }

    fun permissionRendue(code: Int, oui: Boolean) {
        if (code == DEMANDE_MICRO_NATIF) { micro.permissionRendue(oui); return }
        if (code != DEMANDE_MICRO) return
        val r = requeteMicro ?: return
        requeteMicro = null
        if (oui) { r.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE)); activite.rendreLeSonBientot() } else r.deny()
    }

    suspend fun appeler(fonction: String, vararg args: Any?): String? {
        val a = args.joinToString(",") { v ->
            when (v) {
                null -> "null"
                is Number, is Boolean -> v.toString()
                else -> JSONArray().put(v.toString()).toString().let { it.substring(1, it.length - 1) }
            }
        }
        val code = "(function(){try{var H=window.__harmoniaNatif;if(!H)return null;var r=H.$fonction($a);return r==null?null:String(r)}catch(e){return 'erreur: '+e}})()"
        return suspendCancellableCoroutine { suite ->
            vue.evaluateJavascript(code) { r -> if (suite.isActive) suite.resume(lireValeur(r)) }
        }
    }

    private fun lireValeur(r: String?): String? {
        if (r == null || r == "null" || r == "undefined") return null
        return try { JSONArray("[$r]").getString(0) } catch (_: Exception) { r }
    }

    fun detruire() {
        micro.arreter()
        try { vue.destroy() } catch (_: Exception) {}
    }

    inner class Pont {
        @JavascriptInterface fun ecran(id: String) { attente.post { ecouteur.ecran(id) } }
        @JavascriptInterface fun menu() { attente.post { ecouteur.menu() } }
        @JavascriptInterface fun enregistrer(texte: String, nom: String) { attente.post { ecouteur.enregistrer(texte, nom) } }
        @JavascriptInterface fun version(): String = BuildConfigLu.versionName(activite)

        @JavascriptInterface fun nuit(): Boolean = activite.nuitSysteme()
        @JavascriptInterface fun theme(choix: String) { attente.post { ecouteur.theme(choix) } }
    }

    fun fond(sombre: Boolean) { vue.setBackgroundColor(Color.parseColor(if (sombre) "#131513" else "#F2EFE6")) }

    inner class MicroNatif {
        @Volatile private var etat = "arret"
        @Volatile private var enreg: AudioRecord? = null
        @Volatile private var canal: WebMessagePortCompat? = null
        @Volatile private var sourceRetenue = ""
        @Volatile private var envoiBinaire = false
        private var taux = 48000
        private var voulue = "brut"
        private val effets = ArrayList<android.media.audiofx.AudioEffect>()

        @JavascriptInterface
        fun demarrer(t: Int): String = demarrerSource(t, "brut")

        @JavascriptInterface
        fun demarrerSource(t: Int, source: String?): String {
            taux = if (t in 8000..192000) t else 48000
            voulue = if (source == "vocale") "vocale" else "brut"
            if (!WebViewFeature.isFeatureSupported(WebViewFeature.CREATE_WEB_MESSAGE_CHANNEL) ||
                !WebViewFeature.isFeatureSupported(WebViewFeature.POST_WEB_MESSAGE) ||
                !WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_PORT_POST_MESSAGE)) return "erreur"
            if (activite.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                etat = "attente"
                activite.runOnUiThread { activite.requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), DEMANDE_MICRO_NATIF) }
                return etat
            }
            return lancer()
        }

        @JavascriptInterface
        fun etat(): String = etat

        @JavascriptInterface
        fun source(): String = sourceRetenue + (if (envoiBinaire) " · binaire" else " · base64")

        @JavascriptInterface
        fun arreter() {
            val e = enreg
            enreg = null
            try { e?.stop() } catch (_: Exception) {}
            try { e?.release() } catch (_: Exception) {}
            synchronized(effets) { effets.forEach { try { it.release() } catch (_: Exception) {} }; effets.clear() }
            val c = canal
            canal = null
            if (c != null) attente.post { try { c.close() } catch (_: Exception) {} }
            if (etat == "ok") etat = "arret"
        }

        fun permissionRendue(oui: Boolean) {
            if (etat != "attente") return
            if (oui) lancer() else etat = "refuse"
        }

        @SuppressLint("MissingPermission")
        private fun ouvrir(source: Int): AudioRecord? = try {
            val min = AudioRecord.getMinBufferSize(taux, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT)
            val r = AudioRecord.Builder()
                .setAudioSource(source)
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(taux).setChannelMask(AudioFormat.CHANNEL_IN_MONO).build())
                .setBufferSizeInBytes(maxOf(min * 2, taux / 5 * 4))
                .build()
            if (r.state == AudioRecord.STATE_INITIALIZED) r else { r.release(); null }
        } catch (_: Exception) { null }

        private fun brutAnnonce(): Boolean = try {
            val am = activite.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
        } catch (_: Exception) { false }

        private fun couperTraitements(session: Int) {
            val coupes = ArrayList<String>()
            synchronized(effets) {
                try { if (android.media.audiofx.AutomaticGainControl.isAvailable()) android.media.audiofx.AutomaticGainControl.create(session)?.let { if (it.enabled) { it.enabled = false; coupes.add("gain auto") }; effets.add(it) } } catch (_: Exception) {}
                try { if (android.media.audiofx.NoiseSuppressor.isAvailable()) android.media.audiofx.NoiseSuppressor.create(session)?.let { if (it.enabled) { it.enabled = false; coupes.add("anti-bruit") }; effets.add(it) } } catch (_: Exception) {}
                try { if (android.media.audiofx.AcousticEchoCanceler.isAvailable()) android.media.audiofx.AcousticEchoCanceler.create(session)?.let { if (it.enabled) { it.enabled = false; coupes.add("anti-écho") }; effets.add(it) } } catch (_: Exception) {}
            }
            Log.i(JOURNAL, "micro : source $sourceRetenue, traitements coupés : " + (if (coupes.isEmpty()) "aucun n'était actif" else coupes.joinToString(", ")))
        }

        @Synchronized
        private fun lancer(): String {
            arreter()
            val ordre = ArrayList<Pair<Int, String>>()
            if (voulue == "brut" && brutAnnonce()) ordre.add(MediaRecorder.AudioSource.UNPROCESSED to "unprocessed")
            ordre.add(MediaRecorder.AudioSource.VOICE_RECOGNITION to "voice_recognition")
            ordre.add(MediaRecorder.AudioSource.MIC to "mic")
            var ouvert: AudioRecord? = null
            for ((src, nom) in ordre) {
                val o = ouvrir(src) ?: continue
                try { o.startRecording() } catch (_: Exception) { o.release(); continue }
                if (o.recordingState != AudioRecord.RECORDSTATE_RECORDING) { o.release(); continue }
                ouvert = o; sourceRetenue = nom; break
            }
            val r: AudioRecord = ouvert ?: run { etat = "erreur"; return etat }
            enreg = r
            couperTraitements(r.audioSessionId)
            val binaire = WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_ARRAY_BUFFER)
            envoiBinaire = binaire
            attente.post {
                if (enreg !== r) return@post
                try {
                    val bouts = WebViewCompat.createWebMessageChannel(vue)
                    canal = bouts[0]
                    WebViewCompat.postWebMessage(vue, WebMessageCompat("harmonia-micro", arrayOf(bouts[1])), Uri.parse("https://$HOTE"))
                } catch (_: Exception) { etat = "erreur"; arreter() }
            }
            val morceau = FloatArray(maxOf(256, taux / 50))
            val octets = ByteBuffer.allocate(morceau.size * 4).order(ByteOrder.LITTLE_ENDIAN)
            Thread {
                try { android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO) } catch (_: Exception) {}
                while (enreg === r) {
                    val n = try { r.read(morceau, 0, morceau.size, AudioRecord.READ_BLOCKING) } catch (_: Exception) { -1 }
                    if (n < 0) break
                    if (n == 0) continue
                    val c = canal ?: continue
                    octets.clear()
                    octets.asFloatBuffer().put(morceau, 0, n)
                    val message = if (binaire) WebMessageCompat(octets.array().copyOf(n * 4))
                                  else WebMessageCompat(Base64.encodeToString(octets.array(), 0, n * 4, Base64.NO_WRAP))
                    try { c.postMessage(message) } catch (_: Exception) {}
                }
            }.apply { name = "Harmonia-micro"; isDaemon = true; start() }
            etat = "ok"
            return etat
        }
    }

    inner class Partage {
        @JavascriptInterface
        fun partagerImage(base64: String, nom: String, texte: String): String {
            try {
                val octets = Base64.decode(base64, Base64.DEFAULT)
                if (octets.size < 8 || octets[0] != 0x89.toByte() || octets[1] != 0x50.toByte() ||
                    octets[2] != 0x4E.toByte() || octets[3] != 0x47.toByte()
                ) return "refusé"
                val titre = nom.removeSuffix(".png").ifBlank { "Harmonia" }
                val fichier = File(dossier(), nomFichier(titre) + ".png")
                fichier.writeBytes(octets)
                return envoyer(fichier, "image/png", titre, texte)
            } catch (e: Throwable) {
                return "échec : " + e.message
            }
        }

        @JavascriptInterface
        fun partagerFichier(texte: String, nom: String, genre: String, sujet: String): String {
            try {
                val mime = when (genre) { "text/csv" -> "text/csv"; "text/plain" -> "text/plain"; else -> return "refusé" }
                if (texte.length > 2_000_000) return "trop gros"
                val ext = if (mime == "text/csv") ".csv" else ".txt"
                val corps = if (mime == "text/csv" && !texte.startsWith("﻿")) "﻿" + texte else texte
                val titre = nom.removeSuffix(ext).ifBlank { "Harmonia" }
                val fichier = File(dossier(), nomFichier(titre) + ext)
                fichier.writeText(corps, Charsets.UTF_8)
                return envoyer(fichier, mime, titre, sujet)
            } catch (e: Throwable) {
                return "échec : " + e.message
            }
        }

        private fun dossier(): File = File(activite.cacheDir, "partage").apply { mkdirs() }

        private fun envoyer(fichier: File, mime: String, titre: String, texte: String): String {
            val uri = FileProvider.getUriForFile(activite, "${activite.packageName}.partage", fichier)
            val envoi = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, titre)
                if (texte.isNotBlank()) putExtra(Intent.EXTRA_TEXT, texte)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            activite.runOnUiThread { activite.startActivity(Intent.createChooser(envoi, titre)) }
            return "ok"
        }

        private fun nomFichier(nom: String): String {
            val propre = nom.replace(Regex("[^A-Za-z0-9._-]+"), "-").trim('-')
            return if (propre.isBlank()) "Harmonia" else propre.take(60)
        }
    }

    companion object {
        const val HOTE = "appassets.androidplatform.net"
        const val ADRESSE = "https://$HOTE/assets/harmonia.html"
        const val JOURNAL = "Harmonia"
        const val DEMANDE_MICRO = 1
        const val DEMANDE_MICRO_NATIF = 2
    }
}

object BuildConfigLu {
    fun versionName(c: Context): String = try {
        c.packageManager.getPackageInfo(c.packageName, 0).versionName ?: ""
    } catch (_: Exception) { "" }

    fun versionCode(c: Context): Long = try {
        val i = c.packageManager.getPackageInfo(c.packageName, 0)
        if (Build.VERSION.SDK_INT >= 28) i.longVersionCode else @Suppress("DEPRECATION") i.versionCode.toLong()
    } catch (_: Exception) { 0L }
}
