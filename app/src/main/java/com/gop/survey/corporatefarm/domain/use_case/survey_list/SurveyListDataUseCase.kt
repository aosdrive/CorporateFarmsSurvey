package com.gop.survey.corporatefarm.domain.use_case.survey_list

data class SurveyListDataUseCase(
    val getAllSurveysUseCase: GetAllSurveysUseCase,
    val getAllFilteredSurveysUseCase: GetAllFilteredSurveysUseCase,
)
