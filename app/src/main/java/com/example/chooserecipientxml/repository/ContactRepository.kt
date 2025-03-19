package com.example.chooserecipientxml.repository

import com.example.chooserecipientxml.model.Contact
import com.example.chooserecipientxml.model.ContactSource
import com.example.chooserecipientxml.network.ApiService

class ContactRepository(private val apiService: ApiService) {
    suspend fun fetchRecipients(start: Int, limit: Int): List<Contact> {
        return try {
            val response = apiService.getRecipients(start, limit)
            if (response.isSuccessful) {
                response.body()?.map { contact ->
                    contact.source = ContactSource.SERVICE // ✅ Assign SERVICE source in Android
                    contact
                } ?: emptyList()
            } else {
                emptyList() // Handle API errors gracefully
            }
        } catch (e: Exception) {
            emptyList() // Handle API errors gracefully
        }
    }
}