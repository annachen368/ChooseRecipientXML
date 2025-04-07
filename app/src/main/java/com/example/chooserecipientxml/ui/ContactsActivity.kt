package com.example.chooserecipientxml.ui

import android.os.Bundle
import android.view.View
import android.widget.SearchView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.chooserecipientxml.adapter.ContactAdapter
import com.example.chooserecipientxml.databinding.ActivityContactsBinding
import com.example.chooserecipientxml.databinding.ItemContactGridBinding
import com.example.chooserecipientxml.network.ApiService
import com.example.chooserecipientxml.repository.ContactRepository
import com.example.chooserecipientxml.viewmodel.ContactListItem
import com.example.chooserecipientxml.viewmodel.ContactViewModel
import com.example.chooserecipientxml.viewmodel.ContactViewModelFactory
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class ContactsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityContactsBinding
    private lateinit var viewModel: ContactViewModel
    private lateinit var adapter: ContactAdapter

    private var lastStatusCheckTime = 0L
    private val statusCheckDebounceMs = 300L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityContactsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val apiService = ApiService.create()
        val repository = ContactRepository(applicationContext, apiService)
        val factory = ContactViewModelFactory(repository)

        viewModel = ViewModelProvider(this, factory)[ContactViewModel::class.java]

        adapter = ContactAdapter(context = this)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
        binding.recyclerView.itemAnimator = null


        viewModel.loadAllContacts()
        observeContacts()
        setupSearchView()

        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                // Debounce the scroll-triggered calls
                //In your scroll listener, it’s possible to call checkVisibleSearchStatus()
                // multiple times per frame. Instead, debounce it slightly using Handler.postDelayed
                // or store a flag
                val now = System.currentTimeMillis()
                if (now - lastStatusCheckTime < statusCheckDebounceMs) return
                lastStatusCheckTime = now

                val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
                val firstVisible = layoutManager.findFirstVisibleItemPosition()
                val lastVisible = layoutManager.findLastVisibleItemPosition()

                if (!viewModel.isSearchMode.value) return

                // To check if any of the currently visible contacts have an unknown status in the RecyclerView,
                for (i in firstVisible..lastVisible) {
                    val item = adapter.currentList.getOrNull(i)
                    if (item is ContactListItem.ContactItem) {
                        val contact = item.contact
                        if (viewModel.isSearchMode.value && contact.status == null) {
                            viewModel.checkVisibleSearchStatus()
                            // can't use for normal mode as we can't see the unknown status contacts
                            break
                        }
                    }
                }
            }
        })

    }

    private fun observeContacts() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    combine(
                        viewModel.displayList,
                        viewModel.shouldScrollToTop
                    ) { items, shouldScroll ->
                        items to shouldScroll
                    }.distinctUntilChanged().collectLatest { (items, shouldScroll) ->
                        adapter.submitList(items) {
                            if (shouldScroll) {
                                binding.recyclerView.scrollToPosition(0)
                            }
                        }
                    }
                }

                launch {
                    viewModel.topRecentServiceContacts.collect { contacts ->
                        binding.contactGrid.removeAllViews()

                        val columnCount = 3
                        val maxItems = 6
                        val visibleCount = contacts.size

                        // Add real items
                        contacts.forEach { contact ->
                            val itemBinding = ItemContactGridBinding.inflate(
                                layoutInflater, binding.contactGrid, false
                            )

                            itemBinding.name.text = contact.name
                            itemBinding.token.text = contact.phoneNumber

                            itemBinding.root.setOnClickListener {
                                // Handle click
                            }

                            binding.contactGrid.addView(itemBinding.root)
                        }

                        // Add invisible placeholders to maintain grid structure
                        val totalToAdd = (columnCount - (visibleCount % columnCount)) % columnCount
                        repeat(totalToAdd) {
                            val placeholder = ItemContactGridBinding.inflate(
                                layoutInflater, binding.contactGrid, false
                            )
                            placeholder.root.visibility = View.INVISIBLE
                            binding.contactGrid.addView(placeholder.root)
                        }
                    }
                }

                launch {
                    viewModel.isListScreenVisible.collect { isListVisible ->
                        if (isListVisible) {
                            binding.gridViewContainer.visibility = View.GONE
                            binding.listViewContainer.visibility = View.VISIBLE
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
                return true
            }

            override fun onQueryTextChange(query: String?): Boolean {
                viewModel.setSearchQuery(query.orEmpty())
                return true
            }
        })

        binding.searchView2.setOnCloseListener {
            viewModel.showGridScreen()
            false
        }
    }
}
