package com.gop.survey.corporatefarm.common

import com.gop.survey.corporatefarm.domain.model.SurveyFormEntity

interface ViewRecordClickListener {
    fun onViewImagesClicked(survey: SurveyFormEntity)
}