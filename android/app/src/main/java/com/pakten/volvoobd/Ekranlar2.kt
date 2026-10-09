package com.pakten.volvoobd

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

private val Turuncu = Color(0xFFB26A00)

// ------------------------------------------------------------------ ariza gecmisi

internal fun LazyListScope.gecmisBolumleri(m: AracModel) {
    val s = m.silinme
    if (s != null && (s.kmSilindiginden != null || s.isinmaSilindiginden != null)) {
        item {
            Kart {
                Baslik("Kodlar en son ne zaman silinmiş?")
                s.kmSilindiginden?.let { Bilgi("Silindiğinden beri yol", "$it km") }
                s.isinmaSilindiginden?.let { Bilgi("Silindiğinden beri motor ısınması", "$it kez") }
                s.dakikaSilindiginden?.let { Bilgi("Silindiğinden beri motor çalışma", "${it / 60} sa ${it % 60} dk") }
                s.kmLambaYanarken?.let { Bilgi("Arıza lambası yanarken gidilen yol", "$it km") }
                s.dakikaLambaYanarken?.let { Bilgi("Arıza lambası yanarken çalışma", "$it dk") }
                val yeni = (s.kmSilindiginden ?: Int.MAX_VALUE) < 200 || (s.isinmaSilindiginden ?: Int.MAX_VALUE) < 10
                if (yeni) {
                    Text(
                        "⚠ Kodlar yakın zamanda silinmiş. Siz silmediyseniz (ör. aracı satın aldıysanız) eski arızalar " +
                            "gizlenmiş olabilir; aşağıdaki hazırlık testleri ve modül hafızasına bakın.",
                        color = Turuncu, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
    if (m.hazirlik.isNotEmpty()) {
        item {
            Kart {
                Baslik("Hazırlık testleri (readiness)")
                m.hazirlik.forEach { mon ->
                    Row(Modifier.padding(vertical = 2.dp)) {
                        Text(if (mon.tamam) "✓" else "…", color = if (mon.tamam) Yesil else Turuncu,
                            fontWeight = FontWeight.Bold, modifier = Modifier.width(24.dp))
                        Text(mon.ad + if (mon.tamam) "" else " – tamamlanmadı")
                    }
                }
                Text(
                    "Kodlar silinince bu testler sıfırlanır ve araç birkaç gün normal kullanıldıkça tamamlanır. " +
                        "Tamamlanmamış test çoksa ya kodlar yeni silinmiştir ya da ilgili sistemde sorun vardır. " +
                        "TÜVTÜRK egzoz emisyon ölçümünden önce hepsinin ✓ olması iyi olur.",
                    fontSize = 12.sp, color = Gri, modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
    m.donmusKare?.let { dk ->
        item {
            Kart {
                Baslik("Donmuş kare: ${dk.kod} oluştuğu an")
                Text(kodAciklamasi(dk.kod), fontSize = 13.sp, color = Gri)
                Spacer(Modifier.height(6.dp))
                dk.degerler.forEach { (ad, d) -> Bilgi(ad, d) }
            }
        }
    }
    if (m.tekleme.isNotEmpty()) {
        item {
            Kart {
                Baslik("Silindir bazında tekleme sayaçları")
                m.tekleme.forEach { (sil, sayac) ->
                    val (bu, ort) = sayac
                    val kotu = (bu ?: 0) > 0 || (ort ?: 0) > 2
                    Bilgi(
                        "$sil. silindir",
                        "bu sürüş: ${bu ?: "-"} · son 10 sürüş ort.: ${ort ?: "-"}" + if (kotu) "  ⚠" else "",
                    )
                }
                Text(
                    "Tek bir silindirde sürekli tekleme varsa genelde o silindirin bobini veya bujisidir; " +
                        "bobini başka silindirle yer değiştirip sayacın silindirle birlikte taşınıp taşınmadığına bakın.",
                    fontSize = 12.sp, color = Gri, modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
    item {
        Kart {
            Baslik("Modül arıza hafızası (deneysel)")
            if (m.modulGecmisi.isEmpty()) {
                Text(
                    "Modüller bu doğrudan okumayı yanıtlamadı. Volvo'nun kendi geçmiş kayıtları için VIDA / VDASH gerekir.",
                    fontSize = 13.sp, color = Gri,
                )
            }
            m.modulGecmisi.forEach { g ->
                Text("${g.modul} (${g.yontem})", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
                if (g.kodlar.isEmpty()) Text("Hafızada kod yok ✓", color = Yesil)
                g.kodlar.forEach { k ->
                    Row(Modifier.padding(vertical = 3.dp)) {
                        Text(k.kod, fontWeight = FontWeight.Bold, color = if (k.aktif) Kirmizi else Turuncu,
                            modifier = Modifier.width(96.dp))
                        Column {
                            Text(kodAciklamasi(k.kod.substringBefore("-")), fontSize = 14.sp)
                            Text(k.durum, fontSize = 12.sp, color = Gri)
                        }
                    }
                }
            }
        }
    }
}

internal fun LazyListScope.arsivBolumu(m: AracModel) {
    if (m.taramaArsivi.isEmpty()) return
    item { Baslik("Tarama geçmişi (bu tablette)") }
    items(m.taramaArsivi.size) { i ->
        val k = m.taramaArsivi[i]
        Kart(arka = Color(0xFFF7F8FA)) {
            Text(k.tarih, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            k.ozet.forEach { Text(it, fontSize = 13.sp, color = Gri) }
        }
    }
}

// ------------------------------------------------------------------ performans

private fun sn(v: Double?) = v?.let { "%.2f sn".format(Locale.US, it) } ?: "–"

@Composable
internal fun PerformansEkrani(m: AracModel) {
    if (m.durum != Durum.BAGLI) {
        BosEkran("Önce Bağlantı sekmesinden adaptöre bağlanın."); return
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Kart {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Baslik("Performans ölçümü")
                        Text(m.perfDurum, fontSize = 15.sp)
                    }
                    if (m.perfModu) {
                        Button(onClick = { m.perfAcKapa() }, colors = ButtonDefaults.buttonColors(containerColor = Kirmizi)) {
                            Text("Kapat")
                        }
                    } else {
                        Button(onClick = { m.perfAcKapa() }) { Text("Performans modunu aç") }
                    }
                }
                if (m.perfModu) {
                    Text(
                        "Ölçüm aralığı ~${m.olcumAraligiMs} ms (kesinlik yaklaşık ±${"%.2f".format(Locale.US, m.olcumAraligiMs / 2000.0)} sn). " +
                            "Bu modda yalnızca hız, devir, turbo, gaz, avans ve emme havası okunur.",
                        fontSize = 12.sp, color = Gri, modifier = Modifier.padding(top = 6.dp),
                    )
                }
                Text(
                    "Hızlanma ölçümlerini yalnızca kapalı pistte ya da trafiğe kapalı, yasal bir alanda yapın.",
                    fontSize = 12.sp, color = Turuncu, modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Kart {
                        Text("0–100 km/s", color = Gri, fontSize = 13.sp)
                        Text(sn(m.sure0100), fontSize = 30.sp, fontWeight = FontWeight.Bold)
                        Text("en iyi: ${sn(m.enIyi0100)}", fontSize = 13.sp, color = Gri)
                    }
                }
                Column(Modifier.weight(1f)) {
                    Kart {
                        Text("80–120 km/s", color = Gri, fontSize = 13.sp)
                        Text(sn(m.sure80120), fontSize = 30.sp, fontWeight = FontWeight.Bold)
                        Text("en iyi: ${sn(m.enIyi80120)}", fontSize = 13.sp, color = Gri)
                    }
                }
            }
        }
        item {
            Kart {
                Baslik("Tepe değerler")
                Bilgi("En yüksek turbo", m.tepeTurbo?.let { "%.2f bar".format(Locale.US, it) } ?: "–")
                Bilgi("En yüksek emme havası", m.tepeEmme?.let { "%.0f °C".format(Locale.US, it) } ?: "–")
                Bilgi("Tam gazda en düşük avans", m.enDusukAvansYukte?.let { "%.1f °".format(Locale.US, it) } ?: "–")
                Bilgi("En yüksek devir", m.tepeDevir?.let { "%.0f rpm".format(Locale.US, it) } ?: "–")
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { m.tepeleriSifirla() }) { Text("Sıfırla") }
            }
        }
        val c = m.sonCekis
        if (c.isNotEmpty()) {
            item {
                Text("Son tam gaz çekişi (${c.size} nokta, %.0f → %.0f rpm)".format(Locale.US, c.first().devir, c.last().devir),
                    fontWeight = FontWeight.SemiBold)
            }
            item { Grafik("Turbo (bar)", c.map { it.turbo }, Color(0xFFE07A00)) }
            item { Grafik("Ateşleme avansı (°) – ani düşüş vuruntu düzeltmesi olabilir", c.map { it.avans }, Lacivert) }
            item { Grafik("Emme havası (°C)", c.map { it.emme }, Kirmizi) }
        }
        item { BenzinKarti(m) }
        item { Stage1Kontrol(m) }
        item { Stage1Bilgi() }
    }
}

@Composable
private fun Stage1Kontrol(m: AracModel) {
    Kart {
        Baslik("Stage 1 öncesi sağlık kontrolü")
        val kodYok = m.kodlar?.all { it.kayitli.isEmpty() && it.bekleyen.isEmpty() }
        val teklemeYok = if (m.tekleme.isEmpty()) null else m.tekleme.all { (it.second.first ?: 0) == 0 && (it.second.second ?: 0) <= 2 }
        val ltft = m.canli[0x07]
        val volt = m.canli[0x42]
        val calisiyor = (m.canli[0x0C] ?: 0.0) > 600
        val hazir = if (m.hazirlik.isEmpty()) null else m.hazirlik.all { it.tamam }
        KontrolSatiri("Arıza kodu yok", kodYok, "Arızalar sekmesinden tarayın")
        KontrolSatiri("Tekleme yok", teklemeYok, "Arızalar taramasında okunur")
        KontrolSatiri(
            "Yakıt düzeltmesi normal (±%10)", ltft?.let { kotlin.math.abs(it) < 10 },
            ltft?.let { "uzun vadeli %+.1f%%".format(Locale.US, it) } ?: "motor çalışırken okunur",
        )
        KontrolSatiri(
            "Şarj voltajı ≥ 13,5 V", if (calisiyor && volt != null) volt >= 13.5 else null,
            volt?.let { "%.2f V".format(Locale.US, it) } ?: "motor çalışırken okunur",
        )
        KontrolSatiri("Hazırlık testleri tamam", hazir, "Arızalar taramasında okunur")
        Text(
            "Bunlara ek olarak elle: bujiler (yazılım sonrası bir derece soğuk buji önerilebilir), bobinler, hava filtresi, " +
                "intercooler hortumlarında kaçak/yağlanma ve motor yağı. Sağlıksız motora yazılım atılmaz.",
            fontSize = 12.sp, color = Gri, modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun KontrolSatiri(ad: String, tamam: Boolean?, aciklama: String) {
    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            when (tamam) { true -> "✓"; false -> "⚠"; null -> "?" },
            color = when (tamam) { true -> Yesil; false -> Kirmizi; null -> Gri },
            fontWeight = FontWeight.Bold, modifier = Modifier.width(24.dp),
        )
        Column {
            Text(ad, fontSize = 14.sp)
            Text(aciklama, fontSize = 12.sp, color = Gri)
        }
    }
}

@Composable
private fun Stage1Bilgi() {
    Kart(arka = Color(0xFFF3F6FA)) {
        Baslik("Stage 1 nasıl yapılır?")
        Text(
            "Motor: 1.5 T3 (B4154T4), Denso motor beyni. Fabrika 152 hp / 250 Nm. Tuner tablolarında Stage 1 " +
                "yaklaşık 190 hp / 300 Nm olarak geçiyor (gerçek değer dyno ile ölçülür).",
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(8.dp))
        Text("1) Polestar Optimization (resmi)", fontWeight = FontWeight.SemiBold)
        Text(
            "Volvo bayisi yükler; emisyon ve garanti etkilenmez. Kazancı küçüktür. Aracınıza uygun olup olmadığını " +
                "şasi numarasıyla bayiye sorun.",
            fontSize = 13.sp, color = Gri,
        )
        Spacer(Modifier.height(6.dp))
        Text("2) Profesyonel tuner + dyno", fontWeight = FontWeight.SemiBold)
        Text(
            "Orijinal yazılımın yedeğini aldırın ve size versinler. Öncesi/sonrası dyno grafiği isteyin. " +
                "Katalizör, oksijen sensörü veya EGR gibi emisyon sistemlerini kapatan yazılım istemeyin; " +
                "muayenede ve yasal olarak sorun olur. Otomatik şanzımanın tork sınırı aşılmamalı. " +
                "Değişikliği kasko/sigorta şirketine bildirin.",
            fontSize = 13.sp, color = Gri,
        )
        Spacer(Modifier.height(6.dp))
        Text("Bu uygulamanın rolü", fontWeight = FontWeight.SemiBold)
        Text(
            "Yazılım öncesi: sağlık kontrolü, 0-100 / 80-120 ve tam gaz çekişi kaydı. Yazılım sonrası: aynı ölçümleri " +
                "tekrarlayıp karşılaştırın; tam gazda avansın ani düşmesi (vuruntu) ve emme havasının aşırı ısınması " +
                "yazılımın motoru zorladığını gösterir, tuner'a geri dönün.",
            fontSize = 13.sp, color = Gri,
        )
    }
}
