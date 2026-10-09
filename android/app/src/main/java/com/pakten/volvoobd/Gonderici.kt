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

    /** Gonderilen dosyanin sunucudaki adini dondurur. */
    suspend fun gonder(ekran: String, govde: String): String = withContext(Dispatchers.IO) {
        val damga = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val ad = "${damga}_${ekran}_%04x.json".format(Random.nextInt(0x10000))
        val bayt = govde.toByteArray()
        require(bayt.size < 4_500_000) { "Veri çok büyük (${bayt.size / 1024} KB)" }
        val bag = (URL(ADRES + ad).openConnection() as HttpURLConnection).apply {
            requestMethod = "PUT"
            doOutput = true
            connectTimeout = 10000
            readTimeout = 20000
            setFixedLengthStreamingMode(bayt.size)
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            val kimlik = Base64.encodeToString("${BuildConfig.YUKLEME_KULLANICI}:${BuildConfig.YUKLEME_SIFRE}".toByteArray(), Base64.NO_WRAP)
            setRequestProperty("Authorization", "Basic $kimlik")
        }
        try {
            bag.outputStream.use { it.write(bayt) }
            val kod = bag.responseCode
            if (kod !in 200..299) throw IllegalStateException("Sunucu yanıtı HTTP $kod")
            ad
        } finally {
            bag.disconnect()
        }
    }
}
