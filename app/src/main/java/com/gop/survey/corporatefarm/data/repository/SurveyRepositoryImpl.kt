package com.gop.survey.corporatefarm.data.repository

import android.content.SharedPreferences
import com.gop.survey.corporatefarm.common.Constants
import com.gop.survey.corporatefarm.data.local.AppDatabase
import com.gop.survey.corporatefarm.domain.model.SurveyEntity
import com.gop.survey.corporatefarm.domain.repository.SurveyRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class SurveyRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val sharedPreferences: SharedPreferences
) : SurveyRepository {

    override fun getSurveyList(showAll: Boolean): Flow<List<SurveyEntity>> {
        val id = sharedPreferences.getLong(
            Constants.SHARED_PREF_USER_SELECTED_AREA_ID,
            Constants.SHARED_PREF_DEFAULT_INT.toLong()
        )

        return if (showAll) {
            db.surveyDao().getSurveysForKachiAbadiShowAll(id)
        } else {
            db.surveyDao().getSurveysForKachiAbadi(id)
        }
    }

    override fun getFilteredSurveyList(value: String, showAll: Boolean): Flow<List<SurveyEntity>> {
        val id = sharedPreferences.getLong(
            Constants.SHARED_PREF_USER_SELECTED_AREA_ID,
            Constants.SHARED_PREF_DEFAULT_INT.toLong()
        )

        return if (showAll) {
            db.surveyDao().getFilterSurveysForKachiAbadiShowAll(id, value)
        } else {
            db.surveyDao().getFilterSurveysForKachiAbadi(id, value)

        }
    }




}