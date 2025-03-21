package com.example.chooserecipientxml.model

data class Customer(
    val eligibilityProfile: EligibilityProfile,
    val customerType: String,
    val P2PserviceStatus: Boolean
)