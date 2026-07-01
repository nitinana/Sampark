package com.sampark

fun runScan(repo: ContactsRepository, transliterator: Transliterator): Int {
    var count = 0
    for (contact in repo.allWritableContacts()) {
        if (repo.hasCustomField(contact.id)) continue
        if (!containsLatinChars(contact.displayName)) continue
        val marathi = transliterator.transliterate(contact.displayName) ?: continue
        repo.updateDisplayName(contact.id, marathi)
        repo.writeCustomField(contact.id, contact.displayName, marathi)
        count++
    }
    return count
}

fun runRollback(repo: ContactsRepository) {
    for (contact in repo.allContactsWithCustomField()) {
        val current = repo.getDisplayName(contact.id) ?: continue
        if (current != contact.transliteratedName) continue
        repo.updateDisplayName(contact.id, contact.originalName)
    }
    repo.deleteAllCustomFields()
}
