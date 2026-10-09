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
    Pid(0x0E, "Ateşleme avansı", "°", "atesleme_avans", false, 1) { it[0] / 2.0 - 64 },
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
