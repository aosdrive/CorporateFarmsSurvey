package com.gop.survey.corporatefarm.domain.use_case.auth

import com.gop.survey.corporatefarm.common.SimpleResource
import com.gop.survey.corporatefarm.domain.repository.AuthRepository
import javax.inject.Inject

class OtpVerificationUseCase @Inject constructor(
    private val repository: AuthRepository
) {
    suspend operator fun invoke(cnic: String, otp: Int): SimpleResource {
        return repository.otpVerification(cnic, otp)
    }
}