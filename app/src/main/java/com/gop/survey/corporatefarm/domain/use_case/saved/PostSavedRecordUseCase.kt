package com.gop.survey.corporatefarm.domain.use_case.saved

import com.gop.survey.corporatefarm.common.SimpleResource
import com.gop.survey.corporatefarm.domain.repository.SavedRepository
import javax.inject.Inject

class PostSavedRecordUseCase @Inject constructor(
    private val repository: SavedRepository
) {
    suspend operator fun invoke(parcelNo: Long, uniqueId: String): SimpleResource {
        return repository.postSavedData(parcelNo, uniqueId)
    }
}
