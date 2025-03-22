package com.example.chooserecipientxml.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.chooserecipientxml.model.Contact
import com.example.chooserecipientxml.repository.ContactRepository
import kotlinx.coroutines.launch

class ContactViewModel(val repository: ContactRepository) : ViewModel() {

    // TODO: Inject ApiService and ContactRepository
//    private val apiService = ApiService.create()
//    private val repository = ContactRepository(apiService)

    private val _recipients = MutableLiveData<List<Contact>>()
    val recipients: LiveData<List<Contact>> get() = _recipients

    private var currentStartIndex = 0
    private val batchSize = 50 // ✅ Batch size for service recipients pagination
    private val allServiceContacts = mutableListOf<Contact>() // ✅ Store all service contacts
    private var hasMoreServiceContacts = true // ✅ Track if more service contacts exist

    fun loadServiceContacts() {
        if (!hasMoreServiceContacts) return // ✅ Stop requesting if all service contacts are loaded

        viewModelScope.launch {
            val serviceContacts = repository.fetchRecipients()

            if (serviceContacts.isNotEmpty()) {
                val updatedList = (_recipients.value ?: emptyList()).toMutableList()
                updatedList.addAll(serviceContacts)
                _recipients.postValue(updatedList)
            }

//            if (newRecipients.isNotEmpty()) {
//                val updatedList = (_recipients.value ?: emptyList()).toMutableList()
//                updatedList.addAll(newRecipients)
//                _recipients.postValue(updatedList)
//
//                currentStartIndex += batchSize
//            } else {
//                hasMoreServiceContacts = false
//
//                // ✅ Force LiveData update so `observe` is triggered
//                _recipients.postValue(_recipients.value) // ✅ Even though data is the same, this triggers observers
//            }
        }
    }
}

class ContactViewModelFactory(private val repository: ContactRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ContactViewModel::class.java)) {
            return ContactViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
