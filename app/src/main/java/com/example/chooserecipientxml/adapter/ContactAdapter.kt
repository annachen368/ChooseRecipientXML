package com.example.chooserecipientxml.adapter

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.chooserecipientxml.databinding.ItemContactBinding
import com.example.chooserecipientxml.databinding.ItemDisclosureBinding
import com.example.chooserecipientxml.databinding.ItemHeaderBinding
import com.example.chooserecipientxml.model.Contact
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
            binding.contactStatus.text = contact.status ?: "Unknown"
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

        private const val CELL_TYPE_CONTACT = 0
        private const val CELL_TYPE_HEADER = 1
        private const val CELL_TYPE_DISCLOSURE = 2
    }

    inner class HeaderViewHolder(private val binding: ItemHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(title: String) {
            binding.headerTitle.text = title
        }
    }

    inner class DisclosureViewHolder(private val binding: ItemDisclosureBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind() {
//            val viewStub = binding.disclosure
//            if (viewStub.parent != null) {
//                viewStub.inflate()
//            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_HEADER -> {
                val binding = ItemHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                HeaderViewHolder(binding)
            }
//            VIEW_TYPE_LOADING -> {
//                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_loading_footer, parent, false)
//                object : RecyclerView.ViewHolder(view) {}
//            }
            VIEW_TYPE_DISCLOSURE -> {
                val binding = ItemDisclosureBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                DisclosureViewHolder(binding)
            }
            else -> {
                val binding = ItemContactBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                ContactViewHolder(binding)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val currentList = getCurrentListWithHeaders()

//        if (showLoadingFooter && position == currentList.size) {
//            // This is the footer view. No binding needed.
//            return
//        }

        val item = currentList[position]

        when (holder) {
            is ContactViewHolder -> {
                holder.bind(item)
            }
            is HeaderViewHolder -> {
                holder.bind(item.name)
            }
            is DisclosureViewHolder -> {
                // Handle disclosure view binding if needed
                holder.bind()
            }
        }
    }

    override fun getItemCount(): Int {
        var count = getCurrentListWithHeaders().size
//        if (showLoadingFooter) count += 1
        return count
    }

    override fun getItemViewType(position: Int): Int {
        val currentList = getCurrentListWithHeaders()
        return when(currentList[position].cellType) {
            CELL_TYPE_HEADER -> VIEW_TYPE_HEADER
            CELL_TYPE_DISCLOSURE -> VIEW_TYPE_DISCLOSURE
            else -> VIEW_TYPE_CONTACT
        }
    }

    private fun getHeader(title: String): Contact {
        return Contact(id = "", name = title, phoneNumber = "", cellType = CELL_TYPE_HEADER)
    }

    fun getDisclosure(): Contact {
        return Contact(id = "", name = "Disclosure", phoneNumber = "", cellType = CELL_TYPE_DISCLOSURE)
    }

    private fun getCurrentListWithHeaders(): List<Contact> {
        val list = mutableListOf<Contact>()
        return if (isSearching) {
            filteredContacts
            list.add(getDisclosure())
            list
        } else {
//            val list = mutableListOf<Contact>()
            if (serviceRecentContacts.isNotEmpty()) {
                list.add(getHeader("Service Contacts - Recent")) // Header placeholder
                list.addAll(serviceRecentContacts)
            }
            if (serviceMyContacts.isNotEmpty()) {
                list.add(getHeader("Service Contacts - My Contacts")) // Another header
                list.addAll(serviceMyContacts)
            }
            if (deviceActiveContacts.isNotEmpty()) {
                list.add(getHeader("ACTIVATED Device Contacts")) // Another header
                list.addAll(deviceActiveContacts)
            }
//            if (deviceContacts.isNotEmpty()) {
//                list.add(null) // Another header
//                list.addAll(deviceContacts)
//            }
            list.add(getDisclosure()) // Disclosure
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