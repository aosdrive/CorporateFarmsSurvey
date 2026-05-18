package com.gop.survey.corporatefarm.domain.use_case.survey_list

import com.gop.survey.corporatefarm.domain.model.SurveyEntity
import com.gop.survey.corporatefarm.domain.repository.SurveyRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetAllSurveysUseCase @Inject constructor(
    private val repository: SurveyRepository
) {
    operator fun invoke(showAll: Boolean): Flow<List<SurveyEntity>> {
        return repository.getSurveyList(showAll)
    }
}
