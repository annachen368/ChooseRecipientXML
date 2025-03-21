package com.example.chooserecipientxml.repository

import com.example.chooserecipientxml.model.Contact
import com.example.chooserecipientxml.model.ContactSource
import com.example.chooserecipientxml.network.ApiService

class ContactRepository(private val apiService: ApiService) {
    suspend fun fetchRecipients(): List<Contact> {
        return try {
            val response = apiService.getCustomerProfile()
            if (response.isSuccessful) {
                response.body()?.recipients?.map { recipient ->
                    Contact(
                        id = recipient.recipientId,
                        name = recipient.displayName,
                        phoneNumber = recipient.token,
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
}