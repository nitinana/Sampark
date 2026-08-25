package com.sampark.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * Converts an OKLCH color (as used verbatim in the design file at
 * docs/design/Swarra Anande Static Gallery/Marathi Contacts App.dc.html)
 * to a Compose Color, via OKLab -> linear sRGB -> gamma-corrected sRGB.
 * Reference: Björn Ottosson, https://bottosson.github.io/posts/oklab/
 */
fun oklch(lightness: Float, chroma: Float, hueDegrees: Float, alpha: Float = 1f): Color {
    val hueRad = Math.toRadians(hueDegrees.toDouble())
    val a = (chroma * cos(hueRad)).toFloat()
    val b = (chroma * sin(hueRad)).toFloat()

    val lPrime = lightness + 0.3963377774f * a + 0.2158037573f * b
    val mPrime = lightness - 0.1055613458f * a - 0.0638541728f * b
    val sPrime = lightness - 0.0894841775f * a - 1.2914855480f * b

    val l = lPrime * lPrime * lPrime
    val m = mPrime * mPrime * mPrime
    val s = sPrime * sPrime * sPrime

    val rLinear = 4.0767416621f * l - 3.3077115913f * m + 0.2309699292f * s
    val gLinear = -1.2684380046f * l + 2.6097574011f * m - 0.3413193965f * s
    val bLinear = -0.0041960863f * l - 0.7034186147f * m + 1.7076147010f * s

    return Color(
        red = linearToSrgb(rLinear),
        green = linearToSrgb(gLinear),
        blue = linearToSrgb(bLinear),
        alpha = alpha
    )
}

private fun linearToSrgb(component: Float): Float {
    val clamped = component.coerceIn(0f, 1f)
    return if (clamped <= 0.0031308f) {
        clamped * 12.92f
    } else {
        (1.055f * clamped.toDouble().pow(1.0 / 2.4) - 0.055f).toFloat()
    }
}
