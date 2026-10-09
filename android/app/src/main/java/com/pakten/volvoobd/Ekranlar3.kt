package com.pakten.volvoobd

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Turuncu3 = Color(0xFFB26A00)

@Composable
internal fun ModullerEkrani(m: AracModel) {
    if (m.durum != Durum.BAGLI) {
        BosEkran("Önce Bağlantı sekmesinden adaptöre bağlanın."); return
    }
    var konsolAcik by rememberSaveable { mutableStateOf(false) }
    var konsolGirdi by rememberSaveable { mutableStateOf("CB 40 B9 F0 00 00 00 00") }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Kart(arka = Color(0xFFFFF4E5)) {
                Text("Deneysel – Volvo'ya özel protokol", fontWeight = FontWeight.Bold, color = Turuncu3)
                Text(
                    "Bu bölüm VIDA'nın kullandığı Volvo protokolüyle modüllere doğrudan konuşur. Yalnızca OKUMA yapar; " +
                        "modüle yazma, çıkış tetikleme ve güvenlik erişimi kapalıdır. Motor kapalı, kontak açık (2. konum) kullanın.",
                    fontSize = 13.sp,
                )
            }
        }
        item {
            Kart {
                Baslik("Hat seçimi")
                VolvoHat.entries.forEach { h ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = m.volvoHat == h, onClick = { m.volvoHat = h }, enabled = !m.volvoMesgul)
                        Column {
                            Text(h.ad, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(h.aciklama, fontSize = 12.sp, color = Gri)
                        }
                    }
                }
                if (m.volvoHat != VolvoHat.HIZLI) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Klima (CCM), kapı modülleri (DDM/PDM), arka modül (REM) ve gösterge paneli yavaş hattadır. " +
                            "ELM327 mini yalnızca pin 6/14'e bağlıdır; bu hatta ulaşmak için ya adaptöre HS/MS anahtarı " +
                            "eklenmeli ya da MS-CAN destekli bir adaptör (OBDLink MX+, vLinker MC+/FS) kullanılmalıdır.",
                        fontSize = 13.sp, color = Turuncu3,
                    )
                }
                if (m.volvoHat == VolvoHat.YAVAS_ELM) {
                    Text(
                        "İş bitince anahtarı tekrar 6/14 (HS) konumuna almayı unutmayın; yoksa motor verileri okunmaz.",
                        fontSize = 12.sp, color = Gri, modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { m.volvoTara() }, enabled = !m.volvoMesgul) { Text("Modülleri tara") }
                    Spacer(Modifier.width(12.dp))
                    if (m.volvoMesgul) CircularProgressIndicator(Modifier.size(24.dp))
                }
                m.volvoMesaj?.let { Text(it, modifier = Modifier.padding(top = 8.dp), fontSize = 14.sp) }
            }
        }
        val bulunan = m.volvoBulgular.filter { it.yanit }
        val yok = m.volvoBulgular.filter { !it.yanit }
        items(bulunan, key = { it.modul.adres }) { b ->
            Kart {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${b.modul.kisa} – ${b.modul.ad}", fontWeight = FontWeight.Bold)
                        Text(
                            "Adres %02X".format(b.modul.adres) + if (b.kimlik.isNotEmpty()) " · ${b.kimlik}" else "",
                            fontSize = 12.sp, color = Gri,
                        )
                    }
                    OutlinedButton(onClick = { m.volvoKodOku(b.modul) }, enabled = !m.volvoMesgul) { Text("Arıza kodları") }
                }
                m.volvoKodlar[b.modul.adres]?.let { k ->
                    Spacer(Modifier.height(8.dp))
                    if (k.kodlar.isEmpty()) {
                        Text(if (k.not == null) "Kod yok ✓" else "Kod bulunamadı", color = if (k.not == null) Yesil else Gri)
                    }
                    k.kodlar.forEach { Text(it, fontWeight = FontWeight.SemiBold, color = Kirmizi) }
                    k.not?.let { Text(it, fontSize = 12.sp, color = Gri) }
                    if (k.ham.isNotEmpty()) {
                        Text(k.ham, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Gri,
                            modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
        if (yok.isNotEmpty()) {
            item {
                Kart(arka = Color(0xFFF7F8FA)) {
                    Text("Yanıt vermeyenler", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    yok.forEach { b ->
                        Text("${b.modul.kisa} – ${b.modul.ad}", fontSize = 13.sp, color = Gri)
                        if (b.ham.isNotEmpty()) Text(b.ham, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = Gri)
                    }
                    Text(
                        "Modül bu araçta olmayabilir, başka hatta olabilir ya da adresi farklı olabilir.",
                        fontSize = 12.sp, color = Gri, modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
        item {
            Kart {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Baslik("Konsol (gelişmiş)")
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { konsolAcik = !konsolAcik }) { Text(if (konsolAcik) "Gizle" else "Aç") }
                }
                if (konsolAcik) {
                    Text(
                        "8 baytlık D2 çerçevesi ya da AT komutu. Birden fazla komutu ; ile ayırın. " +
                            "Örnek: CB 40 B9 F0 00 00 00 00 (CEM kimliği).",
                        fontSize = 12.sp, color = Gri,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(konsolGirdi, { konsolGirdi = it }, Modifier.weight(1f), singleLine = true)
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = { m.konsolGonder(konsolGirdi) }, enabled = !m.volvoMesgul) { Text("Gönder") }
                    }
                    Spacer(Modifier.height(8.dp))
                    Column(Modifier.fillMaxWidth().background(Color(0xFF1E1E1E)).padding(8.dp)) {
                        if (m.konsol.isEmpty()) Text("—", color = Color(0xFF9E9E9E), fontSize = 12.sp)
                        m.konsol.takeLast(60).forEach {
                            Text(it, color = if (it.startsWith(">")) Color(0xFF8AB4F8) else Color(0xFFE0E0E0),
                                fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ guncelleme

@Composable
internal fun GuncellemePenceresi(m: AracModel) {
    val y = m.yeniSurum
    if (!m.guncellemePenceresi || y == null) return
    val yuzde = m.indirmeYuzde
    AlertDialog(
        onDismissRequest = { if (yuzde == null) m.guncellemePenceresi = false },
        title = { Text("Yeni sürüm: ${y.surum}") },
        text = {
            Column {
                if (y.notlar.isNotBlank()) Text(y.notlar.take(600), fontSize = 14.sp)
                if (yuzde != null) {
                    Spacer(Modifier.height(12.dp))
                    Text("İndiriliyor… %$yuzde", fontSize = 13.sp)
                    LinearProgressIndicator(progress = { yuzde / 100f }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                }
                m.guncellemeMesaj?.let { Text(it, fontSize = 13.sp, color = Turuncu3, modifier = Modifier.padding(top = 8.dp)) }
                if (m.kayitAktif) {
                    Text("Sürüş kaydı açık; güncellemeden önce kaydı durdurun.", fontSize = 13.sp, color = Kirmizi,
                        modifier = Modifier.padding(top = 8.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { m.guncellemeyiKur() }, enabled = yuzde == null && !m.kayitAktif) { Text("Güncelle") }
        },
        dismissButton = {
            TextButton(onClick = { m.guncellemePenceresi = false }, enabled = yuzde == null) { Text("Sonra") }
        },
    )
}

@Composable
internal fun SurumKarti(m: AracModel) {
    val c = LocalContext.current
    Kart(arka = Color(0xFFF7F8FA)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("S60 OBD sürüm ${Guncelleme.mevcutSurum(c)}", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                m.yeniSurum?.let { Text("Yeni sürüm hazır: ${it.surum}", color = Turuncu3, fontSize = 13.sp) }
                m.guncellemeMesaj?.takeIf { !m.guncellemePenceresi }?.let { Text(it, fontSize = 12.sp, color = Gri) }
            }
            if (m.yeniSurum != null) {
                Button(onClick = { m.guncellemePenceresi = true }) { Text("Güncelle") }
            } else {
                OutlinedButton(onClick = { m.guncellemeKontrol(elle = true) }) { Text("Güncellemeleri denetle") }
            }
        }
    }
}
