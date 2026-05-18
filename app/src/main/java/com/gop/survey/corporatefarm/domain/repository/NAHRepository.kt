package com.gop.survey.corporatefarm.domain.repository

import com.gop.survey.corporatefarm.common.SimpleResource
import com.gop.survey.corporatefarm.domain.model.NotAtHomeSurveyFormEntity

interface NAHRepository {
    suspend fun saveNotAtHomeData(notAtHomeSurveyFormEntity: NotAtHomeSurveyFormEntity): SimpleResource
}
