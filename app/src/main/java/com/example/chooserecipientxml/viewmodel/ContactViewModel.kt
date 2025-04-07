package com.example.chooserecipientxml.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.chooserecipientxml.model.Contact
import com.example.chooserecipientxml.repository.ContactRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
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

    // Global variables
    val shouldScrollToTop: StateFlow<Boolean> = _shouldScrollToTop

    // Search mode
    private var _searchServerContacts = listOf<Contact>()
    private var _searchDeviceContacts = listOf<Contact>()
    private val _searchQuery = MutableStateFlow("") // Search query input from UI
    private var searchStatusOffset = 0
    private val searchStatusPageSize = 200
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

    val topRecentServiceContacts: StateFlow<List<Contact>> = _serverRecentContacts
        .map { it.take(6) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ================================= Search mode ============================================
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
            var lastQuery: String? = null
            var debounceJob: Job? = null

            _searchQuery
                .filter { it.isNotBlank() }
                .debounce(200L)
                .distinctUntilChanged()
                .collectLatest { query ->
                    if (query == lastQuery) return@collectLatest
                    lastQuery = query

                    _shouldScrollToTop.value = true
                    performSearch(query)

                    debounceJob?.cancel()
                    debounceJob = launch {
                        delay(1000L)

                        // Optional: check if this is still the latest query
                        if (query == lastQuery) {
                            Log.d("ThreadCheck", "debounceJob: query=$query")
                            searchStatusOffset = 0 // reset paging for new results
                            checkVisibleSearchStatus()
                        } else {
                            Log.d("ThreadCheck", "Not matched - debounceJob: query=$query, lastQuery=$lastQuery")
                        }
                    }
                }
        }
    }

    fun setSearchQuery(query: String) {
        _shouldScrollToTop.value = true
        _searchQuery.value = query
    }

    // When user types, filter and separate service vs device contacts
    fun performSearch(query: String) {
        Log.d("ThreadCheck", "performSearch: query=$query")
        _searchServerContacts = (_serverRecentContacts.value + _serverMyContacts.value).filter {
            it.name.contains(query, ignoreCase = true) || it.phoneNumber.contains(query)
        }
        _searchDeviceContacts = _deviceContacts.value.filter {
            it.name.contains(query, ignoreCase = true) || it.phoneNumber.contains(query)
        }

        val matched = (_searchServerContacts + _searchDeviceContacts).sortedBy { it.name }

        val results = matched.map { ContactListItem.ContactItem(it.copy()) } + ContactListItem.Disclosure
        _searchResults.value = results
    }

    fun checkVisibleSearchStatus() {
        viewModelScope.launch {
            // Try to acquire the lock
            if (!searchStatusMutex.tryLock()) {
                Log.d("ThreadCheck", "🔒 Already checking, skipping this run")
                return@launch
            }

            try {
                // Skip if offset is out of bounds
                if (searchStatusOffset >= _searchDeviceContacts.size) return@launch

                val startIndex = searchStatusOffset
                val endIndex = (startIndex + searchStatusPageSize).coerceAtMost(_searchDeviceContacts.size)

                // Prevent invalid subList crash
                if (startIndex >= endIndex) return@launch

                val visibleContacts = _searchDeviceContacts.subList(startIndex, endIndex)
                val batch = visibleContacts.filter { it.status.isNullOrEmpty() }

                // Move offset forward for next batch
                searchStatusOffset = endIndex

                if (batch.isEmpty()) return@launch

                Log.d("ThreadCheck", "checkVisibleSearchStatus: batch size=${batch.size}, start=$startIndex, end=$endIndex")

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

    private suspend fun updateContactStatusInBackground(contacts: List<Contact>) {
        Log.d("ThreadCheck", "updateContactStatusInBackground: ${Thread.currentThread().name}. ${contacts.size} contacts")
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

            // TODO: check if there is a chance to have more than 6 recent contacts and do i need to sort by name as well
            val recent = serviceContacts.filter { it.level != null }.sortedBy { it.level }
            val my = serviceContacts.filter { it.level == null }.sortedBy { it.name }

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
