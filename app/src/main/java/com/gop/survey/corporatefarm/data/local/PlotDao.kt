package com.gop.survey.corporatefarm.data.local

import androidx.room.*
import com.gop.survey.corporatefarm.domain.model.PlotEntity

@Dao
interface PlotDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlots(plots: List<PlotEntity>)

    @Query("SELECT * FROM plots WHERE blockId = :blockId")
    suspend fun getPlotsByBlock(blockId: Long): List<PlotEntity>

    @Query("SELECT * FROM plots")
    suspend fun getAllPlots(): List<PlotEntity>

    @Query("SELECT * FROM plots WHERE plotId = :plotId")
    suspend fun getPlotById(plotId: Long): PlotEntity?

    @Query("DELETE FROM plots")
    suspend fun deleteAllPlots()

}