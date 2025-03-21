package com.example.chooserecipientxml.ui

import android.os.Bundle
import android.widget.SearchView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.chooserecipientxml.adapter.ContactAdapter
import com.example.chooserecipientxml.databinding.ActivityContactsBinding
import com.example.chooserecipientxml.utils.getDeviceContacts
import com.example.chooserecipientxml.viewmodel.RecipientViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ContactsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityContactsBinding
    private val viewModel: RecipientViewModel by viewModels()
    private lateinit var adapter: ContactAdapter

    private var deviceStartIndex = 0
    private val batchSize = 50 // ✅ Batch size for device contacts
    private var isLoading = false // ✅ Prevent duplicate requests
    private var isDeviceLoading =
        false // ✅ Flag to load device contacts **after** service contacts are finished
    private var isSearching = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityContactsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = ContactAdapter(context = this)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        binding.progressBar.visibility = android.view.View.VISIBLE

        // ✅ Load all service contacts
        viewModel.loadServiceContacts()

        // ✅ Observe service recipients
        viewModel.recipients.observe(this) { serviceContacts ->
            if (!isSearching) {
                adapter.addServiceContacts(serviceContacts) // ✅ Append service contacts

                // ✅ If all service recipients are loaded, start loading device contacts
                if (!isDeviceLoading) {
                    isDeviceLoading = true
                    loadDeviceContacts()
                }
            }
            binding.progressBar.visibility = android.view.View.GONE
        }

        // ✅ Implement Infinite Scroll for Device Contacts
        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)

                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val lastVisibleItem = layoutManager.findLastVisibleItemPosition()
                val totalItemCount = layoutManager.itemCount

                val visibleThreshold = 10
                if (!isSearching && !isLoading && lastVisibleItem >= totalItemCount - visibleThreshold) {
                    if (isDeviceLoading) {
                        loadDeviceContacts()
                    }
                }
            }
        })

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

    private fun loadDeviceContacts() {
        // Even though you're using lifecycleScope.launch, it's still running on the main thread
        // by default, which can block UI rendering.
        lifecycleScope.launch {
            isLoading = true // ✅ Prevent duplicate requests
            val newDeviceContacts = withContext(Dispatchers.IO) { // Run Heavy Work on Dispatchers.IO
                getDeviceContacts(this@ContactsActivity, deviceStartIndex, batchSize)
            }

            adapter.addDeviceContacts(newDeviceContacts) // ✅ Append device contacts
            adapter.addActivatedDeviceContacts(newDeviceContacts.filter { it.status == "ACTIVE" })
            deviceStartIndex += batchSize // ✅ Move to next batch
            isLoading = false // ✅ set after done loading
        }
    }
}
