"""Volvo S60 T3 (2017) icin OBD okuma araci.

ELM327 / OBDLink adaptorle calisir. Ariza kodlarini okur ve siler, canli veriyi
CSV'ye kaydeder, kayittan grafikli rapor uretir. --demo ile adaptorsuz denenir.

Ornekler:
    python volvo_obd.py portlar
    python volvo_obd.py kodlar
    python volvo_obd.py kaydet --sure 600
    python volvo_obd.py rapor kayitlar\\kayit_20261009_1430.csv
    python volvo_obd.py kaydet --demo --sure 60
"""

import argparse
import csv
import math
import random
import sys
import time
from datetime import datetime
from pathlib import Path

KLASOR = Path(__file__).parent
KAYIT_KLASORU = KLASOR / "kayitlar"

# Sik gorulen kodlarin Turkce aciklamalari; olmayanlar icin kutuphanenin
# Ingilizce aciklamasi kullanilir.
KOD_TR = {
    "P0010": "Eksantrik (VVT) kontrol devresi - emme, sira 1",
    "P0011": "Eksantrik zamanlamasi fazla avans - emme, sira 1",
    "P0016": "Krank / eksantrik konum sensoru uyumsuzlugu",
    "P0030": "Oksijen sensoru isitici devresi (sira 1 sensor 1)",
    "P0101": "Hava akis (MAF) sensoru olcum araligi / performans",
    "P0106": "Manifold basinc (MAP) sensoru araligi / performans",
    "P0113": "Emme havasi sicaklik sensoru yuksek giris",
    "P0117": "Motor sogutma suyu sicaklik sensoru dusuk giris",
    "P0118": "Motor sogutma suyu sicaklik sensoru yuksek giris",
    "P0128": "Termostat: sogutma suyu calisma sicakligina gec ulasiyor",
    "P0171": "Karisim cok fakir (sira 1) - hava kacagi / enjektor / MAF",
    "P0172": "Karisim cok zengin (sira 1)",
    "P0234": "Turbo asiri basinc",
    "P0236": "Turbo basinc sensoru araligi / performans",
    "P0299": "Turbo dusuk basinc (underboost) - kacak / wastegate",
    "P0300": "Rastgele / coklu silindir tekleme",
    "P0301": "1. silindir tekleme",
    "P0302": "2. silindir tekleme",
    "P0303": "3. silindir tekleme",
    "P0304": "4. silindir tekleme",
    "P0325": "Vuruntu sensoru devresi",
    "P0335": "Krank mili konum sensoru devresi",
    "P0340": "Eksantrik konum sensoru devresi",
    "P0401": "EGR akisi yetersiz",
    "P0420": "Katalitik konvertor verimi esik altinda (sira 1)",
    "P0442": "Yakit buhari (EVAP) sisteminde kucuk kacak",
    "P0455": "Yakit buhari (EVAP) sisteminde buyuk kacak - depo kapagi?",
    "P0500": "Arac hiz sensoru",
    "P0562": "Sistem voltaji dusuk - aku / sarj dinamosu",
    "P0563": "Sistem voltaji yuksek",
    "P0700": "Sanziman kontrol sistemi arizasi (TCM'de ayrinti var)",
    "P0715": "Sanziman giris mili hiz sensoru",
    "P0741": "Tork konvertoru kilit kavramasi performans / takili kapali",
    "P2096": "Katalizor sonrasi karisim cok fakir",
    "P2097": "Katalizor sonrasi karisim cok zengin",
    "P2187": "Rolantide karisim cok fakir",
    "P2188": "Rolantide karisim cok zengin",
    "P2263": "Turbo destek sistemi performansi",
}

# (sutun adi, python-OBD komutu, birim donusumu)
CANLI_VERILER = [
    ("devir_rpm", "RPM", "rpm"),
    ("hiz_kmh", "SPEED", "kph"),
    ("sogutma_c", "COOLANT_TEMP", "degC"),
    ("emme_hava_c", "INTAKE_TEMP", "degC"),
    ("manifold_kpa", "INTAKE_PRESSURE", "kPa"),
    ("atmosfer_kpa", "BAROMETRIC_PRESSURE", "kPa"),
    ("maf_gs", "MAF", "gps"),
    ("yuk_yuzde", "ENGINE_LOAD", "percent"),
    ("gaz_kelebegi_yuzde", "THROTTLE_POS", "percent"),
    ("atesleme_avans_derece", "TIMING_ADVANCE", "degree"),
    ("kisa_yakit_duz_yuzde", "SHORT_FUEL_TRIM_1", "percent"),
    ("uzun_yakit_duz_yuzde", "LONG_FUEL_TRIM_1", "percent"),
    ("aku_volt", "CONTROL_MODULE_VOLTAGE", "volt"),
]


def kod_aciklamasi(kod, ingilizce):
    return KOD_TR.get(kod) or ingilizce or "Aciklama bulunamadi (ureticiye ozel kod olabilir)"


# ---------------------------------------------------------------- baglanti

def baglan(port):
    import obd

    obd.logger.setLevel(obd.logging.WARNING)
    print("Adaptore baglaniliyor..." + (f" ({port})" if port else " (port otomatik araniyor)"))
    baglanti = obd.OBD(port, fast=False, timeout=1)
    if not baglanti.is_connected():
        print("\nBaglanti kurulamadi. Kontrol edin:")
        print("  - Kontak ACIK mi? (motor calisiyor ya da kontak 2. konumda)")
        print("  - Adaptor OBD soketine tam oturdu mu? (direksiyonun sol alti)")
        print("  - Bluetooth ise Windows'ta eslestirildi mi? 'portlar' komutuyla COM portunu bulup --port COM5 gibi verin.")
        sys.exit(1)
    print(f"Baglandi: {baglanti.port_name()}  protokol: {baglanti.protocol_name()}")
    return baglanti


def cmd_portlar(_):
    import obd

    portlar = obd.scan_serial()
    if not portlar:
        print("Hicbir seri port bulunamadi. Adaptor takili / Bluetooth eslesmis mi?")
        return
    print("Bulunan portlar (Bluetooth adaptorlerde genelde iki COM portu cikar, 'Giden' olani deneyin):")
    for p in portlar:
        print("  ", p)


# ---------------------------------------------------------------- ariza kodlari

def cmd_kodlar(args):
    if args.demo:
        kodlar = [("P0171", ""), ("P0455", "")]
        print("[DEMO] Ornek kodlar gosteriliyor.\n")
    else:
        import obd

        baglanti = baglan(args.port)
        durum = baglanti.query(obd.commands.STATUS)
        if not durum.is_null():
            mil = "YANIYOR" if durum.value.MIL else "sonuk"
            print(f"Motor ariza lambasi: {mil}, kayitli kod sayisi: {durum.value.DTC_count}")
        kodlar = baglanti.query(obd.commands.GET_DTC).value or []
        bekleyen = baglanti.query(obd.commands.GET_CURRENT_DTC).value or []
        for kod, ing in bekleyen:
            if kod not in {k for k, _ in kodlar}:
                kodlar.append((kod, ing + " [bekleyen - henuz lamba yakmadi]"))
        baglanti.close()

    if not kodlar:
        print("\nMotor beyninde kayitli ariza kodu yok.")
    else:
        print(f"\n{len(kodlar)} kod bulundu:")
        for kod, ing in kodlar:
            print(f"  {kod}  {kod_aciklamasi(kod, ing)}")
    print("\nNot: Bu yalnizca motor beyni (ECM). Sanziman, ABS, airbag ve govde modulleri")
    print("icin VIDA veya VDASH gibi Volvo'ya ozel arac gerekir.")


def cmd_sil(args):
    print("Ariza kodlari ve donmus kare verisi silinecek. Ariza giderilmediyse kod geri gelir.")
    print("Silmeden once 'kodlar' ile not almaniz onerilir. Motor KAPALI, kontak ACIK olmali.")
    if input("Devam etmek icin EVET yazin: ").strip().upper() != "EVET":
        print("Iptal edildi.")
        return
    if args.demo:
        print("[DEMO] Kodlar silindi (gercek islem yapilmadi).")
        return
    import obd

    baglanti = baglan(args.port)
    baglanti.query(obd.commands.CLEAR_DTC)
    kalan = baglanti.query(obd.commands.GET_DTC).value or []
    baglanti.close()
    print("Silindi." if not kalan else f"Silme sonrasi hala {len(kalan)} kod var: {kalan}")


# ---------------------------------------------------------------- canli kayit

def demo_olcum(t):
    """Gercekci gorunen sahte surus verisi (adaptorsuz deneme icin)."""
    hizlanma = (math.sin(t / 15) + 1) / 2
    devir = 800 + hizlanma * 3800 + random.uniform(-50, 50)
    atmosfer = 101.0
    return {
        "devir_rpm": round(devir),
        "hiz_kmh": round(hizlanma * 110),
        "sogutma_c": round(min(92, 25 + t * 0.8) + random.uniform(-0.5, 0.5), 1),
        "emme_hava_c": round(28 + hizlanma * 8, 1),
        "manifold_kpa": round(35 + hizlanma * 140 + random.uniform(-3, 3), 1),
        "atmosfer_kpa": atmosfer,
        "maf_gs": round(3 + hizlanma * 80, 1),
        "yuk_yuzde": round(18 + hizlanma * 70, 1),
        "gaz_kelebegi_yuzde": round(12 + hizlanma * 75, 1),
        "atesleme_avans_derece": round(18 - hizlanma * 10, 1),
        "kisa_yakit_duz_yuzde": round(random.uniform(-4, 4), 1),
        "uzun_yakit_duz_yuzde": 2.3,
        "aku_volt": round(14.1 + random.uniform(-0.1, 0.1), 2),
    }


def cmd_kaydet(args):
    KAYIT_KLASORU.mkdir(exist_ok=True)
    dosya = KAYIT_KLASORU / f"kayit_{datetime.now():%Y%m%d_%H%M%S}{'_demo' if args.demo else ''}.csv"

    baglanti = None
    komutlar = []
    if not args.demo:
        import obd

        baglanti = baglan(args.port)
        for sutun, ad, birim in CANLI_VERILER:
            komut = getattr(obd.commands, ad)
            if baglanti.supports(komut):
                komutlar.append((sutun, komut, birim))
            else:
                print(f"  (arac desteklemiyor, atlaniyor: {sutun})")
    sutunlar = ["zaman", "saniye"] + [s for s, _, _ in CANLI_VERILER]

    print(f"\nKayit basladi -> {dosya}")
    print("Durdurmak icin Ctrl+C. Surerken ekrana BAKMAYIN; kaydi sonra inceleriz.\n")
    baslangic = time.time()
    satir_sayisi = 0
    try:
        with open(dosya, "w", newline="", encoding="utf-8") as f:
            yazici = csv.DictWriter(f, fieldnames=sutunlar)
            yazici.writeheader()
            while args.sure == 0 or time.time() - baslangic < args.sure:
                t = time.time() - baslangic
                if args.demo:
                    satir = demo_olcum(t)
                    time.sleep(args.aralik)
                else:
                    satir = {}
                    for sutun, komut, birim in komutlar:
                        cevap = baglanti.query(komut)
                        if not cevap.is_null():
                            satir[sutun] = round(cevap.value.to(birim).magnitude, 2)
                satir["zaman"] = datetime.now().strftime("%H:%M:%S")
                satir["saniye"] = round(t, 1)
                yazici.writerow(satir)
                f.flush()
                satir_sayisi += 1
                print(f"\r{satir['saniye']:>7.0f} sn  devir {satir.get('devir_rpm', '-'):>5}  "
                      f"hiz {satir.get('hiz_kmh', '-'):>4}  su {satir.get('sogutma_c', '-'):>5}C  "
                      f"aku {satir.get('aku_volt', '-')}V   ", end="", flush=True)
    except KeyboardInterrupt:
        pass
    finally:
        if baglanti:
            baglanti.close()
    print(f"\n\n{satir_sayisi} olcum kaydedildi: {dosya}")
    if satir_sayisi:
        rapor_uret(dosya)


# ---------------------------------------------------------------- rapor

def sayi(deger):
    try:
        return float(deger)
    except (TypeError, ValueError):
        return None


def rapor_uret(dosya):
    import matplotlib

    matplotlib.use("Agg")
    import matplotlib.pyplot as plt

    dosya = Path(dosya)
    with open(dosya, encoding="utf-8") as f:
        satirlar = list(csv.DictReader(f))
    if not satirlar:
        print("Kayit bos.")
        return

    def seri(ad):
        return [sayi(s.get(ad)) for s in satirlar]

    t = seri("saniye")
    turbo = [None if m is None or a is None else round((m - a) / 100, 2)
             for m, a in zip(seri("manifold_kpa"), seri("atmosfer_kpa"))]

    grafikler = [
        ("Devir (rpm)", [("devir_rpm", seri("devir_rpm"))]),
        ("Hiz (km/s)", [("hiz_kmh", seri("hiz_kmh"))]),
        ("Turbo basinci (bar, atmosfer ustu)", [("turbo", turbo)]),
        ("Sicaklik (C)", [("sogutma suyu", seri("sogutma_c")), ("emme havasi", seri("emme_hava_c"))]),
        ("Yakit duzeltmesi (%)", [("kisa", seri("kisa_yakit_duz_yuzde")), ("uzun", seri("uzun_yakit_duz_yuzde"))]),
        ("Aku / sarj (V)", [("volt", seri("aku_volt"))]),
    ]
    fig, eksenler = plt.subplots(len(grafikler), 1, figsize=(12, 2.4 * len(grafikler)), sharex=True)
    for eksen, (baslik, cizgiler) in zip(eksenler, grafikler):
        for etiket, degerler in cizgiler:
            noktalar = [(x, y) for x, y in zip(t, degerler) if x is not None and y is not None]
            if noktalar:
                eksen.plot(*zip(*noktalar), label=etiket, linewidth=1.2)
        eksen.set_title(baslik, fontsize=10, loc="left")
        eksen.grid(alpha=0.3)
        if len(cizgiler) > 1:
            eksen.legend(fontsize=8, loc="upper right")
    eksenler[2].axhline(0, color="gray", linewidth=0.6)
    eksenler[4].axhspan(-10, 10, color="green", alpha=0.07)
    eksenler[-1].set_xlabel("saniye")
    fig.suptitle(f"Volvo S60 T3 - {dosya.stem}", fontsize=12)
    fig.tight_layout()
    png = dosya.with_suffix(".png")
    fig.savefig(png, dpi=110)
    plt.close(fig)

    print("\n=== OZET ===")
    for uyari in degerlendir(satirlar, turbo):
        print(" ", uyari)
    print(f"\nGrafik: {png}")


def degerlendir(satirlar, turbo):
    def temiz(ad):
        return [v for v in (sayi(s.get(ad)) for s in satirlar) if v is not None]

    sonuc = []
    su = temiz("sogutma_c")
    if su:
        sonuc.append(f"Sogutma suyu en yuksek {max(su):.0f} C"
                     + ("  <-- 105 C ustu, sogutma sistemine baktirin!" if max(su) > 105 else ""))
        sure = max(temiz("saniye") or [0])
        if sure > 900 and max(su) < 80:
            sonuc.append("  15 dk+ surus ama su 80 C'ye ulasmamis: termostat acik kalmis olabilir.")
    tb = [v for v in turbo if v is not None]
    if tb:
        sonuc.append(f"En yuksek turbo basinci {max(tb):.2f} bar")
    uzun = temiz("uzun_yakit_duz_yuzde")
    if uzun:
        ort = sum(uzun) / len(uzun)
        not_ = ""
        if ort > 10:
            not_ = "  <-- motor fazla yakit ekliyor: hava kacagi / MAF / yakit basinci olabilir"
        elif ort < -10:
            not_ = "  <-- motor yakit kisiyor: enjektor kacirma / zengin karisim olabilir"
        sonuc.append(f"Uzun vadeli yakit duzeltmesi ortalama %{ort:+.1f} (normal: +-%10){not_}")
    volt = [v for v, d in zip((sayi(s.get("aku_volt")) for s in satirlar),
                              (sayi(s.get("devir_rpm")) for s in satirlar))
            if v is not None and d and d > 600]
    if volt:
        en_dusuk = min(volt)
        not_ = "  <-- motor calisirken 13 V alti: sarj dinamosu / aku kontrol" if en_dusuk < 13.0 else ""
        sonuc.append(f"Motor calisirken voltaj {en_dusuk:.1f}-{max(volt):.1f} V{not_}")
    return sonuc or ["Degerlendirilecek veri yok."]


def cmd_rapor(args):
    rapor_uret(args.dosya)


# ---------------------------------------------------------------- giris

def main():
    ortak = argparse.ArgumentParser(add_help=False)
    ortak.add_argument("--port", help="COM portu (orn. COM5). Verilmezse otomatik aranir.")
    ortak.add_argument("--demo", action="store_true", help="Adaptorsuz, sahte veriyle dene")
    p = argparse.ArgumentParser(description="Volvo S60 T3 OBD araci")
    alt = p.add_subparsers(dest="komut", required=True)
    alt.add_parser("portlar", help="Bilgisayardaki seri portlari listele").set_defaults(f=cmd_portlar)
    alt.add_parser("kodlar", parents=[ortak], help="Ariza kodlarini oku").set_defaults(f=cmd_kodlar)
    alt.add_parser("sil", parents=[ortak], help="Ariza kodlarini sil").set_defaults(f=cmd_sil)
    k = alt.add_parser("kaydet", parents=[ortak], help="Canli veriyi CSV'ye kaydet")
    k.add_argument("--sure", type=int, default=0, help="Saniye (0 = Ctrl+C'ye kadar)")
    k.add_argument("--aralik", type=float, default=0.5, help="Demo modunda olcum araligi (sn)")
    k.set_defaults(f=cmd_kaydet)
    r = alt.add_parser("rapor", help="Kayittan grafik ve ozet uret")
    r.add_argument("dosya")
    r.set_defaults(f=cmd_rapor)
    args = p.parse_args()
    args.f(args)


if __name__ == "__main__":
    main()
