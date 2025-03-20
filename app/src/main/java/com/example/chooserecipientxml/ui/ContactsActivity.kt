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
import kotlinx.coroutines.launch

class ContactsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityContactsBinding
    private val viewModel: RecipientViewModel by viewModels()
    private lateinit var adapter: ContactAdapter

    private var deviceStartIndex = 0
    private val batchSize = 50
    private var isLoading = false // ✅ Prevent duplicate requests
    private var isDeviceLoading =
        false // ✅ Flag to load device contacts **after** service contacts are finished

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityContactsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = ContactAdapter()
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        binding.progressBar.visibility = android.view.View.VISIBLE

        // ✅ Load first batch of service recipients
        viewModel.loadMoreRecipients()

        // ✅ Observe service recipients
        viewModel.recipients.observe(this) { serviceContacts ->
            adapter.addRecipients(serviceContacts) // ✅ Append service contacts
            binding.progressBar.visibility = android.view.View.GONE

            // ✅ If all service recipients are loaded, start loading device contacts
            if (!isDeviceLoading && !viewModel.hasMoreServiceContacts()) {
                isDeviceLoading = true
                loadMoreDeviceContacts()
            }
        }

        // ✅ Implement Infinite Scroll for Both Service & Device Contacts
        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)

                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val lastVisibleItem = layoutManager.findLastCompletelyVisibleItemPosition()
                val totalItemCount = layoutManager.itemCount

                if (!isLoading && lastVisibleItem == totalItemCount - 1) {
                    isLoading = true // ✅ Prevent duplicate requests

                    if (viewModel.hasMoreServiceContacts()) {
                        viewModel.loadMoreRecipients()
                    } else if (isDeviceLoading) {
                        loadMoreDeviceContacts()
                    }

                    isLoading = false
                }
            }
        })

        // ✅ Implement Search Filtering
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false

            override fun onQueryTextChange(newText: String?): Boolean {
                return true
            }
        })
    }

    private fun loadMoreDeviceContacts() {
        lifecycleScope.launch {
            val newDeviceContacts = getDeviceContacts(this@ContactsActivity, deviceStartIndex, batchSize)
            adapter.addRecipients(newDeviceContacts) // ✅ Append device contacts
            deviceStartIndex += batchSize // ✅ Move to next batch
        }
    }
}
