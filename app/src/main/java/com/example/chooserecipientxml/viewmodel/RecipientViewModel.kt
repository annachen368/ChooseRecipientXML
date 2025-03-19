package com.example.chooserecipientxml.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chooserecipientxml.model.Contact
import com.example.chooserecipientxml.network.ApiService
import com.example.chooserecipientxml.repository.ContactRepository
import kotlinx.coroutines.launch

class RecipientViewModel : ViewModel() {

    private val apiService = ApiService.create()
    private val repository = ContactRepository(apiService)

    private val _recipients = MutableLiveData<List<Contact>>()
    val recipients: LiveData<List<Contact>> get() = _recipients

    private var currentStartIndex = 0
    private val batchSize = 50
    private val allServiceContacts = mutableListOf<Contact>() // ✅ Store all contacts

    fun loadMoreRecipients() {
        viewModelScope.launch {
            val newRecipients = repository.fetchRecipients(currentStartIndex, batchSize)

            if (newRecipients.isNotEmpty()) {
                allServiceContacts.addAll(newRecipients) // ✅ Append data
                _recipients.postValue(allServiceContacts.toList()) // ✅ Update UI safely
                currentStartIndex += batchSize
            }
        }
    }
}