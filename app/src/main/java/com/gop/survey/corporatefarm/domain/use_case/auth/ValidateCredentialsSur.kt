package com.gop.survey.corporatefarm.domain.use_case.auth

import com.gop.survey.corporatefarm.common.Constants
import com.gop.survey.corporatefarm.common.Resource
import com.gop.survey.corporatefarm.data.remote.response.LoginSurveyorResponse
import com.gop.survey.corporatefarm.domain.repository.AuthRepository
import javax.inject.Inject

class ValidateCredentialsSur @Inject constructor(
    private val repository: AuthRepository
) {
    suspend operator fun invoke(username: String, password: String): Resource<LoginSurveyorResponse> {
        // Validate inputs before calling repository
        if (username.isBlank() || password.isBlank()) {
            return Resource.Error("Username and password cannot be empty")
        }

        if (password.length < 8) {
            return Resource.Error("Password must be at least 8 characters")
        }

        // Include app version in the login request
        return repository.loginSurveyor(
            cnic = username,
            password = password,
            appVersion = Constants.VERSION_NAME
        )
    }
}
