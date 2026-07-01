package com.sampark

enum class AppScreen { SETUP, SCANNING, ACTIVE, INACTIVE }

fun resolveAppScreen(prefs: AppPreferences, repo: ContactsRepository): AppScreen {
    if (!prefs.setupComplete) return AppScreen.SETUP
    if (prefs.scanInProgress) return AppScreen.SCANNING
    return if (repo.allContactsWithCustomField().isNotEmpty()) AppScreen.ACTIVE
    else AppScreen.INACTIVE
}
