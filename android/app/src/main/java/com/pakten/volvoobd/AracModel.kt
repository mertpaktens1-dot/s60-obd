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
import org.json.JSONArray
import org.json.JSONObject
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

class ModulKimlik(val modul: String, val ad: String, val kalibrasyon: List<String>, val cvn: List<String>)

class SilinmeBilgisi(
    val kmSilindiginden: Int?,
    val isinmaSilindiginden: Int?,
    val dakikaSilindiginden: Int?,
    val kmLambaYanarken: Int?,
    val dakikaLambaYanarken: Int?,
)

class DonmusKare(val kod: String, val degerler: List<Pair<String, String>>)

class ModulGecmisi(val modul: String, val yontem: String, val kodlar: List<GecmisKod>)

class TaramaKaydi(val tarih: String, val ozet: List<String>)

class VolvoBulgu(val modul: VolvoModul, val yanit: Boolean, val kimlik: String, val ham: String)

class VolvoKodSonucu(val kodlar: List<String>, val ham: String, val not: String?)

class CekisNoktasi(val devir: Float, val turbo: Float, val avans: Float, val emme: Float)

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
    var kimlikler by mutableStateOf<List<ModulKimlik>>(emptyList()); private set
    var hizliSorgu by mutableStateOf(true); private set

    // ariza ve gecmis
    var kodlar by mutableStateOf<List<ModulKodlari>?>(null); private set
    var arizaLambasi by mutableStateOf<Boolean?>(null); private set
    var taraniyor by mutableStateOf(false); private set
    var hazirlik by mutableStateOf<List<Monitor>>(emptyList()); private set
    var silinme by mutableStateOf<SilinmeBilgisi?>(null); private set
    var donmusKare by mutableStateOf<DonmusKare?>(null); private set
    var tekleme by mutableStateOf<List<Pair<Int, Pair<Int?, Int?>>>>(emptyList()); private set
    var modulGecmisi by mutableStateOf<List<ModulGecmisi>>(emptyList()); private set
    val taramaArsivi = mutableStateListOf<TaramaKaydi>()

    // kayit
    var kayitAktif by mutableStateOf(false); private set
    var kayitSaniye by mutableStateOf(0); private set
    var sonKayit by mutableStateOf<File?>(null); private set
    var ozet by mutableStateOf<List<String>>(emptyList()); private set
    val grafikDevir = mutableStateListOf<Float>()
    val grafikTurbo = mutableStateListOf<Float>()
    val grafikSu = mutableStateListOf<Float>()

    // performans
    var perfModu by mutableStateOf(false); private set
    var perfDurum by mutableStateOf("Performans modunu açın."); private set
    var sure0100 by mutableStateOf<Double?>(null); private set
    var enIyi0100 by mutableStateOf<Double?>(null); private set
    var sure80120 by mutableStateOf<Double?>(null); private set
    var enIyi80120 by mutableStateOf<Double?>(null); private set
    var tepeTurbo by mutableStateOf<Double?>(null); private set
    var tepeEmme by mutableStateOf<Double?>(null); private set
    var enDusukAvansYukte by mutableStateOf<Double?>(null); private set
    var tepeDevir by mutableStateOf<Double?>(null); private set
    var sonCekis by mutableStateOf<List<CekisNoktasi>>(emptyList()); private set
    var olcumAraligiMs by mutableStateOf(0); private set

    // Volvo modulleri (D2)
    var volvoHat by mutableStateOf(VolvoHat.HIZLI)
    var volvoMesgul by mutableStateOf(false); private set
    var volvoMesaj by mutableStateOf<String?>(null); private set
    var volvoBulgular by mutableStateOf<List<VolvoBulgu>>(emptyList()); private set
    val volvoKodlar = mutableStateMapOf<Int, VolvoKodSonucu>()
    val konsol = mutableStateListOf<String>()

    // uygulama ici guncelleme
    var yeniSurum by mutableStateOf<Guncelleme.YeniSurum?>(null); private set
    var guncellemePenceresi by mutableStateOf(false)
    var guncellemeMesaj by mutableStateOf<String?>(null); private set
    var indirmeYuzde by mutableStateOf<Int?>(null); private set
    private var inenApk: File? = null

    // otomatik baglanma / kayit
    private val ayarlar = app.getSharedPreferences("ayarlar", android.content.Context.MODE_PRIVATE)
    var otoBaglan by mutableStateOf(ayarlar.getBoolean("oto_baglan", true)); private set
    var otoKayit by mutableStateOf(ayarlar.getBoolean("oto_kayit", false)); private set
    var sonCihazAdi by mutableStateOf(ayarlar.getString("son_cihaz_adi", null)); private set
    private var elleKesildi = false
    private var otoKayitBaslatti = false
    private var devirSifirSaniye = 0.0

    // benzin kalitesi
    val benzin = BenzinTakip(File(app.filesDir, "benzin.json"))

    // Claude'a gonder
    var gonderMesaj by mutableStateOf<String?>(null); private set
    var gonderiliyor by mutableStateOf(false); private set

    private var elm: Elm? = null
    private var dongu: Job? = null
    // Canli veri dongusu ile ariza taramasi ayni anda adaptoru kullanmasin (baslik degisiyor).
    private val islem = Mutex()
    private var atmosfer = 101.3
    private var yazici: PrintWriter? = null
    private var kayitBaslangic = 0L
    private val ornekler = mutableListOf<Map<String, Double>>()
    private val arsivDosyasi = File(app.filesDir, "tarama_arsivi.json")

    init {
        arsivYukle()
        otoBaglanmaDongusu()
    }

    private fun yaz(s: String) {
        gunluk += s
        if (gunluk.size > 40) gunluk.removeAt(0)
    }

    // ------------------------------------------------------------ baglanti

    @SuppressLint("MissingPermission")
    fun bluetoothIleBaglan(cihaz: BluetoothDevice) =
        baglan("${cihaz.name ?: cihaz.address}", basarili = {
            sonCihazAdi = cihaz.name ?: cihaz.address
            ayarlar.edit().putString("son_cihaz", cihaz.address).putString("son_cihaz_adi", sonCihazAdi).apply()
        }) { bluetoothBaglan(cihaz) }

    fun wifiIleBaglan(adres: String, port: Int) = baglan("$adres:$port") { wifiBaglan(adres, port) }

    private fun baglan(ad: String, basarili: () -> Unit = {}, ac: () -> Tasima) {
        if (durum != Durum.YOK) return
        durum = Durum.BAGLANIYOR
        elleKesildi = false
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
                basarili()
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
        val yanit = e.iste("0100", 3000)
        moduller = yanit.keys.map { modulAdi(it) }
        yaz("Yanıt veren modüller: ${moduller.joinToString()}")

        // Beyin yazilim kimligi: kalibrasyon numarasi (yazilim surumu) ve CVN (yazilim imzasi).
        val adlar = e.iste("090A", 4000)
        val calid = e.iste("0904", 4000)
        val cvn = e.iste("0906", 4000)
        kimlikler = yanit.keys.map { h ->
            ModulKimlik(
                modulAdi(h),
                adlar[h]?.takeIf { it.size > 3 && it[0] == 0x49 }?.let { asciiParcalari(it, 3, 20).joinToString(" ") }.orEmpty(),
                calid[h]?.takeIf { it.size > 3 && it[0] == 0x49 }?.let { asciiParcalari(it, 3, 16) }.orEmpty(),
                cvn[h]?.takeIf { it.size > 3 && it[0] == 0x49 }?.let { v ->
                    v.drop(3).chunked(4).filter { it.size == 4 }.map { p -> p.joinToString("") { "%02X".format(it) } }
                }.orEmpty(),
            )
        }

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

        // Tek istekte 6 PID (ISO 15765-4 zorunlu kilar); desteklemeyen beyinde tekli sorguya donulur.
        hizliSorgu = e.can && cokluOku(e, listOf(0x0C, 0x0D, 0x05)).size >= 2
        yaz(if (hizliSorgu) "Hızlı (çoklu) sorgu açık." else "Hızlı sorgu desteklenmiyor, tekli sorgu kullanılacak.")
    }

    private fun motorYaniti(cevap: Map<String, IntArray>): IntArray? = cevap["7E8"] ?: cevap.values.firstOrNull()

    private suspend fun pidOku(e: Elm, p: Pid): Double? {
        val v = motorYaniti(e.iste("01%02X".format(p.kod))) ?: return null
        if (v.size < 3 || v[0] != 0x41 || v[1] != p.kod) return null
        return runCatching { p.coz(v.copyOfRange(2, v.size)) }.getOrNull()
    }

    /** En fazla 6 PID'i tek istekte okur, ham veri baytlarini dondurur. */
    private suspend fun cokluOku(e: Elm, kodlar: List<Int>): Map<Int, IntArray> {
        val v = motorYaniti(e.iste("01" + kodlar.joinToString("") { "%02X".format(it) })) ?: return emptyMap()
        return cokluCoz(v, 0x41)
    }

    private suspend fun pidleriOku(e: Elm, secim: List<Pid>) {
        if (hizliSorgu) {
            for (grup in secim.chunked(6)) {
                val ham = cokluOku(e, grup.map { it.kod })
                for (p in grup) ham[p.kod]?.let { d -> runCatching { p.coz(d) }.getOrNull()?.let { canli[p.kod] = it } }
            }
        } else {
            for (p in secim) pidOku(e, p)?.let { canli[p.kod] = it }
        }
    }

    fun demoBaslat() {
        if (durum != Durum.YOK) return
        demo = true
        gunluk.clear()
        yaz("Demo modu: sahte veri gösteriliyor, araca bağlı değil.")
        adaptor = "DEMO"; protokol = "ISO 15765-4 (CAN 11/500)"; vin = "YV1FS40DEMO000000"
        moduller = listOf("Motor (ECM)", "Şanzıman (TCM)")
        kimlikler = listOf(
            ModulKimlik("Motor (ECM)", "ECM-EngineControl", listOf("DEMO31459999AA"), listOf("1A2B3C4D")),
            ModulKimlik("Şanzıman (TCM)", "TCM-TransmissionCtl", listOf("DEMO31256666"), listOf("0F0E0D0C")),
        )
        destek = PIDLER.map { it.kod }.toSet()
        durum = Durum.BAGLI
        donguBaslat()
    }

    /** Kullanici "Baglantiyi kes" dedi: otomatik yeniden baglanma bu oturumda durur. */
    fun kullaniciKopardi() {
        elleKesildi = true
        kopar()
    }

    fun kopar() {
        if (kayitAktif) kayitDurdur()
        otoKayitBaslatti = false
        devirSifirSaniye = 0.0
        benzin.kaydet()
        dongu?.cancel(); dongu = null
        elm?.kapat(); elm = null
        demo = false
        perfModu = false
        durum = Durum.YOK
        canli.clear(); turbo = null
    }

    override fun onCleared() {
        kopar()
    }

    // ------------------------------------------------------------ canli veri

    // Performans modunda yalnizca bunlar okunur: hiz, devir, manifold, gaz, avans, emme havasi.
    private val PERF_PIDLER = listOf(0x0D, 0x0C, 0x0B, 0x11, 0x0E, 0x0F)

    private fun donguBaslat() {
        dongu?.cancel()
        dongu = viewModelScope.launch {
            var tur = 0
            val baslangic = System.currentTimeMillis()
            var oncekiZaman = System.currentTimeMillis()
            while (isActive) {
                try {
                    if (demo) {
                        demoOlcum((System.currentTimeMillis() - baslangic) / 1000.0)
                        delay(if (perfModu) 120 else 300)
                    } else {
                        val e = elm ?: break
                        islem.withLock {
                            val secili = if (perfModu) {
                                PIDLER.filter { it.kod in PERF_PIDLER && it.kod in destek }
                            } else {
                                PIDLER.filter { it.kod in destek && it.kod != 0x33 && (it.hizli || tur % 5 == 0) }
                            }
                            pidleriOku(e, secili)
                        }
                        if (!perfModu) delay(50)
                    }
                    val simdi = System.currentTimeMillis()
                    olcumAraligiMs = (simdi - oncekiZaman).toInt()
                    oncekiZaman = simdi
                    canli[0x0B]?.let { turbo = (it - atmosfer) / 100.0 }
                    tur++
                    if (perfModu) perfIsle(simdi / 1000.0)
                    if (!demo) benzin.isle(simdi / 1000.0, canli[0x0C], canli[0x11], canli[0x04], canli[0x0E], canli[0x0F])
                    otoKayitIsle(olcumAraligiMs / 1000.0)
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
        canli[0x0D] = if (h < 0.05) 0.0 else h * 130
        canli[0x0B] = 35 + h * 140
        canli[0x05] = minOf(91.0, 30 + t * 2)
        canli[0x42] = 14.1 + Random.nextDouble(-0.08, 0.08)
        canli[0x04] = 18 + h * 70
        canli[0x11] = 12 + h * 85
        canli[0x10] = 3 + h * 80
        canli[0x0F] = 28 + h * 8
        canli[0x0E] = 18 - h * 10
        canli[0x06] = Random.nextDouble(-4.0, 4.0)
        canli[0x07] = 2.3
        canli[0x5C] = minOf(98.0, 30 + t * 1.5)
        canli[0x2F] = 62.0
        canli[0x46] = 18.0
    }

    // ------------------------------------------------------------ performans

    private enum class Kronometre { BEKLE, HAZIR, OLCUYOR }

    private var k0100 = Kronometre.BEKLE
    private var bas0100 = 0.0
    private var sonDurmaZamani = 0.0
    private var bas80120: Double? = null
    private var oncekiHiz: Double? = null
    private var oncekiZamanSn = 0.0
    private val cekis = mutableListOf<CekisNoktasi>()
    private var cekisBaslangic = 0.0

    fun perfAcKapa() {
        perfModu = !perfModu
        k0100 = Kronometre.BEKLE
        bas80120 = null
        oncekiHiz = null
        cekis.clear()
        perfDurum = if (perfModu) "Aracı tam durdurun." else "Performans modunu açın."
    }

    fun tepeleriSifirla() {
        tepeTurbo = null; tepeEmme = null; enDusukAvansYukte = null; tepeDevir = null
        sure0100 = null; sure80120 = null; sonCekis = emptyList()
    }

    /** Iki ornek arasinda [esik] hizinin gecildigi ani dogrusal olarak bulur. */
    private fun kesisim(t0: Double, h0: Double, t1: Double, h1: Double, esik: Double) =
        if (h1 == h0) t1 else t0 + (esik - h0) / (h1 - h0) * (t1 - t0)

    private fun perfIsle(t: Double) {
        val hiz = canli[0x0D] ?: return
        val devir = canli[0x0C]
        val gaz = canli[0x11] ?: 0.0
        val avans = canli[0x0E]
        val emme = canli[0x0F]
        val tb = turbo

        tb?.let { if (tepeTurbo == null || it > tepeTurbo!!) tepeTurbo = it }
        emme?.let { if (tepeEmme == null || it > tepeEmme!!) tepeEmme = it }
        devir?.let { if (tepeDevir == null || it > tepeDevir!!) tepeDevir = it }
        if (gaz >= 85 && avans != null && (enDusukAvansYukte == null || avans < enDusukAvansYukte!!)) {
            enDusukAvansYukte = avans
        }

        // 0-100
        when (k0100) {
            Kronometre.BEKLE -> if (hiz == 0.0) { k0100 = Kronometre.HAZIR; sonDurmaZamani = t; perfDurum = "Hazır: gaza basın." }
            Kronometre.HAZIR -> if (hiz == 0.0) {
                sonDurmaZamani = t
            } else {
                // Hareketin son "0" ornegiyle ilk hareketli ornek arasinda basladigi varsayilir.
                bas0100 = (sonDurmaZamani + t) / 2
                k0100 = Kronometre.OLCUYOR
                perfDurum = "0-100 ölçülüyor..."
            }
            Kronometre.OLCUYOR -> {
                val ph = oncekiHiz ?: 0.0
                if (hiz >= 100) {
                    val s = kesisim(oncekiZamanSn, ph, t, hiz, 100.0) - bas0100
                    sure0100 = s
                    if (enIyi0100 == null || s < enIyi0100!!) enIyi0100 = s
                    k0100 = Kronometre.BEKLE
                    perfDurum = "0-100: %.2f sn. Yeni ölçüm için tam durun.".format(Locale.US, s)
                } else if (hiz == 0.0) {
                    k0100 = Kronometre.HAZIR; sonDurmaZamani = t; perfDurum = "İptal. Hazır: gaza basın."
                } else if (t - bas0100 > 30) {
                    k0100 = Kronometre.BEKLE; perfDurum = "Süre aşıldı, tam durun."
                }
            }
        }

        // 80-120 (vitesi sabit tutarak ara hizlanma)
        oncekiHiz?.let { ph ->
            val b = bas80120
            if (b == null && ph < 80 && hiz >= 80) bas80120 = kesisim(oncekiZamanSn, ph, t, hiz, 80.0)
            if (b != null) {
                if (ph < 120 && hiz >= 120) {
                    val s = kesisim(oncekiZamanSn, ph, t, hiz, 120.0) - b
                    sure80120 = s
                    if (enIyi80120 == null || s < enIyi80120!!) enIyi80120 = s
                    bas80120 = null
                } else if (hiz < 75 || t - b > 25) {
                    bas80120 = null
                }
            }
        }
        oncekiHiz = hiz
        oncekiZamanSn = t

        // Tam gaz cekisi: gaz %85 ustu ve 1500 rpm ustundeyken noktalari topla.
        if (gaz >= 85 && (devir ?: 0.0) > 1500 && tb != null) {
            if (cekis.isEmpty()) cekisBaslangic = t
            cekis += CekisNoktasi(devir!!.toFloat(), tb.toFloat(), (avans ?: 0.0).toFloat(), (emme ?: 0.0).toFloat())
        } else if (gaz < 70 && cekis.isNotEmpty()) {
            if (t - cekisBaslangic >= 1.5 && cekis.size >= 5) sonCekis = cekis.toList()
            cekis.clear()
        }
    }

    // ------------------------------------------------------------ ariza kodlari ve gecmis

    fun kodlariTara() {
        if (taraniyor || durum != Durum.BAGLI) return
        taraniyor = true
        hata = null
        viewModelScope.launch {
            try {
                if (demo) demoTarama() else {
                    val e = elm ?: return@launch
                    islem.withLock {
                        e.hedef("7DF")
                        val kayitli = e.iste("03", 4000)
                        val bekleyen = e.iste("07", 4000)
                        val kalici = e.iste("0A", 4000)
                        val tumu = (kayitli.keys + bekleyen.keys + kalici.keys).toSortedSet()
                        kodlar = tumu.map { h ->
                            ModulKodlari(
                                modulAdi(h),
                                kayitli[h]?.let { dtcListesi(it, e.can) }.orEmpty(),
                                bekleyen[h]?.let { dtcListesi(it, e.can) }.orEmpty(),
                                kalici[h]?.let { dtcListesi(it, e.can) }.orEmpty(),
                            )
                        }
                        e.hedef("7E0")
                        gecmisOku(e)
                    }
                }
                arsiveEkle()
            } catch (ex: Exception) {
                hata = "Tarama başarısız: ${ex.message}"
            } finally {
                runCatching { elm?.hedef("7E0") }
                taraniyor = false
            }
        }
    }

    /** Silinmis / gecmis arizalara dair ne varsa: hazirlik testleri, sayaclar, donmus kare, mod 06, UDS. */
    private suspend fun gecmisOku(e: Elm) {
        motorYaniti(e.iste("0101"))?.let { v ->
            if (v.size >= 6 && v[0] == 0x41 && v[1] == 0x01) {
                arizaLambasi = (v[2] and 0x80) != 0
                hazirlik = hazirlikCoz(v.copyOfRange(2, 6))
            }
        }

        val sayac = listOf(0x31, 0x30, 0x4E, 0x21, 0x4D).filter { it in destek }
        val ham = if (sayac.isEmpty()) emptyMap() else {
            val v = motorYaniti(e.iste("01" + sayac.joinToString("") { "%02X".format(it) }))
            v?.let { cokluCoz(it, 0x41) }.orEmpty()
        }
        fun iki(k: Int) = ham[k]?.takeIf { it.size == 2 }?.let { it[0] * 256 + it[1] }
        silinme = SilinmeBilgisi(iki(0x31), ham[0x30]?.getOrNull(0), iki(0x4E), iki(0x21), iki(0x4D))

        // Donmus kare (mod 02, kare 0): lambayi yaktiran kod ve o anki degerler.
        donmusKare = null
        val dk = motorYaniti(e.iste("020200"))?.let { cokluCoz(it, 0x42, kareBayti = true) }
        val dkKod = dk?.get(0x02)?.takeIf { it[0] != 0 || it[1] != 0 }?.let { dtcCoz(it[0], it[1]) }
        if (dkKod != null) {
            val istenen = listOf(0x0C, 0x0D, 0x05, 0x04, 0x0B, 0x06, 0x07, 0x0F, 0x11).filter { it in destek }
            val degerler = mutableListOf<Pair<String, String>>()
            for (grup in istenen.chunked(3)) {
                val istek = "02" + grup.joinToString("") { "%02X00".format(it) }
                val r = motorYaniti(e.iste(istek))?.let { cokluCoz(it, 0x42, kareBayti = true) }.orEmpty()
                for (k in grup) {
                    val p = PIDLER.first { it.kod == k }
                    r[k]?.let { d -> degerler += p.ad to "%.${p.ondalik}f %s".format(Locale.US, p.coz(d), p.birim) }
                }
            }
            donmusKare = DonmusKare(dkKod, degerler)
        }

        // Mod 06: silindir bazinda tekleme sayaclari (MID A2..A5 = silindir 1..4).
        tekleme = (0xA2..0xA5).mapNotNull { mid ->
            val t = motorYaniti(e.iste("06%02X".format(mid)))?.let { mod06Coz(it) }.orEmpty().filter { it.mid == mid }
            if (t.isEmpty()) null else (mid - 0xA1) to (t.firstOrNull { it.tid == 0x0C }?.deger to t.firstOrNull { it.tid == 0x0B }?.deger)
        }

        // Deneysel: UDS 19 02 / KWP 18 ile modulun kendi ariza hafizasi (gecmis kodlar dahil).
        val gecmis = mutableListOf<ModulGecmisi>()
        for ((adres, cevapAdresi) in listOf("7E0" to "7E8", "7E1" to "7E9")) {
            e.hedef(adres)
            val uds = e.iste("1902FF", 3000)[cevapAdresi]?.let { uds19Coz(it) }
            if (uds != null) {
                gecmis += ModulGecmisi(modulAdi(cevapAdresi), "UDS", uds); continue
            }
            val kwp = e.iste("1802FF00", 3000)[cevapAdresi]?.let { kwp18Coz(it) }
            if (kwp != null) gecmis += ModulGecmisi(modulAdi(cevapAdresi), "KWP2000", kwp)
        }
        e.hedef("7E0")
        modulGecmisi = gecmis
    }

    private suspend fun demoTarama() {
        delay(800)
        arizaLambasi = true
        kodlar = listOf(
            ModulKodlari("Motor (ECM)", listOf("P0171"), listOf("P0455"), emptyList()),
            ModulKodlari("Şanzıman (TCM)", emptyList(), emptyList(), emptyList()),
        )
        hazirlik = listOf(
            Monitor("Tekleme izleme", true), Monitor("Yakıt sistemi", true), Monitor("Bileşen izleme", true),
            Monitor("Katalizör", false), Monitor("Yakıt buharı (EVAP)", false), Monitor("Oksijen sensörü", true),
        )
        silinme = SilinmeBilgisi(84, 3, 95, 12, 20)
        donmusKare = DonmusKare("P0171", listOf("Devir" to "2150 rpm", "Hız" to "64 km/s", "Soğutma suyu" to "88 °C"))
        tekleme = listOf(1 to (0 to 1), 2 to (0 to 0), 3 to (4 to 9), 4 to (0 to 0))
        modulGecmisi = listOf(
            ModulGecmisi("Motor (ECM)", "UDS", listOf(
                GecmisKod("P0171-00", "şu an hatalı, onaylanmış, lamba yaktırıyor", true),
                GecmisKod("P0303-00", "son silmeden beri hata vermiş", false),
            )),
        )
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

    // ------------------------------------------------------------ tarama arsivi

    private fun arsivYukle() {
        runCatching {
            if (!arsivDosyasi.exists()) return
            val dizi = JSONArray(arsivDosyasi.readText())
            for (i in 0 until dizi.length()) {
                val o = dizi.getJSONObject(i)
                val oz = o.getJSONArray("ozet")
                taramaArsivi += TaramaKaydi(o.getString("tarih"), List(oz.length()) { oz.getString(it) })
            }
        }
    }

    private fun arsiveEkle() {
        val satirlar = mutableListOf<String>()
        kodlar?.forEach { mk ->
            val tum = mk.kayitli.map { "$it (kayıtlı)" } + mk.bekleyen.map { "$it (bekleyen)" } + mk.kalici.map { "$it (kalıcı)" }
            satirlar += "${mk.modul}: " + (if (tum.isEmpty()) "kod yok" else tum.joinToString())
        }
        modulGecmisi.forEach { g -> if (g.kodlar.isNotEmpty()) satirlar += "${g.modul} hafıza: " + g.kodlar.joinToString { it.kod } }
        silinme?.kmSilindiginden?.let { satirlar += "Kodlar silineli $it km" }
        if (satirlar.isEmpty()) return
        val tarih = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("tr")).format(Date()) + if (demo) " (demo)" else ""
        taramaArsivi.add(0, TaramaKaydi(tarih, satirlar))
        while (taramaArsivi.size > 100) taramaArsivi.removeAt(taramaArsivi.size - 1)
        runCatching {
            val dizi = JSONArray()
            taramaArsivi.forEach { k -> dizi.put(JSONObject().put("tarih", k.tarih).put("ozet", JSONArray(k.ozet))) }
            arsivDosyasi.writeText(dizi.toString())
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

    // ------------------------------------------------------------ Volvo modulleri (D2)

    /** Adaptoru D2 moduna alip [is]'i calistirir, sonra her durumda OBD moduna dondurur. */
    private fun volvoOturumu(is_: suspend (Elm) -> Unit) {
        if (volvoMesgul || durum != Durum.BAGLI) return
        volvoMesgul = true
        volvoMesaj = null
        viewModelScope.launch {
            try {
                if (demo) {
                    delay(700)
                    is_(Elm(object : Tasima {
                        override val giris = java.io.ByteArrayInputStream(ByteArray(0))
                        override val cikis = java.io.ByteArrayOutputStream()
                        override fun kapat() {}
                    }))
                } else {
                    val e = elm ?: return@launch
                    islem.withLock {
                        try {
                            e.d2Ac(volvoHat)?.let { volvoMesaj = it; return@withLock }
                            is_(e)
                        } finally {
                            e.d2Kapat()
                        }
                    }
                }
            } catch (ex: Exception) {
                volvoMesaj = "İşlem başarısız: ${ex.message}"
            } finally {
                volvoMesgul = false
            }
        }
    }

    fun volvoTara() = volvoOturumu { e ->
        val hedefHat = if (volvoHat == VolvoHat.HIZLI) VolvoHat.HIZLI else VolvoHat.YAVAS_ELM
        val adaylar = VOLVO_MODULLER.filter { it.hat == hedefHat }
        val sonuc = mutableListOf<VolvoBulgu>()
        for (m in adaylar) {
            if (demo) {
                val var_ = m.kisa in setOf("ECM", "TCM", "BCM", "CEM", "CCM", "DDM", "PDM", "REM", "DIM")
                sonuc += VolvoBulgu(m, var_, if (var_) "DEMO 3${m.adres}1234 A" else "", "")
                continue
            }
            val satirlar = e.d2Gonder(d2Istek(m.adres, 0xB9, 0xF0))
            val yanit = d2Coz(satirlar).values.firstOrNull { it.size >= 2 && it[0] == m.adres }
            sonuc += VolvoBulgu(
                m, yanit != null && (yanit[1] == 0xF9 || yanit[1] == 0x7F),
                yanit?.takeIf { it[1] == 0xF9 }?.let { d2Kimlik(it) }.orEmpty(),
                (yanit?.let { hex(it) } ?: satirlar.joinToString(" | ")).take(300),
            )
        }
        volvoBulgular = sonuc
        val bulunan = sonuc.count { it.yanit }
        volvoMesaj = if (bulunan == 0) {
            if (volvoHat == VolvoHat.HIZLI) "Hiçbir modül Volvo protokolüne yanıt vermedi. Ham yanıtları Konsol'da inceleyebiliriz."
            else "Yavaş hatta yanıt yok. Adaptör gerçekten pin 3/11'e bağlı mı (anahtar konumu / OBDLink)?"
        } else "$bulunan modül yanıt verdi."
    }

    fun volvoKodOku(m: VolvoModul) = volvoOturumu { e ->
        if (demo) {
            volvoKodlar[m.adres] = VolvoKodSonucu(
                if (m.kisa == "CCM") listOf("CCM-9A04 (durum 2C)") else emptyList(), "DEMO", null,
            )
            return@volvoOturumu
        }
        var ham = ""
        for (param in listOf(0x01, 0x00)) {
            val satirlar = e.d2Gonder(d2Istek(m.adres, 0xAE, param), 2500)
            val v = d2Coz(satirlar).values.firstOrNull { it.size >= 2 && it[0] == m.adres }
            ham += "AE %02X → ".format(param) + (v?.let { hex(it) } ?: satirlar.joinToString(" | ").ifEmpty { "yanıt yok" }) + "\n"
            if (v != null && v[1] == 0xEE) {
                volvoKodlar[m.adres] = VolvoKodSonucu(d2ArizaKodlari(m.kisa, v), ham.trim(), "Kod çözümü deneysel; ham yanıtı da saklayın.")
                return@volvoOturumu
            }
        }
        volvoKodlar[m.adres] = VolvoKodSonucu(emptyList(), ham.trim(), "Modül arıza okuma isteğini kabul etmedi.")
    }

    /**
     * Gelismis: ham D2 cercevesi (yalnizca okuma servisleri) ya da AT komutu gonderir.
     * ";" ile ayrilan komutlar ayni oturumda sirayla calisir (ornek: "ATCM00000000; CB 40 B9 F0 00 00 00 00").
     */
    fun konsolGonder(girdi: String) {
        val parcalar = girdi.split(';').map { it.trim().uppercase() }.filter { it.isNotEmpty() }
        if (parcalar.isEmpty()) return
        val komutlar = mutableListOf<Pair<Boolean, String>>()
        for (g in parcalar) {
            val at = g.startsWith("AT") || g.startsWith("ST")
            if (at && (g.replace(" ", "").startsWith("ATPP") || g.startsWith("STS"))) {
                konsol += "✗ Adaptör ayarını kalıcı değiştiren komutlar kapalı: $g"; return
            }
            val cerceve = g.replace(" ", "").chunked(2).joinToString(" ")
            if (!at && !d2IstekGuvenliMi(cerceve)) {
                konsol += "✗ Yalnızca 8 baytlık OKUMA çerçeveleri (A1, A5, A6, AE, B9) gönderilebilir: $g"; return
            }
            komutlar += at to (if (at) g else cerceve)
        }
        volvoOturumu { e ->
            for ((at, k) in komutlar) {
                konsol += "> $k"
                if (demo) {
                    konsol += if (at) "OK" else "00 80 00 03 CD ${k.split(' ').getOrElse(1) { "40" }} F9 F0 44 45 4D 4F"
                    continue
                }
                val satirlar = if (at) e.komut(k, 2000) else e.d2Gonder(k, 2000)
                if (satirlar.isEmpty()) konsol += "(yanıt yok)"
                satirlar.take(40).forEach { konsol += it }
            }
            while (konsol.size > 200) konsol.removeAt(0)
        }
    }

    // ------------------------------------------------------------ uygulama ici guncelleme

    private var acilisKontroluYapildi = false

    fun acilisKontrolu() {
        if (acilisKontroluYapildi) return
        acilisKontroluYapildi = true
        guncellemeKontrol(elle = false)
    }

    /** Acilista sessizce, elle istendiginde sonucu bildirerek son surumu denetler. */
    fun guncellemeKontrol(elle: Boolean) {
        viewModelScope.launch {
            val c = getApplication<Application>()
            try {
                val y = Guncelleme.kontrol(c)
                yeniSurum = y
                if (y != null) {
                    guncellemeMesaj = null
                    guncellemePenceresi = true
                } else if (elle) {
                    guncellemeMesaj = "Uygulama güncel (sürüm ${Guncelleme.mevcutSurum(c)})."
                }
            } catch (ex: Exception) {
                if (elle) guncellemeMesaj = "Güncelleme denetlenemedi: internet bağlantısını kontrol edin."
            }
        }
    }

    fun guncellemeyiKur() {
        val c = getApplication<Application>()
        val y = yeniSurum ?: return
        if (!Guncelleme.kurulumIzniVar(c)) {
            guncellemeMesaj = "Açılan ekranda \"Bu kaynaktan izin ver\" seçeneğini açın, sonra geri dönüp tekrar Güncelle'ye basın."
            Guncelleme.kurulumIzniEkrani(c)
            return
        }
        inenApk?.takeIf { it.exists() && it.name.contains(y.surum) }?.let { Guncelleme.kur(c, it); return }
        if (indirmeYuzde != null) return
        indirmeYuzde = 0
        guncellemeMesaj = null
        viewModelScope.launch {
            try {
                val apk = Guncelleme.indir(c, y) { indirmeYuzde = it }
                inenApk = apk
                Guncelleme.kur(c, apk)
            } catch (ex: Exception) {
                guncellemeMesaj = "İndirme başarısız: ${ex.message}"
            } finally {
                indirmeYuzde = null
            }
        }
    }

    // ------------------------------------------------------------ otomatik baglanma / kayit

    fun otoBaglanAyarla(acik: Boolean) {
        otoBaglan = acik
        ayarlar.edit().putBoolean("oto_baglan", acik).apply()
    }

    fun otoKayitAyarla(acik: Boolean) {
        otoKayit = acik
        ayarlar.edit().putBoolean("oto_kayit", acik).apply()
    }

    /** Uygulama acikken son kullanilan Bluetooth adaptore 15 saniyede bir baglanmayi dener. */
    @SuppressLint("MissingPermission")
    private fun otoBaglanmaDongusu() {
        viewModelScope.launch {
            delay(1500)
            while (isActive) {
                val adres = ayarlar.getString("son_cihaz", null)
                if (otoBaglan && !elleKesildi && durum == Durum.YOK && !demo && adres != null) {
                    val c = getApplication<Application>()
                    val izin = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S ||
                        androidx.core.content.ContextCompat.checkSelfPermission(c, android.Manifest.permission.BLUETOOTH_CONNECT) ==
                        android.content.pm.PackageManager.PERMISSION_GRANTED
                    val bt = (c.getSystemService(android.content.Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager).adapter
                    if (izin && bt != null && bt.isEnabled) {
                        bt.bondedDevices.orEmpty().firstOrNull { it.address == adres }?.let { bluetoothIleBaglan(it) }
                    }
                }
                delay(15_000)
            }
        }
    }

    /** Motor calisinca kaydi baslatir, 30 sn devir 0 kalinca durdurur (yalnizca kendi baslattigi kaydi). */
    private fun otoKayitIsle(aralikSn: Double) {
        if (demo) return
        val devir = canli[0x0C] ?: 0.0
        if (otoKayit && !kayitAktif && devir > 400) {
            kayitBaslat()
            otoKayitBaslatti = true
            devirSifirSaniye = 0.0
        } else if (kayitAktif && otoKayitBaslatti) {
            devirSifirSaniye = if (devir < 100) devirSifirSaniye + aralikSn else 0.0
            if (devirSifirSaniye > 30) {
                kayitDurdur()
                otoKayitBaslatti = false
            }
        }
    }

    // ------------------------------------------------------------ Claude'a gonder

    private fun JSONObject.koy(ad: String, deger: Any?): JSONObject = put(ad, deger ?: JSONObject.NULL)

    /** O an acik sekmenin verisini JSON olarak toplar. */
    private fun anlikGoruntu(sekme: Int, not: String): JSONObject {
        val c = getApplication<Application>()
        val o = JSONObject()
            .koy("zaman", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
            .koy("surum", Guncelleme.mevcutSurum(c))
            .koy("sekme", listOf("baglanti", "gosterge", "performans", "arizalar", "moduller", "kayit").getOrElse(sekme) { "?" })
            .koy("not", not)
            .koy("durum", durum.name).koy("demo", demo)
            .koy("vin", vin).koy("adaptor", adaptor).koy("protokol", protokol)
            .koy("moduller", JSONArray(moduller))
        o.put("canli", JSONObject().apply {
            PIDLER.forEach { p -> canli[p.kod]?.let { put(p.kolon, "%.${p.ondalik}f".format(Locale.US, it)) } }
            turbo?.let { put("turbo_bar", "%.2f".format(Locale.US, it)) }
        })
        hata?.let { o.put("hata", it) }
        when (sekme) {
            0 -> {
                o.put("gunluk", JSONArray(gunluk.toList()))
                o.put("kimlikler", JSONArray(kimlikler.map {
                    JSONObject().put("modul", it.modul).put("ad", it.ad).put("calid", JSONArray(it.kalibrasyon)).put("cvn", JSONArray(it.cvn))
                }))
                o.put("destek", JSONArray(destek.sorted().map { "%02X".format(it) }))
                o.put("hizli_sorgu", hizliSorgu)
            }
            2 -> {
                o.koy("sure0100", sure0100).koy("eniyi0100", enIyi0100).koy("sure80120", sure80120).koy("eniyi80120", enIyi80120)
                o.koy("tepe_turbo", tepeTurbo).koy("tepe_emme", tepeEmme).koy("min_avans_yukte", enDusukAvansYukte).koy("tepe_devir", tepeDevir)
                o.put("son_cekis", JSONArray(sonCekis.map {
                    JSONArray(listOf(it.devir.toDouble(), it.turbo.toDouble(), it.avans.toDouble(), it.emme.toDouble()))
                }))
                o.put("benzin_depolar", JSONArray(benzin.kayitlar.map { it.json() }))
            }
            3 -> {
                o.koy("ariza_lambasi", arizaLambasi)
                o.put("kodlar", JSONArray(kodlar.orEmpty().map {
                    JSONObject().put("modul", it.modul).put("kayitli", JSONArray(it.kayitli))
                        .put("bekleyen", JSONArray(it.bekleyen)).put("kalici", JSONArray(it.kalici))
                }))
                o.put("hazirlik", JSONArray(hazirlik.map { JSONObject().put("ad", it.ad).put("tamam", it.tamam) }))
                silinme?.let {
                    o.put("silinme", JSONObject().koy("km", it.kmSilindiginden).koy("isinma", it.isinmaSilindiginden)
                        .koy("dakika", it.dakikaSilindiginden).koy("km_lamba", it.kmLambaYanarken).koy("dk_lamba", it.dakikaLambaYanarken))
                }
                donmusKare?.let { dk ->
                    o.put("donmus_kare", JSONObject().put("kod", dk.kod).put("degerler", JSONObject(dk.degerler.toMap())))
                }
                o.put("tekleme", JSONArray(tekleme.map { (s, d) -> JSONObject().put("silindir", s).koy("bu", d.first).koy("ort10", d.second) }))
                o.put("modul_gecmisi", JSONArray(modulGecmisi.map { g ->
                    JSONObject().put("modul", g.modul).put("yontem", g.yontem)
                        .put("kodlar", JSONArray(g.kodlar.map { "${it.kod} | ${it.durum}" }))
                }))
                o.put("arsiv", JSONArray(taramaArsivi.take(20).map { JSONObject().put("tarih", it.tarih).put("ozet", JSONArray(it.ozet)) }))
            }
            4 -> {
                o.put("hat", volvoHat.name).koy("mesaj", volvoMesaj)
                o.put("bulgular", JSONArray(volvoBulgular.map {
                    JSONObject().put("modul", it.modul.kisa).put("adres", "%02X".format(it.modul.adres))
                        .put("yanit", it.yanit).put("kimlik", it.kimlik).put("ham", it.ham)
                }))
                o.put("kodlar", JSONObject().apply {
                    volvoKodlar.forEach { (a, k) -> put("%02X".format(a), JSONObject().put("kodlar", JSONArray(k.kodlar)).put("ham", k.ham).koy("not", k.not)) }
                })
                o.put("konsol", JSONArray(konsol.takeLast(120)))
            }
            5 -> {
                o.put("ozet", JSONArray(ozet))
                o.koy("kayit_aktif", kayitAktif)
                sonKayit?.takeIf { it.exists() && !kayitAktif }?.let { f ->
                    o.put("csv_ad", f.name)
                    // Cok uzun kayitlarda sunucu sinirina takilmamak icin son ~3 MB.
                    val metin = f.readText()
                    o.put("csv", if (metin.length > 3_000_000) metin.lines().first() + "\n" + metin.takeLast(3_000_000).substringAfter('\n') else metin)
                }
            }
        }
        return o
    }

    fun claudeyaGonder(sekme: Int, not: String) {
        if (gonderiliyor) return
        gonderiliyor = true
        gonderMesaj = null
        viewModelScope.launch {
            try {
                val govde = withContext(Dispatchers.Default) { anlikGoruntu(sekme, not).toString(1) }
                val ekran = listOf("baglanti", "gosterge", "performans", "arizalar", "moduller", "kayit").getOrElse(sekme) { "ekran" }
                val ad = Gonderici.gonder(ekran, govde)
                gonderMesaj = "Gönderildi ✓ ($ad). Claude'a \"gönderdim\" yazmanız yeterli."
            } catch (ex: Exception) {
                gonderMesaj = "Gönderilemedi: ${ex.message}"
            } finally {
                gonderiliyor = false
            }
        }
    }

    fun gonderMesajiTemizle() {
        gonderMesaj = null
    }
}
