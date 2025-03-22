package com.example.chooserecipientxml.utils

import android.content.Context
import android.provider.ContactsContract
import android.telephony.PhoneNumberUtils
import android.util.Log
import com.example.chooserecipientxml.model.Contact
import com.example.chooserecipientxml.model.ContactSource
import com.example.chooserecipientxml.model.ContactStatusRequest
import com.example.chooserecipientxml.model.ContactTokenRequest
import com.example.chooserecipientxml.model.Identifier
import com.example.chooserecipientxml.network.ApiService
import com.example.chooserecipientxml.repository.ContactRepository
import java.util.UUID

// TODO: Inject ApiService and ContactRepository
private val apiService = ApiService.create()
private val repository = ContactRepository(apiService)

suspend fun getDeviceContacts(context: Context, startIndex: Int, batchSize: Int): List<Contact> {
    val contacts = mutableListOf<Contact>()
    val contentResolver = context.contentResolver
    val cursor = contentResolver.query(
        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
        arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        ),
        null,
        null,
        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC LIMIT $batchSize OFFSET $startIndex"
    )

    val contactMap = mutableMapOf<String, MutableSet<String>>() // ✅ Track unique numbers per contact

    cursor?.use {
        val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
        val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

        while (it.moveToNext()) {
            val name = it.getString(nameIndex)
            val number = it.getString(numberIndex)

            val normalizedNumber = normalizePhoneNumber(number) // ✅ Normalize number

            if (contactMap[name] == null) {
                contactMap[name] = mutableSetOf()
            }

            // ✅ Only add the contact if the number is unique for this person
            if (contactMap[name]?.add(normalizedNumber) == true) {
                contacts.add(Contact(UUID.randomUUID().toString(), name, normalizedNumber, ContactSource.DEVICE))
            }
        }
    }

    // 🔄 Call backend service to get ACTIVE status // TODO
    if (contacts.isNotEmpty()) {
        try {
            Log.d("ThreadCheck", "ContactStatusRequest ${Thread.currentThread().name}")
            val request = ContactStatusRequest(
                contactTokens = contacts.map {
                    ContactTokenRequest(
                        identifier = Identifier("MOBILE", it.phoneNumber)
                    )
                }
            )

            val response = apiService.getContactStatus(request)

            if (response.isSuccessful) {
                val body = response.body()
                val statusMap = body?.contactTokens?.associateBy(
                    { it.identifier.value },
                    { it.contactStatus?.value }
                ) ?: emptyMap()

                // ✅ Update contact list with ACTIVE status
                contacts.forEach { contact ->
                    if (statusMap[contact.phoneNumber] == "ACTIVE") {
                        contact.status = "ACTIVE"
                    }
                }
            } else {
                Log.e("ContactCheck", "API call failed: ${response.errorBody()?.string()}")
            }
        } catch (e: Exception) {
            Log.e("ContactCheck", "Error checking contact status: ${e.message}")
        }
    }

    return contacts
}

/**
 * ✅ Normalize phone numbers to avoid duplicates with different formats.
 */
fun normalizePhoneNumber(phoneNumber: String): String {
    return PhoneNumberUtils.normalizeNumber(phoneNumber).replace(Regex("[^0-9+]"), "")
}
