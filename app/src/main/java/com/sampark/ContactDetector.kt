package com.sampark

fun containsLatinChars(name: String): Boolean {
    return name.any { it in 'A'..'Z' || it in 'a'..'z' }
}
