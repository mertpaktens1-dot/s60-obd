package com.pakten.volvoobd

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

/**
 * "Claude'a gonder": ekranin anlik verisini sunucudaki yalniz-yazilir klasore koyar.
 * Sunucu tarafi nginx WebDAV PUT; dosyalar disariya servis edilmez, Claude SSH ile okur.
 */
object Gonderici {
    private const val ADRES = "https://server.pakalite.com/s60/yukle/"

    /** En fazla bu kadar bayt gonderilir (sunucu siniri 5 MB). */
    const val AZAMI = 4_500_000

    /** Gonderilen dosyanin sunucudaki adini dondurur. */
    suspend fun gonder(ekran: String, govde: String): String = gonderHam(ekran, "json", govde.toByteArray(), "application/json")

    /** Ham dosya (ornegin surus kaydi CSV'si) gonderir. */
    suspend fun gonderHam(ad: String, uzanti: String, bayt: ByteArray, tur: String): String = withContext(Dispatchers.IO) {
        val damga = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val temiz = ad.replace(Regex("[^A-Za-z0-9_-]"), "_").take(60)
        val hedefAd = "${damga}_${temiz}_%04x.$uzanti".format(Random.nextInt(0x10000))
        require(bayt.size < AZAMI) { "Veri çok büyük (${bayt.size / 1024} KB)" }
        val bag = (URL(ADRES + hedefAd).openConnection() as HttpURLConnection).apply {
            requestMethod = "PUT"
            doOutput = true
            connectTimeout = 10000
            readTimeout = 60000
            setFixedLengthStreamingMode(bayt.size)
            setRequestProperty("Content-Type", "$tur; charset=utf-8")
            val kimlik = Base64.encodeToString("${BuildConfig.YUKLEME_KULLANICI}:${BuildConfig.YUKLEME_SIFRE}".toByteArray(), Base64.NO_WRAP)
            setRequestProperty("Authorization", "Basic $kimlik")
        }
        try {
            bag.outputStream.use { it.write(bayt) }
            val kod = bag.responseCode
            if (kod !in 200..299) throw IllegalStateException("Sunucu yanıtı HTTP $kod")
            hedefAd
        } finally {
            bag.disconnect()
        }
    }
}
