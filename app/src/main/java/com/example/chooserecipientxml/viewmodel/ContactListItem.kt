package com.example.chooserecipientxml.viewmodel

import com.example.chooserecipientxml.model.Contact

sealed class ContactListItem {
    data class Header(val title: String) : ContactListItem()
    data class ContactItem(val contact: Contact) : ContactListItem()
    object Disclosure : ContactListItem()
}