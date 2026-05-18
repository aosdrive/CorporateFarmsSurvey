package com.gop.survey.corporatefarm.domain.repository

import android.content.Context
import com.gop.survey.corporatefarm.domain.model.NewSurveyNewEntity
import kotlinx.coroutines.flow.Flow
import com.gop.survey.corporatefarm.common.Resource
import com.gop.survey.corporatefarm.domain.model.ActiveParcelEntity
import com.gop.survey.corporatefarm.domain.model.SurveyPersonEntity

interface NewSurveyRepository {
    fun getAllPendingSurveys(): Flow<List<NewSurveyNewEntity>>
    fun getTotalPendingCount(): Flow<Int>

    suspend fun deleteSurvey(survey: NewSurveyNewEntity): Resource<Unit>

    //    suspend fun uploadSurvey(survey: NewSurveyNewEntity): Resource<Unit>
    suspend fun uploadSurvey(context: Context, survey: NewSurveyNewEntity): Resource<Unit>
    suspend fun getOnePendingSurvey(): NewSurveyNewEntity?

    suspend fun getSurveyById(id: Long): NewSurveyNewEntity?
    suspend fun getPersonsForSurvey(surveyId: Long): List<SurveyPersonEntity>


    suspend fun getAllSurveys(): List<NewSurveyNewEntity>
    suspend fun getActiveParcelById(parcelId: Long): ActiveParcelEntity?

}