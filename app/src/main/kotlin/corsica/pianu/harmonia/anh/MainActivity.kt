package corsica.pianu.harmonia.anh

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebMessagePortCompat
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MainActivity : Activity() {

    private lateinit var vue: WebView
    private var requeteMicro: PermissionRequest? = null
    private val demandeMicro = 1
    private val demandeMicroNatif = 2
    private val micro = MicroNatif()
    private val fond = Color.parseColor("#F2EFE6")              // le fond de la page : la place des barres s'y fond
    private val audio by lazy { getSystemService(AudioManager::class.java) }
    private val attente = Handler(Looper.getMainLooper())

    /*
     * Le son des autres applis (musique au casque) ne doit pas changer quand Harmonia écoute.
     * Le navigateur intégré, à l'ouverture du micro, se déclare « en communication » (setCommunicationDevice, parfois
     * MODE_IN_COMMUNICATION) : Android traite alors le téléphone comme pendant un appel et la musique en sort altérée.
     * On rend aussitôt le réglage normal ; le micro continue d'écouter (micro du téléphone, ou celui du casque filaire).
     * Seulement pendant qu'Harmonia est à l'écran, et seulement pour les demandes de ce processus : un vrai appel
     * ou une autre appli ne sont jamais touchés.
     */
    private val surAppareil = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        AudioManager.OnCommunicationDeviceChangedListener { _ -> rendreLeSon() } else null
    private val surMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        AudioManager.OnModeChangedListener { _ -> rendreLeSon() } else null
    private var enTrainDeRendre = false
    private var microOuvert = false   // tant qu'Harmonia n'a pas ouvert le micro, on ne touche à rien

    private fun rendreLeSon() {
        if (enTrainDeRendre || !microOuvert) return
        enTrainDeRendre = true
        try {
            if (audio.mode == AudioManager.MODE_IN_COMMUNICATION) audio.mode = AudioManager.MODE_NORMAL
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audio.clearCommunicationDevice()   // n'efface que la demande de ce processus
            }
        } catch (_: Exception) {
        } finally {
            enTrainDeRendre = false
        }
    }

    /** Le micro s'ouvre un peu après l'accord de la permission : on repasse derrière, deux fois. */
    private fun rendreLeSonBientot() {
        microOuvert = true
        attente.postDelayed({ rendreLeSon() }, 400)
        attente.postDelayed({ rendreLeSon() }, 1500)
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)   // on accorde : l'écran reste allumé

        val chargeur = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        vue = WebView(this)
        vue.settings.javaScriptEnabled = true
        vue.settings.domStorageEnabled = true                    // les réglages et les fichiers d'accord (localStorage)
        vue.settings.mediaPlaybackRequiresUserGesture = false    // le son du diapason
        vue.settings.allowFileAccess = false
        vue.settings.allowContentAccess = false
        vue.addJavascriptInterface(micro, "HarmoniaMicro")      // le micro écouté par l'appli (voir MicroNatif)
        vue.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(v: WebView, r: WebResourceRequest): WebResourceResponse? =
                chargeur.shouldInterceptRequest(r.url)
        }
        vue.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(requete: PermissionRequest) {
                if (!requete.resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) {
                    requete.deny()
                    return
                }
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    requete.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE))
                    rendreLeSonBientot()
                } else {
                    requeteMicro?.deny()
                    requeteMicro = requete
                    runOnUiThread { requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), demandeMicro) }
                }
            }
        }
        vue.setBackgroundColor(fond)
        // Depuis Android 15, l'appli est dessinée jusque sous la barre de notifications : le haut de la page s'y
        // cachait et le doigt ne l'atteignait plus. On rend à la page la place des barres du système.
        val cadre = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(fond)
            addView(vue)
            setOnApplyWindowInsetsListener { v, insets -> rendreLesBarres(v, insets) }
        }
        setContentView(cadre)
        habillerLesBarres()   // après setContentView : avant, la fenêtre n'a pas encore de décor (plantage du 18/11)
        if (savedInstanceState != null) vue.restoreState(savedInstanceState)
        else vue.loadUrl("https://appassets.androidplatform.net/assets/harmonia.html")
    }

    override fun onRequestPermissionsResult(code: Int, permissions: Array<out String>, resultats: IntArray) {
        super.onRequestPermissionsResult(code, permissions, resultats)
        if (code == demandeMicroNatif) {
            micro.permissionRendue(resultats.isNotEmpty() && resultats[0] == PackageManager.PERMISSION_GRANTED)
            return
        }
        if (code != demandeMicro) return
        val r = requeteMicro ?: return
        requeteMicro = null
        if (resultats.isNotEmpty() && resultats[0] == PackageManager.PERMISSION_GRANTED) {
            r.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE))
            rendreLeSonBientot()
        } else {
            r.deny()
        }
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
            audio.removeOnCommunicationDeviceChangedListener(surAppareil!!)
            audio.removeOnModeChangedListener(surMode!!)
        }
        attente.removeCallbacksAndMessages(null)
        super.onPause()
    }

    override fun onStop() {
        micro.arreter()   // l'appli n'écoute jamais en arrière-plan (la page coupe aussi son micro quand elle est cachée)
        super.onStop()
    }

    override fun onDestroy() {
        micro.arreter()
        super.onDestroy()
    }

    /*
     * Le micro écouté par l'appli elle-même (18/11/2026). Même avec le correctif ci-dessus, le navigateur intégré passait
     * le téléphone « en communication » une fraction de seconde à chaque ouverture du micro, donc à chaque ouverture de
     * l'appli et à chaque retour dessus : la musique au casque bougeait à ce moment-là. Ici l'appli lit le micro
     * directement (source « reconnaissance vocale » : sans réduction de bruit ni gain automatique, réponse plate),
     * sans jamais toucher au mode audio du téléphone. Le son part par un canal de messages (WebMessageChannel) que la page
     * remet au fil audio de son AudioWorklet : il n'attend jamais le fil principal de la page, occupé à dessiner.
     * Morceaux de 20 ms, flottants 32 bits en base64. Si quelque chose échoue, la page reprend le micro du navigateur.
     *
     * (21/11/2026, chaîne temps réel) :
     *  - le son ne passe plus par le fil principal : WebMessagePortCompat est @AnyThread, le fil du micro poste
     *    lui-même chaque morceau (seules la création du canal et sa remise à la page restent sur le fil principal) ;
     *  - en binaire (ArrayBuffer) quand la WebView le permet (WebViewFeature.WEB_MESSAGE_ARRAY_BUFFER), sinon base64 ;
     *  - fil du micro en priorité audio (THREAD_PRIORITY_URGENT_AUDIO) ;
     *  - source : « brut » (défaut) = UNPROCESSED si le téléphone l'annonce, puis VOICE_RECOGNITION, puis MIC ;
     *    « vocale » = VOICE_RECOGNITION puis MIC (l'ancien défaut). La source retenue se lit par source().
     */
    inner class MicroNatif {
        @Volatile private var etat = "arret"          // arret | attente | ok | refuse | erreur
        @Volatile private var enreg: AudioRecord? = null
        @Volatile private var canal: WebMessagePortCompat? = null
        @Volatile private var sourceRetenue = ""
        @Volatile private var envoiBinaire = false
        private var taux = 48000
        private var voulue = "brut"

        @JavascriptInterface
        fun demarrer(t: Int): String = demarrerSource(t, "brut")

        /** source : « brut » (défaut) ou « vocale ». */
        @JavascriptInterface
        fun demarrerSource(t: Int, source: String?): String {
            taux = if (t in 8000..192000) t else 48000
            voulue = if (source == "vocale") "vocale" else "brut"
            if (!WebViewFeature.isFeatureSupported(WebViewFeature.CREATE_WEB_MESSAGE_CHANNEL) ||
                !WebViewFeature.isFeatureSupported(WebViewFeature.POST_WEB_MESSAGE) ||
                !WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_PORT_POST_MESSAGE)) return "erreur"
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                etat = "attente"
                runOnUiThread { requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), demandeMicroNatif) }
                return etat
            }
            return lancer()
        }

        @JavascriptInterface
        fun etat(): String = etat

        /** La source réellement ouverte (unprocessed, voice_recognition, mic) et le transport (binaire, base64). */
        @JavascriptInterface
        fun source(): String = sourceRetenue + (if (envoiBinaire) " · binaire" else " · base64")

        @JavascriptInterface
        fun arreter() {
            val e = enreg
            enreg = null
            try { e?.stop() } catch (_: Exception) {}
            try { e?.release() } catch (_: Exception) {}
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
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
        } catch (_: Exception) { false }

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
            val binaire = WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_ARRAY_BUFFER)
            envoiBinaire = binaire
            // le canal : un bout gardé ici, l'autre remis à la page (elle le passe à son fil audio)
            attente.post {
                if (enreg !== r) return@post
                try {
                    val bouts = WebViewCompat.createWebMessageChannel(vue)
                    canal = bouts[0]
                    WebViewCompat.postWebMessage(vue, WebMessageCompat("harmonia-micro", arrayOf(bouts[1])),
                        Uri.parse("https://appassets.androidplatform.net"))
                } catch (_: Exception) { etat = "erreur"; arreter() }
            }
            val morceau = FloatArray(maxOf(256, taux / 50))     // 20 ms
            val octets = ByteBuffer.allocate(morceau.size * 4).order(ByteOrder.LITTLE_ENDIAN)
            Thread {
                try { android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO) } catch (_: Exception) {}
                while (enreg === r) {
                    val n = try { r.read(morceau, 0, morceau.size, AudioRecord.READ_BLOCKING) } catch (_: Exception) { -1 }
                    if (n < 0) break
                    if (n == 0) continue
                    val c = canal ?: continue                   // canal pas encore remis à la page : morceau perdu, comme avant
                    octets.clear()
                    octets.asFloatBuffer().put(morceau, 0, n)
                    val message = if (binaire) WebMessageCompat(octets.array().copyOf(n * 4))
                                  else WebMessageCompat(Base64.encodeToString(octets.array(), 0, n * 4, Base64.NO_WRAP))
                    try { c.postMessage(message) } catch (_: Exception) {}   // ici, sur le fil du micro (@AnyThread)
                }
            }.apply { name = "Harmonia-micro"; isDaemon = true; start() }
            etat = "ok"
            return etat
        }
    }

    /** La place des barres du système, rendue à la page. */
    private fun rendreLesBarres(v: View, insets: WindowInsets): WindowInsets {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val b = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            v.setPadding(b.left, b.top, b.right, b.bottom)
        } else {
            @Suppress("DEPRECATION")
            v.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
        }
        return insets
    }

    /** Barres couleur de la page, icônes sombres (la page est claire). */
    @Suppress("DEPRECATION")
    private fun habillerLesBarres() {
        window.statusBarColor = fond
        window.navigationBarColor = fond
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val clair = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            window.insetsController?.setSystemBarsAppearance(clair, clair)
        } else {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        vue.saveState(outState)
    }

    @Deprecated("Retour : on quitte (la page gère elle-même ses écrans)")
    override fun onBackPressed() {
        if (vue.canGoBack()) vue.goBack() else super.onBackPressed()
    }
}
