package com.example.chooserecipientxml.model

data class EligibilityProfile(
    val accountPayEligibility: String,
    val requestEligibility: String,
    val payScheduleEligibility: String,
    val accountPayScheduleEligibility: String,
    val enrollmentEligibility: String,
    val payEligibility: String
)