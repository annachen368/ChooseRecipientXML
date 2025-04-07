package com.example.chooserecipientxml.repository

import android.content.Context
import android.provider.ContactsContract
import android.telephony.PhoneNumberUtils
import android.util.Log
import com.example.chooserecipientxml.model.Contact
import com.example.chooserecipientxml.model.ContactSource
import com.example.chooserecipientxml.model.ContactStatusRequest
import com.example.chooserecipientxml.model.ContactTokenRequest
import com.example.chooserecipientxml.model.CustomerProfileResponse
import com.example.chooserecipientxml.model.Identifier
import com.example.chooserecipientxml.network.ApiService
import java.util.UUID

class ContactRepository(private val context: Context, private val apiService: ApiService) {

    private var cachedCustomerProfileDto: CustomerProfileResponse? = null

    // TODO: check
    suspend fun fetchCustomerProfileDto(): CustomerProfileResponse? {
        return cachedCustomerProfileDto ?: apiService.getCustomerProfile().body()?.also {
            cachedCustomerProfileDto = it
        }
    }

    suspend fun fetchServiceContacts(): List<Contact> {
        Log.d("ThreadCheck", "ServiceContactsRequest start ${Thread.currentThread().name}")
        return try {
            val response = apiService.getCustomerProfile()
            if (response.isSuccessful) {
                Log.d("ThreadCheck", "ServiceContactsRequest end ${Thread.currentThread().name}")
                response.body()?.recipients?.map { recipient ->
                    Contact(
                        id = recipient.recipientId,
                        name = recipient.displayName,
                        phoneNumber = recipient.token,
                        level = recipient.displayIndicatorList?.firstOrNull()?.level,
                        source = ContactSource.SERVICE
                    )
                } ?: emptyList()
            } else {
                emptyList() // Handle API errors gracefully
            }
        } catch (e: Exception) {
            emptyList() // Handle API errors gracefully
        }
    }

    suspend fun fetchDeviceContacts(
        startIndex: Int,
        batchSize: Int
    ): List<Contact> {
        Log.d("ThreadCheck", "DeviceContactsRequest start ${Thread.currentThread().name}")
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

        val contactMap =
            mutableMapOf<String, MutableSet<String>>() // ✅ Track unique numbers per contact

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
                    contacts.add(
                        Contact(
                            UUID.randomUUID().toString(),
                            name,
                            normalizedNumber,
                            ContactSource.DEVICE
                        )
                    )
                }
            }
        }

        Log.d("ThreadCheck", "DeviceContactsRequest end ${Thread.currentThread().name}")
        return contacts
    }

    suspend fun fetchAllDeviceContacts(tokenThumbnailMap: MutableMap<String, String>): List<Contact> {
        Log.d("ThreadCheck", "FetchAllDeviceContacts start ${Thread.currentThread().name}")
        val contacts = mutableListOf<Contact>()
        val contentResolver = context.contentResolver

        // Step 1: Map contact names to contactId and thumbnail URI
//        val tokenThumbnailMap = mutableMapOf<String, String>()

        val selection = "${ContactsContract.Data.MIMETYPE}=? OR ${ContactsContract.Data.MIMETYPE}=?"
        val selectionArgs = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE
        )

        val projection = arrayOf(
            ContactsContract.Data.DISPLAY_NAME,
            ContactsContract.Data.MIMETYPE,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Email.ADDRESS,
            ContactsContract.Contacts.PHOTO_THUMBNAIL_URI
        )

        val cursor = contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            ContactsContract.Data.DISPLAY_NAME + " ASC"
        )

        val contactMap = mutableMapOf<String, MutableSet<String>>()

        cursor?.use {
            val nameIndex = it.getColumnIndex(ContactsContract.Data.DISPLAY_NAME)
            val mimeTypeIndex = it.getColumnIndex(ContactsContract.Data.MIMETYPE)
            val phoneIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val emailIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Email.ADDRESS)
            val photoIndex = it.getColumnIndex(ContactsContract.Contacts.PHOTO_THUMBNAIL_URI)

            while (it.moveToNext()) {
                val name = it.getString(nameIndex)
                val mimeType = it.getString(mimeTypeIndex)
                val thumbnail = it.getString(photoIndex)
                val value = when (mimeType) {
                    ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE -> {
                        normalizePhoneNumber(it.getString(phoneIndex))
                    }
                    ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE -> {
                        it.getString(emailIndex)
                    }
                    else -> null
                }

                if (value != null) {
                    // TODO: check if different person with same token has different thumbnail??
                    thumbnail?.let { photo ->
                        tokenThumbnailMap[value] = photo
                    }
                    if (contactMap[name] == null) {
                        contactMap[name] = mutableSetOf()
                    }

                    if (contactMap[name]?.add(value) == true) {
                        contacts.add(
                            Contact(
                                UUID.randomUUID().toString(),
                                name,
                                value,
                                ContactSource.DEVICE,
                                thumbnail,
                            )
                        )
                    }
                }
            }
        }

        Log.d("ThreadCheck", "FetchAllDeviceContacts end ${Thread.currentThread().name}")
        return contacts
    }

    suspend fun fetchDeviceContactsByQuery(query: String): List<Contact> {
        Log.d("ThreadCheck", "DeviceContactsByQuery start ${Thread.currentThread().name}")
        val contacts = mutableListOf<Contact>()
        val contentResolver = context.contentResolver

        val selection = "(${ContactsContract.Data.MIMETYPE}=? AND ${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?) " +
                "OR (${ContactsContract.Data.MIMETYPE}=? AND ${ContactsContract.CommonDataKinds.Email.ADDRESS} LIKE ?)"
        val selectionArgs = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
            "%${normalizePhoneNumber(query)}%",
            ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE,
            "%$query%"
        )

        val projection = arrayOf(
            ContactsContract.Data.DISPLAY_NAME,
            ContactsContract.Data.MIMETYPE,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Email.ADDRESS
        )

        val cursor = contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            ContactsContract.Data.DISPLAY_NAME + " ASC"
        )

        val contactMap = mutableMapOf<String, MutableSet<String>>()

        cursor?.use {
            val nameIndex = it.getColumnIndex(ContactsContract.Data.DISPLAY_NAME)
            val mimeTypeIndex = it.getColumnIndex(ContactsContract.Data.MIMETYPE)
            val phoneIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val emailIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Email.ADDRESS)

            while (it.moveToNext()) {
                val name = it.getString(nameIndex)
                val mimeType = it.getString(mimeTypeIndex)
                val value = when (mimeType) {
                    ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE -> {
                        normalizePhoneNumber(it.getString(phoneIndex))
                    }
                    ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE -> {
                        it.getString(emailIndex)
                    }
                    else -> null
                }

                if (value != null) {
                    if (contactMap[name] == null) {
                        contactMap[name] = mutableSetOf()
                    }

                    if (contactMap[name]?.add(value) == true) {
                        contacts.add(
                            Contact(
                                UUID.randomUUID().toString(),
                                name,
                                value,
                                ContactSource.DEVICE
                            )
                        )
                    }
                }
            }
        }

        Log.d("ThreadCheck", "DeviceContactsByQuery end ${Thread.currentThread().name}")
        return contacts
    }

    suspend fun checkDeviceContactStatus(contacts: List<Contact>) {
        if (contacts.isEmpty()) return

        try {
            Log.d("ThreadCheck", "ContactStatusRequest start ${Thread.currentThread().name}, size: ${contacts.size}")
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

                contacts.forEach { contact ->
                    if (statusMap[contact.phoneNumber] == "ACTIVE") {
                        contact.status = "ACTIVE"
                    } else {
                        contact.status = "INACTIVE"
                    }
                }
                Log.d("ThreadCheck", "ContactStatusRequest end ${Thread.currentThread().name}")
            } else {
                Log.e("ThreadCheck", "API call failed: ${response.errorBody()?.string()}")
            }
        } catch (e: Exception) {
            Log.e("ThreadCheck", "Error checking contact status: ${e.message}")
        }
    }

    /**
     * ✅ Normalize phone numbers to avoid duplicates with different formats.
     */
    fun normalizePhoneNumber(phoneNumber: String): String {
        return PhoneNumberUtils.normalizeNumber(phoneNumber).replace(Regex("[^0-9+]"), "")
    }
}