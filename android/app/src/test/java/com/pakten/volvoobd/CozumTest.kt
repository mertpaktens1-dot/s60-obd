package com.pakten.volvoobd

import org.junit.Assert.assertEquals
import org.junit.Test

class CozumTest {
    @Test
    fun tekCerceveIkiModul() {
        // 7DF'ye 0100: motor ve sanziman ayri yanit verir
        val r = isoTpCoz(listOf("7E8 06 41 00 BE 3F A8 13", "7E9 06 41 00 98 18 80 11", "NO DATA"))
        assertEquals(setOf("7E8", "7E9"), r.keys)
        assertEquals(listOf(0x41, 0x00, 0xBE, 0x3F, 0xA8, 0x13), r["7E8"]!!.toList())
    }

    @Test
    fun cokCerceveVin() {
        val r = isoTpCoz(listOf(
            "7E8 10 14 49 02 01 59 56 31",
            "7E8 21 46 53 34 30 44 41 32",
            "7E8 22 31 32 33 34 35 36 37",
        ))
        val v = r["7E8"]!!
        assertEquals(20, v.size)
        assertEquals("YV1FS40DA21234567", v.takeLast(17).map { it.toChar() }.joinToString(""))
    }

    @Test
    fun arizaKodlari() {
        val r = isoTpCoz(listOf("7E8 06 43 02 01 71 04 55", "7E9 02 43 00"))
        assertEquals(listOf("P0171", "P0455"), dtcListesi(r["7E8"]!!, can = true))
        assertEquals(emptyList<String>(), dtcListesi(r["7E9"]!!, can = true))
        assertEquals("U0100", dtcCoz(0xC1, 0x00))
        assertEquals("C0035", dtcCoz(0x40, 0x35))
    }

    @Test
    fun pidFormulleri() {
        val devir = PIDLER.first { it.kod == 0x0C }.coz(intArrayOf(0x1A, 0xF8))
        assertEquals(1726.0, devir, 0.01)
        val volt = PIDLER.first { it.kod == 0x42 }.coz(intArrayOf(0x37, 0x46))
        assertEquals(14.15, volt, 0.01)
    }

    @Test
    fun cokluPid() {
        val r = isoTpCoz(listOf("7E8 10 0B 41 0C 1A F8 0D 40", "7E8 21 05 7B 42 37 46 00 00"))
        val c = cokluCoz(r["7E8"]!!, 0x41)
        assertEquals(listOf(0x1A, 0xF8), c[0x0C]!!.toList())
        assertEquals(listOf(0x40), c[0x0D]!!.toList())
        assertEquals(listOf(0x7B), c[0x05]!!.toList())
        assertEquals(listOf(0x37, 0x46), c[0x42]!!.toList())
    }

    @Test
    fun donmusKare() {
        val c = cokluCoz(intArrayOf(0x42, 0x02, 0x00, 0x01, 0x71), 0x42, kareBayti = true)
        assertEquals("P0171", dtcCoz(c[0x02]!![0], c[0x02]!![1]))
    }

    @Test
    fun hazirlik() {
        // A=81 (lamba + 1 kod), B=07 (3 ortak test destekli, hepsi tamam), C=65 (katalizor, EVAP, O2, O2 isitici), D=04 (EVAP eksik)
        val l = hazirlikCoz(intArrayOf(0x81, 0x07, 0x65, 0x04))
        assertEquals(7, l.size)
        assertEquals(listOf("Yakıt buharı (EVAP)"), l.filter { !it.tamam }.map { it.ad })
    }

    @Test
    fun mod06Tekleme() {
        val v = intArrayOf(0x46, 0xA4, 0x0B, 0x24, 0x00, 0x09, 0x00, 0x00, 0xFF, 0xFF,
            0xA4, 0x0C, 0x24, 0x00, 0x04, 0x00, 0x00, 0xFF, 0xFF)
        val t = mod06Coz(v)
        assertEquals(9, t.first { it.tid == 0x0B }.deger)
        assertEquals(4, t.first { it.tid == 0x0C }.deger)
    }

    @Test
    fun udsGecmis() {
        val k = uds19Coz(intArrayOf(0x59, 0x02, 0xFF, 0x01, 0x71, 0x00, 0x09, 0x03, 0x03, 0x00, 0x28))!!
        assertEquals("P0171-00", k[0].kod)
        assertEquals(true, k[0].aktif)
        assertEquals("P0303-00", k[1].kod)
        assertEquals(false, k[1].aktif)
        assertEquals(null, uds19Coz(intArrayOf(0x7F, 0x19, 0x11)))
    }

    @Test
    fun d2IstekCercevesi() {
        // hackingvolvo: CEM akü voltajı isteği "cd 40 a6 1a 02 01 00 00"
        assertEquals("CD 40 A6 1A 02 01 00 00", d2Istek(0x40, 0xA6, 0x1A, 0x02, 0x01))
        assertEquals("CB 40 B9 F0 00 00 00 00", d2Istek(0x40, 0xB9, 0xF0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun d2YazmaServisiEngelli() {
        d2Istek(0x40, 0xB1, 0x5F, 0x3B, 0x01, 0x01, 0x84)   // cikis kontrolu: kapali olmali
    }

    @Test
    fun d2KonsolGuvenligi() {
        assertEquals(true, d2IstekGuvenliMi("CB 40 B9 F0 00 00 00 00"))
        assertEquals(false, d2IstekGuvenliMi("CF 40 B1 5F 3B 01 01 84"))
        assertEquals(false, d2IstekGuvenliMi("CB 40 B9"))
    }

    @Test
    fun d2TekCerceveYanit() {
        // Tigo2000/Volvo-VIDA: ECM yaniti
        val r = d2Coz(listOf("00 40 00 21 CD 7A E6 12 9D 95 00 00", "NO DATA"))
        assertEquals(listOf(0x7A, 0xE6, 0x12, 0x9D, 0x95), r["00400021"]!!.toList())
    }

    @Test
    fun d2CokCerceveYanit() {
        val r = d2Coz(listOf(
            "00 80 00 03 8F 40 F9 F0 33 30 37",
            "00 80 00 03 0F 38 31 32 33 34 20",
            "00 80 00 03 4A 41 42 00 00 00 00",
        ))
        val v = r["00800003"]!!
        assertEquals(0x40, v[0]); assertEquals(0xF9, v[1])
        assertEquals("30781234 AB", d2Kimlik(v))
    }
}
