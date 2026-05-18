package com.gop.survey.corporatefarm.data.remote.request

data class UpdatePasswordRequest(
    val cnic: String,
    val password: String,
)
