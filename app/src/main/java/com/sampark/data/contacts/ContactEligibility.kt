package com.sampark.data.contacts

/**
 * Requirement.md "Contact eligibility for translation" — every skip rule
 * decided there, implemented as one pure function.
 */
object ContactEligibility {

    private val urlOrEmailPattern = Regex(
        "(https?://|www\\.|[a-zA-Z0-9.+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})"
    )

    fun isEligibleForTranslation(name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return false
        if (!containsAsciiLatinLetter(trimmed)) return false
        if (containsNonAsciiLetter(trimmed)) return false
        if (urlOrEmailPattern.containsMatchIn(trimmed)) return false
        if (isAllCapsAcronym(trimmed)) return false
        if (isBareInitials(trimmed)) return false
        if (looksLeetspeak(trimmed)) return false
        return true
    }

    private fun containsAsciiLatinLetter(s: String): Boolean =
        s.any { it in 'A'..'Z' || it in 'a'..'z' }

    /**
     * Catches already-Devanagari/other-Indic-script letters (mixed-script
     * names) AND accented Latin letters like "é"/"ñ" (non-English
     * Latin-script names) — both are letters but outside plain ASCII A-Z/a-z,
     * which is exactly the line Requirement.md drew for "skip, don't guess."
     */
    private fun containsNonAsciiLetter(s: String): Boolean =
        s.any { ch -> ch.isLetter() && ch !in 'A'..'Z' && ch !in 'a'..'z' }

    private fun isAllCapsAcronym(s: String): Boolean {
        val tokens = s.split(Regex("\\s+")).filter { it.isNotBlank() }
        return tokens.any { token ->
            val letters = token.filter { it.isLetter() }
            letters.length >= 2 && letters.all { it.isUpperCase() }
        }
    }

    private fun isBareInitials(s: String): Boolean {
        val tokens = s.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return false
        return tokens.all { token -> token.trim('.', ',').length <= 1 }
    }

    private fun looksLeetspeak(s: String): Boolean {
        val tokens = s.split(Regex("\\s+"))
        return tokens.any { token ->
            val hasLetter = token.any { it.isLetter() }
            val hasDigitOrSymbol = token.any { it.isDigit() || it in "@_$" }
            hasLetter && hasDigitOrSymbol
        }
    }
}
