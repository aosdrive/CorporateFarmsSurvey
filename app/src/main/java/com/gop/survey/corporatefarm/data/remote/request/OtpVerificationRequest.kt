package com.gop.survey.corporatefarm.data.remote.request

data class OtpVerificationRequest(
    val cnic: String,
    val otp: Int,
)
