package com.example.chooserecipientxml.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.chooserecipientxml.model.Contact
import com.example.chooserecipientxml.repository.ContactRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
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
    private val _shouldScrollToTop = MutableStateFlow(false)
    private val _searchResults = MutableStateFlow<List<ContactListItem>>(emptyList())
    private val _searchServerContacts = MutableStateFlow<List<Contact>>(emptyList())
    private val _searchDeviceContacts = MutableStateFlow<List<Contact>>(emptyList())

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
    val shouldScrollToTop: StateFlow<Boolean> = _shouldScrollToTop
    val searchResults: StateFlow<List<ContactListItem>> = _searchResults.asStateFlow()

    private val checkedContacts = mutableSetOf<String>()

    private var currentOffset = 0
    private val pageSize = 200
    private var isLoading = false
    private var allLoaded = false

    private var statusCheckOffset = 200 // Start after first batch
    private val statusPageSize = 100
    private val alreadyCheckedPhones = mutableSetOf<String>()
    private var isCheckingStatus = false

    // Final list to be displayed in RecyclerView
    val contactsForUI: StateFlow<List<ContactListItem>> = combine(
        _serverRecentContacts,
        _serverMyContacts,
        _deviceActiveContacts,
    ) { recent, my, deviceActive ->
        buildContactList(recent, my, deviceActive)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isSearchMode: StateFlow<Boolean> = _searchQuery
        .map { it.isNotBlank() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val displayList = combine(
        isSearchMode,
        contactsForUI,
        searchResults
    ) { isSearchMode, normalList, searchList ->
        if (isSearchMode) searchList else normalList
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            _searchQuery
                .debounce(300L)
                .mapLatest { query ->
                    _shouldScrollToTop.value = true
                    if (query.isBlank()) {
                        emptyList()
                    } else {
                        searchContacts(query)
                    }
                }
                .collect { results ->
                    _searchResults.value = results
                }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    private suspend fun searchContacts(query: String): List<ContactListItem> {
        val mergedList = _serverRecentContacts.value +
                _serverMyContacts.value +
                _deviceContacts.value

        val matched = mergedList.filter { it.name.contains(query, ignoreCase = true) }

        // Only check device contacts with unknown status and not already checked
        val uncachedToCheck = matched.filter {
            it.source.toString() == "DEVICE" && it.status.isNullOrEmpty() && checkedContacts.add(it.phoneNumber)
        }

        // Limit to 200 contacts for status check on initial search
        val toCheck = uncachedToCheck.take(pageSize)

        if (toCheck.isNotEmpty()) {
            updateContactStatusInBackground(toCheck)
        }

        return matched.map { ContactListItem.ContactItem(it) } + ContactListItem.Disclosure
    }

    fun loadAllContacts() {
        _shouldScrollToTop.value = true
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
                repository.fetchAllDeviceContacts()
            }

            _deviceContacts.value = deviceContacts

            val firstBatch = deviceContacts.take(pageSize)

            // Replace first 200 with ones that include status
            withContext(Dispatchers.IO) {
                repository.checkDeviceContactStatus(firstBatch)
            }

            // Now firstBatch has updated statuses in-place
            val updatedList = firstBatch + deviceContacts.drop(pageSize)

            _deviceContacts.value = updatedList
            _deviceActiveContacts.value = updatedList.filter { it.status == "ACTIVE" }

//            currentOffset = deviceContacts.size
//            allLoaded = deviceContacts.size < pageSize
        }
    }

    private fun updateContactStatusInBackground(contacts: List<Contact>) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.checkDeviceContactStatus(contacts) // updates in-place

            withContext(Dispatchers.Main) {
                _deviceContacts.update { current ->
                    current.map { existing ->
                        val updated = contacts.find { it.id == existing.id }
                        updated ?: existing
                    }
                }

                _deviceActiveContacts.update { current ->
                    (current + contacts.filter { it.status == "ACTIVE" }).distinctBy { it.phoneNumber }
                }

                // causing issues with UI - duplicates
//                _deviceActiveContacts.update { current ->
//                    current + contacts.filter { it.status == "ACTIVE" && it !in current }
//                }
            }
        }
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

    fun showListScreen() {
        _isListScreenVisible.value = true
    }

    fun showGridScreen() {
        _isListScreenVisible.value = false
    }

//    fun checkNextDeviceContactStatusPage() {
//        if (isCheckingStatus) return
//
//        val all = _deviceContacts.value
//        val unverified = all
//            .drop(statusCheckOffset)
//            .filter {
//                it.status.isNullOrEmpty() && alreadyCheckedPhones.add(it.phoneNumber)
//            }
//            .take(statusPageSize)
//
//        if (unverified.isEmpty()) return
//
//        isCheckingStatus = true
//
//        updateContactStatusInBackground(unverified) {
//            statusCheckOffset += unverified.size
//            isCheckingStatus = false
//        }
//    }

//    fun loadMoreDeviceContacts() {
//        if (isLoading || allLoaded || isSearchMode.value) return
//
//        _shouldScrollToTop.value = false
//        isLoading = true
//
//        viewModelScope.launch {
//            val deviceContacts = withContext(Dispatchers.IO) {
//                repository.fetchDeviceContacts(currentOffset, pageSize)
//            }
//
//            if (deviceContacts.isEmpty()) {
//                allLoaded = true
//            } else {
//                // ✅ Check contact status before updating state
//                withContext(Dispatchers.IO) {
//                    repository.checkDeviceContactStatus(deviceContacts)
//                }
//
//                _deviceContacts.update { current ->
//                    current + deviceContacts
//                }
//
//                _deviceActiveContacts.update { current ->
//                    current + deviceContacts.filter { it.status == "ACTIVE" }
//                }
//
//                currentOffset += deviceContacts.size
//                allLoaded = deviceContacts.size < pageSize
//            }
//
//            isLoading = false
//        }
//    }

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
