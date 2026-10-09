package com.pakten.volvoobd

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID

/** Adaptorle konusulan ham kanal: Bluetooth (SPP) ya da WiFi (TCP). */
interface Tasima {
    val giris: InputStream
    val cikis: OutputStream
    fun kapat()
}

private val SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

@SuppressLint("MissingPermission")
fun bluetoothBaglan(cihaz: BluetoothDevice): Tasima {
    val denemeler: List<() -> BluetoothSocket> = listOf(
        { cihaz.createRfcommSocketToServiceRecord(SPP) },
        { cihaz.createInsecureRfcommSocketToServiceRecord(SPP) },
        // Bazi ucuz ELM327 klonlari SDP kaydi yayinlamaz; dogrudan 1. kanala baglanilir.
        {
            cihaz.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                .invoke(cihaz, 1) as BluetoothSocket
        },
    )
    var sonHata: Exception? = null
    for (olustur in denemeler) {
        var soket: BluetoothSocket? = null
        try {
            soket = olustur()
            soket.connect()
            val s = soket
            return object : Tasima {
                override val giris: InputStream = s.inputStream
                override val cikis: OutputStream = s.outputStream
                override fun kapat() { runCatching { s.close() } }
            }
        } catch (e: Exception) {
            runCatching { soket?.close() }
            sonHata = e
        }
    }
    throw IOException("Bluetooth baglantisi kurulamadi: ${sonHata?.message}", sonHata)
}

fun wifiBaglan(adres: String, port: Int): Tasima {
    val s = Socket()
    s.connect(InetSocketAddress(adres, port), 5000)
    return object : Tasima {
        override val giris: InputStream = s.getInputStream()
        override val cikis: OutputStream = s.getOutputStream()
        override fun kapat() { runCatching { s.close() } }
    }
}

private val HEX = Regex("[0-9A-Fa-f]+")

/**
 * ELM327 komut katmani. Basliklar (ATH1) ve bosluklar (ATS1) acik calisir; boylece
 * ayni istege birden fazla modul yanit verdiginde hangi yanitin hangi modulden
 * geldigi (7E8 motor, 7E9 sanziman...) ayirt edilir.
 */
class Elm(private val t: Tasima) {
    private val kilit = Mutex()
    var can = true
        private set
    var surum = ""
        private set
    var protokol = ""
        private set

    suspend fun komut(k: String, zamanAsimiMs: Long = 2500): List<String> = kilit.withLock {
        withContext(Dispatchers.IO) {
            while (t.giris.available() > 0) t.giris.read()
            t.cikis.write((k + "\r").toByteArray())
            t.cikis.flush()
            val sb = StringBuilder()
            val bitis = System.currentTimeMillis() + zamanAsimiMs
            while (System.currentTimeMillis() < bitis) {
                if (t.giris.available() > 0) {
                    val c = t.giris.read()
                    if (c < 0) throw IOException("Baglanti koptu")
                    if (c == '>'.code) break
                    sb.append(c.toChar())
                } else {
                    delay(5)
                }
            }
            sb.toString().split('\r', '\n')
                .map { it.trim() }
                .filter { it.isNotEmpty() && it.replace(" ", "") != k && !it.startsWith("SEARCHING") }
        }
    }

    suspend fun baslat(gunluk: (String) -> Unit) {
        komut("ATZ", 4000)
        delay(300)
        for (k in listOf("ATE0", "ATL0", "ATS1", "ATH1")) komut(k)
        surum = komut("ATI").joinToString(" ")
        gunluk("Adaptor: $surum")

        // 2017 S60: ISO 15765-4 CAN 11 bit 500 kbps (protokol 6). Once dogrudan dene, olmazsa otomatik ara.
        komut("ATSP6")
        var cevap = komut("0100", 5000)
        if (cevap.none { "41 00" in it }) {
            gunluk("Protokol 6 yanit vermedi, otomatik araniyor...")
            komut("ATSP0")
            cevap = komut("0100", 15000)
            if (cevap.none { "41 00" in it || "4100" in it.replace(" ", "") }) {
                throw IOException("Arac yanit vermedi (${cevap.joinToString(" ")}). Kontak acik mi?")
            }
        }
        val dpn = komut("ATDPN").firstOrNull().orEmpty().removePrefix("A")
        can = dpn in setOf("6", "7", "8", "9")
        protokol = komut("ATDP").firstOrNull().orEmpty()
        if (!can) komut("ATH0")
        gunluk("Protokol: $protokol")
    }

    /** CAN'de istegin gidecegi adres: 7E0 motor beyni, 7DF tum moduller (toplu). */
    suspend fun hedef(adres: String) {
        if (can) komut("ATSH$adres")
    }

    /** Istegi gonderir; yanit veren her modulun birlestirilmis veri baytlarini dondurur. */
    suspend fun iste(istek: String, zamanAsimiMs: Long = 2500): Map<String, IntArray> {
        val satirlar = komut(istek, zamanAsimiMs)
        return if (can) {
            isoTpCoz(satirlar)
        } else {
            satirlar.mapIndexedNotNull { i, s ->
                val p = s.split(' ').filter { it.isNotEmpty() }
                if (p.isNotEmpty() && p.all { it.length == 2 && it.matches(HEX) }) {
                    "ECU${i + 1}" to p.map { it.toInt(16) }.toIntArray()
                } else null
            }.toMap()
        }
    }

    fun kapat() = t.kapat()
}

/** ISO-TP cercevelerini (tekli / ilk / ardisik) modul basligina gore birlestirir. */
fun isoTpCoz(satirlar: List<String>): Map<String, IntArray> {
    val veri = LinkedHashMap<String, MutableList<Int>>()
    val beklenen = HashMap<String, Int>()
    for (s in satirlar) {
        val p = s.split(' ').filter { it.isNotEmpty() }
        if (p.size < 2 || p[0].length != 3 || !p.all { it.matches(HEX) }) continue
        val b = p.drop(1).map { it.toInt(16) }
        val h = p[0].uppercase()
        val liste = veri.getOrPut(h) { mutableListOf() }
        when (b[0] shr 4) {
            0 -> {
                liste.clear(); beklenen[h] = b[0] and 0xF; liste += b.drop(1)
            }
            1 -> if (b.size > 1) {
                liste.clear(); beklenen[h] = ((b[0] and 0xF) shl 8) + b[1]; liste += b.drop(2)
            }
            2 -> liste += b.drop(1)
        }
    }
    return veri.mapValues { (h, l) -> l.take(beklenen[h] ?: l.size).toIntArray() }
}
