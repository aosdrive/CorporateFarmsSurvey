package com.gop.survey.corporatefarm.common

import com.gop.survey.corporatefarm.domain.model.SurveyFormEntity
import com.gop.survey.corporatefarm.domain.model.TempSurveyFormEntity

fun List<TempSurveyFormEntity>.toSurveyFormEntityList(): List<SurveyFormEntity> {
    return map {
        SurveyFormEntity(
            surveyId = it.surveyId,
            surveyPkId = it.surveyPkId,
            surveyStatus = it.surveyStatus,
            propertyNumber = it.propertyNumber,
            parcelId = it.parcelId,
            parcelPkId = it.parcelPkId,
            parcelStatus = it.parcelStatus,
            parcelOperation = it.parcelOperation,
            parcelOperationValue = it.parcelOperationValue,
            discrepancyPicturePath = it.discrepancyPicturePath,
            subParcelId = it.subParcelId,
            interviewStatus = it.interviewStatus,
            area = it.area,
            name = it.name,
            fatherName = it.fatherName,
            gender = it.gender,
            cnic = it.cnic,
            cnicSource = it.cnicSource,
            cnicOtherSource = it.cnicOtherSource,
            mobile = it.mobile,
            mobileSource = it.mobileSource,
            mobileOtherSource = it.mobileOtherSource,
            ownershipType = it.ownershipType,
            ownershipOtherType = it.ownershipOtherType,
            floorsList = it.floorsList,
            picturesList = it.picturesList,
            remarks = it.remarks,
            userId = it.userId,
            kachiAbadiId = it.kachiAbadiId,
            gpsAccuracy = it.gpsAccuracy,
            gpsAltitude = it.gpsAltitude,
            gpsProvider = it.gpsProvider,
            gpsTimestamp = it.gpsTimestamp,
            latitude = it.latitude,
            longitude = it.longitude,
            timeZoneId = it.timeZoneId,
            timeZoneName = it.timeZoneName,
            mobileTimestamp = it.mobileTimestamp,
            appVersion = it.appVersion,
            uniqueId = it.uniqueId,
            qrCode = it.qrCode,
            statusBit = it.statusBit,
            centroidGeom = it.centroidGeom,
            geom = it.geom,
            parcelNo = it.parcelNo.toLongOrNull() ?: 0L,
            subParcelNo = it.subParcelNo,
            newStatusId = it.newStatusId,
            subParcelsStatusList = it.subParcelsStatusList,
            isRevisit = it.isRevisit
        )
    }
}
