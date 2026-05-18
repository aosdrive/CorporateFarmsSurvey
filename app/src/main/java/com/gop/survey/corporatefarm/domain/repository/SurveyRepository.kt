package com.gop.survey.corporatefarm.domain.repository

import com.gop.survey.corporatefarm.domain.model.SurveyEntity
import kotlinx.coroutines.flow.Flow

interface SurveyRepository {
    fun getSurveyList(showAll: Boolean): Flow<List<SurveyEntity>>
    fun getFilteredSurveyList(value: String, showAll: Boolean): Flow<List<SurveyEntity>>
}