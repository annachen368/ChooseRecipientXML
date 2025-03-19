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
import com.example.chooserecipientxml.utils.getDeviceContacts
import com.example.chooserecipientxml.viewmodel.RecipientViewModel
import kotlinx.coroutines.launch

class ContactsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityContactsBinding
    private val viewModel: RecipientViewModel by viewModels()
    private lateinit var adapter: ContactAdapter

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
            adapter.addRecipients(deviceContacts) // ✅ Add device contacts first
        }

        // ✅ Observe service contacts (outside of `launch`)
        viewModel.recipients.observe(this) { serviceContacts ->
            adapter.addRecipients(serviceContacts) // ✅ Append service contacts instead of replacing
            binding.progressBar.visibility = android.view.View.GONE
        }

        // ✅ Load first batch of service contacts
        viewModel.loadMoreRecipients()

        // ✅ Implement Infinite Scroll
        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)

                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val lastVisibleItem = layoutManager.findLastCompletelyVisibleItemPosition()
                val totalItemCount = layoutManager.itemCount

                // ✅ If we reached the last item, load more data
                if (lastVisibleItem == totalItemCount - 1) {
                    viewModel.loadMoreRecipients()
                }
            }
        })
    }
}
