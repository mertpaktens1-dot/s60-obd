package com.pakten.volvoobd

/**
 * Volvo VIDA "D2 over CAN" protokolu (P1/P2/P3 kusagi).
 *
 * Istekler 29 bit 000FFFFE kimligiyle gider; her cerceve 8 bayttir:
 *   [C8 | uzunluk] [modul adresi] [servis] [parametreler...] [00 dolgu]
 * Yanitta servis + 0x40 doner (A6 -> E6, AE -> EE, B9 -> F9). Cok cerceveli yanitta
 * ilk baytin 7. biti "ilk cerceve", 6. biti "son cerceve", alt 3 bit o cercevedeki
 * veri sayisidir.
 *
 * Kaynaklar: hackingvolvo.blogspot.com (modul tablosu), github.com/Tigo2000/Volvo-VIDA,
 * github.com/Alfaa123/Volvo-CAN-Gauge. P3'te birebir dogrulanmamistir; bu yuzden ekranda
 * ham yanitlar da gosterilir.
 */

enum class VolvoHat(val ad: String, val aciklama: String) {
    HIZLI("Hızlı CAN (HS, 500k)", "OBD pin 6/14 – her ELM327 ile çalışır. Motor, şanzıman, ABS, CEM."),
    YAVAS_ELM("Yavaş CAN (MS, 125k) – anahtarlı ELM327", "Adaptör içindeki anahtar 3/11 konumuna alınmış olmalı."),
    YAVAS_STN("Yavaş CAN (MS, 125k) – OBDLink / STN", "OBDLink MX+, EX vb. STN yongalı adaptörler hattı kendisi değiştirir."),
}

class VolvoModul(val adres: Int, val kisa: String, val ad: String, val hat: VolvoHat)

/** Bilinen modul adresleri. HS/MS dagilimi P1/P3 icin; bazi modullerin yeri modele gore degisebilir. */
val VOLVO_MODULLER = listOf(
    VolvoModul(0x7A, "ECM", "Motor beyni", VolvoHat.HIZLI),
    VolvoModul(0x6E, "TCM", "Şanzıman", VolvoHat.HIZLI),
    VolvoModul(0x28, "BCM", "Fren / ABS", VolvoHat.HIZLI),
    VolvoModul(0x30, "SAS", "Direksiyon açı sensörü", VolvoHat.HIZLI),
    VolvoModul(0x58, "SRS", "Hava yastığı", VolvoHat.HIZLI),
    VolvoModul(0x40, "CEM", "Merkezi elektronik (gövde) modülü", VolvoHat.HIZLI),
    VolvoModul(0x29, "CCM", "Klima", VolvoHat.YAVAS_ELM),
    VolvoModul(0x43, "DDM", "Sürücü kapı modülü", VolvoHat.YAVAS_ELM),
    VolvoModul(0x45, "PDM", "Yolcu kapı modülü", VolvoHat.YAVAS_ELM),
    VolvoModul(0x46, "REM", "Arka elektronik modül", VolvoHat.YAVAS_ELM),
    VolvoModul(0x51, "DIM", "Gösterge paneli", VolvoHat.YAVAS_ELM),
    VolvoModul(0x48, "SWM", "Direksiyon modülü", VolvoHat.YAVAS_ELM),
    VolvoModul(0x2E, "PSM", "Elektrikli koltuk", VolvoHat.YAVAS_ELM),
    VolvoModul(0x47, "UEM", "Üst elektronik modül (tavan)", VolvoHat.YAVAS_ELM),
    VolvoModul(0x60, "AUM", "Ses / multimedya", VolvoHat.YAVAS_ELM),
    VolvoModul(0x64, "PHM", "Telefon modülü", VolvoHat.YAVAS_ELM),
)

fun volvoModulAdi(adres: Int): String =
    VOLVO_MODULLER.firstOrNull { it.adres == adres }?.let { "${it.kisa} – ${it.ad}" } ?: "Modül %02X".format(adres)

/**
 * Yalnizca OKUYAN servisler. Cikis kontrolu (B0/B1), guvenlik erisimi (A3), yazma ve
 * programlama servisleri bilerek engellenir: yanlis bir bayt modulu kilitleyebilir.
 */
val OKUMA_SERVISLERI = setOf(0xA1, 0xA5, 0xA6, 0xAE, 0xB9)

/** Istek cercevesi: 8 bayt, bosluklu hex ("CB 40 B9 F0 00 00 00 00"). */
fun d2Istek(adres: Int, servis: Int, vararg parametre: Int): String {
    require(servis in OKUMA_SERVISLERI) { "Bu servis güvenlik nedeniyle kapalı: %02X".format(servis) }
    val veri = listOf(adres, servis) + parametre.toList()
    require(veri.size <= 7) { "Tek çerçeveye sığmıyor" }
    val bas = 0xC8 or veri.size
    return (listOf(bas) + veri + List(7 - veri.size) { 0 }).joinToString(" ") { "%02X".format(it) }
}

/** Ham konsol girdisinin okuma servisi olup olmadigini denetler. */
fun d2IstekGuvenliMi(hex: String): Boolean {
    val b = hex.split(' ').filter { it.isNotEmpty() }.mapNotNull { it.toIntOrNull(16) }
    return b.size == 8 && b[0] and 0xC8 == 0xC8 && b[2] in OKUMA_SERVISLERI
}

/**
 * ATH1 + ATS1 + ATCAF0 ile gelen satirlar: "00 80 00 03 CD 40 F9 F0 31 32 33 34".
 * Yanit kimligine gore cerceveleri birlestirir; her modul icin veri baytlarini dondurur
 * (ilk bayt modul adresi, ikinci bayt servis+40).
 */
fun d2Coz(satirlar: List<String>): Map<String, IntArray> {
    val veri = LinkedHashMap<String, MutableList<Int>>()
    for (s in satirlar) {
        val p = s.split(' ').filter { it.isNotEmpty() }
        if (p.size < 6 || !p.all { it.length == 2 && it.toIntOrNull(16) != null }) continue
        val kimlik = p.take(4).joinToString("")
        val b = p.drop(4).map { it.toInt(16) }
        val bas = b[0]
        val n = (bas and 0x07).coerceAtMost(b.size - 1)
        val liste = veri.getOrPut(kimlik) { mutableListOf() }
        if (bas and 0x80 != 0) liste.clear()
        liste += b.subList(1, 1 + n)
    }
    return veri.filterValues { it.isNotEmpty() }.mapValues { it.value.toIntArray() }
}

fun hex(v: IntArray): String = v.joinToString(" ") { "%02X".format(it) }

/** Kimlik blogundaki (B9 F0) okunabilir parca numaralarini cikarir. */
fun d2Kimlik(v: IntArray): String {
    if (v.size < 3) return ""
    val govde = v.drop(3)
    val ascii = govde.map { it.toChar() }.joinToString("").filter { it.code in 32..126 }.trim()
    return if (ascii.length >= 4) ascii else hex(govde.toIntArray())
}

/**
 * AE yanitindan Volvo ariza kodlarini cikarmayi dener. Volvo kodlari 2 bayt kod + 1 bayt
 * durum olarak gelir (ornek CEM-1A2B). P3'te dogrulanmadigi icin ham veri de saklanir.
 */
fun d2ArizaKodlari(modul: String, v: IntArray): List<String> {
    if (v.size < 3 || v[1] != 0xEE) return emptyList()
    return v.drop(3).chunked(3)
        .filter { it.size == 3 && (it[0] != 0 || it[1] != 0) }
        .map { "%s-%02X%02X (durum %02X)".format(modul, it[0], it[1], it[2]) }
}
