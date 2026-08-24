package com.gop.survey.corporatefarm.domain.repository

import com.gop.survey.corporatefarm.common.Resource
import com.gop.survey.corporatefarm.common.SimpleResource
import com.gop.survey.corporatefarm.data.remote.response.LoginDto
import com.gop.survey.corporatefarm.data.remote.response.LoginSurveyorResponse
import com.gop.survey.corporatefarm.data.remote.response.LogoutResponse
import com.gop.survey.corporatefarm.data.remote.response.VersionCheckResponse

interface AuthRepository {

    suspend fun checkAppVersion(appVersion: String): Resource<VersionCheckResponse>

    suspend fun login(
        username: String,
        password: String,
    ): Resource<LoginDto>

    suspend fun loginSurveyor(
        cnic: String,
        password: String,
        appVersion: String? = null
    ): Resource<LoginSurveyorResponse>

    fun authenticate(): SimpleResource

    suspend fun otpVerification(
        cnic: String,
        otp: Int,
    ): SimpleResource

    suspend fun forgotPassword(
        cnic: String,
    ): SimpleResource

    suspend fun updatePassword(
        cnic: String,
        password: String
    ): SimpleResource


    suspend fun logoutUser(
        userId: Long,
        mode: String
    ): LogoutResponse


}