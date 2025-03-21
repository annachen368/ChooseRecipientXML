package com.example.chooserecipientxml.model

data class CustomerProfileResponse(
    val outageFlag: Boolean,
    val customer: Customer,
    val recipients: List<Recipient>,
    val defaultFundingAccountId: String,
    val payFeaturesEnabled: Boolean,
    val requestFeatureEnabled: Boolean,
    val tagEnabled: Boolean
)
