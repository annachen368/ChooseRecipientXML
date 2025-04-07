package com.example.chooserecipientxml.ui

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.SearchView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.chooserecipientxml.adapter.ContactAdapter
import com.example.chooserecipientxml.databinding.ActivityContactsBinding
import com.example.chooserecipientxml.databinding.ItemContactGridBinding
import com.example.chooserecipientxml.network.ApiService
import com.example.chooserecipientxml.repository.ContactRepository
import com.example.chooserecipientxml.viewmodel.ContactListItem
import com.example.chooserecipientxml.viewmodel.ContactViewModel
import com.example.chooserecipientxml.viewmodel.ContactViewModelFactory
import kotlinx.coroutines.delay
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

        adapter = ContactAdapter(context = this, viewModel.tokenThumbnailMap)
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
                if (!viewModel.isSearchMode.value) return

                val now = System.currentTimeMillis()
                if (now - lastStatusCheckTime < statusCheckDebounceMs) return
                lastStatusCheckTime = now

                val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
                val firstVisible = layoutManager.findFirstVisibleItemPosition()
                val lastVisible = layoutManager.findLastVisibleItemPosition()

                // To check if any of the currently visible contacts have an unknown status in the RecyclerView,
                for (i in firstVisible..lastVisible) {
                    val item = adapter.currentList.getOrNull(i)
                    if (item is ContactListItem.ContactItem) {
                        val contact = item.contact
                        if (contact.status == null) {
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
                    while (true) {
                        delay(1000L) // Check every second

                        if (viewModel.isSearchMode.value) continue // only check in normal mode

                        val layoutManager =
                            binding.recyclerView.layoutManager as? LinearLayoutManager ?: continue
                        val totalCount = layoutManager.itemCount
                        val lastVisible = layoutManager.findLastVisibleItemPosition()

                        // Trigger status check if user is near the bottom of the list
                        if (lastVisible != -1 && lastVisible >= totalCount - 2) {
                            Log.d("ThreadCheck", "lastVisible: $lastVisible, totalCount: $totalCount")
                            viewModel.checkVisibleStatus()
                        }
                    }
                }

                launch {
                    // Might not need to listen to this
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

                            val thumbnailUrl = contact.thumbnail ?: viewModel.tokenThumbnailMap[contact.phoneNumber]

                            itemBinding.name.text = contact.name
                            itemBinding.token.text = contact.phoneNumber
                            Glide.with(this@ContactsActivity)
                                .load(thumbnailUrl) // TODO: check how to load image for service contacts, should i use map here to match token?
//                .placeholder(R.drawable.placeholder_avatar)
//                .error(R.drawable.default_avatar)
                                .circleCrop()
                                .into(itemBinding.contactImage)

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
