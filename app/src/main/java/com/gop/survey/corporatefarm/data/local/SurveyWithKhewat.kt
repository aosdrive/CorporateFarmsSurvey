package com.gop.survey.corporatefarm.data.local

import com.gop.survey.corporatefarm.domain.model.NewSurveyNewEntity

data class SurveyWithKhewat(
    val survey: NewSurveyNewEntity,
    val khewatInfo: String
)