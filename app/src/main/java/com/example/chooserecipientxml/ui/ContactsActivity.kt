package com.example.chooserecipientxml.ui

import android.animation.ValueAnimator
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.SearchView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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
    private var isHeaderCollapsed = false
    private var originalHeaderHeight = 0

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

                // Slight delay before checking scroll state (to give layout time to update)
//              // ✅ Important: Check again in case list is still too short to scroll
//                binding.recyclerView.post {
//                    if (!binding.recyclerView.canScrollVertically(1) && contacts.isNotEmpty()) {
//                        isLoading = true
//                        loadMoreContacts()
//                    }
//                }

                // Keep loading more if RecyclerView is still not scrollable
//                // TODO: check why last one is not showing...?? last step
//                if (!isLoading && !binding.recyclerView.canScrollVertically(1)) {
//                    if (contacts.isNotEmpty()) {
//                        loadMoreContacts()
//                    } else {
//                        binding.recyclerView.scrollToPosition(adapter.itemCount - 1)
//                    }
//                }
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
//            if (isLoaded == true) {
//                binding.progressBar.visibility = View.GONE
//            } else {
//                binding.progressBar.visibility = View.VISIBLE
//            }
        }

        // ✅ Implement Infinite Scroll for Device Contacts
//        Since NestedScrollView is now handling the scrolling, you need to observe its scroll instead of RecyclerView’s:
//        binding.nestedScrollView.setOnScrollChangeListener { v, _, scrollY, _, oldScrollY ->
//            val view = v as NestedScrollView
//            val diff = view.getChildAt(view.childCount - 1).bottom - (view.height + scrollY)
//
//            if (diff <= 0 && !isLoading) {
//                isLoading = true
//                loadMoreContacts()
//            }
//        }

//        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
//            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
//                super.onScrolled(recyclerView, dx, dy)
//
//                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
//                val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()
//                val lastVisibleItemPosition = layoutManager.findLastVisibleItemPosition()
//                val visibleItemCount = layoutManager.childCount
//                val totalItemCount = layoutManager.itemCount
//
//                Log.d("ScrollCheck", "visible=$visibleItemCount, firstVisible=$firstVisibleItemPosition, total=$totalItemCount")
//
////                val visibleThreshold = 10 // small value to pre-load early
////                if (!isSearching && !isLoading && (firstVisibleItemPosition + visibleItemCount + visibleThreshold) >= totalItemCount) {
////                    loadMoreContacts()
////                }
//
//                // Don’t rely solely on canScrollVertically(1)
//                // near
//                val isAtBottom = lastVisibleItemPosition + 5 >= totalItemCount//firstVisibleItemPosition + visibleItemCount >= totalItemCount - 1
//                if (!isLoading && isAtBottom) {
//                    loadMoreContacts()
//                }
//            }
//        })

        // ✅ Implement Search Filtering
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false

            override fun onQueryTextChange(newText: String?): Boolean {
                isSearching = !newText.isNullOrEmpty()
                adapter.filter(newText ?: "") // ✅ Update list dynamically
                return true
            }
        })

        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {

            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)

                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val firstVisible = layoutManager.findFirstVisibleItemPosition()

                // 🟢 When scrolling stops, check if we're at the top
                if (newState == RecyclerView.SCROLL_STATE_IDLE && firstVisible == 0) {
                    showHeader()
                }
            }

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)

                if (dy > 10) {
                    hideHeader()
                }
            }
        })

    }
    private var isHeaderHidden = false

    private fun hideHeader() {
        if (!isHeaderHidden) {
            binding.headerGroup.visibility = View.GONE
            isHeaderHidden = true
        }
    }

    private fun showHeader() {
        if (isHeaderHidden) {
            binding.headerGroup.visibility = View.VISIBLE
            isHeaderHidden = false
        }
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
//    private fun isLastItemVisible(): Boolean {
//        val layoutManager = binding.recyclerView.layoutManager as LinearLayoutManager
//        val lastVisibleItemPosition = layoutManager.findLastCompletelyVisibleItemPosition()
//        val totalItemCount = layoutManager.itemCount
//
//        return lastVisibleItemPosition == totalItemCount - 1
//    }
    private fun isLastItemVisible(): Boolean {
        val lastIndex = binding.recyclerView.adapter?.itemCount?.minus(1) ?: return false
        val viewHolder = binding.recyclerView.findViewHolderForAdapterPosition(lastIndex) ?: return false

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
