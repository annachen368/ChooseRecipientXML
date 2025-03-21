package com.example.chooserecipientxml.model

data class ContactStatusRequest(
    val contactTokens: List<ContactTokenRequest>
)

data class ContactTokenRequest(
    val identifier: Identifier
)

data class Identifier(
    var type: String,
    val value: String
)

data class ContactStatusResponse(
    val contactTokens: List<ContactTokenResponse>
)

data class ContactTokenResponse(
    val identifier: Identifier,
    val contactStatus: ContactStatus?
)

data class ContactStatus(
    val value: String
)