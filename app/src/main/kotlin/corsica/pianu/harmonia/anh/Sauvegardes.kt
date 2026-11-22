package corsica.pianu.harmonia.anh

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Sauvegardes {
    private const val GARDER = 12

    fun dossier(c: Context): File = File(c.filesDir, "sauvegardes").apply { mkdirs() }

    fun aFaire(c: Context): Boolean {
        val p = c.getSharedPreferences("harmonia", Context.MODE_PRIVATE)
        val v = BuildConfigLu.versionCode(c)
        val dernier = p.getLong("sauvegarde_le", 0L)
        return p.getLong("sauvegarde_version", -1L) != v || System.currentTimeMillis() - dernier > 24L * 3600 * 1000
    }

    fun ecrire(c: Context, stockage: String?): File? {
        if (stockage.isNullOrBlank()) return null
        return try {
            val o = JSONObject(stockage)
            if (o.length() == 0) return null
            val v = BuildConfigLu.versionCode(c)
            val nom = "stockage-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.FRANCE).format(Date()) + "-v$v.json"
            val f = File(dossier(c), nom)
            f.writeText(JSONObject().put("version", BuildConfigLu.versionName(c)).put("le", System.currentTimeMillis()).put("stockage", o).toString(), Charsets.UTF_8)
            dossier(c).listFiles { x -> x.name.startsWith("stockage-") }?.sortedByDescending { it.name }?.drop(GARDER)?.forEach { it.delete() }
            c.getSharedPreferences("harmonia", Context.MODE_PRIVATE).edit()
                .putLong("sauvegarde_version", v).putLong("sauvegarde_le", System.currentTimeMillis()).apply()
            Log.i(MoteurPage.JOURNAL, "copie du stockage : ${f.name} (${f.length()} octets)")
            f
        } catch (e: Exception) {
            Log.w(MoteurPage.JOURNAL, "copie du stockage impossible", e); null
        }
    }
}
