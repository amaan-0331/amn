package dev.amaan.amn.widgets.core

import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/** Colors are ARGB ints. [contrast] is the worst-case ratio of [ink] against both stops. */
data class WidgetPalette(val top: Int, val bottom: Int, val ink: Int, val contrast: Double)

object Palettes {
    const val MIN_CONTRAST = 4.5

    private class Stops(val top: Int, val bottom: Int, val ink: Int)
    private fun s(top: Long, bottom: Long, ink: Long) = Stops(top.toInt(), bottom.toInt(), ink.toInt())

    private val day = mapOf(
        Condition.CLEAR to s(0xFFFFD25A, 0xFFFF8F3A, 0xFF3A1500),
        Condition.PARTLY to s(0xFF8CCBFF, 0xFFFFD889, 0xFF0E2A47),
        Condition.CLOUDY to s(0xFFC9D3E2, 0xFF8E9DB5, 0xFF18202E),
        Condition.RAIN to s(0xFF4E70AE, 0xFF2D4677, 0xFFF3F7FF),
        Condition.STORM to s(0xFF4A3B7A, 0xFF1D1838, 0xFFF6F0FF),
        Condition.SNOW to s(0xFFEEF4FF, 0xFFBCD0EE, 0xFF14305C),
        Condition.FOG to s(0xFFE2DCD1, 0xFFADA699, 0xFF2A2621),
    )
    private val night = mapOf(
        Condition.CLEAR to s(0xFF101B52, 0xFF2C2775, 0xFFEEF0FF),
        Condition.PARTLY to s(0xFF17265A, 0xFF3B4380, 0xFFEEF1FF),
        Condition.CLOUDY to s(0xFF2C3446, 0xFF161B26, 0xFFE9EDF5),
        Condition.RAIN to s(0xFF1C2A48, 0xFF0B1324, 0xFFE8EFFF),
        Condition.STORM to s(0xFF1E1236, 0xFF08060F, 0xFFF3ECFF),
        Condition.SNOW to s(0xFF2D3D63, 0xFF141E37, 0xFFEAF2FF),
        Condition.FOG to s(0xFF3B3934, 0xFF1E1C19, 0xFFEEEAE2),
    )
    private val clearDawn = s(0xFFFFB59A, 0xFFFFDFAE, 0xFF3B1209)
    private val clearDusk = s(0xFFC2456B, 0xFF3E2A7A, 0xFFFFF1EC)

    private val lightInk = 0xFFF7F5FF.toInt()
    private val darkInk = 0xFF141824.toInt()
    private val darkBase = 0xFF0A0C15.toInt()
    private val black = 0xFF07080F.toInt()
    private val white = 0xFFFFFFFF.toInt()

    fun of(condition: Condition, phase: Phase, dark: Boolean): WidgetPalette {
        var top: Int
        var bottom: Int
        val preferred: Int
        when {
            phase == Phase.NIGHT -> night.getValue(condition).let { top = it.top; bottom = it.bottom; preferred = it.ink }
            condition == Condition.CLEAR && phase == Phase.DAWN -> clearDawn.let { top = it.top; bottom = it.bottom; preferred = it.ink }
            condition == Condition.CLEAR && phase == Phase.DUSK -> clearDusk.let { top = it.top; bottom = it.bottom; preferred = it.ink }
            else -> day.getValue(condition).let {
                top = it.top; bottom = it.bottom; preferred = it.ink
                if (phase == Phase.DAWN) { top = mix(top, 0xFFFF9E7A.toInt(), .38); bottom = mix(bottom, 0xFFFFD9A8.toInt(), .25) }
                if (phase == Phase.DUSK) { top = mix(top, 0xFFC2456B.toInt(), .35); bottom = mix(bottom, 0xFF3E2A7A.toInt(), .45) }
            }
        }
        if (dark && phase != Phase.NIGHT) {
            // Dawn and dusk are already deep; darken them less so the sunrise and sunset colors survive.
            val soft = phase == Phase.DAWN || phase == Phase.DUSK
            top = mix(top, darkBase, if (soft) .32 else .55)
            bottom = mix(bottom, darkBase, if (soft) .40 else .62)
        }
        var (ink, ratio) = pickInk(top, bottom, preferred)
        var guard = 0
        // Contrast guard: push both stops away from the text color until it passes.
        while (ratio < MIN_CONTRAST && guard < 14) {
            val toward = if (luminance(ink) > .5) black else white
            top = mix(top, toward, .08)
            bottom = mix(bottom, toward, .08)
            pickInk(top, bottom, ink).also { ink = it.first; ratio = it.second }
            guard++
        }
        return WidgetPalette(top, bottom, ink, ratio)
    }

    private fun pickInk(top: Int, bottom: Int, preferred: Int): Pair<Int, Double> {
        fun score(c: Int) = min(contrast(c, top), contrast(c, bottom))
        if (score(preferred) >= MIN_CONTRAST) return preferred to score(preferred)
        return listOf(preferred, lightInk, darkInk).map { it to score(it) }.maxBy { it.second }
    }

    fun mix(a: Int, b: Int, t: Double): Int {
        fun ch(c: Int, shift: Int) = (c shr shift) and 0xFF
        fun lerp(shift: Int) = (ch(a, shift) + (ch(b, shift) - ch(a, shift)) * t).roundToInt().coerceIn(0, 255)
        return (0xFF shl 24) or (lerp(16) shl 16) or (lerp(8) shl 8) or lerp(0)
    }

    fun luminance(c: Int): Double {
        fun lin(v: Int): Double { val x = v / 255.0; return if (x <= .03928) x / 12.92 else ((x + .055) / 1.055).pow(2.4) }
        return .2126 * lin((c shr 16) and 0xFF) + .7152 * lin((c shr 8) and 0xFF) + .0722 * lin(c and 0xFF)
    }

    fun contrast(a: Int, b: Int): Double {
        val x = luminance(a); val y = luminance(b)
        return (maxOf(x, y) + .05) / (minOf(x, y) + .05)
    }
}
