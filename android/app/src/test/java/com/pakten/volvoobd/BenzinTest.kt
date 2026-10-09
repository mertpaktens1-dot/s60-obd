package com.pakten.volvoobd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BenzinTest {
    private fun takip() = BenzinTakip(File.createTempFile("benzin", ".json").apply { delete(); deleteOnExit() })

    /** Yuksek yukte [n] olcum besler; [dususlu] ise her 10 olcumde bir avansi 5 derece dusurur. */
    private fun besle(b: BenzinTakip, n: Int, avans: Double, dususlu: Boolean, bas: Double = 0.0) {
        for (i in 0 until n) {
            val a = if (dususlu && i % 10 == 9) avans - 5 else avans
            b.isle(bas + i * 0.5, 3500.0, 90.0, 85.0, a, 30.0)
        }
    }

    @Test
    fun dusukYukSayilmaz() {
        val b = takip()
        b.yakitAldim("A")
        repeat(50) { b.isle(it * 0.5, 1800.0, 30.0, 40.0, 20.0, 25.0) }
        assertEquals(0, b.aktif!!.ornek)
    }

    @Test
    fun dususSayimi() {
        val b = takip()
        b.yakitAldim("A")
        besle(b, 100, 12.0, dususlu = true)
        assertEquals(100, b.aktif!!.ornek)
        assertEquals(10, b.aktif!!.dusus)
    }

    @Test
    fun siralama() {
        val b = takip()
        b.yakitAldim("Kotu Petrol")
        besle(b, 80, 8.0, dususlu = true)
        b.yakitAldim("Iyi Petrol 97")
        besle(b, 80, 12.0, dususlu = false, bas = 1000.0)
        b.yakitAldim("Az veri")
        besle(b, 10, 15.0, dususlu = false, bas = 2000.0)
        val s = b.siralama()
        assertEquals(listOf("Iyi Petrol 97", "Kotu Petrol"), s.map { it.istasyon })
        assertTrue(s[0].dususDakika < s[1].dususDakika)
    }

    @Test
    fun kalicilik() {
        val dosya = File.createTempFile("benzin", ".json").apply { delete(); deleteOnExit() }
        BenzinTakip(dosya).apply { yakitAldim("Shell"); besle(this, 70, 11.0, false); kaydet() }
        val yeniden = BenzinTakip(dosya)
        assertEquals("Shell", yeniden.aktif!!.istasyon)
        assertEquals(70, yeniden.aktif!!.ornek)
    }
}
