package com.gop.survey.corporatefarm.domain.use_case.not_at_home

import com.gop.survey.corporatefarm.common.SimpleResource
import com.gop.survey.corporatefarm.domain.model.NotAtHomeSurveyFormEntity
import com.gop.survey.corporatefarm.domain.repository.NAHRepository
import javax.inject.Inject

class NAHSaveUseCase @Inject constructor(
    private val repository: NAHRepository
) {
    suspend operator fun invoke(notAtHomeSurveyFormEntity: NotAtHomeSurveyFormEntity): SimpleResource {
        return repository.saveNotAtHomeData(notAtHomeSurveyFormEntity)
    }
}