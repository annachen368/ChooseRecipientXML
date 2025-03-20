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

    // TODO: Inject ApiService and ContactRepository
    private val apiService = ApiService.create()
    private val repository = ContactRepository(apiService)

    private val _recipients = MutableLiveData<List<Contact>>()
    val recipients: LiveData<List<Contact>> get() = _recipients

    private var currentStartIndex = 0
    private val batchSize = 50 // ✅ Batch size for service recipients pagination
    private val allServiceContacts = mutableListOf<Contact>() // ✅ Store all service contacts
    private var hasMoreServiceContacts = true // ✅ Track if more service contacts exist

    fun loadMoreRecipients() {
        if (!hasMoreServiceContacts) return // ✅ Stop requesting if all service contacts are loaded

        viewModelScope.launch {
            val newRecipients = repository.fetchRecipients(currentStartIndex, batchSize)

            if (newRecipients.isNotEmpty()) {
                val updatedList = (_recipients.value ?: emptyList()).toMutableList()
                updatedList.addAll(newRecipients)
                _recipients.postValue(updatedList)

                currentStartIndex += batchSize
            } else {
                hasMoreServiceContacts = false

                // ✅ Force LiveData update so `observe` is triggered
                _recipients.postValue(_recipients.value) // ✅ Even though data is the same, this triggers observers
            }
        }
    }

    fun hasMoreServiceContacts(): Boolean {
        return hasMoreServiceContacts
    }
}