package com.gop.survey.corporatefarm.domain.use_case.survey_form

import com.gop.survey.corporatefarm.domain.use_case.survey_form.main.SaveAllSurveyFormUseCase
import com.gop.survey.corporatefarm.domain.use_case.survey_form.main.SaveSurveyFormUseCase
import com.gop.survey.corporatefarm.domain.use_case.survey_form.not_at_home.GetAllNotAtHomeUseCase
import com.gop.survey.corporatefarm.domain.use_case.survey_form.not_at_home.NotAtHomeSaveAllUseCase
import com.gop.survey.corporatefarm.domain.use_case.survey_form.not_at_home.NotAtHomeSaveUseCase
import com.gop.survey.corporatefarm.domain.use_case.survey_form.temp.GetAllTempSurveyFormUseCase
import com.gop.survey.corporatefarm.domain.use_case.survey_form.temp.TempSaveSurveyFormUseCase

data class SurveyFormUseCase(
    val saveSurveyFormUseCase: SaveSurveyFormUseCase,
    val saveAllSurveyFormUseCase: SaveAllSurveyFormUseCase,
    val saveTempSurveyFormUseCase: TempSaveSurveyFormUseCase,
    val getAllTempSurveyFormUseCase: GetAllTempSurveyFormUseCase,
    val notAtHomeSaveAllUseCase: NotAtHomeSaveAllUseCase,
    val notAtHomeSaveUseCase: NotAtHomeSaveUseCase,
    val getAllNotAtHomeUseCase: GetAllNotAtHomeUseCase,
)
