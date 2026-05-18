package com.gop.survey.corporatefarm.common

import java.io.Serializable


data class ImageDetails(
    val type: String,
    val path: String
) : Serializable