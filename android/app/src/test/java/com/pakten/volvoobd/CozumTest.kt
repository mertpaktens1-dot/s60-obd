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
}
