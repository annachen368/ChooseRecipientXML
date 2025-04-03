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
import com.example.chooserecipientxml.viewmodel.ContactListItem
import java.util.Locale

class ContactAdapter(private val context: Context) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<ContactListItem>()

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_CONTACT = 1
        private const val VIEW_TYPE_DISCLOSURE = 2
    }

    inner class ContactViewHolder(private val binding: ItemContactBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(contact: Contact) {
            binding.contactName.text = contact.name
            binding.contactPhone.text = contact.phoneNumber
            binding.contactStatus.text = contact.status ?: "Unknown"
            binding.contactSource.text = contact.source?.name ?: "Unknown"

            binding.root.setOnClickListener {
                val intent = Intent(context, ContactDetailActivity::class.java).apply {
                    putExtra("CONTACT", contact)
                }
                context.startActivity(intent)
            }
        }
    }

    inner class HeaderViewHolder(private val binding: ItemHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(title: String) {
            binding.headerTitle.text = title
        }
    }

    inner class DisclosureViewHolder(binding: ItemDisclosureBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is ContactListItem.Header -> VIEW_TYPE_HEADER
            is ContactListItem.ContactItem -> VIEW_TYPE_CONTACT
            is ContactListItem.Disclosure -> VIEW_TYPE_DISCLOSURE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_HEADER -> {
                val binding = ItemHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                HeaderViewHolder(binding)
            }
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

    override fun getItemCount(): Int = items.size


    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is ContactListItem.Header -> (holder as HeaderViewHolder).bind(item.title)
            is ContactListItem.ContactItem -> (holder as ContactViewHolder).bind(item.contact)
            is ContactListItem.Disclosure -> Unit // No binding needed
        }
    }

    fun submitItems(newItems: List<ContactListItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}