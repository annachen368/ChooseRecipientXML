package com.example.chooserecipientxml.utils

import androidx.recyclerview.widget.DiffUtil
import com.example.chooserecipientxml.model.Contact

class DiffCallback : DiffUtil.ItemCallback<Contact>() {
    override fun areItemsTheSame(oldItem: Contact, newItem: Contact): Boolean {
        return oldItem.id == newItem.id  // Compare unique IDs
    }

    override fun areContentsTheSame(oldItem: Contact, newItem: Contact): Boolean {
        return oldItem == newItem  // Compare entire object
    }
}