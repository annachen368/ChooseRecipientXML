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

    private var serviceContacts = mutableListOf<Contact>()
    private var deviceContacts = mutableListOf<Contact>() // pagination
    private var activatedDeviceContacts = mutableListOf<Contact>() // pagination
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

//    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
//        val binding = ItemContactBinding.inflate(LayoutInflater.from(parent.context), parent, false)
//        return ContactViewHolder(binding)
//    }

//    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
//        holder.bind(getCurrentList()[position]) // ✅ Use filtered list when searching
//    }
    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_CONTACT = 1
        private const val VIEW_TYPE_LOADING = 2
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

        val item = currentList[position]

        if (holder is ContactViewHolder && item != null) {
            holder.bind(item)
        } else if (holder is HeaderViewHolder && item == null) {
            val title = if (position == 0) {
                "Service Contacts"
            } else if (position == 1) {
                "Device Contacts"
            } else {
                "ACTIVATED Device Contacts"
            }
            holder.bind(title)
        }
    }

    override fun getItemCount(): Int {
        var count = getCurrentListWithHeaders().size
        if (showLoadingFooter) count += 1
        return count
    }

    override fun getItemViewType(position: Int): Int {
        val currentList = getCurrentListWithHeaders()
        return when {
            position < currentList.size -> {
                if (currentList[position] == null) VIEW_TYPE_HEADER else VIEW_TYPE_CONTACT
            }
            showLoadingFooter && position == currentList.size -> VIEW_TYPE_LOADING
            else -> VIEW_TYPE_CONTACT
        }
    }

    private fun getCurrentListWithHeaders(): List<Contact?> {
        return if (isSearching) {
            filteredContacts
        } else {
            val list = mutableListOf<Contact?>()
            if (serviceContacts.isNotEmpty()) {
                list.add(null) // Header placeholder
                list.addAll(serviceContacts)
            }
//            if (deviceContacts.isNotEmpty()) {
//                list.add(null) // Another header
//                list.addAll(deviceContacts)
//            }
            if (activatedDeviceContacts.isNotEmpty()) {
                list.add(null) // Another header
                list.addAll(activatedDeviceContacts)
            }
            list
        }
    }

    /**
     * ✅ Adds new contacts from service response.
     */
    fun addServiceContacts(newRecipients: List<Contact>) {
        if (!isSearching) { // ✅ Avoid modifying contacts if filtering is active
            val uniqueRecipients = newRecipients.filter { newContact ->
                serviceContacts.none { it.id == newContact.id } // ✅ Prevents duplicates
            }

            serviceContacts.addAll(uniqueRecipients) // ✅ Append only unique contacts
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

    fun addActivatedDeviceContacts(newDeviceContacts: List<Contact>) {
        if (!isSearching) {
            activatedDeviceContacts.addAll(newDeviceContacts)
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
            (serviceContacts + deviceContacts).filter { contact ->
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
                notifyItemInserted(itemCount) // last item
            } else {
                notifyItemRemoved(itemCount) // last item
            }
        }
    }
}