package com.example.chooserecipientxml.utils

import android.content.Context
import android.provider.ContactsContract
import android.telephony.PhoneNumberUtils
import com.example.chooserecipientxml.model.Contact
import com.example.chooserecipientxml.model.ContactSource
import java.util.UUID
import kotlin.random.Random

// ✅ Global variable to track total fake contacts generated
private var totalFakeContactsGenerated = 0
private const val MAX_FAKE_CONTACTS = 200

fun getDeviceContacts(context: Context, startIndex: Int, batchSize: Int): List<Contact> {
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
    if (contacts.size < batchSize && totalFakeContactsGenerated < MAX_FAKE_CONTACTS) {
        val remainingFakeContacts = minOf(batchSize - contacts.size, MAX_FAKE_CONTACTS - totalFakeContactsGenerated)

        if (remainingFakeContacts > 0) {
            contacts.addAll(generateFakeContacts(totalFakeContactsGenerated, remainingFakeContacts))
            totalFakeContactsGenerated += remainingFakeContacts // ✅ Update total fake count
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
