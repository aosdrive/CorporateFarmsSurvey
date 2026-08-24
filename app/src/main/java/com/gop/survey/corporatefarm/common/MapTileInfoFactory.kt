package com.gop.survey.corporatefarm.common

import com.esri.arcgisruntime.arcgisservices.LevelOfDetail
import com.esri.arcgisruntime.arcgisservices.TileInfo
import com.esri.arcgisruntime.geometry.Point
import com.esri.arcgisruntime.geometry.SpatialReferences

object MapTileInfoFactory {

    private const val DPI = 96
    private const val TILE_SIZE = 256

    private val LEVELS: List<LevelOfDetail> = listOf(
        LevelOfDetail(7, 1222.992452561855, 591657527.591555),
        LevelOfDetail(8, 611.4962262809275, 295828763.7957775),
        LevelOfDetail(9, 305.7481131404638, 147914381.89788872),
        LevelOfDetail(10, 152.8740565702319, 73957190.94894436),
        LevelOfDetail(11, 76.43702828511594, 36978595.47447218),
        LevelOfDetail(12, 38.21851414255798, 18489297.73723609),
        LevelOfDetail(13, 19.10925707127899, 9244648.868618045),
        LevelOfDetail(14, 9.554628535639495, 4622324.4343090225),
        LevelOfDetail(15, 4.777314267819747, 2311162.2171545113),
        LevelOfDetail(16, 2.3886571339098737, 1155581.1085772556),
        LevelOfDetail(17, 1.1943285669549368, 577790.5542886278),
        LevelOfDetail(18, 0.5971642834774684, 288895.2771443139),
        LevelOfDetail(19, 0.2985821417387342, 144447.63857215695),
        LevelOfDetail(20, 0.1492910708693671, 72223.81928607848)
    )

    fun create(): TileInfo = TileInfo(
        DPI,
        TileInfo.ImageFormat.PNG24,
        LEVELS,
        Point(-20037508.3427892, 20037508.3427892, SpatialReferences.getWebMercator()),
        SpatialReferences.getWebMercator(),
        TILE_SIZE,
        TILE_SIZE
    )
}