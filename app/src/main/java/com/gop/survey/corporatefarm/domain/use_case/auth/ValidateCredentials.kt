package com.gop.survey.corporatefarm.domain.use_case.auth

import com.gop.survey.corporatefarm.common.Resource
import com.gop.survey.corporatefarm.data.remote.response.LoginDto
import com.gop.survey.corporatefarm.domain.repository.AuthRepository
import javax.inject.Inject

class ValidateCredentials @Inject constructor(
    private val repository: AuthRepository
) {
    suspend operator fun invoke(cnic: String, password: String): Resource<LoginDto> {
        return repository.login(cnic, password)
    }
}