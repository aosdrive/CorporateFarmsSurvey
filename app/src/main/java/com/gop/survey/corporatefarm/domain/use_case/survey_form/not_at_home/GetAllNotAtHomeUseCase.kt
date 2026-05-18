package com.gop.survey.corporatefarm.domain.use_case.survey_form.not_at_home

import com.gop.survey.corporatefarm.domain.model.NotAtHomeSurveyFormEntity
import com.gop.survey.corporatefarm.domain.repository.SurveyFormRepository
import javax.inject.Inject

class GetAllNotAtHomeUseCase @Inject constructor(
    private val repository: SurveyFormRepository
) {
    suspend operator fun invoke(parcelNo: Long, uniqueId: String): List<NotAtHomeSurveyFormEntity> {
        return repository.getAllNotAtHomeData(parcelNo,uniqueId)
    }
}
