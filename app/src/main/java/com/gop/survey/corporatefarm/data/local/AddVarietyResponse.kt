package com.gop.survey.corporatefarm.data.local

data class AddVarietyResponse(
    val message: String,
    val variety: DropdownItem? = null,
    val alreadyExists: Boolean? = null
)
