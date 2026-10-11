/*
 * Copyright (c) 2026. Francisco Seixas.
 * This file is part of PsyJournal, a fork of PsychonautWiki Journal.
 *
 * PsyJournal is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * PsyJournal is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with PsyJournal.  If not, see https://www.gnu.org/licenses/gpl-3.0.en.html.
 */

package com.isaakhanimann.journal.data.room.experiences.entities

import androidx.compose.ui.graphics.Color
import androidx.room.TypeConverter
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.math.abs

// a color the user picked, as 0xRRGGBB
data class CustomColor(val rgb: Int) : AdaptiveColor {
    override val name: String get() = PREFIX + hexOf(rgb)
    override val isPreferred: Boolean get() = false

    override fun getComposeColor(isDarkTheme: Boolean): Color {
        val adjusted = themeAdjusted(rgb, isDarkTheme)
        return Color(red = adjusted shr 16 and 0xFF, green = adjusted shr 8 and 0xFF, blue = adjusted and 0xFF)
    }

    companion object {
        private const val PREFIX = "CUSTOM:"

        fun fromName(name: String): CustomColor? =
            if (name.startsWith(PREFIX)) parseHexColor(name.removePrefix(PREFIX))?.let { CustomColor(it) } else null
    }
}

// "#3FA7D6" or "3fa7d6" to 0x3FA7D6
fun parseHexColor(text: String): Int? {
    val hex = text.trim().removePrefix("#")
    if (hex.length != 6) return null
    return hex.toIntOrNull(16)
}

fun hexOf(rgb: Int): String = "#%06X".format(rgb and 0xFFFFFF)

// like the presets, a custom color is a bit lighter in dark mode and darker in light mode when it would be hard to see
fun themeAdjusted(rgb: Int, isDarkTheme: Boolean): Int {
    val (h, s, l) = rgbToHsl(rgb)
    val bounded = if (isDarkTheme) l.coerceIn(0.45f, 0.8f) else l.coerceIn(0.25f, 0.62f)
    return if (bounded == l) rgb else hslToRgb(h, s, bounded)
}

fun lightnessOf(rgb: Int): Float = rgbToHsl(rgb).third

// hue 0..360, saturation and brightness 0..1
fun rgbToHsv(rgb: Int): Triple<Float, Float, Float> {
    val r = (rgb shr 16 and 0xFF) / 255f
    val g = (rgb shr 8 and 0xFF) / 255f
    val b = (rgb and 0xFF) / 255f
    val max = maxOf(r, g, b)
    val delta = max - minOf(r, g, b)
    return Triple(hueOf(r, g, b, max, delta), if (max == 0f) 0f else delta / max, max)
}

fun hsvToRgb(hue: Float, saturation: Float, value: Float): Int {
    val c = value * saturation
    return fromChroma(hue, c, value - c)
}

private fun rgbToHsl(rgb: Int): Triple<Float, Float, Float> {
    val r = (rgb shr 16 and 0xFF) / 255f
    val g = (rgb shr 8 and 0xFF) / 255f
    val b = (rgb and 0xFF) / 255f
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min
    val l = (max + min) / 2
    val s = if (delta == 0f) 0f else delta / (1 - abs(2 * l - 1))
    return Triple(hueOf(r, g, b, max, delta), s, l)
}

private fun hslToRgb(hue: Float, saturation: Float, lightness: Float): Int {
    val c = (1 - abs(2 * lightness - 1)) * saturation
    return fromChroma(hue, c, lightness - c / 2)
}

private fun hueOf(r: Float, g: Float, b: Float, max: Float, delta: Float): Float {
    if (delta == 0f) return 0f
    val h = when (max) {
        r -> 60 * (((g - b) / delta) % 6)
        g -> 60 * ((b - r) / delta + 2)
        else -> 60 * ((r - g) / delta + 4)
    }
    return if (h < 0) h + 360 else h
}

private fun fromChroma(hue: Float, c: Float, m: Float): Int {
    val x = c * (1 - abs((hue / 60) % 2 - 1))
    val (r, g, b) = when ((hue / 60).toInt().coerceIn(0, 5)) {
        0 -> Triple(c, x, 0f)
        1 -> Triple(x, c, 0f)
        2 -> Triple(0f, c, x)
        3 -> Triple(0f, x, c)
        4 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    fun channel(v: Float) = ((v + m) * 255).let { Math.round(it) }.coerceIn(0, 255)
    return (channel(r) shl 16) or (channel(g) shl 8) or channel(b)
}

// the database keeps the same text the preset enum used to be stored as
class AdaptiveColorConverter {
    @TypeConverter
    fun fromColor(color: AdaptiveColor): String = color.name

    @TypeConverter
    fun toColor(name: String): AdaptiveColor = AdaptiveColor.fromName(name) ?: AdaptiveColor.RED
}

// backups keep the same text too, so old backups still import
object AdaptiveColorSerializer : KSerializer<AdaptiveColor> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("AdaptiveColor", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: AdaptiveColor) = encoder.encodeString(value.name)
    override fun deserialize(decoder: Decoder): AdaptiveColor =
        AdaptiveColor.fromName(decoder.decodeString()) ?: AdaptiveColor.RED
}
