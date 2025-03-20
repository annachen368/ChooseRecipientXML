package com.example.chooserecipientxml.adapter

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.chooserecipientxml.databinding.ItemContactBinding
import com.example.chooserecipientxml.model.Contact
import com.example.chooserecipientxml.ui.ContactDetailActivity
import java.util.Locale

class ContactAdapter(private val context: Context) : RecyclerView.Adapter<ContactAdapter.ContactViewHolder>() {

    private var allContacts = mutableListOf<Contact>() // ✅ Full dataset (pagination)
    private var filteredContacts = mutableListOf<Contact>() // ✅ Stores filtered contacts
    private var isSearching = false // ✅ Tracks search state

    inner class ContactViewHolder(private val binding: ItemContactBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(contact: Contact) {
            binding.contactName.text = contact.name
            binding.contactPhone.text = contact.phoneNumber
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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        val binding = ItemContactBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ContactViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        holder.bind(getCurrentList()[position]) // ✅ Use filtered list when searching
    }

    override fun getItemCount(): Int = getCurrentList().size

    private fun getCurrentList(): List<Contact> {
        return if (isSearching) filteredContacts else allContacts // ✅ Show search results if filtering
    }

    /**
     * ✅ Adds new recipients from pagination.
     */
    fun addRecipients(newRecipients: List<Contact>) {
        if (!isSearching) { // ✅ Avoid modifying contacts if filtering is active
            val uniqueRecipients = newRecipients.filter { newContact ->
                allContacts.none { it.id == newContact.id } // ✅ Prevents duplicates
            }

            allContacts.addAll(uniqueRecipients) // ✅ Append only unique contacts
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
            allContacts.filter { contact ->
                contact.name.lowercase(Locale.getDefault()).contains(lowerCaseQuery) ||
                        contact.phoneNumber.lowercase(Locale.getDefault()).contains(lowerCaseQuery)
            }.toMutableList()
        } else {
            mutableListOf() // ✅ Clear filtered list when search is empty
        }

        notifyDataSetChanged()
    }
}