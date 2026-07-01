package com.sampark

data class Contact(val id: Long, val displayName: String)

data class ContactWithCustomField(
    val id: Long,
    val originalName: String,
    val transliteratedName: String
)

interface ContactsRepository {
    fun allWritableContacts(): List<Contact>
    fun hasCustomField(contactId: Long): Boolean
    fun updateDisplayName(contactId: Long, newName: String)
    fun writeCustomField(contactId: Long, originalName: String, marathi: String)
    fun deleteAllCustomFields()
    fun allContactsWithCustomField(): List<ContactWithCustomField>
    fun getDisplayName(contactId: Long): String?
}
