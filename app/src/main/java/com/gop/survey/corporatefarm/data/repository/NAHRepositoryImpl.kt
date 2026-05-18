package com.gop.survey.corporatefarm.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.gop.survey.corporatefarm.common.Constants
import com.gop.survey.corporatefarm.common.Resource
import com.gop.survey.corporatefarm.common.SimpleResource
//import com.gop.survey.corporatefarm.common.toEntityList
import com.gop.survey.corporatefarm.data.local.AppDatabase
import com.gop.survey.corporatefarm.data.remote.ServerApi
import com.gop.survey.corporatefarm.domain.model.NotAtHomeSurveyFormEntity
import com.gop.survey.corporatefarm.domain.model.ParcelStatus
import com.gop.survey.corporatefarm.domain.repository.NAHRepository
import javax.inject.Inject

class NAHRepositoryImpl @Inject constructor(
    private val context: Context,
    private val api: ServerApi,
    private val db: AppDatabase,
    private val sharedPreferences: SharedPreferences
) : NAHRepository {

    override suspend fun saveNotAtHomeData(notAtHomeSurveyFormEntity: NotAtHomeSurveyFormEntity): SimpleResource {
        val recordId = db.notAtHomeSurveyFormDao().insertSurvey(notAtHomeSurveyFormEntity)

        if (notAtHomeSurveyFormEntity.parcelOperation == "Same" || notAtHomeSurveyFormEntity.parcelOperation == "Merge") {

            if (Constants.ReVisitThreshold == notAtHomeSurveyFormEntity.visitCount || notAtHomeSurveyFormEntity.interviewStatus != "Respondent Not Present") {
                val surveyForms = db.notAtHomeSurveyFormDao()
                    .getAllSurveysForm(notAtHomeSurveyFormEntity.parcelNo, notAtHomeSurveyFormEntity.uniqueId)
//                db.surveyFormDao().insertSurveys(surveyForms.toEntityList())
                db.notAtHomeSurveyFormDao().deleteAllSurveys(notAtHomeSurveyFormEntity.parcelNo,notAtHomeSurveyFormEntity.uniqueId)

                when (notAtHomeSurveyFormEntity.parcelOperation) {
                    "Same" -> {
                        db.parcelDao().updateParcelSurveyStatus(
                            notAtHomeSurveyFormEntity.newStatusId,
                            ParcelStatus.IN_PROCESS,
                            notAtHomeSurveyFormEntity.centroidGeom
                        )
                    }

                    "Merge" -> {
                        db.parcelDao().updateParcelSurveyStatus(
                            notAtHomeSurveyFormEntity.newStatusId,
                            ParcelStatus.IN_PROCESS,
                            notAtHomeSurveyFormEntity.centroidGeom
                        )
                        val parcelOperationValue =
                            notAtHomeSurveyFormEntity.parcelOperationValue

                        if (parcelOperationValue.contains(",")) {

                            val parcelNos = parcelOperationValue.split(",")

                            for (parcelNo in parcelNos) {
                                val newStatusId = db.parcelDao().getNewStatusId(parcelNo.toLong(), notAtHomeSurveyFormEntity.kachiAbadiId,)
                                db.parcelDao().updateParcelSurveyStatusWrtParcelId(
                                    newStatusId,
                                    ParcelStatus.MERGE,
                                    parcelNo.toLong()
                                )
                            }

                        } else {
                            val newStatusId = db.parcelDao().getNewStatusId(parcelOperationValue.toLong(), notAtHomeSurveyFormEntity.kachiAbadiId,)
                            db.parcelDao().updateParcelSurveyStatusWrtParcelId(
                                newStatusId,
                                ParcelStatus.MERGE,
                                parcelOperationValue.toLong()
                            )
                        }
                    }
                }
            }

        } else {

            if (notAtHomeSurveyFormEntity.interviewStatus == "Respondent Not Present") {

                val subParcelVisitCount = db.notAtHomeSurveyFormDao().getSubParcelVisitCount(notAtHomeSurveyFormEntity.parcelNo,
                    notAtHomeSurveyFormEntity.subParcelId, notAtHomeSurveyFormEntity.uniqueId)

                if(subParcelVisitCount == 3){

                    db.notAtHomeSurveyFormDao().updateSurveyStatusWrtParcel(1, notAtHomeSurveyFormEntity.parcelNo,
                        notAtHomeSurveyFormEntity.subParcelId, notAtHomeSurveyFormEntity.uniqueId)

                    val recordsCount = db.notAtHomeSurveyFormDao().getCountNAHForm(notAtHomeSurveyFormEntity.parcelNo, notAtHomeSurveyFormEntity.uniqueId)

                    if (recordsCount == null || recordsCount == 0) {

                        db.notAtHomeSurveyFormDao().updateAllSurveyStatusWrtParcel(0, notAtHomeSurveyFormEntity.parcelNo, notAtHomeSurveyFormEntity.uniqueId)

                        val surveyForms = db.notAtHomeSurveyFormDao()
                            .getAllSurveysForm(notAtHomeSurveyFormEntity.parcelNo,notAtHomeSurveyFormEntity.uniqueId)
//                        db.surveyFormDao().insertSurveys(surveyForms.toEntityList())
                        db.notAtHomeSurveyFormDao()
                            .deleteAllSurveys(notAtHomeSurveyFormEntity.parcelNo,notAtHomeSurveyFormEntity.uniqueId)

                        db.parcelDao().updateParcelSurveyStatus(
                            notAtHomeSurveyFormEntity.newStatusId,
                            ParcelStatus.IN_PROCESS,
                            notAtHomeSurveyFormEntity.centroidGeom
                        )
                    }
                }

            }else{

                db.notAtHomeSurveyFormDao().updateSurveyStatusWrtParcel(1, notAtHomeSurveyFormEntity.parcelNo,
                    notAtHomeSurveyFormEntity.subParcelId,notAtHomeSurveyFormEntity.uniqueId)

                val recordsCount = db.notAtHomeSurveyFormDao().getCountNAHForm(notAtHomeSurveyFormEntity.parcelNo,notAtHomeSurveyFormEntity.uniqueId)

                if (recordsCount == null || recordsCount == 0) {

                    db.notAtHomeSurveyFormDao().updateAllSurveyStatusWrtParcel(0, notAtHomeSurveyFormEntity.parcelNo,notAtHomeSurveyFormEntity.uniqueId)

                    val surveyForms = db.notAtHomeSurveyFormDao()
                        .getAllSurveysForm(notAtHomeSurveyFormEntity.parcelNo,notAtHomeSurveyFormEntity.uniqueId)
//                    db.surveyFormDao().insertSurveys(surveyForms.toEntityList())
                    db.notAtHomeSurveyFormDao()
                        .deleteAllSurveys(notAtHomeSurveyFormEntity.parcelNo,notAtHomeSurveyFormEntity.uniqueId)

                    db.parcelDao().updateParcelSurveyStatus(
                        notAtHomeSurveyFormEntity.newStatusId,
                        ParcelStatus.IN_PROCESS,
                        notAtHomeSurveyFormEntity.centroidGeom
                    )
                }
            }

        }

        return Resource.Success(Unit)

    }
}