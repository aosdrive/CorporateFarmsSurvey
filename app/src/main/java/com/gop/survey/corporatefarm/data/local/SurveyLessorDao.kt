// data/local/SurveyLessorDao.kt
package com.gop.survey.corporatefarm.data.local

import androidx.room.*
import com.gop.survey.corporatefarm.domain.model.SurveyLessorEntity

@Dao
interface SurveyLessorDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(lessor: SurveyLessorEntity): Long

    @Query("SELECT * FROM new_survey_lessors WHERE surveyId = :surveyId")
    suspend fun getLessorsForSurvey(surveyId: Long): List<SurveyLessorEntity>

    @Query("DELETE FROM new_survey_lessors WHERE surveyId = :surveyId")
    suspend fun deleteForSurvey(surveyId: Long)

    @Query("SELECT * FROM new_survey_lessors WHERE isSynced = 0")
    suspend fun getUnsyncedLessors(): List<SurveyLessorEntity>

    @Query("UPDATE new_survey_lessors SET isSynced = 1 WHERE surveyId = :surveyId")
    suspend fun markSurveyLessorsSynced(surveyId: Long)
}