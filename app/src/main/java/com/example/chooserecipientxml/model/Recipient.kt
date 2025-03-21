package com.example.chooserecipientxml.model

data class Recipient(
    val displayName: String,
    val shortHandName: String,
    val token: String,
    val tokenType: String,
    val recipientId: String,
    val recipientTokenStatus: String,
    val recType: String,
    val businessRecipient: Boolean,
    val lastUpdatedDate: String,
    val latestTransferAmountDisplay: String,
    val latestTransferAmount: Double,
    val latestTransferDate: String?,
    val defaultFundingAccountAdx: String?,
    val displayEligible: Boolean,
    val displayIndicatorList: List<DisplayIndicator>?,
    val scheduleAllowed: Boolean,
    val recurringAllowed: Boolean,
    val nickName: String?,
    val editEligible: Boolean,
    val deleteEligible: Boolean,
    val verified: Boolean
)