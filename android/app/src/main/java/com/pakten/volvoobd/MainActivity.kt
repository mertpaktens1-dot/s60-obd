package com.pakten.volvoobd

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.util.Locale

internal val Lacivert = Color(0xFF1C3F6E)
internal val Kirmizi = Color(0xFFC62828)
internal val Yesil = Color(0xFF2E7D32)
internal val Gri = Color(0xFF5F6368)

class MainActivity : ComponentActivity() {
    private val model: AracModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Lacivert, secondary = Lacivert)) {
                // Bagliyken ekran kapanmasin; kayit ve gosterge kesilmesin.
                val bagli = model.durum == Durum.BAGLI
                DisposableEffect(bagli) {
                    if (bagli) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    onDispose { }
                }
                Uygulama(model)
            }
        }
    }
}

private class Sekme(val ad: String, val ikon: ImageVector)

private val SEKMELER = listOf(
    Sekme("Bağlantı", Icons.Filled.Link),
    Sekme("Gösterge", Icons.Filled.Speed),
    Sekme("Performans", Icons.Filled.Timer),
    Sekme("Arızalar", Icons.Filled.Warning),
    Sekme("Modüller", Icons.Filled.Memory),
    Sekme("Kayıt", Icons.Filled.FiberManualRecord),
)

@Composable
private fun Uygulama(m: AracModel) {
    var sekme by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { m.acilisKontrolu() }
    GuncellemePenceresi(m)
    Scaffold(
        bottomBar = {
            NavigationBar {
                SEKMELER.forEachIndexed { i, s ->
                    NavigationBarItem(
                        selected = sekme == i,
                        onClick = { sekme = i },
                        icon = { Icon(s.ikon, null) },
                        label = { Text(s.ad, fontSize = 11.sp, maxLines = 1) },
                    )
                }
            }
        },
    ) { ic ->
        Column(Modifier.padding(ic).fillMaxSize()) {
            UstBant(m, sekme)
            when (sekme) {
                0 -> BaglantiEkrani(m) { sekme = 1 }
                1 -> GostergeEkrani(m)
                2 -> PerformansEkrani(m)
                3 -> ArizaEkrani(m)
                4 -> ModullerEkrani(m)
                5 -> KayitEkrani(m)
            }
        }
    }
}

@Composable
private fun UstBant(m: AracModel, sekme: Int) {
    var gonderAcik by remember { mutableStateOf(false) }
    if (gonderAcik) GonderPenceresi(m, sekme) { gonderAcik = false }
    val (renk, yazi) = when (m.durum) {
        Durum.BAGLI -> Yesil to (if (m.demo) "Demo modu" else "Bağlı · ${m.protokol}")
        Durum.BAGLANIYOR -> Color(0xFFE09A00) to "Bağlanıyor..."
        Durum.YOK -> Gri to "Bağlı değil"
    }
    Row(
        Modifier.fillMaxWidth().background(Lacivert).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Volvo S60 T3", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(Modifier.weight(1f))
        Box(Modifier.size(10.dp).background(renk, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(yazi, color = Color.White, fontSize = 13.sp, maxLines = 1)
        if (m.kayitAktif) {
            Spacer(Modifier.width(12.dp))
            Text("● KAYIT ${m.kayitSaniye / 60}:%02d".format(m.kayitSaniye % 60), color = Color(0xFFFF8A80), fontSize = 13.sp)
        }
        Spacer(Modifier.width(4.dp))
        IconButton(onClick = { m.gonderMesajiTemizle(); gonderAcik = true }) {
            Icon(Icons.Filled.CloudUpload, "Claude'a gönder", tint = Color.White)
        }
    }
}

// ------------------------------------------------------------------ baglanti

private fun btIzniVar(c: Context) = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
    ContextCompat.checkSelfPermission(c, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

@SuppressLint("MissingPermission")
private fun eslesmisCihazlar(c: Context): List<BluetoothDevice> {
    val adaptor = (c.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter ?: return emptyList()
    if (!btIzniVar(c)) return emptyList()
    return adaptor.bondedDevices.orEmpty().sortedByDescending { d ->
        val ad = (d.name ?: "").uppercase()
        listOf("OBD", "ELM", "V-LINK", "VLINK", "CAR").any { it in ad }
    }
}

@SuppressLint("MissingPermission")
@Composable
private fun BaglantiEkrani(m: AracModel, gostergeyeGec: () -> Unit) {
    val c = LocalContext.current
    var yenile by remember { mutableIntStateOf(0) }
    val izinIste = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { yenile++ }
    val cihazlar = remember(yenile, m.durum) { eslesmisCihazlar(c) }
    var wifiAdres by rememberSaveable { mutableStateOf("192.168.0.10:35000") }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        m.hata?.let { h ->
            item { Kart(arka = Color(0xFFFDECEA)) { Text(h, color = Kirmizi) } }
        }
        if (m.durum == Durum.BAGLI) {
            item {
                Kart {
                    Baslik("Araç bilgisi")
                    Bilgi("Şasi no (VIN)", m.vin.ifEmpty { "okunamadı" })
                    Bilgi("Adaptör", m.adaptor)
                    Bilgi("Protokol", m.protokol)
                    Bilgi("Yanıt veren modüller", m.moduller.joinToString().ifEmpty { "-" })
                    Bilgi("Desteklenen canlı veri", "${PIDLER.count { it.kod in m.destek }} / ${PIDLER.size}")
                    Bilgi("Sorgu modu", if (m.hizliSorgu) "Hızlı (6 değer tek istekte)" else "Tekli")
                    m.kimlikler.forEach { k ->
                        Spacer(Modifier.height(10.dp))
                        Text(k.modul + if (k.ad.isNotEmpty()) " · ${k.ad}" else "", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Bilgi("Yazılım (kalibrasyon) no", k.kalibrasyon.joinToString().ifEmpty { "okunamadı" })
                        Bilgi("CVN (yazılım imzası)", k.cvn.joinToString().ifEmpty { "okunamadı" })
                    }
                    if (m.kimlikler.isNotEmpty()) {
                        Text(
                            "Yazılım numarasını ve CVN'i not edin: yazılım değişirse CVN de değişir. Tuner bu numaraya göre dosya hazırlar.",
                            fontSize = 12.sp, color = Gri, modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = gostergeyeGec) { Text("Göstergeye geç") }
                        OutlinedButton(onClick = { m.kullaniciKopardi() }) { Text("Bağlantıyı kes") }
                    }
                }
            }
        } else {
            item {
                Kart {
                    Baslik("Bluetooth ELM327")
                    Text(
                        "1) Adaptörü OBD soketine takın (direksiyonun sol altı), kontağı açın.\n" +
                            "2) Tablette Bluetooth ayarlarından adaptörü eşleştirin (adı genelde OBDII; PIN 1234 veya 0000).\n" +
                            "3) Aşağıdaki listeden seçin.",
                        fontSize = 14.sp, color = Gri,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { c.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }) {
                            Text("Bluetooth ayarları")
                        }
                        OutlinedButton(onClick = { yenile++ }) { Text("Listeyi yenile") }
                    }
                }
            }
            if (!btIzniVar(c)) {
                item {
                    Kart(arka = Color(0xFFFFF4E5)) {
                        Text("Eşleşmiş cihazları görmek için Bluetooth izni gerekli.")
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { izinIste.launch(Manifest.permission.BLUETOOTH_CONNECT) }) { Text("İzin ver") }
                    }
                }
            } else if (cihazlar.isEmpty()) {
                item { Text("Eşleşmiş Bluetooth cihazı yok. Önce adaptörü eşleştirin.", color = Gri) }
            }
            items(cihazlar, key = { it.address }) { d ->
                Kart(
                    Modifier.clickable(enabled = m.durum == Durum.YOK) { m.bluetoothIleBaglan(d) },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Bluetooth, null, tint = Lacivert)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(d.name ?: "(adsız)", fontWeight = FontWeight.SemiBold)
                            Text(d.address, fontSize = 12.sp, color = Gri)
                        }
                        Text("Bağlan", color = Lacivert, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            item {
                Kart {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Wifi, null, tint = Lacivert)
                        Spacer(Modifier.width(8.dp))
                        Baslik("WiFi ELM327")
                    }
                    Text("Tableti önce adaptörün WiFi ağına bağlayın.", fontSize = 14.sp, color = Gri)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            wifiAdres, { wifiAdres = it.trim() }, Modifier.weight(1f),
                            singleLine = true, label = { Text("Adres:port") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(enabled = m.durum == Durum.YOK, onClick = {
                            val p = wifiAdres.split(":")
                            m.wifiIleBaglan(p[0], p.getOrNull(1)?.toIntOrNull() ?: 35000)
                        }) { Text("Bağlan") }
                    }
                }
            }
            item {
                OutlinedButton(onClick = { m.demoBaslat() }, enabled = m.durum == Durum.YOK, modifier = Modifier.fillMaxWidth()) {
                    Text("Araç olmadan dene (demo)")
                }
            }
        }
        if (m.durum == Durum.BAGLANIYOR) {
            item { Row { CircularProgressIndicator(Modifier.size(24.dp)); Spacer(Modifier.width(12.dp)); Text("Bağlanıyor, 20 saniye sürebilir...") } }
        }
        item { OtomatikKarti(m) }
        item { SurumKarti(m) }
        if (m.gunluk.isNotEmpty()) {
            item {
                Kart(arka = Color(0xFFF3F4F6)) {
                    Baslik("Bağlantı günlüğü")
                    m.gunluk.forEach { Text(it, fontSize = 12.sp, color = Gri) }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ gosterge

@Composable
private fun GostergeEkrani(m: AracModel) {
    if (m.durum != Durum.BAGLI) {
        BosEkran("Önce Bağlantı sekmesinden adaptöre bağlanın."); return
    }
    val pidler = PIDLER.filter { it.kod in m.destek && it.kod != 0x33 }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(170.dp),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            val t = m.turbo
            Gosterge("Turbo basıncı", t?.let { "%.2f".format(Locale.US, it) } ?: "–", "bar", buyuk = true)
        }
        items(pidler, key = { it.kod }) { p ->
            val v = m.canli[p.kod]
            val uyari = when (p.kod) {
                0x05 -> v != null && v > 105
                0x42 -> v != null && v < 13.0 && (m.canli[0x0C] ?: 0.0) > 600
                0x07, 0x06 -> v != null && kotlin.math.abs(v) > 10
                0x5C -> v != null && v > 130
                else -> false
            }
            Gosterge(
                p.ad,
                v?.let { "%.${p.ondalik}f".format(Locale.US, it) } ?: "–",
                p.birim,
                buyuk = p.kod == 0x0C || p.kod == 0x0D,
                uyari = uyari,
            )
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                "Hızlı veriler her turda, sıcaklık / yakıt düzeltmesi gibi yavaş değişenler 5 turda bir okunur. " +
                    "Sürüş sırasında ekrana bakmayın; Kayıt sekmesinden kaydedip sonra inceleyin.",
                fontSize = 12.sp, color = Gri,
            )
        }
    }
}

@Composable
private fun Gosterge(ad: String, deger: String, birim: String, buyuk: Boolean = false, uyari: Boolean = false) {
    Card(
        colors = CardDefaults.cardColors(containerColor = if (uyari) Color(0xFFFDECEA) else Color.White),
        elevation = CardDefaults.cardElevation(2.dp),
    ) {
        Column(Modifier.padding(14.dp).fillMaxWidth()) {
            Text(ad, fontSize = 13.sp, color = Gri, maxLines = 1)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    deger, fontSize = if (buyuk) 40.sp else 30.sp, fontWeight = FontWeight.Bold,
                    color = if (uyari) Kirmizi else Color(0xFF1F1F1F),
                )
                Spacer(Modifier.width(6.dp))
                Text(birim, fontSize = 14.sp, color = Gri, modifier = Modifier.padding(bottom = 6.dp))
            }
        }
    }
}

// ------------------------------------------------------------------ arizalar

@Composable
private fun ArizaEkrani(m: AracModel) {
    if (m.durum != Durum.BAGLI) {
        BosEkran("Önce Bağlantı sekmesinden adaptöre bağlanın."); return
    }
    var silOnay by remember { mutableStateOf(false) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { m.kodlariTara() }, enabled = !m.taraniyor) { Text("Tüm modülleri tara") }
                OutlinedButton(
                    onClick = { silOnay = true }, enabled = !m.taraniyor && m.kodlar != null,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Kirmizi),
                ) { Text("Kodları sil") }
                if (m.taraniyor) CircularProgressIndicator(Modifier.size(24.dp))
            }
        }
        m.hata?.let { item { Text(it, color = Kirmizi) } }
        if (m.kodlar == null && m.taramaArsivi.isNotEmpty()) {
            item { Text("Önceki taramalar aşağıda. Güncel durum için taramayı başlatın.", color = Gri, fontSize = 13.sp) }
        }
        m.arizaLambasi?.let { mil ->
            item {
                Kart(arka = if (mil) Color(0xFFFDECEA) else Color(0xFFE8F5E9)) {
                    Text(
                        if (mil) "Motor arıza lambası YANIYOR" else "Motor arıza lambası sönük",
                        fontWeight = FontWeight.Bold, color = if (mil) Kirmizi else Yesil,
                    )
                }
            }
        }
        val kodlar = m.kodlar
        if (kodlar != null) {
            if (kodlar.isEmpty()) item { Text("Hiçbir modül yanıt vermedi.", color = Gri) }
            items(kodlar) { mk ->
                Kart {
                    Baslik(mk.modul)
                    val bos = mk.kayitli.isEmpty() && mk.bekleyen.isEmpty() && mk.kalici.isEmpty()
                    if (bos) Text("Kayıtlı arıza kodu yok ✓", color = Yesil)
                    KodGrubu("Kayıtlı", mk.kayitli)
                    KodGrubu("Bekleyen (henüz lamba yakmadı)", mk.bekleyen)
                    KodGrubu("Kalıcı (silinemez, onarım sonrası kendiliğinden gider)", mk.kalici)
                }
            }
        }
        if (m.kodlar != null) gecmisBolumleri(m)
        arsivBolumu(m)
        item {
            Text(
                "Bu tarama OBD standardını konuşan modülleri kapsar (motor, şanzıman). ABS, airbag, klima ve kapı " +
                    "modülleri Volvo'ya özel protokol kullanır; onlar için VIDA / VDASH gerekir.",
                fontSize = 12.sp, color = Gri,
            )
        }
    }
    if (silOnay) {
        AlertDialog(
            onDismissRequest = { silOnay = false },
            title = { Text("Arıza kodları silinsin mi?") },
            text = {
                Text(
                    "Tüm modüllerdeki kayıtlı kodlar ve donmuş kare verisi silinir. Arıza giderilmediyse kod geri gelir. " +
                        "Motor KAPALI, kontak AÇIK olmalı. Kodları not aldınız mı?",
                )
            },
            confirmButton = {
                TextButton(onClick = { silOnay = false; m.kodlariSil() }) { Text("Sil", color = Kirmizi) }
            },
            dismissButton = { TextButton(onClick = { silOnay = false }) { Text("Vazgeç") } },
        )
    }
}

@Composable
internal fun KodGrubu(baslik: String, kodlar: List<String>) {
    if (kodlar.isEmpty()) return
    Spacer(Modifier.height(8.dp))
    Text(baslik, fontSize = 13.sp, color = Gri, fontWeight = FontWeight.SemiBold)
    kodlar.forEach { k ->
        Row(Modifier.padding(vertical = 4.dp)) {
            Text(k, fontWeight = FontWeight.Bold, modifier = Modifier.width(72.dp), color = Kirmizi)
            Text(kodAciklamasi(k), fontSize = 14.sp)
        }
    }
}

// ------------------------------------------------------------------ kayit

@Composable
private fun KayitEkrani(m: AracModel) {
    val c = LocalContext.current
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Kart {
                Baslik("Sürüş kaydı")
                Text(
                    "Kaydı başlatın, 15–20 dakika normal sürün, sonra durdurun. Özet ve grafikler burada çıkar; " +
                        "CSV dosyasını paylaşıp bilgisayarda da inceleyebilirsiniz.",
                    fontSize = 14.sp, color = Gri,
                )
                Spacer(Modifier.height(12.dp))
                if (m.kayitAktif) {
                    Button(
                        onClick = { m.kayitDurdur() },
                        colors = ButtonDefaults.buttonColors(containerColor = Kirmizi),
                    ) { Text("Kaydı durdur  (${m.kayitSaniye / 60}:%02d)".format(m.kayitSaniye % 60)) }
                } else {
                    Button(onClick = { m.kayitBaslat() }, enabled = m.durum == Durum.BAGLI) { Text("Kaydı başlat") }
                    if (m.durum != Durum.BAGLI) Text("Kayıt için önce bağlanın.", fontSize = 12.sp, color = Gri)
                }
            }
        }
        if (m.ozet.isNotEmpty()) {
            item {
                Kart {
                    Baslik("Özet")
                    m.ozet.forEach {
                        Text(it, color = if ("⚠" in it) Kirmizi else Color.Unspecified, modifier = Modifier.padding(vertical = 2.dp))
                    }
                }
            }
        }
        if (m.grafikDevir.size > 2) item { Grafik("Devir (rpm)", m.grafikDevir, Lacivert) }
        if (m.grafikTurbo.size > 2) item { Grafik("Turbo basıncı (bar)", m.grafikTurbo, Color(0xFFE07A00)) }
        if (m.grafikSu.size > 2) item { Grafik("Soğutma suyu (°C)", m.grafikSu, Kirmizi) }
        m.sonKayit?.takeIf { !m.kayitAktif && it.exists() }?.let { f ->
            item {
                OutlinedButton(onClick = {
                    val uri = FileProvider.getUriForFile(c, "${c.packageName}.dosya", f)
                    val i = Intent(Intent.ACTION_SEND).apply {
                        type = "text/csv"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    c.startActivity(Intent.createChooser(i, "Kaydı paylaş"))
                }) {
                    Icon(Icons.Filled.Share, null); Spacer(Modifier.width(8.dp)); Text("CSV'yi paylaş (${f.name})")
                }
            }
        }
    }
}

@Composable
internal fun Grafik(baslik: String, degerler: List<Float>, renk: Color) {
    Kart {
        val enAz = degerler.min()
        val enCok = degerler.max()
        Row {
            Text(baslik, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text("en düşük %.1f · en yüksek %.1f".format(Locale.US, enAz, enCok), fontSize = 12.sp, color = Gri)
        }
        Spacer(Modifier.height(8.dp))
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            val aralik = (enCok - enAz).takeIf { it > 0f } ?: 1f
            val yol = Path()
            degerler.forEachIndexed { i, v ->
                val x = size.width * i / (degerler.size - 1)
                val y = size.height - (v - enAz) / aralik * size.height
                if (i == 0) yol.moveTo(x, y) else yol.lineTo(x, y)
            }
            drawPath(yol, renk, style = Stroke(width = 2.5f))
        }
    }
}

// ------------------------------------------------------------------ ortak

@Composable
internal fun Kart(modifier: Modifier = Modifier, arka: Color = Color.White, icerik: @Composable () -> Unit) {
    Card(
        modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = arka),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Column(Modifier.padding(16.dp)) { icerik() }
    }
}

@Composable
internal fun Baslik(s: String) {
    Text(s, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(bottom = 6.dp))
}

@Composable
internal fun Bilgi(etiket: String, deger: String) {
    Row(Modifier.padding(vertical = 3.dp)) {
        Text(etiket, color = Gri, fontSize = 14.sp, modifier = Modifier.width(180.dp))
        Text(deger, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
    HorizontalDivider(color = Color(0xFFEEEEEE))
}

@Composable
internal fun BosEkran(yazi: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(yazi, color = Gri)
    }
}
