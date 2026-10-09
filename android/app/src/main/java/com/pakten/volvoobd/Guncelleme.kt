package com.pakten.volvoobd

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** GitHub'daki son yayin (releases/latest) uzerinden uygulama ici guncelleme. */
object Guncelleme {
    private const val SON_SURUM = "https://api.github.com/repos/mertpaktens1-dot/s60-obd/releases/latest"

    class YeniSurum(val surum: String, val apkAdresi: String, val notlar: String, val boyut: Long)

    fun mevcutSurum(c: Context): String =
        c.packageManager.getPackageInfo(c.packageName, 0).versionName ?: "0"

    /** "1.10.0" > "1.9.2" gibi sayisal karsilastirma. */
    fun dahaYeni(aday: String, mevcut: String): Boolean {
        val a = aday.trimStart('v', 'V').split('.').map { it.toIntOrNull() ?: 0 }
        val m = mevcut.trimStart('v', 'V').split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, m.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = m.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** Yeni surum varsa dondurur; yoksa ya da ag yoksa null. */
    suspend fun kontrol(c: Context): YeniSurum? = withContext(Dispatchers.IO) {
        val bag = (URL(SON_SURUM).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        try {
            if (bag.responseCode != 200) return@withContext null
            val o = JSONObject(bag.inputStream.bufferedReader().readText())
            val surum = o.getString("tag_name").trimStart('v', 'V')
            if (!dahaYeni(surum, mevcutSurum(c))) return@withContext null
            val dosyalar = o.getJSONArray("assets")
            for (i in 0 until dosyalar.length()) {
                val d = dosyalar.getJSONObject(i)
                if (d.getString("name").endsWith(".apk")) {
                    return@withContext YeniSurum(surum, d.getString("browser_download_url"), o.optString("body"), d.optLong("size"))
                }
            }
            null
        } finally {
            bag.disconnect()
        }
    }

    suspend fun indir(c: Context, y: YeniSurum, ilerleme: (Int) -> Unit): File = withContext(Dispatchers.IO) {
        val klasor = File(c.cacheDir, "guncelleme").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val hedef = File(klasor, "S60_OBD_${y.surum}.apk")
        val bag = (URL(y.apkAdresi).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 30000
            instanceFollowRedirects = true
        }
        try {
            if (bag.responseCode != 200) throw IllegalStateException("İndirme başarısız (HTTP ${bag.responseCode})")
            val toplam = bag.contentLengthLong.takeIf { it > 0 } ?: y.boyut
            var inen = 0L
            bag.inputStream.use { giris ->
                hedef.outputStream().use { cikis ->
                    val tampon = ByteArray(64 * 1024)
                    while (true) {
                        val n = giris.read(tampon)
                        if (n < 0) break
                        cikis.write(tampon, 0, n)
                        inen += n
                        if (toplam > 0) ilerleme((inen * 100 / toplam).toInt())
                    }
                }
            }
            hedef
        } finally {
            bag.disconnect()
        }
    }

    /** Android 8+: bu uygulamaya "bilinmeyen uygulama yukleme" izni verilmis mi. */
    fun kurulumIzniVar(c: Context) = Build.VERSION.SDK_INT < Build.VERSION_CODES.O || c.packageManager.canRequestPackageInstalls()

    fun kurulumIzniEkrani(c: Context) {
        c.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${c.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun kur(c: Context, apk: File) {
        val uri = FileProvider.getUriForFile(c, "${c.packageName}.dosya", apk)
        c.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
