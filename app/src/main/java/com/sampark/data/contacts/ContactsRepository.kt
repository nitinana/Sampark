package com.sampark.data.contacts

data class ContactRef(val lookupKey: String, val name: String)

/**
 * All methods are `suspend` because every implementation of this interface talks
 * to the system ContactsProvider over binder IPC — hundreds to thousands of
 * synchronous calls for a real contact list. Implementations must move that work
 * off the main thread (see [AndroidContactsRepository], which wraps each body in
 * `withContext(Dispatchers.IO)`).
 */
interface ContactsRepository {
    suspend fun hasContactsPermission(): Boolean
    suspend fun getEligibleContacts(): List<ContactRef>
    suspend fun getCurrentName(lookupKey: String): String?
    suspend fun updateName(lookupKey: String, newName: String)
}
