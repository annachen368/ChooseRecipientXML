package com.example.chooserecipientxml.viewmodel

import android.util.Log
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

/**
 * In grid-list-two-views-group branch:
 * 1. search mode is almost complete, normal mode is not.
 * 2. search mode refresh status is still not working. If you are in the middle of search and
 *    device contact status is unknown, it will not be updated unless you scroll to the bottom.
 * 3. thinking about using a different approach for search mode. Currently, it is using the same
 *    contact source as normal mode. This is not ideal because it will cause the status pagination
 *    to be hard to manage. I think we might need to create a separate source for search mode.
 */
class ContactViewModel(private val repository: ContactRepository) : ViewModel() {

    // Normal mode
    private val _serverRecentContacts = MutableStateFlow<List<Contact>>(emptyList())
    private val _serverMyContacts = MutableStateFlow<List<Contact>>(emptyList())
    private val _deviceContacts = MutableStateFlow<List<Contact>>(emptyList())
    private val _shouldScrollToTop = MutableStateFlow(false)
    private val pageSize = 200
    private var statusCheckOffset = 200 // Start after first batch
    private val statusPageSize = 100
    private val alreadyCheckedPhones = mutableSetOf<String>()
    private var isCheckingStatus = false

    // Visibility state for list screen
    private val _isListScreenVisible = MutableStateFlow(false)
    val isListScreenVisible: StateFlow<Boolean> = _isListScreenVisible.asStateFlow()

    // Public state exposed to UI
//    val serverRecentContacts: StateFlow<List<Contact>> = _serverRecentContacts.asStateFlow()
//    val serverMyContacts: StateFlow<List<Contact>> = _serverMyContacts.asStateFlow()
//    val deviceContacts: StateFlow<List<Contact>> = _deviceContacts.asStateFlow()
//    val deviceActiveContacts: StateFlow<List<Contact>> = _deviceActiveContacts.asStateFlow()
//    val isDeviceContactsLoaded: StateFlow<Boolean> = _isDeviceContactsLoaded.asStateFlow()
//    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val shouldScrollToTop: StateFlow<Boolean> = _shouldScrollToTop
    private val checkedContacts = mutableSetOf<String>()

    // Search mode
//    private val _searchServerContacts = MutableStateFlow<List<Contact>>(emptyList())
//    private val _searchDeviceContacts = MutableStateFlow<List<Contact>>(emptyList())
    private var _searchServerContacts = listOf<Contact>()
    private var _searchDeviceContacts = listOf<Contact>()
    private val _searchQuery = MutableStateFlow("") // Search query input from UI
    private val _searchRefreshTrigger = MutableStateFlow(0)
    private var searchStatusOffset = 0
    private val searchStatusPageSize = 200
    private var isCheckingSearchStatus = false
    private var lastSearchStatusCheckTime = 0L
    private val searchStatusMutex = Mutex()

    val isSearchMode: StateFlow<Boolean> = _searchQuery
        .map { it.isNotBlank() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val contactsForUI = combine(
        _serverRecentContacts,
        _serverMyContacts,
        _deviceContacts
    ) { recent, my, device ->
        val active = device.filter { it.status == "ACTIVE" }
        buildContactList(recent, my, active)
    }

    // ================================= Search mode ============================================
    // Let UI always reflect updated status in search results when background check completes.
    // Combine search results for the UI
//    val searchResults: StateFlow<List<ContactListItem>> = combine(
//        _searchQuery.debounce(300),
//        _searchServerContacts,
//        _searchDeviceContacts,
//        _searchRefreshTrigger
//    ) { query, server, device, _ ->
//        if (query.isBlank()) return@combine emptyList()
//
//        val items = mutableListOf<ContactListItem>()
//
//        if (server.isNotEmpty()) {
//            items += ContactListItem.Header("Service Contacts")
//            items += server.map { ContactListItem.ContactItem(it.copy()) }
//        }
//
//        if (device.isNotEmpty()) {
//            items += ContactListItem.Header("Device Contacts")
//            items += device.map { ContactListItem.ContactItem(it.copy()) }
//        }
//
//        items + ContactListItem.Disclosure
//    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _searchResults = MutableStateFlow<List<ContactListItem>>(emptyList())
    val searchResults: StateFlow<List<ContactListItem>> = _searchResults.asStateFlow()

    // Final list to be displayed in RecyclerView
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
                .filter { query -> query.isNotBlank() } // ✅ ignore empty or blank queries
                .mapLatest { query ->
                    _shouldScrollToTop.value = true
                    performSearch(query)
                }
                .collect {}
        }
    }

    fun setSearchQuery(query: String) {
        _shouldScrollToTop.value = true
        _searchQuery.value = query
//        searchStatusOffset = 0 // ← only here
    }

    fun resetScrollFlag() {
        _shouldScrollToTop.value = false
    }

    // When user types, filter and separate service vs device contacts
    fun performSearch(query: String) {
        _searchServerContacts = (_serverRecentContacts.value + _serverMyContacts.value).filter {
            it.name.contains(query, ignoreCase = true)
        }
        _searchDeviceContacts = _deviceContacts.value.filter {
            it.name.contains(query, ignoreCase = true)
        }

        val matched = (_searchServerContacts + _searchDeviceContacts).sortedBy { it.name }

        // Reset search status offset
        searchStatusOffset = 0

        val results = matched.map { ContactListItem.ContactItem(it.copy()) } + ContactListItem.Disclosure
        _searchResults.value = results
    }

    fun checkVisibleSearchStatus() {
        viewModelScope.launch {
            // Try to acquire the lock
            if (!searchStatusMutex.tryLock()) {
                Log.d("ContactViewModel", "🔒 Already checking, skipping this run")
                return@launch
            }

            try {
                // Skip if offset is out of bounds
                if (searchStatusOffset >= _searchDeviceContacts.size) return@launch

                // Set checked contact statuses to ACTIVE
                _searchDeviceContacts.forEach { contact ->
                    if (contact.phoneNumber in checkedContacts) {
                        contact.status = "ACTIVE"
                    }
                }

                val startIndex = searchStatusOffset
                val endIndex = (startIndex + searchStatusPageSize).coerceAtMost(_searchDeviceContacts.size)

                // Prevent invalid subList crash
                if (startIndex >= endIndex) return@launch

                val visibleContacts = _searchDeviceContacts.subList(startIndex, endIndex)
                val batch = visibleContacts.filter { it.status.isNullOrEmpty() }

                // Move offset forward for next batch
                searchStatusOffset = endIndex

                if (batch.isEmpty()) return@launch

                Log.d("ContactViewModel", "checkVisibleSearchStatus: batch size=${batch.size}, start=$startIndex, end=$endIndex")

                // Now call suspending function
                updateContactStatusInBackground(batch)

                _shouldScrollToTop.value = false

                val matched = (_searchServerContacts + _searchDeviceContacts).sortedBy { it.name }
                val results = matched.map { ContactListItem.ContactItem(it.copy()) } + ContactListItem.Disclosure

                _searchResults.value = results
            } finally {
                // Always unlock after completion
                searchStatusMutex.unlock()
            }
        }
    }

//    fun checkVisibleSearchStatus() {
//        if (isCheckingSearchStatus) return
//        isCheckingSearchStatus = true
//
//        // Update _searchDeviceContacts so that any contact whose phone is in checkedContacts
//        // gets its status set to ACTIVE
//        _searchDeviceContacts.forEach { contact ->
//            if (contact.phoneNumber in checkedContacts) {
//                contact.status = "ACTIVE"
//            }
//        }
//
//        val visibleContacts = _searchDeviceContacts
//            .subList(searchStatusOffset, (searchStatusOffset + searchStatusPageSize).coerceAtMost(_searchDeviceContacts.size))
//
//        val batch = visibleContacts.filter { contact -> contact.status.isNullOrEmpty() }
//
//        // Always move the offset
//        searchStatusOffset += searchStatusPageSize
//
//        if (batch.isEmpty()) {
//            isCheckingSearchStatus = false
//            return
//        }
//        Log.d("ContactViewModel", "checkVisibleSearchStatus: batch size=${batch.size}, searchStatusOffset=$searchStatusOffset, end=${(searchStatusOffset + searchStatusPageSize).coerceAtMost(_searchDeviceContacts.size)}")
//
//        updateContactStatusInBackground(batch) {
//            // This doesn't need offset tracking, since it's based on visible range
//            _shouldScrollToTop.value = false
//
//            // Update UI
//            val matched = (_searchServerContacts + _searchDeviceContacts).sortedBy { it.name }
//            val results = matched.map { ContactListItem.ContactItem(it.copy()) } + ContactListItem.Disclosure
//            isCheckingSearchStatus = false
//            _searchResults.value = results
////            searchStatusOffset += searchStatusPageSize
//        }
//    }

//    fun checkNextSearchStatusPage() {
//        if (isCheckingSearchStatus || searchStatusOffset >= cachedSearchMatchedContacts.size) return
//
//        Log.d("ContactViewModel", "checkNextSearchStatusPage: searchStatusOffset=$searchStatusOffset, cachedSearchMatchedContacts=${cachedSearchMatchedContacts.size}")
//        val batch = cachedSearchMatchedContacts
//            .asSequence() // Lazily evaluates
//            .drop(searchStatusOffset)
//            .filter { contact ->
//                contact.source?.toString() == "DEVICE" &&
//                        contact.status.isNullOrEmpty() &&
//                        checkedContacts.add(contact.phoneNumber) // ensures not rechecking
//            }
//            .take(searchStatusPageSize)
//            .toList() // Finalize into list for service call
//
//        if (batch.isEmpty()) return
//
//        isCheckingSearchStatus = true
//
//        Log.d("ContactViewModel", "Start Updated status for ${batch.size} contacts")
//        updateContactStatusInBackground(batch) {
//            Log.d("ContactViewModel", "End Updated status for ${batch.size} contacts")
//            searchStatusOffset += batch.size
//            isCheckingSearchStatus = false
//
//            _shouldScrollToTop.value = false
//
//            // 🔁 Instead of calling performSearch again:
//            _searchResults.update { current ->
//                val updatedMatched = cachedSearchMatchedContacts.map { ContactListItem.ContactItem(it.copy()) }
//                updatedMatched + ContactListItem.Disclosure
//            }
//
//            // Re-trigger searchResults to reflect updated statuses
////            refreshSearch()
//        }
//    }

    private fun refreshSearch() {
        _searchRefreshTrigger.update { it + 1 }
    }

    private suspend fun updateContactStatusInBackground(contacts: List<Contact>) {
        // Run network call on IO dispatcher
        withContext(Dispatchers.IO) {
            repository.checkDeviceContactStatus(contacts)
        }

        // Safely update device contacts on the main thread
        _deviceContacts.update { current ->
            current.map { existing ->
                val updated = contacts.find { it.id == existing.id }
                if (updated != null && existing.status != updated.status) {
                    existing.copy(status = updated.status)
                } else {
                    existing
                }
            }
        }
    }

//    private fun updateContactStatusInBackground(
//        contacts: List<Contact>,
//        onComplete: (() -> Unit)? = null
//    ) {
//        viewModelScope.launch(Dispatchers.IO) {
//            repository.checkDeviceContactStatus(contacts)
//
//            withContext(Dispatchers.Main) {
//                _deviceContacts.update { current ->
//                    current.map { existing ->
//                        val updated = contacts.find { it.id == existing.id }
//                        if (updated != null && existing.status != updated.status) {
//                            existing.copy(status = updated.status)
//                        } else {
//                            existing
//                        }
//                    }
//                }
//
//                onComplete?.invoke()
//            }
//        }
//    }

    // ================================= Normal mode ============================================

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
        }
    }

    private fun buildContactList(
        recent: List<Contact>,
        my: List<Contact>,
        device: List<Contact> // already filtered to ACTIVE
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
//        // TODO: don't know how to verify
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
