package com.example.chooserecipientxml.utils

import android.content.Context
import android.provider.ContactsContract
import com.example.chooserecipientxml.model.Contact
import com.example.chooserecipientxml.model.ContactSource
import java.util.UUID

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

    cursor?.use {
        val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
        val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

        while (it.moveToNext()) {
            val name = it.getString(nameIndex)
            val number = it.getString(numberIndex)

            contacts.add(Contact(UUID.randomUUID().toString(), name, number, ContactSource.DEVICE))
        }
    }
    return contacts
}
