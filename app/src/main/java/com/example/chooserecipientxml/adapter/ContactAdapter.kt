package com.example.chooserecipientxml.adapter

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.chooserecipientxml.R
import com.example.chooserecipientxml.databinding.ItemContactBinding
import com.example.chooserecipientxml.databinding.ItemHeaderBinding
import com.example.chooserecipientxml.model.Contact
import com.example.chooserecipientxml.model.ContactSource
import com.example.chooserecipientxml.ui.ContactDetailActivity
import java.util.Locale

class ContactAdapter(private val context: Context) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var serviceRecentContacts = mutableListOf<Contact>()
    private var serviceMyContacts = mutableListOf<Contact>()
    private var deviceContacts = mutableListOf<Contact>() // pagination
    private var deviceActiveContacts = mutableListOf<Contact>() // pagination
    private var filteredContacts = mutableListOf<Contact>() // ✅ Stores filtered contacts
    private var isSearching = false // ✅ Tracks search state
    private var showLoadingFooter = false

    inner class ContactViewHolder(private val binding: ItemContactBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(contact: Contact) {
            binding.contactName.text = contact.name
            binding.contactPhone.text = contact.phoneNumber
            if (contact.source == ContactSource.SERVICE) {
                binding.contactStatus.text = "ACTIVE"
            } else {
                binding.contactStatus.text = contact.status ?: "Unknown"
            }

            binding.contactSource.text = contact.source?.name ?: "Unknown"

            // Set the click listener for the item
            binding.root.setOnClickListener {
                val intent = Intent(context, ContactDetailActivity::class.java).apply {
                    putExtra("CONTACT", contact)
                }
                context.startActivity(intent)
            }
        }
    }

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_CONTACT = 1
        private const val VIEW_TYPE_LOADING = 2
        private const val VIEW_TYPE_DISCLOSURE = 3
    }

    inner class HeaderViewHolder(private val binding: ItemHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(title: String) {
            binding.headerTitle.text = title
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_HEADER -> {
                val binding = ItemHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                HeaderViewHolder(binding)
            }
            VIEW_TYPE_LOADING -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_loading_footer, parent, false)
                object : RecyclerView.ViewHolder(view) {}
            }
            VIEW_TYPE_DISCLOSURE -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_disclosure_footer, parent, false)
                object : RecyclerView.ViewHolder(view) {}
            }
            else -> {
                val binding = ItemContactBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                ContactViewHolder(binding)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val currentList = getCurrentListWithHeaders()

        if (showLoadingFooter && position == currentList.size) {
            // This is the footer view. No binding needed.
            return
        }

        if (position == itemCount - 1 && getItemViewType(position) == VIEW_TYPE_DISCLOSURE) {
            // optional: configure disclosure text if needed
            return
        }

        val item = currentList[position]

        if (holder is ContactViewHolder && item != null) {
            holder.bind(item)
        } else if (holder is HeaderViewHolder && item == null) {
            val title = if (position == 0) {
                "Service Contacts - Recent"
            } else if (position == 1) {
                "Service Contacts - My Contacts"
            } else {
                "ACTIVATED Device Contacts"
            }
            holder.bind(title)
        }
    }

    override fun getItemCount(): Int {
        var count = getCurrentListWithHeaders().size
        if (showLoadingFooter) count += 1
        count += 1 // 👈 For disclosure
        return count
    }

    override fun getItemViewType(position: Int): Int {
        val currentList = getCurrentListWithHeaders()
        return when {
            position < currentList.size -> {
                if (currentList[position] == null) VIEW_TYPE_HEADER else VIEW_TYPE_CONTACT
            }
            showLoadingFooter && position == currentList.size -> VIEW_TYPE_LOADING
            position == itemCount - 1 -> VIEW_TYPE_DISCLOSURE // 👈 Final position is disclosure
            else -> VIEW_TYPE_CONTACT
        }
    }

    private fun getCurrentListWithHeaders(): List<Contact?> {
        return if (isSearching) {
            filteredContacts
        } else {
            val list = mutableListOf<Contact?>()
            if (serviceRecentContacts.isNotEmpty()) {
                list.add(null) // Header placeholder
                list.addAll(serviceRecentContacts)
            }
            if (serviceMyContacts.isNotEmpty()) {
                list.add(null) // Another header
                list.addAll(serviceMyContacts)
            }
            if (deviceActiveContacts.isNotEmpty()) {
                list.add(null) // Another header
                list.addAll(deviceActiveContacts)
            }
//            if (deviceContacts.isNotEmpty()) {
//                list.add(null) // Another header
//                list.addAll(deviceContacts)
//            }
            list
        }
    }

    /**
     * ✅ Adds new contacts from service response.
     */
    fun addServiceRecentContacts(newRecipients: List<Contact>) {
        if (!isSearching) { // ✅ Avoid modifying contacts if filtering is active
            serviceRecentContacts.addAll(newRecipients) // ✅ Append only unique contacts
            notifyDataSetChanged()
        }
    }

    /**
     * ✅ Adds new contacts from service response.
     */
    fun addServiceMyContacts(newRecipients: List<Contact>) {
        if (!isSearching) { // ✅ Avoid modifying contacts if filtering is active
            serviceMyContacts.addAll(newRecipients) // ✅ Append only unique contacts
            notifyDataSetChanged()
        }
    }

    /**
     * ✅ Adds new contacts from device contact pagination.
     */
    fun addDeviceContacts(newDeviceContacts: List<Contact>) {
        if (!isSearching) {
            deviceContacts.addAll(newDeviceContacts)
            notifyDataSetChanged()
        }
    }

    fun addDeviceActiveContacts(newDeviceContacts: List<Contact>) {
        if (!isSearching) {
            deviceActiveContacts.addAll(newDeviceContacts)
            notifyDataSetChanged()
        }
    }

    /**
     * ✅ Filters contacts when user types in search.
     */
    fun filter(query: String) {
        val lowerCaseQuery = query.lowercase(Locale.getDefault())

        isSearching = lowerCaseQuery.isNotEmpty()

        filteredContacts = if (isSearching) {
            (serviceRecentContacts + serviceMyContacts + deviceContacts).filter { contact ->
                contact.name.lowercase(Locale.getDefault()).contains(lowerCaseQuery) ||
                        contact.phoneNumber.lowercase(Locale.getDefault()).contains(lowerCaseQuery)
            }.toMutableList()
        } else {
            mutableListOf() // ✅ Clear filtered list when search is empty
        }

        notifyDataSetChanged()
    }

    fun setLoadingFooterVisible(visible: Boolean) {
        if (showLoadingFooter != visible) {
            showLoadingFooter = visible
            if (visible) {
//                notifyItemInserted(itemCount) // last item
                notifyDataSetChanged()
            } else {
//                notifyItemRemoved(itemCount) // last item
                notifyDataSetChanged()
            }
        }
    }
}