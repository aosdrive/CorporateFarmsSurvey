package com.gop.survey.corporatefarm.common

import android.graphics.Color
import com.esri.arcgisruntime.symbology.SimpleFillSymbol
import com.esri.arcgisruntime.symbology.SimpleLineSymbol
import com.esri.arcgisruntime.symbology.SimpleMarkerSymbol
import com.esri.arcgisruntime.symbology.TextSymbol

object MapSymbols {

    private val RED_OUTLINE = SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, Color.RED, 2f)
    private val GREEN_OUTLINE = SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, Color.GREEN, 3f)
    private val BOUNDARY_ORANGE = Color.rgb(255, 140, 0)


    fun unsurveyedParcel(): SimpleFillSymbol =
        SimpleFillSymbol(SimpleFillSymbol.Style.NULL, Color.WHITE, RED_OUTLINE)

    fun surveyedParcel(): SimpleFillSymbol =
        SimpleFillSymbol(SimpleFillSymbol.Style.NULL, Color.WHITE, GREEN_OUTLINE)

    fun parcelFor(surveyStatusCode: Int): SimpleFillSymbol =
        if (surveyStatusCode == 2) surveyedParcel() else unsurveyedParcel()


    fun aoiBoundary(): SimpleFillSymbol = SimpleFillSymbol(
        SimpleFillSymbol.Style.NULL,
        Color.TRANSPARENT,
        SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, BOUNDARY_ORANGE, 4f)
    )


    fun drawVertex(): SimpleMarkerSymbol =
        SimpleMarkerSymbol(SimpleMarkerSymbol.Style.CIRCLE, Color.RED, 8f).apply {
            outline = SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, Color.WHITE, 2f)
        }

    fun drawLine(): SimpleLineSymbol =
        SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, Color.BLUE, 3f)

    fun drawPreviewFill(): SimpleFillSymbol = SimpleFillSymbol(
        SimpleFillSymbol.Style.SOLID,
        Color.argb(80, 0, 200, 0),
        SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, Color.GREEN, 2f)
    )

    // ===== Current location =====

    fun currentLocation(colorInt: Int): SimpleMarkerSymbol =
        SimpleMarkerSymbol(SimpleMarkerSymbol.Style.CIRCLE, colorInt, 22f).apply {
            outline = SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, Color.WHITE, 2f)
        }

    // ===== Labels =====

    fun parcelLabel(text: String, colorInt: Int, sizeSp: Float = 16f): TextSymbol =
        TextSymbol().apply {
            this.text = text
            this.size = sizeSp
            this.color = colorInt
            horizontalAlignment = TextSymbol.HorizontalAlignment.CENTER
            verticalAlignment = TextSymbol.VerticalAlignment.MIDDLE
            haloColor = Color.WHITE
            haloWidth = 1f
            fontWeight = TextSymbol.FontWeight.NORMAL
        }
}