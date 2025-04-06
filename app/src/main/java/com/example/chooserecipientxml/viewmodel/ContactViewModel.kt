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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
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
    private val _searchServerContacts = MutableStateFlow<List<Contact>>(emptyList())
    private val _searchDeviceContacts = MutableStateFlow<List<Contact>>(emptyList())
    private val _searchQuery = MutableStateFlow("") // Search query input from UI
    private val _searchRefreshTrigger = MutableStateFlow(0)
    private var searchStatusOffset = 0
    private val searchStatusPageSize = 100
    private var isCheckingSearchStatus = false
    private var cachedSearchMatchedContacts: List<Contact> = emptyList()
    private var lastSearchStatusCheckTime = 0L

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
    val searchResults: StateFlow<List<ContactListItem>> = combine(
        _searchQuery.debounce(300L),
        _serverRecentContacts,
        _serverMyContacts,
        _deviceContacts,
        _searchRefreshTrigger
    ) { query, recent, my, device, _ ->
        if (query.isBlank()) {
            emptyList()
        } else {
            val matched = (recent + my + device)
                .filter { it.name.contains(query, ignoreCase = true) }

            cachedSearchMatchedContacts = matched
            searchStatusOffset = 0
            checkNextSearchStatusPage()

            matched.map { contact ->
                ContactListItem.ContactItem(contact.copy()) // <--- forces new instances
            } + ContactListItem.Disclosure
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Final list to be displayed in RecyclerView
    val displayList = combine(
        isSearchMode,
        contactsForUI,
        searchResults
    ) { isSearchMode, normalList, searchList ->
        if (isSearchMode) searchList else normalList
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSearchQuery(query: String) {
        _shouldScrollToTop.value = true
        _searchQuery.value = query
    }

    fun resetScrollFlag() {
        _shouldScrollToTop.value = false
    }

    fun checkNextSearchStatusPage() {
        val now = System.currentTimeMillis()
        if (now - lastSearchStatusCheckTime < 300L) return // debounce
        lastSearchStatusCheckTime = now

        if (isCheckingSearchStatus || searchStatusOffset >= cachedSearchMatchedContacts.size) return

        val batch = cachedSearchMatchedContacts
            .drop(searchStatusOffset)
            .filter {
                it.source.toString() == "DEVICE" &&
                        it.status.isNullOrEmpty() &&
                        checkedContacts.add(it.phoneNumber)
            }
            .take(searchStatusPageSize)

        if (batch.isEmpty()) return

        isCheckingSearchStatus = true

        updateContactStatusInBackground(batch) {
            searchStatusOffset += batch.size
            isCheckingSearchStatus = false
            refreshSearch() // triggers recompute
        }
    }

    private fun refreshSearch() {
        _searchRefreshTrigger.update { it + 1 }
    }

    private fun updateContactStatusInBackground(
        contacts: List<Contact>,
        onComplete: (() -> Unit)? = null
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.checkDeviceContactStatus(contacts)

            withContext(Dispatchers.Main) {
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

                onComplete?.invoke()
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

    fun checkNextDeviceContactStatusPage() {
        if (isCheckingStatus) return

        // TODO: don't know how to verify
        val all = _deviceContacts.value
        val unverified = all
            .drop(statusCheckOffset)
            .filter {
                it.status.isNullOrEmpty() && alreadyCheckedPhones.add(it.phoneNumber)
            }
            .take(statusPageSize)

        if (unverified.isEmpty()) return

        isCheckingStatus = true

        updateContactStatusInBackground(unverified) {
            statusCheckOffset += unverified.size
            isCheckingStatus = false
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
