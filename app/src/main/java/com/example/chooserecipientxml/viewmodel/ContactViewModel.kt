package com.example.chooserecipientxml.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.chooserecipientxml.model.Contact
import com.example.chooserecipientxml.repository.ContactRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ContactViewModel(private val repository: ContactRepository) : ViewModel() {

    // Internal state for raw contact groups
    private val _serverRecentContacts = MutableStateFlow<List<Contact>>(emptyList())
    private val _serverMyContacts = MutableStateFlow<List<Contact>>(emptyList())
    private val _deviceContacts = MutableStateFlow<List<Contact>>(emptyList())
    private val _deviceActiveContacts = MutableStateFlow<List<Contact>>(emptyList())
    private val _isDeviceContactsLoaded = MutableStateFlow(false)

    // Search query input from UI
    private val _searchQuery = MutableStateFlow("")

    // Visibility state for list screen
    private val _isListScreenVisible = MutableStateFlow(false)

    // Public state exposed to UI
    val serverRecentContacts: StateFlow<List<Contact>> = _serverRecentContacts.asStateFlow()
    val serverMyContacts: StateFlow<List<Contact>> = _serverMyContacts.asStateFlow()
    val deviceContacts: StateFlow<List<Contact>> = _deviceContacts.asStateFlow()
    val deviceActiveContacts: StateFlow<List<Contact>> = _deviceActiveContacts.asStateFlow()
    val isDeviceContactsLoaded: StateFlow<Boolean> = _isDeviceContactsLoaded.asStateFlow()
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    val isListScreenVisible: StateFlow<Boolean> = _isListScreenVisible.asStateFlow()

    // Final list to be displayed in RecyclerView
    val contactsForUI: StateFlow<List<ContactListItem>> = combine(
        _serverRecentContacts,
        _serverMyContacts,
        _deviceActiveContacts,
        _searchQuery
    ) { recent, my, device, query ->
        buildContactList(recent, my, device, query)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Combined derived state: filtered contacts matching query
    val queryContacts: StateFlow<List<Contact>> = combine(
        _serverRecentContacts,
        _serverMyContacts,
        _deviceContacts,
        _searchQuery
    ) { recent, my, device, query ->
        val allContacts = recent + my + device
        if (query.isBlank()) allContacts
        else allContacts.filter { it.name.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Public setter for search query
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    // Example loader function
    fun loadContacts() {
        viewModelScope.launch {
            val serviceDeferred = async(Dispatchers.IO) {
                repository.fetchServiceContacts()
            }
            val deviceDeferred = async(Dispatchers.IO) {
                repository.fetchDeviceContacts(0, 200)
            }

            val serviceContacts = serviceDeferred.await()
            val device = deviceDeferred.await()

            val recent = serviceContacts.filter { it.level != null }
            val my = serviceContacts.filter { it.level == null }

            _deviceContacts.value = device
            _deviceActiveContacts.value = device.filter { it.status == "ACTIVE" }
            _serverRecentContacts.value = recent
            _serverMyContacts.value = my
            _isDeviceContactsLoaded.value = true
        }
    }

    private fun buildContactList(
        recent: List<Contact>,
        my: List<Contact>,
        device: List<Contact>,
        query: String
    ): List<ContactListItem> {
        val result = mutableListOf<ContactListItem>()

        val shouldFilter = query.isNotBlank()
        val lowerQuery = query.lowercase()

        fun List<Contact>.filtered() = if (shouldFilter) {
            this.filter {
                it.name.lowercase().contains(lowerQuery) ||
                        it.phoneNumber.lowercase().contains(lowerQuery)
            }
        } else this

        if (recent.filtered().isNotEmpty()) {
            result.add(ContactListItem.Header("Service Contacts - Recent"))
            result.addAll(recent.filtered().map { ContactListItem.ContactItem(it) })
        }

        if (my.filtered().isNotEmpty()) {
            result.add(ContactListItem.Header("Service Contacts - My Contacts"))
            result.addAll(my.filtered().map { ContactListItem.ContactItem(it) })
        }

        if (device.filtered().isNotEmpty()) {
            result.add(ContactListItem.Header("Activated Device Contacts"))
            result.addAll(device.filtered().map { ContactListItem.ContactItem(it) })
        }

        result.add(ContactListItem.Disclosure)
        return result
    }

    fun showListScreen() {
        _isListScreenVisible.value = true
    }

    fun showGridScreen() {
        _isListScreenVisible.value = false
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
