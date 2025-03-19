package com.example.chooserecipientxml.model

data class Contact(
    val id: String,
    val name: String,
    val phoneNumber: String,
    var source: ContactSource? = null
)
