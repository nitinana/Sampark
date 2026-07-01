package com.sampark

import android.icu.text.Transliterator as IcuT

class IcuTransliterator : Transliterator {
    private val engine = IcuT.getInstance("Latin-Devanagari")

    override fun transliterate(name: String): String? = try {
        engine.transliterate(name).takeIf { it.isNotBlank() }
    } catch (e: Exception) {
        null
    }
}
