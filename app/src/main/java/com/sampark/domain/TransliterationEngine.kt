package com.sampark.domain

import android.icu.text.Transliterator

class TransliterationEngine {
    private val transliterator: Transliterator by lazy {
        Transliterator.getInstance("Latin-Devanagari")
    }

    fun transliterate(name: String): String = transliterator.transliterate(name)
}
