package com.example.chooserecipientxml.model

import java.io.Serializable

data class Contact(
    val id: String,
    val name: String,
    val phoneNumber: String,
    var source: ContactSource? = null
): Serializable
