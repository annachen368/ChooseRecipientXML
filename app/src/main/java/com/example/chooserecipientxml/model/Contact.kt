package com.example.chooserecipientxml.model

import java.io.Serializable

data class Contact(
    val name: String,
    val token: String,
    var source: ContactSource? = null,
    var thumbnail: String? = null,
    var status: String? = null,
    var level: String? = null,
    var cellType: Int = 0
): Serializable
