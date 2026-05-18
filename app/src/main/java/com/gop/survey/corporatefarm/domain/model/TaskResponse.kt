package com.gop.survey.corporatefarm.domain.model

data class TaskResponse(
    val success: Boolean,
    val message: String,
    val taskId: Long?
)