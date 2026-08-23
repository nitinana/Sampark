package com.sampark.data.contacts

data class ContactRef(val lookupKey: String, val name: String)

interface ContactsRepository {
    fun hasContactsPermission(): Boolean
    fun getEligibleContacts(): List<ContactRef>
    fun getCurrentName(lookupKey: String): String?
    fun updateName(lookupKey: String, newName: String)
}
