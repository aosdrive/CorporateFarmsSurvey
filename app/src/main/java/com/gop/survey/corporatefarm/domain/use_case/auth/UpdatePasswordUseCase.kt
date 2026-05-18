package com.gop.survey.corporatefarm.domain.use_case.auth

import com.gop.survey.corporatefarm.common.SimpleResource
import com.gop.survey.corporatefarm.domain.repository.AuthRepository
import javax.inject.Inject

class UpdatePasswordUseCase @Inject constructor(
    private val repository: AuthRepository
) {
    suspend operator fun invoke(cnic: String, password: String): SimpleResource {
        return repository.updatePassword(cnic, password)
    }
}