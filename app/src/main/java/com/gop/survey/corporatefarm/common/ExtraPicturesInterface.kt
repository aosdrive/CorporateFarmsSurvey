package com.gop.survey.corporatefarm.common


interface ExtraPicturesInterface {
    fun makeAddMoreButtonVisible()
    fun checkCameraPermission(): Boolean
    fun requestCameraPermission(requestCode: Int)
    fun startImageCapture(requestCode: Int)
}