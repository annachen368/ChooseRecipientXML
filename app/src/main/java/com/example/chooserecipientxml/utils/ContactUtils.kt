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
import kotlin.random.Random

// ✅ Global variable to track total fake contacts generated
private var totalFakeContactsGenerated = 0
private const val MAX_FAKE_CONTACTS = 200

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

    // ✅ If real contacts are fewer than the requested batch, generate fake contacts
    // These list will not be added to the list of real contacts and not sorted in ASC with the real contacts
//    if (contacts.size < batchSize && totalFakeContactsGenerated < MAX_FAKE_CONTACTS) {
//        val remainingFakeContacts = minOf(batchSize - contacts.size, MAX_FAKE_CONTACTS - totalFakeContactsGenerated)
//
//        if (remainingFakeContacts > 0) {
//            contacts.addAll(generateFakeContacts(totalFakeContactsGenerated, remainingFakeContacts))
//            totalFakeContactsGenerated += remainingFakeContacts // ✅ Update total fake count
//        }
//    }

    // 🔄 Call backend service to get ACTIVE status
    if (contacts.isNotEmpty()) {
        try {
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

/**
 * ✅ Generates fake contacts but stops at 200 total
 */
fun generateFakeContacts(startIndex: Int, count: Int): List<Contact> {
    val fakeContacts = mutableListOf<Contact>()

    repeat(count) {
        val index = startIndex + it // ✅ Ensure correct numbering
        fakeContacts.add(
            Contact(
                id = UUID.randomUUID().toString(),
                name = "Fake User $index",
                phoneNumber = "+847420${Random.nextInt(1000, 9999)}",
                source = ContactSource.DEVICE
            )
        )
    }

    return fakeContacts
}
