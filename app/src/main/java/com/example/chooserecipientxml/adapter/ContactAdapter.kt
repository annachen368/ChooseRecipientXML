package com.example.chooserecipientxml.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.chooserecipientxml.databinding.ItemContactBinding
import com.example.chooserecipientxml.model.Contact
import com.example.chooserecipientxml.utils.DiffCallback

class ContactAdapter : ListAdapter<Contact, ContactAdapter.ContactViewHolder>(DiffCallback()) {

    private val allContacts = mutableListOf<Contact>() // Stores all data for filtering

    class ContactViewHolder(private val binding: ItemContactBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(contact: Contact) {
            binding.contactName.text = contact.name
            binding.contactPhone.text = contact.phoneNumber
            binding.contactSource.text = contact.source?.name ?: "Unknown" // ✅ Prevents null crashes
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        val binding = ItemContactBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ContactViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    /**
     * Append new recipients (for pagination)
     */
    fun addRecipients(newRecipients: List<Contact>) {
        allContacts.clear()
        allContacts.addAll(newRecipients)
        submitList(allContacts.toList()) // ✅ Use submitList for efficient UI updates
    }

    fun getAllContacts(): List<Contact> {
        return allContacts.toList() // ✅ Needed to merge device + service contacts
    }
//    fun addRecipients(newRecipients: List<Contact>) {
//        allContacts.addAll(newRecipients) // Store all contacts for filtering
//        submitList(allContacts.toList()) // Trigger DiffUtil for smooth updates
//    }

    /**
     * Filter contacts based on the search query
     */
    fun filter(query: String) {
        val filteredList = if (query.isEmpty()) {
            allContacts
        } else {
            allContacts.filter { it.name.contains(query, ignoreCase = true) }
        }
        submitList(filteredList) // Updates UI efficiently
    }
}