package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SurveyDao {
    /** Read-only, complete snapshot across all layouts; never filter by current map. */
    @Query("SELECT * FROM survey_points ORDER BY id ASC")
    suspend fun snapshotAllPoints(): List<SurveyEntity>

    /** Detect any existing survey data before admitting an import. */
    @Query("SELECT COUNT(*) FROM survey_points")
    suspend fun countAllPoints(): Int

    @Query("SELECT * FROM survey_points WHERE layoutId = :layoutId ORDER BY timestamp DESC")
    fun getPointsForLayout(layoutId: String): Flow<List<SurveyEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPoint(point: SurveyEntity): Long

    @Query("DELETE FROM survey_points WHERE id = :pointId")
    suspend fun deletePoint(pointId: Long)

    @Query("DELETE FROM survey_points WHERE layoutId = :layoutId")
    suspend fun clearLayoutPoints(layoutId: String)
}
