package com.pakten.volvoobd

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothDevice
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.sin
import kotlin.random.Random

enum class Durum { YOK, BAGLANIYOR, BAGLI }

class ModulKodlari(
    val modul: String,
    val kayitli: List<String>,
    val bekleyen: List<String>,
    val kalici: List<String>,
)

class AracModel(app: Application) : AndroidViewModel(app) {
    var durum by mutableStateOf(Durum.YOK); private set
    var demo by mutableStateOf(false); private set
    var hata by mutableStateOf<String?>(null)
    val gunluk = mutableStateListOf<String>()

    val canli = mutableStateMapOf<Int, Double>()
    var turbo by mutableStateOf<Double?>(null); private set
    var destek by mutableStateOf<Set<Int>>(emptySet()); private set
    var vin by mutableStateOf(""); private set
    var adaptor by mutableStateOf(""); private set
    var protokol by mutableStateOf(""); private set
    var moduller by mutableStateOf<List<String>>(emptyList()); private set

    var kodlar by mutableStateOf<List<ModulKodlari>?>(null); private set
    var arizaLambasi by mutableStateOf<Boolean?>(null); private set
    var taraniyor by mutableStateOf(false); private set

    var kayitAktif by mutableStateOf(false); private set
    var kayitSaniye by mutableStateOf(0); private set
    var sonKayit by mutableStateOf<File?>(null); private set
    var ozet by mutableStateOf<List<String>>(emptyList()); private set
    val grafikDevir = mutableStateListOf<Float>()
    val grafikTurbo = mutableStateListOf<Float>()
    val grafikSu = mutableStateListOf<Float>()

    private var elm: Elm? = null
    private var dongu: Job? = null
    // Canli veri dongusu ile ariza taramasi ayni anda adaptoru kullanmasin (baslik degisiyor).
    private val islem = Mutex()
    private var atmosfer = 101.3
    private var yazici: PrintWriter? = null
    private var kayitBaslangic = 0L
    private val ornekler = mutableListOf<Map<String, Double>>()

    private fun yaz(s: String) {
        gunluk += s
        if (gunluk.size > 40) gunluk.removeAt(0)
    }

    // ------------------------------------------------------------ baglanti

    @SuppressLint("MissingPermission")
    fun bluetoothIleBaglan(cihaz: BluetoothDevice) =
        baglan("${cihaz.name ?: cihaz.address}") { bluetoothBaglan(cihaz) }

    fun wifiIleBaglan(adres: String, port: Int) = baglan("$adres:$port") { wifiBaglan(adres, port) }

    private fun baglan(ad: String, ac: () -> Tasima) {
        if (durum != Durum.YOK) return
        durum = Durum.BAGLANIYOR
        hata = null
        gunluk.clear()
        yaz("$ad cihazına bağlanılıyor...")
        viewModelScope.launch {
            try {
                val tasima = withContext(Dispatchers.IO) { ac() }
                val e = Elm(tasima)
                elm = e
                yaz("Bağlandı, adaptör başlatılıyor...")
                e.baslat { yaz(it) }
                adaptor = e.surum
                protokol = e.protokol
                aracBilgisiOku(e)
                durum = Durum.BAGLI
                yaz("Hazır.")
                donguBaslat()
            } catch (ex: Exception) {
                hata = ex.message ?: ex.toString()
                yaz("HATA: $hata")
                kopar()
            }
        }
    }

    private suspend fun aracBilgisiOku(e: Elm) {
        // Toplu istek: yanit veren tum OBD modullerini listele.
        e.hedef("7DF")
        moduller = e.iste("0100", 3000).keys.map { modulAdi(it) }
        yaz("Yanıt veren modüller: ${moduller.joinToString()}")

        e.hedef("7E0")
        val pidler = mutableSetOf<Int>()
        var taban = 0x00
        while (taban <= 0x60) {
            val v = motorYaniti(e.iste("01%02X".format(taban))) ?: break
            if (v.size < 6 || v[0] != 0x41) break
            for (i in 0 until 32) {
                if ((v[2 + i / 8] shr (7 - i % 8)) and 1 == 1) pidler += taban + i + 1
            }
            if (taban + 0x20 !in pidler) break
            taban += 0x20
        }
        destek = pidler
        yaz("Desteklenen canlı veri sayısı: ${PIDLER.count { it.kod in pidler }}/${PIDLER.size}")

        if (0x33 in pidler) pidOku(e, PIDLER.first { it.kod == 0x33 })?.let { atmosfer = it }

        val v = motorYaniti(e.iste("0902", 4000))
        if (v != null && v.size >= 17) {
            vin = v.takeLast(17).map { it.toChar() }.joinToString("").filter { it.isLetterOrDigit() }
            if (vin.isNotEmpty()) yaz("Şasi no: $vin")
        }
    }

    private fun motorYaniti(cevap: Map<String, IntArray>): IntArray? = cevap["7E8"] ?: cevap.values.firstOrNull()

    private suspend fun pidOku(e: Elm, p: Pid): Double? {
        val v = motorYaniti(e.iste("01%02X".format(p.kod))) ?: return null
        if (v.size < 3 || v[0] != 0x41 || v[1] != p.kod) return null
        return runCatching { p.coz(v.copyOfRange(2, v.size)) }.getOrNull()
    }

    fun demoBaslat() {
        if (durum != Durum.YOK) return
        demo = true
        gunluk.clear()
        yaz("Demo modu: sahte veri gösteriliyor, araca bağlı değil.")
        adaptor = "DEMO"; protokol = "ISO 15765-4 (CAN 11/500)"; vin = "YV1FS40DEMO000000"
        moduller = listOf("Motor (ECM)", "Şanzıman (TCM)")
        destek = PIDLER.map { it.kod }.toSet()
        durum = Durum.BAGLI
        donguBaslat()
    }

    fun kopar() {
        if (kayitAktif) kayitDurdur()
        dongu?.cancel(); dongu = null
        elm?.kapat(); elm = null
        demo = false
        durum = Durum.YOK
        canli.clear(); turbo = null
    }

    override fun onCleared() {
        kopar()
    }

    // ------------------------------------------------------------ canli veri

    private fun donguBaslat() {
        dongu?.cancel()
        dongu = viewModelScope.launch {
            val secili = PIDLER.filter { it.kod in destek && it.kod != 0x33 }
            var tur = 0
            val baslangic = System.currentTimeMillis()
            while (isActive) {
                try {
                    if (demo) {
                        demoOlcum((System.currentTimeMillis() - baslangic) / 1000.0)
                        delay(300)
                    } else {
                        val e = elm ?: break
                        islem.withLock {
                            for (p in secili) {
                                if (!p.hizli && tur % 5 != 0) continue
                                pidOku(e, p)?.let { canli[p.kod] = it }
                            }
                        }
                        delay(50)
                    }
                    canli[0x0B]?.let { turbo = (it - atmosfer) / 100.0 }
                    tur++
                    if (kayitAktif) satirYaz()
                } catch (ex: Exception) {
                    hata = "Bağlantı koptu: ${ex.message}"
                    yaz("HATA: $hata")
                    kopar()
                    break
                }
            }
        }
    }

    private fun demoOlcum(t: Double) {
        val h = (sin(t / 8) + 1) / 2
        canli[0x0C] = 800 + h * 3800 + Random.nextDouble(-40.0, 40.0)
        canli[0x0D] = h * 110
        canli[0x0B] = 35 + h * 140
        canli[0x05] = minOf(91.0, 30 + t * 2)
        canli[0x42] = 14.1 + Random.nextDouble(-0.08, 0.08)
        canli[0x04] = 18 + h * 70
        canli[0x11] = 12 + h * 75
        canli[0x10] = 3 + h * 80
        canli[0x0F] = 28 + h * 8
        canli[0x0E] = 18 - h * 10
        canli[0x06] = Random.nextDouble(-4.0, 4.0)
        canli[0x07] = 2.3
        canli[0x5C] = minOf(98.0, 30 + t * 1.5)
        canli[0x2F] = 62.0
        canli[0x46] = 18.0
    }

    // ------------------------------------------------------------ ariza kodlari

    fun kodlariTara() {
        if (taraniyor || durum != Durum.BAGLI) return
        taraniyor = true
        hata = null
        viewModelScope.launch {
            try {
                if (demo) {
                    delay(800)
                    arizaLambasi = true
                    kodlar = listOf(
                        ModulKodlari("Motor (ECM)", listOf("P0171"), listOf("P0455"), emptyList()),
                        ModulKodlari("Şanzıman (TCM)", emptyList(), emptyList(), emptyList()),
                    )
                } else {
                    val e = elm ?: return@launch
                    islem.withLock {
                        e.hedef("7E0")
                        motorYaniti(e.iste("0101"))?.let { v ->
                            if (v.size >= 3 && v[0] == 0x41) arizaLambasi = (v[2] and 0x80) != 0
                        }
                        e.hedef("7DF")
                        val kayitli = e.iste("03", 4000)
                        val bekleyen = e.iste("07", 4000)
                        val kalici = e.iste("0A", 4000)
                        e.hedef("7E0")
                        val tumu = (kayitli.keys + bekleyen.keys + kalici.keys).toSortedSet()
                        kodlar = tumu.map { h ->
                            ModulKodlari(
                                modulAdi(h),
                                kayitli[h]?.let { dtcListesi(it, e.can) }.orEmpty(),
                                bekleyen[h]?.let { dtcListesi(it, e.can) }.orEmpty(),
                                kalici[h]?.let { dtcListesi(it, e.can) }.orEmpty(),
                            )
                        }
                    }
                }
            } catch (ex: Exception) {
                hata = "Tarama başarısız: ${ex.message}"
            } finally {
                taraniyor = false
            }
        }
    }

    fun kodlariSil() {
        if (taraniyor || durum != Durum.BAGLI) return
        taraniyor = true
        viewModelScope.launch {
            try {
                if (!demo) {
                    val e = elm ?: return@launch
                    islem.withLock {
                        e.hedef("7DF")
                        e.iste("04", 5000)
                        e.hedef("7E0")
                    }
                }
            } catch (ex: Exception) {
                hata = "Silme başarısız: ${ex.message}"
            } finally {
                taraniyor = false
            }
            kodlariTara()
        }
    }

    // ------------------------------------------------------------ kayit

    private val kolonlar get() = PIDLER.filter { it.kod in destek }

    fun kayitBaslat() {
        if (kayitAktif || durum != Durum.BAGLI) return
        val klasor = File(getApplication<Application>().getExternalFilesDir(null), "kayitlar").apply { mkdirs() }
        val ad = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val dosya = File(klasor, "kayit_$ad${if (demo) "_demo" else ""}.csv")
        yazici = PrintWriter(dosya.bufferedWriter()).apply {
            println((listOf("zaman", "saniye") + kolonlar.map { it.kolon } + "turbo_bar").joinToString(","))
        }
        sonKayit = dosya
        ornekler.clear(); grafikDevir.clear(); grafikTurbo.clear(); grafikSu.clear()
        ozet = emptyList()
        kayitBaslangic = System.currentTimeMillis()
        kayitSaniye = 0
        kayitAktif = true
    }

    private fun satirYaz() {
        val sn = (System.currentTimeMillis() - kayitBaslangic) / 1000.0
        kayitSaniye = sn.toInt()
        val satir = HashMap<String, Double>()
        canli.forEach { (k, v) -> satir["$k"] = v }
        turbo?.let { satir["turbo"] = it }
        satir["saniye"] = sn
        ornekler += satir
        val zaman = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        val degerler = kolonlar.map { p -> canli[p.kod]?.let { "%.${p.ondalik}f".format(Locale.US, it) }.orEmpty() }
        yazici?.println((listOf(zaman, "%.1f".format(Locale.US, sn)) + degerler +
            (turbo?.let { "%.2f".format(Locale.US, it) } ?: "")).joinToString(","))
        canli[0x0C]?.let { grafikDevir += it.toFloat() }
        turbo?.let { grafikTurbo += it.toFloat() }
        canli[0x05]?.let { grafikSu += it.toFloat() }
    }

    fun kayitDurdur() {
        if (!kayitAktif) return
        kayitAktif = false
        yazici?.close(); yazici = null
        ozet = degerlendir()
    }

    private fun degerlendir(): List<String> {
        fun seri(k: Int) = ornekler.mapNotNull { it["$k"] }
        val sonuc = mutableListOf<String>()
        val sure = ornekler.lastOrNull()?.get("saniye") ?: 0.0
        sonuc += "Kayıt süresi: ${(sure / 60).toInt()} dk ${(sure % 60).toInt()} sn, ${ornekler.size} ölçüm"
        val su = seri(0x05)
        if (su.isNotEmpty()) {
            val enY = su.max()
            sonuc += "Soğutma suyu en yüksek %.0f °C".format(enY) +
                if (enY > 105) "  ⚠ 105 °C üstü, soğutma sistemine baktırın!" else ""
            if (sure > 900 && enY < 80) sonuc += "⚠ 15 dk+ sürüşte su 80 °C'ye ulaşmadı: termostat açık kalmış olabilir."
        }
        ornekler.mapNotNull { it["turbo"] }.takeIf { it.isNotEmpty() }?.let {
            sonuc += "En yüksek turbo basıncı %.2f bar".format(it.max())
        }
        val uzun = seri(0x07)
        if (uzun.isNotEmpty()) {
            val ort = uzun.average()
            sonuc += "Uzun vadeli yakıt düzeltmesi ortalama %%%+.1f (normal ±%%10)".format(ort) + when {
                ort > 10 -> "  ⚠ motor fazla yakıt ekliyor: hava kaçağı / MAF / yakıt basıncı"
                ort < -10 -> "  ⚠ motor yakıt kısıyor: enjektör kaçırma / zengin karışım"
                else -> ""
            }
        }
        val volt = ornekler.filter { (it["${0x0C}"] ?: 0.0) > 600 }.mapNotNull { it["${0x42}"] }
        if (volt.isNotEmpty()) {
            sonuc += "Motor çalışırken voltaj %.1f – %.1f V".format(volt.min(), volt.max()) +
                if (volt.min() < 13.0) "  ⚠ 13 V altı: şarj dinamosu / akü kontrol" else ""
        }
        val yag = seri(0x5C)
        if (yag.isNotEmpty()) sonuc += "Motor yağı en yüksek %.0f °C".format(yag.max()) +
            if (yag.max() > 130) "  ⚠ yüksek" else ""
        return sonuc
    }
}
