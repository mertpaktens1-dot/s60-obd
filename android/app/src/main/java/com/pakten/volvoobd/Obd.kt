package com.pakten.volvoobd

/** Mod 01 canli veri tanimi. [coz] veri baytlarini (A, B, ...) alir. */
class Pid(
    val kod: Int,
    val ad: String,
    val birim: String,
    val kolon: String,
    val hizli: Boolean,
    val ondalik: Int,
    val coz: (IntArray) -> Double,
)

val PIDLER = listOf(
    Pid(0x0C, "Devir", "rpm", "devir_rpm", true, 0) { (it[0] * 256 + it[1]) / 4.0 },
    Pid(0x0D, "Hız", "km/s", "hiz_kmh", true, 0) { it[0].toDouble() },
    Pid(0x0B, "Manifold basıncı", "kPa", "manifold_kpa", true, 0) { it[0].toDouble() },
    Pid(0x05, "Soğutma suyu", "°C", "sogutma_c", true, 0) { it[0] - 40.0 },
    Pid(0x42, "Akü / şarj", "V", "aku_volt", true, 2) { (it[0] * 256 + it[1]) / 1000.0 },
    Pid(0x04, "Motor yükü", "%", "yuk_yuzde", true, 0) { it[0] * 100 / 255.0 },
    Pid(0x11, "Gaz kelebeği", "%", "gaz_kelebegi_yuzde", true, 0) { it[0] * 100 / 255.0 },
    Pid(0x10, "Hava akışı (MAF)", "g/s", "maf_gs", true, 1) { (it[0] * 256 + it[1]) / 100.0 },
    Pid(0x0F, "Emme havası", "°C", "emme_hava_c", false, 0) { it[0] - 40.0 },
    Pid(0x0E, "Ateşleme avansı", "°", "atesleme_avans", true, 1) { it[0] / 2.0 - 64 },
    Pid(0x06, "Kısa yakıt düzeltmesi", "%", "kisa_yakit_duz", false, 1) { (it[0] - 128) * 100 / 128.0 },
    Pid(0x07, "Uzun yakıt düzeltmesi", "%", "uzun_yakit_duz", false, 1) { (it[0] - 128) * 100 / 128.0 },
    Pid(0x5C, "Motor yağı", "°C", "yag_c", false, 0) { it[0] - 40.0 },
    Pid(0x2F, "Yakıt seviyesi", "%", "yakit_yuzde", false, 0) { it[0] * 100 / 255.0 },
    Pid(0x46, "Dış hava", "°C", "dis_hava_c", false, 0) { it[0] - 40.0 },
    Pid(0x33, "Atmosfer basıncı", "kPa", "atmosfer_kpa", false, 0) { it[0].toDouble() },
)

fun modulAdi(baslik: String): String = when (baslik) {
    "7E8" -> "Motor (ECM)"
    "7E9" -> "Şanzıman (TCM)"
    "7EA" -> "Modül 7EA"
    else -> if (baslik.startsWith("ECU")) "Modül ${baslik.removePrefix("ECU")}" else "Modül $baslik"
}

fun dtcCoz(a: Int, b: Int): String =
    "%c%d%X%02X".format("PCBU"[a shr 6], (a shr 4) and 3, a and 0xF, b)

/** Mod 03/07/0A yanitindan kodlari cikarir. CAN'de 2. bayt kod sayisidir. */
fun dtcListesi(veri: IntArray, can: Boolean): List<String> {
    val bas = if (can) 2 else 1
    if (veri.size <= bas) return emptyList()
    return veri.drop(bas).chunked(2)
        .filter { it.size == 2 && (it[0] != 0 || it[1] != 0) }
        .map { dtcCoz(it[0], it[1]) }
}

private val KOD_TR = mapOf(
    "P0010" to "Eksantrik (VVT) kontrol devresi – emme, sıra 1",
    "P0011" to "Eksantrik zamanlaması fazla avans – emme, sıra 1",
    "P0014" to "Eksantrik zamanlaması fazla avans – egzoz, sıra 1",
    "P0016" to "Krank / eksantrik konum sensörü uyumsuzluğu",
    "P0030" to "Oksijen sensörü ısıtıcı devresi (sıra 1 sensör 1)",
    "P0101" to "Hava akış (MAF) sensörü ölçüm aralığı / performans",
    "P0106" to "Manifold basınç (MAP) sensörü aralığı / performans",
    "P0113" to "Emme havası sıcaklık sensörü yüksek giriş",
    "P0117" to "Soğutma suyu sıcaklık sensörü düşük giriş",
    "P0118" to "Soğutma suyu sıcaklık sensörü yüksek giriş",
    "P0128" to "Termostat: motor çalışma sıcaklığına geç ulaşıyor",
    "P0171" to "Karışım çok fakir (sıra 1) – hava kaçağı / enjektör / MAF",
    "P0172" to "Karışım çok zengin (sıra 1)",
    "P0234" to "Turbo aşırı basınç",
    "P0236" to "Turbo basınç sensörü aralığı / performans",
    "P0299" to "Turbo düşük basınç – hortum kaçağı / wastegate",
    "P0300" to "Rastgele / çoklu silindir tekleme",
    "P0301" to "1. silindir tekleme",
    "P0302" to "2. silindir tekleme",
    "P0303" to "3. silindir tekleme",
    "P0304" to "4. silindir tekleme",
    "P0325" to "Vuruntu sensörü devresi",
    "P0335" to "Krank mili konum sensörü devresi",
    "P0340" to "Eksantrik konum sensörü devresi",
    "P0401" to "EGR akışı yetersiz",
    "P0420" to "Katalitik konvertör verimi eşik altında (sıra 1)",
    "P0442" to "Yakıt buharı (EVAP) sisteminde küçük kaçak",
    "P0455" to "Yakıt buharı (EVAP) sisteminde büyük kaçak – depo kapağı?",
    "P0500" to "Araç hız sensörü",
    "P0562" to "Sistem voltajı düşük – akü / şarj dinamosu",
    "P0563" to "Sistem voltajı yüksek",
    "P0700" to "Şanzıman kontrol sistemi arızası (ayrıntı şanzıman modülünde)",
    "P0715" to "Şanzıman giriş mili hız sensörü",
    "P0741" to "Tork konvertörü kilit kavraması performansı",
    "P2096" to "Katalizör sonrası karışım çok fakir",
    "P2097" to "Katalizör sonrası karışım çok zengin",
    "P2187" to "Rölantide karışım çok fakir",
    "P2188" to "Rölantide karışım çok zengin",
    "P2263" to "Turbo destek sistemi performansı",
)

fun kodAciklamasi(kod: String): String {
    KOD_TR[kod]?.let { return it }
    val grup = when {
        kod.startsWith("C") -> "Şasi (ABS, fren, direksiyon)"
        kod.startsWith("B") -> "Gövde (klima, airbag, kapılar)"
        kod.startsWith("U") -> "Modüller arası iletişim (CAN ağı)"
        kod.startsWith("P1") -> "Üreticiye özel (Volvo) motor/şanzıman kodu"
        kod.startsWith("P00") || kod.startsWith("P01") || kod.startsWith("P02") -> "Yakıt ve hava ölçümü"
        kod.startsWith("P03") -> "Ateşleme sistemi / tekleme"
        kod.startsWith("P04") -> "Emisyon kontrolü (katalizör, EVAP, EGR)"
        kod.startsWith("P05") -> "Hız, rölanti kontrolü, yardımcı girişler"
        kod.startsWith("P06") -> "Beyin (ECU) ve çıkış devreleri"
        kod.startsWith("P07") || kod.startsWith("P08") || kod.startsWith("P09") -> "Şanzıman"
        else -> "Genel kod"
    }
    return "$grup – ayrıntılı açıklama için kodu internette arayın"
}

// ---------------------------------------------------------------- coklu PID

/** Mod 01/02 PID veri uzunluklari (bayt). Coklu yanit bunlara gore bolunur. */
val PID_UZUNLUK = mapOf(
    0x01 to 4, 0x02 to 2, 0x04 to 1, 0x05 to 1, 0x06 to 1, 0x07 to 1, 0x0B to 1, 0x0C to 2, 0x0D to 1,
    0x0E to 1, 0x0F to 1, 0x10 to 2, 0x11 to 1, 0x1F to 2, 0x21 to 2, 0x2F to 1, 0x30 to 1, 0x31 to 2,
    0x33 to 1, 0x42 to 2, 0x46 to 1, 0x4D to 2, 0x4E to 2, 0x5C to 1,
)

/**
 * "41 P1 veri P2 veri ..." biciminde coklu yanit. Mod 02'de her PID'den sonra kare numarasi
 * baytı gelir ([kareBayti] = true).
 */
fun cokluCoz(v: IntArray, servis: Int, kareBayti: Boolean = false): Map<Int, IntArray> {
    if (v.isEmpty() || v[0] != servis) return emptyMap()
    val sonuc = LinkedHashMap<Int, IntArray>()
    var i = 1
    while (i < v.size) {
        val pid = v[i]
        val n = PID_UZUNLUK[pid] ?: break
        i += if (kareBayti) 2 else 1
        if (i + n > v.size) break
        sonuc[pid] = v.copyOfRange(i, i + n)
        i += n
    }
    return sonuc
}

// ---------------------------------------------------------------- hazirlik testleri

class Monitor(val ad: String, val tamam: Boolean)

/** PID 01 yaniti (A B C D), benzinli motor. Desteklenmeyen testler listelenmez. */
fun hazirlikCoz(v: IntArray): List<Monitor> {
    if (v.size < 4) return emptyList()
    val (b, c, d) = Triple(v[1], v[2], v[3])
    val l = mutableListOf<Monitor>()
    for ((ad, bit) in listOf("Tekleme izleme" to 0, "Yakıt sistemi" to 1, "Bileşen izleme" to 2)) {
        if ((b shr bit) and 1 == 1) l += Monitor(ad, (b shr (bit + 4)) and 1 == 0)
    }
    val benzin = listOf(
        "Katalizör" to 0, "Isıtmalı katalizör" to 1, "Yakıt buharı (EVAP)" to 2, "İkincil hava" to 3,
        "Oksijen sensörü" to 5, "Oksijen sensörü ısıtıcı" to 6, "EGR / VVT" to 7,
    )
    for ((ad, bit) in benzin) {
        if ((c shr bit) and 1 == 1) l += Monitor(ad, (d shr bit) and 1 == 0)
    }
    return l
}

// ---------------------------------------------------------------- mod 06 tekleme sayaclari

class TestSonucu(val mid: Int, val tid: Int, val deger: Int)

/** CAN mod 06 yaniti: 46 [MID TID UASID deger(2) min(2) max(2)] x n */
fun mod06Coz(v: IntArray): List<TestSonucu> {
    if (v.isEmpty() || v[0] != 0x46) return emptyList()
    val l = mutableListOf<TestSonucu>()
    var i = 1
    while (i + 9 <= v.size) {
        l += TestSonucu(v[i], v[i + 1], v[i + 3] * 256 + v[i + 4])
        i += 9
    }
    return l
}

// ---------------------------------------------------------------- UDS / KWP ariza gecmisi

class GecmisKod(val kod: String, val durum: String, val aktif: Boolean)

/** UDS 19 02 FF yaniti: 59 02 maske [DTC(3) durum] x n */
fun uds19Coz(v: IntArray): List<GecmisKod>? {
    if (v.size < 3 || v[0] != 0x59 || v[1] != 0x02) return null
    return v.drop(3).chunked(4).filter { it.size == 4 && (it[0] != 0 || it[1] != 0) }.map { r ->
        val s = r[3]
        val etiket = buildList {
            if (s and 0x01 != 0) add("şu an hatalı")
            if (s and 0x04 != 0) add("bekleyen")
            if (s and 0x08 != 0) add("onaylanmış")
            if (s and 0x20 != 0 && s and 0x01 == 0) add("son silmeden beri hata vermiş")
            if (s and 0x80 != 0) add("lamba yaktırıyor")
        }.ifEmpty { listOf("geçmiş kayıt") }
        GecmisKod("%s-%02X".format(dtcCoz(r[0], r[1]), r[2]), etiket.joinToString(", "), s and 0x01 != 0)
    }
}

/** KWP2000 18 02 FF 00 yaniti: 58 adet [DTC(2) durum] x n */
fun kwp18Coz(v: IntArray): List<GecmisKod>? {
    if (v.size < 2 || v[0] != 0x58) return null
    return v.drop(2).chunked(3).filter { it.size == 3 && (it[0] != 0 || it[1] != 0) }.map { r ->
        val aktif = r[2] and 0x40 != 0 && r[2] and 0x20 != 0
        GecmisKod(dtcCoz(r[0], r[1]), if (aktif) "aktif" else "kayıtlı (durum %02X)".format(r[2]), aktif)
    }
}

// ---------------------------------------------------------------- beyin kimligi

fun asciiParcalari(v: IntArray, bas: Int, boy: Int): List<String> =
    v.drop(bas).chunked(boy).map { p -> p.map { it.toChar() }.joinToString("").filter { it.code in 33..126 } }
        .filter { it.isNotEmpty() }

// ---------------------------------------------------------------- ne yapmali

private val KOD_ONERI = mapOf(
    "P0101" to "Hava akış (MAF) sensörünün okuduğu hava beklenenle tutmuyor. Sırayla: hava filtresine bakın; " +
        "MAF sensörünü yalnızca MAF temizleyici spreyle temizleyin (dokunmadan, bezle silmeden); hava filtresi " +
        "kutusundan turboya giden hortumda yırtık / gevşek kelepçe arayın. Düzelmezse sensör değişir.",
    "P0420" to "Katalizörün verimi eşik altında. Önce sebebi giderin: tekleme, MAF hatası ya da fakir/zengin karışım " +
        "katalizörü yorar ve bu kodu tetikler. Diğer kodlar çözüldükten sonra da dönerse arka oksijen sensörü ya da " +
        "katalizör kontrol edilmeli. Egzozda katalizörden önce kaçak olmamalı.",
    "P0171" to "Karışım fakir: emme hortumlarında hava kaçağı, kirli MAF sensörü ya da düşük yakıt basıncı en olası sebepler.",
    "P0172" to "Karışım zengin: kirli MAF, kaçıran enjektör ya da tıkalı hava filtresi olabilir.",
    "P0300" to "Birden çok silindirde tekleme: bujiler, bobinler, yakıt kalitesi ya da emme kaçağı. Hangi silindirlerde olduğu " +
        "aşağıdaki tekleme sayaçlarında görünür.",
    "P0301" to "1. silindirde tekleme: o silindirin bobinini başka silindirle değiştirin; tekleme bobinle taşınırsa bobin bozuktur.",
    "P0302" to "2. silindirde tekleme: bobini başka silindirle değiştirip tekleme taşınıyor mu bakın; taşınmıyorsa bujiye bakın.",
    "P0303" to "3. silindirde tekleme: bobini başka silindirle değiştirip tekleme taşınıyor mu bakın; taşınmıyorsa bujiye bakın.",
    "P0304" to "4. silindirde tekleme: bobini başka silindirle değiştirip tekleme taşınıyor mu bakın; taşınmıyorsa bujiye bakın.",
    "P0128" to "Motor çalışma sıcaklığına geç ulaşıyor: termostat açık kalıyor olabilir. Termostat değişimi gerekir.",
    "P0455" to "Yakıt buharı sisteminde büyük kaçak: önce depo kapağının tam kapandığından emin olun; çoğu zaman sebep budur.",
    "P0442" to "Yakıt buharı sisteminde küçük kaçak: depo kapağı contası ya da kömür kanister hortumları.",
    "P0299" to "Turbo yeterli basınç yapmıyor: intercooler hortumlarında yağlanma / yırtık, wastegate ya da turbo kontrol edilmeli.",
    "P0562" to "Sistem voltajı düşük: akü yaşlanmış ya da şarj dinamosu yetersiz olabilir. Akü testi yaptırın.",
    "P0016" to "Krank ve eksantrik konumu uyumsuz: zincir/kayış gerilmesi ya da VVT sorunu. Motor yağı seviyesini kontrol edin, servise gösterin.",
    "P2187" to "Rölantide karışım fakir: emme manifoldu, PCV (karter havalandırma) hortumu ya da vakum hortumlarında kaçak.",
)

fun kodOnerisi(kod: String): String? = KOD_ONERI[kod]
