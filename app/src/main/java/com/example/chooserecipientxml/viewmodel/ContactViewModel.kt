package com.example.chooserecipientxml.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.chooserecipientxml.model.Contact
import com.example.chooserecipientxml.repository.ContactRepository
import com.example.chooserecipientxml.utils.getDeviceContacts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ContactViewModel(private val repository: ContactRepository) : ViewModel() {

    // Service recipient but filter by level has value
    private val _serverRecentContacts = MutableLiveData<List<Contact>>() // TODO: don't use livedata, use stateflow can benefit more. Use sharedFlow is you want to send event to ui
    val serverRecentContacts: LiveData<List<Contact>> get() = _serverRecentContacts

    // Service recipients but filter by not having level
    private val _serverMyContacts = MutableLiveData<List<Contact>>()
    val serverMyContacts: LiveData<List<Contact>> get() = _serverMyContacts

    private val _deviceContacts = MutableLiveData<List<Contact>>()
    val deviceContacts: LiveData<List<Contact>> get() = _deviceContacts

    private val _deviceActiveContacts = MutableLiveData<List<Contact>>()
    val deviceActiveContacts: LiveData<List<Contact>> get() = _deviceActiveContacts

    private val _isDeviceContactsLoaded = MutableLiveData<Boolean>()
    val isDeviceContactsLoaded: LiveData<Boolean> get() = _isDeviceContactsLoaded

    fun loadServiceContacts() {
        viewModelScope.launch(Dispatchers.IO) {
            Log.d("ThreadCheck", "loadServiceContacts ${Thread.currentThread().name}")
            val newRecipients = repository.fetchRecipients()
            if (newRecipients.isNotEmpty()) {
                _serverRecentContacts.postValue(newRecipients.filter { it.level != null })
                _serverMyContacts.postValue(newRecipients.filter { it.level == null })
            }
        }
    }

    fun loadDeviceContacts(context: Context, startIndex: Int, batchSize: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            Log.d("ThreadCheck", "loadDeviceContacts ${Thread.currentThread().name}")
            _isDeviceContactsLoaded.postValue(false) // ✅ Notify UI that loading is started
            val newDeviceContacts = getDeviceContacts(context, startIndex, batchSize)
            if (newDeviceContacts.isNotEmpty()) {
                _deviceContacts.postValue(newDeviceContacts)
                _deviceActiveContacts.postValue(newDeviceContacts.filter { it.status == "ACTIVE" })
            }
            _isDeviceContactsLoaded.postValue(true) // ✅ Notify UI that loading is done
        }
    }
}

class ContactViewModelFactory(private val repository: ContactRepository) :
    ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ContactViewModel::class.java)) {
            return ContactViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
