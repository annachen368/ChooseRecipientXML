package com.example.chooserecipientxml.ui

import android.content.Context
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.SearchView
import androidx.appcompat.R
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.chooserecipientxml.adapter.ContactAdapter
import com.example.chooserecipientxml.databinding.ActivityContactsBinding
import com.example.chooserecipientxml.databinding.ItemContactGridBinding
import com.example.chooserecipientxml.network.ApiService
import com.example.chooserecipientxml.repository.ContactRepository
import com.example.chooserecipientxml.viewmodel.ContactViewModel
import com.example.chooserecipientxml.viewmodel.ContactViewModelFactory
import kotlinx.coroutines.launch

class ContactsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityContactsBinding
    private lateinit var viewModel: ContactViewModel
    private lateinit var adapter: ContactAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityContactsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val apiService = ApiService.create()
        val repository = ContactRepository(applicationContext, apiService)
        val factory = ContactViewModelFactory(repository)

        viewModel = ViewModelProvider(this, factory)[ContactViewModel::class.java]

//        repeat(5) { index ->
//            val itemBinding =
//                ItemContactGridBinding.inflate(layoutInflater, binding.contactGrid, false)
//
//            // Set dynamic content
//            itemBinding.name.text = "Contact $index"
//            itemBinding.token.text = "Token $index"
//
//            // Optional: click handler
//            itemBinding.root.setOnClickListener {
//                // Handle click
//            }
//
//            // Add to grid
//            binding.contactGrid.addView(itemBinding.root)
//        }

        viewModel.loadContacts()
        observeContacts()
        setupSearchView()

        adapter = ContactAdapter(context = this)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        // Start the loop after setting up RecyclerView
//        binding.recyclerView.post(autoLoadRunnable)
    }

    private fun observeContacts() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.contactsForUI.collect { items ->
                        adapter.submitItems(items)
                    }
                }

                launch {
                    viewModel.isDeviceContactsLoaded.collect { loaded ->
                        if (loaded) {
                            // Show something like a progress bar being hidden
                            Log.d("Contacts", "Device contacts loaded")
                        }
                    }
                }

                launch {
                    viewModel.isListScreenVisible.collect { isListVisible ->
                        if (isListVisible) {
                            binding.gridViewContainer.visibility = View.GONE
                            binding.listViewContainer.visibility = View.VISIBLE

                            binding.searchView2.post {
                                val searchEditText = binding.searchView2.findViewById<EditText>(
                                    androidx.appcompat.R.id.search_src_text
                                )
                                searchEditText?.requestFocus()
                            }
                        } else {
                            binding.gridViewContainer.visibility = View.VISIBLE
                            binding.listViewContainer.visibility = View.GONE
                        }
                    }
                }
            }
        }
    }

    private fun setupSearchView() {
        binding.searchView1.setOnQueryTextFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding.searchView1.clearFocus()
                viewModel.showListScreen()
            }
        }

        binding.searchView2.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                viewModel.setSearchQuery(query.orEmpty())
                return true
            }

            override fun onQueryTextChange(query: String?): Boolean {
                viewModel.setSearchQuery(query.orEmpty())
                return true
            }
        })

        binding.searchView2.setOnCloseListener {
            binding.searchView2.clearFocus()
            viewModel.showGridScreen()
            false
        }
    }

    private var isLoading = false
    private var currentPage = 0

    //    private val batchSize = 200 // TODO: check what batch size to use
    private val batchSize = 10

//    private fun loadMoreContacts() {
//        isLoading = true
//        // Show loading footer here if needed
//
//        viewModel.loadDeviceContacts(this, currentPage * batchSize, batchSize)
//        currentPage++
//    }

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
//    private val autoLoadRunnable = object : Runnable {
//        override fun run() {
//            if (!isLoading && isLastItemVisible()) {
//                isLoading = true
//                loadMoreContacts()
//            }
//            // Keep checking every 500ms
//            if (hasMoreContacts) {
//                binding.recyclerView.postDelayed(this, 500)
//            }
//        }
//    }

//    override fun onDestroy() {
//        super.onDestroy()
//        binding.recyclerView.removeCallbacks(autoLoadRunnable)
//    }

    /**
     * Use Android Profiler > CPU > Main Thread to see when frames are dropped or long tasks occur.
     */
}
