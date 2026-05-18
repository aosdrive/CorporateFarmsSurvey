package com.gop.survey.corporatefarm.common

import com.gop.survey.corporatefarm.domain.model.SurveyEntity

interface SurveyItemClickListener {
    fun onSurveyItemClicked(survey: SurveyEntity)
}
