package com.example.chooserecipientxml.adapter

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.constraintlayout.motion.widget.MotionScene.Transition.TransitionOnClick
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.chooserecipientxml.databinding.ItemContactBinding
import com.example.chooserecipientxml.databinding.ItemDisclosureBinding
import com.example.chooserecipientxml.databinding.ItemHeaderBinding
import com.example.chooserecipientxml.model.Contact
import com.example.chooserecipientxml.ui.ContactDetailActivity
import com.example.chooserecipientxml.viewmodel.ContactListItem

class ContactAdapter(private val tokenThumbnailMap: Map<String, String>, private val onContactClick: (Contact) -> Unit) :
    ListAdapter<ContactListItem, RecyclerView.ViewHolder>(ContactListItemDiffCallback()) {

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_CONTACT = 1
        private const val VIEW_TYPE_DISCLOSURE = 2
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is ContactListItem.Header -> VIEW_TYPE_HEADER
            is ContactListItem.ContactItem -> VIEW_TYPE_CONTACT
            is ContactListItem.Disclosure -> VIEW_TYPE_DISCLOSURE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_HEADER -> {
                val binding = ItemHeaderBinding.inflate(inflater, parent, false)
                HeaderViewHolder(binding)
            }
            VIEW_TYPE_CONTACT -> {
                val binding = ItemContactBinding.inflate(inflater, parent, false)
                ContactViewHolder(binding)
            }
            VIEW_TYPE_DISCLOSURE -> {
                val binding = ItemDisclosureBinding.inflate(inflater, parent, false)
                DisclosureViewHolder(binding)
            }
            else -> throw IllegalArgumentException("Unknown view type $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is ContactListItem.Header -> (holder as HeaderViewHolder).bind(item)
            is ContactListItem.ContactItem -> (holder as ContactViewHolder).bind(item.contact)
            is ContactListItem.Disclosure -> {} // No binding needed
        }
    }

    inner class ContactViewHolder(private val binding: ItemContactBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(contact: Contact) {
            binding.contactName.text = contact.name
            binding.contactPhone.text = contact.token
            binding.contactStatus.text = contact.status ?: "Unknown"
            binding.contactSource.text = contact.source?.name ?: "Unknown"

            val thumbnailUrl = contact.thumbnail ?: tokenThumbnailMap[contact.token]

            Glide.with(itemView.context)
                .load(thumbnailUrl) // TODO: check how to load image for service contacts, should i use map here to match token?
//                .placeholder(R.drawable.placeholder_avatar)
//                .error(R.drawable.default_avatar)
                .circleCrop()
                .into(binding.contactImage)

            binding.root.setOnClickListener {
                onContactClick(contact)
            }
        }
    }

    inner class HeaderViewHolder(private val binding: ItemHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(header: ContactListItem.Header) {
            binding.headerTitle.text = header.title
        }
    }

    inner class DisclosureViewHolder(binding: ItemDisclosureBinding) :
        RecyclerView.ViewHolder(binding.root)
}

class ContactListItemDiffCallback : DiffUtil.ItemCallback<ContactListItem>() {
    override fun areItemsTheSame(oldItem: ContactListItem, newItem: ContactListItem): Boolean {
        return when {
            oldItem is ContactListItem.Header && newItem is ContactListItem.Header ->
                oldItem.title == newItem.title
            oldItem is ContactListItem.ContactItem && newItem is ContactListItem.ContactItem ->
                // TODO: check if oldItem.contact.id == newItem.contact.id can be used // Use a unique id
//                oldItem.contact.id == newItem.contact.id // to avoid scrolling to top after update contact status
                oldItem.contact.token == newItem.contact.token
            oldItem is ContactListItem.Disclosure && newItem is ContactListItem.Disclosure ->
                true // Only one disclosure, treat as same
            else -> false
        }
    }

    override fun areContentsTheSame(oldItem: ContactListItem, newItem: ContactListItem): Boolean {
        return oldItem == newItem // for data classes, this checks all fields
    }
}