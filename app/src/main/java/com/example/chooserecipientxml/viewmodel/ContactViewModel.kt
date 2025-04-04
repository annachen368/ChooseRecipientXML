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
import kotlinx.coroutines.flow.update
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

    private var currentOffset = 0
    private val pageSize = 200
    private var isLoading = false
    private var allLoaded = false

    // Final list to be displayed in RecyclerView
    val contactsForUI: StateFlow<List<ContactListItem>> = combine(
        _serverRecentContacts,
        _serverMyContacts,
        _deviceActiveContacts,
        _deviceContacts,
        _searchQuery
    ) { recent, my, deviceActive, device, query ->
//        buildContactList(recent, my, device, query)
        if (query.isNullOrBlank()) {
            buildContactList(recent, my, deviceActive)
        } else {
            buildContactListParallel(recent, my, device, query)
        }
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

    fun loadAllContacts() {
        loadServiceContacts()
        loadDeviceContacts()
    }

    fun loadServiceContacts() {
        viewModelScope.launch {
            val serviceContacts = withContext(Dispatchers.IO) {
                repository.fetchServiceContacts()
            }

            val recent = serviceContacts.filter { it.level != null }
            val my = serviceContacts.filter { it.level == null }

            _serverRecentContacts.value = recent
            _serverMyContacts.value = my
        }
    }

    fun loadDeviceContacts() {
        viewModelScope.launch {
            val deviceContacts = withContext(Dispatchers.IO) {
                repository.fetchDeviceContacts(currentOffset, pageSize)
            }

            // Wait for status check to complete
            withContext(Dispatchers.IO) {
                repository.checkDeviceContactStatus(deviceContacts)
            }

            _deviceContacts.value = deviceContacts
            _deviceActiveContacts.value = deviceContacts.filter { it.status == "ACTIVE" }
            _isDeviceContactsLoaded.value = true

            currentOffset = deviceContacts.size
            allLoaded = deviceContacts.size < pageSize
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

    private fun buildContactList(
        recent: List<Contact>,
        my: List<Contact>,
        device: List<Contact>,
    ): List<ContactListItem> {
        val result = mutableListOf<ContactListItem>()

        if (recent.isNotEmpty()) {
            result.add(ContactListItem.Header("Service Contacts - Recent"))
            result.addAll(recent.map { ContactListItem.ContactItem(it) })
        }

        if (my.isNotEmpty()) {
            result.add(ContactListItem.Header("Service Contacts - My Contacts"))
            result.addAll(my.map { ContactListItem.ContactItem(it) })
        }

        if (device.isNotEmpty()) {
            result.add(ContactListItem.Header("Activated Device Contacts"))
            result.addAll(device.map { ContactListItem.ContactItem(it) })
        }

        result.add(ContactListItem.Disclosure)
        return result
    }

    suspend fun buildContactListParallel(
        recent: List<Contact>,
        my: List<Contact>,
        device: List<Contact>,
        query: String
    ): List<ContactListItem> = withContext(Dispatchers.Default) {
        val shouldFilter = query.isNotBlank()
        val lowerQuery = query.lowercase()

        fun List<Contact>.filtered(): List<Contact> {
            return if (shouldFilter) {
                this.filter {
                    it.name.lowercase().contains(lowerQuery) ||
                            it.phoneNumber.lowercase().contains(lowerQuery)
                }
            } else this
        }

        // Run filtering & transformation for all sections in parallel
        val recentDeferred = async {
            val filtered = recent.filtered()
            if (filtered.isNotEmpty()) {
                filtered.map { ContactListItem.ContactItem(it) }
            } else emptyList()
        }

        val myDeferred = async {
            val filtered = my.filtered()
            if (filtered.isNotEmpty()) {
                filtered.map { ContactListItem.ContactItem(it) }
            } else emptyList()
        }

        val deviceDeferred = async {
            val filtered = device.filtered()
            if (filtered.isNotEmpty()) {
                filtered.map { ContactListItem.ContactItem(it) }
            } else emptyList()
        }

        // Gather all in order
        val result = mutableListOf<ContactListItem>()
        result += recentDeferred.await()
        result += myDeferred.await()
        result += deviceDeferred.await()
        result += ContactListItem.Disclosure // always add disclosure at the end
        result
    }

    fun showListScreen() {
        _isListScreenVisible.value = true
    }

    fun showGridScreen() {
        _isListScreenVisible.value = false
    }

    fun loadMoreDeviceContacts() {
        if (isLoading || allLoaded) return

        isLoading = true

        viewModelScope.launch {
            val newContacts = withContext(Dispatchers.IO) {
                repository.fetchDeviceContacts(currentOffset, pageSize)
            }

            if (newContacts.isEmpty()) {
                allLoaded = true
            } else {
                _deviceContacts.update { current ->
                    current + newContacts
                }

                _deviceActiveContacts.update { current ->
                    current + newContacts.filter { it.status == "ACTIVE" }
                }

                currentOffset += newContacts.size
                allLoaded = newContacts.size < pageSize
            }

            isLoading = false
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
