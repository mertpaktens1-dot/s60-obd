package com.pakten.volvoobd

import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Benzin kalitesi karsilastirmasi.
 *
 * Fikir: kaliteli (vuruntuya dayanikli) benzinde motor beyni yuksek yukte atesleme
 * avansini daha az geri ceker. Her depo dolumu icin yuksek yuk anlarindaki avans
 * ortalamasi ve "ani avans dususu" (vuruntu duzeltmesi belirtisi) sayilir; istasyonlar
 * bu degerlere gore siralanir. Emme havasi sicakligi da avansi etkiledigi icin
 * karsilastirmada yaninda gosterilir.
 */
class DepoKaydi(
    val istasyon: String,
    val tarih: String,
    var ornek: Int = 0,
    var avansToplam: Double = 0.0,
    var emmeToplam: Double = 0.0,
    var dusus: Int = 0,
    var yukSaniye: Double = 0.0,
) {
    val ortAvans get() = if (ornek > 0) avansToplam / ornek else null
    val ortEmme get() = if (ornek > 0) emmeToplam / ornek else null
    /** Yuksek yukte gecen her dakikadaki ani avans dususu sayisi. */
    val dususDakika get() = if (yukSaniye > 0) dusus / (yukSaniye / 60) else null
    val yeterli get() = ornek >= ISTENEN_ORNEK

    fun json(): JSONObject = JSONObject()
        .put("istasyon", istasyon).put("tarih", tarih).put("ornek", ornek)
        .put("avansToplam", avansToplam).put("emmeToplam", emmeToplam)
        .put("dusus", dusus).put("yukSaniye", yukSaniye)

    companion object {
        /** Siralamaya girmek icin gereken yuksek yuk olcumu (~1 dk tam gaz yakin surus). */
        const val ISTENEN_ORNEK = 60

        fun jsondan(o: JSONObject) = DepoKaydi(
            o.getString("istasyon"), o.getString("tarih"), o.optInt("ornek"),
            o.optDouble("avansToplam"), o.optDouble("emmeToplam"), o.optInt("dusus"), o.optDouble("yukSaniye"),
        )
    }
}

class IstasyonOzeti(val istasyon: String, val depo: Int, val ornek: Int, val ortAvans: Double, val ortEmme: Double, val dususDakika: Double)

class BenzinTakip(private val dosya: File) {
    /** En yeni basta; ilk kayit su anki depodur. */
    val kayitlar = mutableStateListOf<DepoKaydi>()

    private var oncekiAvans: Double? = null
    private var oncekiDevir = 0.0
    private var oncekiZaman = 0.0
    private var kaydedilmemis = 0

    init {
        runCatching {
            if (dosya.exists()) {
                val d = JSONArray(dosya.readText())
                for (i in 0 until d.length()) kayitlar += DepoKaydi.jsondan(d.getJSONObject(i))
            }
        }
    }

    val aktif: DepoKaydi? get() = kayitlar.firstOrNull()

    fun yakitAldim(istasyon: String) {
        val tarih = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.US).format(Date())
        kayitlar.add(0, DepoKaydi(istasyon.trim().ifEmpty { "Bilinmeyen istasyon" }, tarih))
        while (kayitlar.size > 200) kayitlar.removeAt(kayitlar.size - 1)
        oncekiAvans = null
        kaydet()
    }

    fun sil(k: DepoKaydi) {
        kayitlar.remove(k)
        kaydet()
    }

    /** Canli veri dongusunden her olcumde cagrilir. */
    fun isle(t: Double, devir: Double?, gaz: Double?, yuk: Double?, avans: Double?, emme: Double?) {
        val depo = aktif ?: return
        val yuksekYuk = devir != null && gaz != null && avans != null &&
            devir in 2500.0..5000.0 && gaz >= 70 && (yuk ?: 100.0) >= 70
        if (!yuksekYuk) {
            oncekiAvans = null
            return
        }
        val aralik = t - oncekiZaman
        val onceki = oncekiAvans
        if (onceki != null && aralik in 0.0..1.0) {
            depo.yukSaniye += aralik
            // Devir pek degismeden avans bir olcumde 3 dereceden fazla dustuyse vuruntu duzeltmesi say.
            if (onceki - avans!! > 3.0 && kotlin.math.abs(devir!! - oncekiDevir) < 400) depo.dusus++
        }
        depo.ornek++
        depo.avansToplam += avans!!
        depo.emmeToplam += emme ?: 0.0
        oncekiAvans = avans
        oncekiDevir = devir!!
        oncekiZaman = t
        if (++kaydedilmemis >= 30) kaydet()
    }

    /** Yeterli veri olan depolari istasyon bazinda toplar; en az avans dususu, sonra en yuksek avans en ustte. */
    fun siralama(): List<IstasyonOzeti> = kayitlar.filter { it.yeterli }
        .groupBy { it.istasyon.lowercase(Locale("tr")) }
        .map { (_, l) ->
            val ornek = l.sumOf { it.ornek }
            val saniye = l.sumOf { it.yukSaniye }
            IstasyonOzeti(
                l.first().istasyon, l.size, ornek,
                l.sumOf { it.avansToplam } / ornek,
                l.sumOf { it.emmeToplam } / ornek,
                if (saniye > 0) l.sumOf { it.dusus } / (saniye / 60) else 0.0,
            )
        }
        .sortedWith(compareBy<IstasyonOzeti> { it.dususDakika }.thenByDescending { it.ortAvans })

    fun kaydet() {
        kaydedilmemis = 0
        runCatching {
            val d = JSONArray()
            kayitlar.forEach { d.put(it.json()) }
            dosya.writeText(d.toString())
        }
    }
}
