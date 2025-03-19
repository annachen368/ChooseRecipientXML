package com.example.chooserecipientxml.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.chooserecipientxml.databinding.ItemContactBinding
import com.example.chooserecipientxml.model.Contact

class ContactAdapter : RecyclerView.Adapter<ContactAdapter.ContactViewHolder>() {

    private val allContacts = mutableListOf<Contact>() // ✅ Store all contacts

    class ContactViewHolder(private val binding: ItemContactBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(contact: Contact) {
            binding.contactName.text = contact.name
            binding.contactPhone.text = contact.phoneNumber
            binding.contactSource.text = contact.source?.name ?: "Unknown"
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        val binding = ItemContactBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ContactViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        holder.bind(allContacts[position])
    }

    override fun getItemCount(): Int = allContacts.size

    /**
     * ✅ Efficiently append new recipients while preventing duplicates.
     */
    fun addRecipients(newRecipients: List<Contact>) {
        val startPosition = allContacts.size

        val filteredRecipients = newRecipients.filter { newContact ->
            allContacts.none { it.id == newContact.id } // ✅ Prevent duplicate entries
        }

        if (filteredRecipients.isNotEmpty()) {
            allContacts.addAll(filteredRecipients) // ✅ Append only unique items
            notifyItemRangeInserted(startPosition, filteredRecipients.size)
        }
    }

    /**
     * ✅ Get all contacts (useful for merging device & service contacts)
     */
    fun getAllContacts(): List<Contact> {
        return allContacts.toList()
    }
}