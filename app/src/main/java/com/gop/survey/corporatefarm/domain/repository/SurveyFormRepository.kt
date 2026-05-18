package com.gop.survey.corporatefarm.domain.repository

import com.gop.survey.corporatefarm.common.SimpleResource
import com.gop.survey.corporatefarm.domain.model.NotAtHomeSurveyFormEntity
import com.gop.survey.corporatefarm.domain.model.SurveyFormEntity
import com.gop.survey.corporatefarm.domain.model.TempSurveyFormEntity

interface SurveyFormRepository {
    suspend fun saveData(surveyFormEntity: SurveyFormEntity): SimpleResource
    suspend fun saveAllData(surveyFormEntityList: List<SurveyFormEntity>): SimpleResource
    suspend fun saveTempData(tempSurveyFormEntity: TempSurveyFormEntity): SimpleResource
    suspend fun getAllTempData(parcelNo: Long): List<TempSurveyFormEntity>

    suspend fun saveAllNotAtHomeData(notAtHomeSurveyFormEntityList: List<NotAtHomeSurveyFormEntity>): SimpleResource
    suspend fun saveNotAtHomeData(notAtHomeSurveyFormEntity: NotAtHomeSurveyFormEntity): SimpleResource
    suspend fun getAllNotAtHomeData(parcelNo: Long, uniqueId: String): List<NotAtHomeSurveyFormEntity>
}
