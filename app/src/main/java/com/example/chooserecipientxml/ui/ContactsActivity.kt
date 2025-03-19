package com.example.chooserecipientxml.ui

import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.chooserecipientxml.adapter.ContactAdapter
import com.example.chooserecipientxml.databinding.ActivityContactsBinding
import com.example.chooserecipientxml.network.ApiService
import com.example.chooserecipientxml.repository.ContactRepository
import com.example.chooserecipientxml.utils.getDeviceContacts
import com.example.chooserecipientxml.viewmodel.RecipientViewModel
import kotlinx.coroutines.launch

class ContactsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityContactsBinding
    private val viewModel: RecipientViewModel by viewModels()
    private lateinit var adapter: ContactAdapter

    // Initialize API service and repository
    private val repository by lazy { ContactRepository(ApiService.create()) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityContactsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = ContactAdapter()
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        binding.progressBar.visibility = View.VISIBLE

        // ✅ Load device contacts first
        lifecycleScope.launch {
            val deviceContacts = getDeviceContacts(this@ContactsActivity)
            adapter.addRecipients(deviceContacts) // ✅ Add device contacts immediately
        }

        // ✅ Observe service contacts (outside of `launch`)
        viewModel.recipients.observe(this) { serviceContacts ->
            val mergedContacts = adapter.getAllContacts() + serviceContacts // ✅ Keep existing contacts
            adapter.addRecipients(mergedContacts)
            binding.progressBar.visibility = View.GONE
        }

        // ✅ Fetch service contacts (pagination)
        viewModel.loadMoreRecipients()

        // ✅ Implement Search Filtering
//        binding.searchView.setOnQueryTextListener(object : android.widget.SearchView.OnQueryTextListener {
//            override fun onQueryTextSubmit(query: String?): Boolean = false
//            override fun onQueryTextChange(newText: String?): Boolean {
//                adapter.filter(newText ?: "")
//                return true
//            }
//        })

//        lifecycleScope.launch {
//            binding.progressBar.visibility = View.VISIBLE
//            val serviceContacts = repository.fetchRecipients(0, 100)
//            val deviceContacts = getDeviceContacts(this@ContactsActivity)
//            adapter.submitList(serviceContacts + deviceContacts)
//            binding.progressBar.visibility = View.GONE
//        }
    }
}
