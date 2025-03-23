package com.example.chooserecipientxml.ui

import android.graphics.Rect
import android.os.Bundle
import android.widget.SearchView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.chooserecipientxml.adapter.ContactAdapter
import com.example.chooserecipientxml.databinding.ActivityContactsBinding
import com.example.chooserecipientxml.network.ApiService
import com.example.chooserecipientxml.repository.ContactRepository
import com.example.chooserecipientxml.viewmodel.ContactViewModel
import com.example.chooserecipientxml.viewmodel.ContactViewModelFactory

class ContactsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityContactsBinding
    private lateinit var viewModel: ContactViewModel
    private lateinit var adapter: ContactAdapter
    private var isSearching = false
    private var hasMoreContacts: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityContactsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val apiService = ApiService.create()
        val repository = ContactRepository(apiService)
        val factory = ContactViewModelFactory(repository)

        viewModel = ViewModelProvider(this, factory)[ContactViewModel::class.java]

        adapter = ContactAdapter(context = this)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        // Start the loop after setting up RecyclerView
        binding.recyclerView.post(autoLoadRunnable)

        // ✅ Load contacts
        viewModel.loadServiceContacts()
        loadMoreContacts()

        // ✅ Observe service contacts
        viewModel.serverRecentContacts.observe(this) { contacts ->
            if (!isSearching) {
                adapter.addServiceRecentContacts(contacts) // ✅ Append service contacts
            }
        }

        // ✅ Observe service contacts
        viewModel.serverMyContacts.observe(this) { contacts ->
            if (!isSearching) {
                adapter.addServiceMyContacts(contacts) // ✅ Append service contacts
            }
        }

        // ✅ Observe device contacts
        viewModel.deviceContacts.observe(this) { contacts ->
            if (!isSearching) {
                adapter.addDeviceContacts(contacts) // ✅ Append service contacts
                isLoading = false

                // No more contacts to load
                if (contacts.isEmpty()) {
                    hasMoreContacts = false
                }
            }
        }

        // ✅ Observe device active contacts
        viewModel.deviceActiveContacts.observe(this) { contacts ->
            if (!isSearching) {
                adapter.addDeviceActiveContacts(contacts) // ✅ Append service contacts
                isLoading = false
            }
        }

        viewModel.isDeviceContactsLoaded.observe(this) { isLoaded ->
            adapter.setLoadingFooterVisible(!isLoaded)
        }

        // ✅ Implement Search Filtering
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false

            override fun onQueryTextChange(newText: String?): Boolean {
                isSearching = !newText.isNullOrEmpty()
                adapter.filter(newText ?: "") // ✅ Update list dynamically
                return true
            }
        })
    }

    private var isLoading = false
    private var currentPage = 0

    //    private val batchSize = 200 // TODO: check what batch size to use
    private val batchSize = 10

    private fun loadMoreContacts() {
        isLoading = true
        // Show loading footer here if needed

        viewModel.loadDeviceContacts(this, currentPage * batchSize, batchSize)
        currentPage++
    }

    /**
     * “As long as the last item in the RecyclerView is visible on screen (even if the user is not scrolling),
     * keep calling loadDeviceContacts() until all data is fetched.”
     *
     * That means:
     *
     * You should not rely solely on setOnScrollChangeListener, because it only triggers on user scroll.
     * You need a persistent check loop to watch whether the RecyclerView's last item is visible even
     * when no scroll happens (e.g., during initial load or after each batch).
     */
    private fun isLastItemVisible(): Boolean {
        val lastIndex = binding.recyclerView.adapter?.itemCount?.minus(1) ?: return false
        val viewHolder =
            binding.recyclerView.findViewHolderForAdapterPosition(lastIndex) ?: return false

        val itemView = viewHolder.itemView
        val visibleRect = Rect()
        val isVisible = itemView.getGlobalVisibleRect(visibleRect)

        return isVisible && visibleRect.height() >= itemView.height / 2
    }

    /**
     * It only runs a simple check: finding a view, measuring visibility.
     * No heavy computation or drawing.
     * It's executed on the main thread, but it’s light enough to not block rendering if implemented correctly.
     */
    private val autoLoadRunnable = object : Runnable {
        override fun run() {
            if (!isLoading && isLastItemVisible()) {
                isLoading = true
                loadMoreContacts()
            }
            // Keep checking every 500ms
            if (hasMoreContacts) {
                binding.recyclerView.postDelayed(this, 500)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.recyclerView.removeCallbacks(autoLoadRunnable)
    }

    /**
     * Use Android Profiler > CPU > Main Thread to see when frames are dropped or long tasks occur.
     */
}
