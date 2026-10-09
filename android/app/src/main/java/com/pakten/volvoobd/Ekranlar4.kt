package com.pakten.volvoobd

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

private val SEKME_ADLARI = listOf("Bağlantı", "Gösterge", "Performans", "Arızalar", "Modüller", "Kayıt")

// ------------------------------------------------------------------ Claude'a gonder

@Composable
internal fun GonderPenceresi(m: AracModel, sekme: Int, kapat: () -> Unit) {
    var not by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!m.gonderiliyor) kapat() },
        title = { Text("Claude'a gönder – ${SEKME_ADLARI.getOrElse(sekme) { "" }}") },
        text = {
            Column {
                Text(
                    "Bu ekrandaki veriler" + (if (sekme == 5) " ve son kaydın CSV dosyası" else "") +
                        " sunucunuza yüklenir; Claude oradan okur. Dışarıya açık değildir.",
                    fontSize = 13.sp, color = Gri,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    not, { not = it }, Modifier.fillMaxWidth(),
                    label = { Text("Not (isteğe bağlı)") },
                    placeholder = { Text("ör. 3. viteste çekişte titreme oldu") },
                    minLines = 2,
                )
                if (m.gonderiliyor) {
                    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.padding(end = 8.dp))
                        Text("Gönderiliyor…")
                    }
                }
                m.gonderMesaj?.let {
                    Text(it, fontSize = 13.sp, color = if (it.startsWith("Gönderildi")) Yesil else Kirmizi,
                        modifier = Modifier.padding(top = 8.dp))
                }
            }
        },
        confirmButton = {
            val bitti = m.gonderMesaj?.startsWith("Gönderildi") == true
            if (bitti) {
                TextButton(onClick = kapat) { Text("Tamam") }
            } else {
                TextButton(onClick = { m.claudeyaGonder(sekme, not) }, enabled = !m.gonderiliyor) { Text("Gönder") }
            }
        },
        dismissButton = {
            TextButton(onClick = kapat, enabled = !m.gonderiliyor) { Text("Kapat") }
        },
    )
}

// ------------------------------------------------------------------ otomatik baglanma / kayit

@Composable
internal fun OtomatikKarti(m: AracModel) {
    Kart {
        Baslik("Otomatik")
        AyarSatiri(
            "Otomatik bağlan",
            "Uygulama açıkken son adaptöre (${m.sonCihazAdi ?: "henüz yok"}) kendiliğinden bağlanır, kopunca 15 sn'de bir tekrar dener.",
            m.otoBaglan, m::otoBaglanAyarla,
        )
        AyarSatiri(
            "Otomatik kayıt",
            "Motor çalışınca sürüş kaydını başlatır, motor durunca (30 sn) kapatır.",
            m.otoKayit, m::otoKayitAyarla,
        )
        Text(
            "Tablet ekranı açık kalmalı; uygulama kapatılırsa ya da arka plana atılırsa bağlantı kesilebilir.",
            fontSize = 12.sp, color = Gri, modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun AyarSatiri(ad: String, aciklama: String, acik: Boolean, degistir: (Boolean) -> Unit) {
    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(ad, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(aciklama, fontSize = 12.sp, color = Gri)
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = acik, onCheckedChange = degistir)
    }
}

// ------------------------------------------------------------------ benzin kalitesi

@Composable
internal fun BenzinKarti(m: AracModel) {
    var pencere by remember { mutableStateOf(false) }
    var istasyon by rememberSaveable { mutableStateOf("") }
    val b = m.benzin
    val aktif = b.aktif
    Kart {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Baslik("Benzin kalitesi")
                Text(
                    aktif?.let { "Şu anki depo: ${it.istasyon} (${it.tarih})" } ?: "Henüz depo kaydı yok.",
                    fontSize = 14.sp,
                )
            }
            Button(onClick = { pencere = true }) { Text("Yakıt aldım") }
        }
        aktif?.let { d ->
            Spacer(Modifier.height(6.dp))
            val ilerleme = (d.ornek * 100 / DepoKaydi.ISTENEN_ORNEK).coerceAtMost(100)
            Bilgi("Yüksek yük ölçümü", "${d.ornek} nokta" + if (!d.yeterli) " (karşılaştırma için %$ilerleme)" else " ✓")
            d.ortAvans?.let { Bilgi("Ort. ateşleme avansı", "%.1f °".format(Locale.US, it)) }
            d.ortEmme?.let { Bilgi("Ort. emme havası", "%.0f °C".format(Locale.US, it)) }
            d.dususDakika?.let { Bilgi("Ani avans düşüşü", "%.1f / dk (toplam ${d.dusus})".format(Locale.US, it)) }
        }
        val liste = b.siralama()
        if (liste.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text("İstasyon sıralaması (en iyi üstte)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            liste.forEachIndexed { i, s ->
                Row(Modifier.padding(vertical = 3.dp)) {
                    Text("${i + 1}.", modifier = Modifier.width(24.dp), fontWeight = FontWeight.Bold,
                        color = if (i == 0) Yesil else Color.Unspecified)
                    Column {
                        Text(s.istasyon, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(
                            "avans %.1f° · düşüş %.1f/dk · emme %.0f °C · %d depo".format(Locale.US, s.ortAvans, s.dususDakika, s.ortEmme, s.depo),
                            fontSize = 12.sp, color = Gri,
                        )
                    }
                }
            }
        }
        Text(
            "Nasıl çalışır: kaliteli benzinde motor beyni yüksek yükte ateşlemeyi daha az geri çeker. Her depoda 2500–5000 rpm " +
                "arası, gaz %70 üstü anlar ölçülür. Sıcak havada avans doğal olarak düşer; emme havası sıcaklığı benzer depoları karşılaştırın.",
            fontSize = 12.sp, color = Gri, modifier = Modifier.padding(top = 8.dp),
        )
    }
    if (pencere) {
        AlertDialog(
            onDismissRequest = { pencere = false },
            title = { Text("Yakıt aldım") },
            text = {
                Column {
                    Text("Yeni depo ölçümü başlar. Depoyu ağzına kadar doldurduysanız karşılaştırma daha doğru olur.", fontSize = 13.sp, color = Gri)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(istasyon, { istasyon = it }, singleLine = true,
                        label = { Text("İstasyon / marka / oktan") }, placeholder = { Text("ör. Shell V-Power 97") })
                }
            },
            confirmButton = {
                TextButton(onClick = { b.yakitAldim(istasyon); istasyon = ""; pencere = false }) { Text("Kaydet") }
            },
            dismissButton = { TextButton(onClick = { pencere = false }) { Text("Vazgeç") } },
        )
    }
}

// ------------------------------------------------------------------ surus kayitlari listesi

/** "kayit_20261009_174512.csv" -> "09.10.2026 17:45" */
private fun kayitTarihi(f: java.io.File): String {
    val r = Regex("""(\d{4})(\d{2})(\d{2})_(\d{2})(\d{2})""").find(f.name) ?: return f.name
    val (y, a, g, s, d) = r.destructured
    return "$g.$a.$y $s:$d" + if ("_demo" in f.name) " (demo)" else ""
}

internal fun androidx.compose.foundation.lazy.LazyListScope.kayitListesi(m: AracModel, paylas: (java.io.File) -> Unit) {
    // Sayac okunarak liste baslat / durdur / gonderimde yenilenir.
    val dosyalar = m.kayitListesiSurumu.let { m.kayitDosyalari() }
    if (dosyalar.isEmpty()) return
    item { Baslik("Kayıtlar (${dosyalar.size})") }
    items(dosyalar.size, key = { dosyalar[it].name }) { i ->
        val f = dosyalar[i]
        val suruyor = m.suruyorMu(f)
        var notAcik by remember { mutableStateOf(false) }
        var not by rememberSaveable(f.name) { mutableStateOf("") }
        Kart(arka = if (suruyor) Color(0xFFFFF4E5) else Color.White) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        kayitTarihi(f) + if (suruyor) "  ● kayıt sürüyor" else "",
                        fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                        color = if (suruyor) Kirmizi else Color.Unspecified,
                    )
                    Text("%.0f KB".format(Locale.US, f.length() / 1024.0) + " · ${f.name}", fontSize = 12.sp, color = Gri)
                }
                if (m.csvGonderilen == f.name) {
                    CircularProgressIndicator(Modifier.padding(end = 8.dp))
                } else {
                    Button(onClick = { notAcik = !notAcik }, enabled = m.csvGonderilen == null) { Text("Claude'a gönder") }
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { paylas(f) }) { Text("Paylaş") }
            }
            if (notAcik) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    not, { not = it }, Modifier.fillMaxWidth(),
                    label = { Text("Not (isteğe bağlı)") },
                    placeholder = { Text("ör. 2. viteste tam gaz, 3000 devirde tekledi") },
                    minLines = 2,
                )
                if (suruyor) {
                    Text("Kayıt sürüyor: şu ana kadarki ölçümler gönderilir, kayıt devam eder.", fontSize = 12.sp, color = Gri)
                }
                Row(Modifier.padding(top = 6.dp)) {
                    Button(onClick = { m.csvGonder(f, not); notAcik = false }, enabled = m.csvGonderilen == null) { Text("Gönder") }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { notAcik = false }) { Text("Vazgeç") }
                }
            }
            m.csvMesaj[f.name]?.let {
                Text(it, fontSize = 13.sp, color = if (it.startsWith("Gönderildi")) Yesil else Kirmizi,
                    modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}
