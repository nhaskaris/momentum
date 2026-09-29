package com.eliteonetube.momentum.data

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WeightDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeight(entry: WeightEntry)

    @Query("SELECT * FROM weight_table ORDER BY date DESC LIMIT 14")
    fun getLastTwoWeeks(): Flow<List<WeightEntry>>

    @Query("SELECT * FROM weight_table ORDER BY date DESC")
    fun getAllWeights(): Flow<List<WeightEntry>>

    @Query("SELECT EXISTS(SELECT 1 FROM weight_table WHERE date = :date)")
    suspend fun hasWeightForDate(date: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProfile(profile: UserProfile)

    @Query("SELECT * FROM user_profile_table WHERE id = 1 LIMIT 1")
    fun getUserProfile(): Flow<UserProfile?>

    @Query("DELETE FROM weight_table WHERE date = :date")
    suspend fun deleteWeight(date: String)

    @Query("SELECT date FROM weight_table ORDER BY date ASC")
    fun getAllWeightDates(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCheckIn(checkIn: CheckIn)

    @Query("SELECT * FROM check_in_table ORDER BY date DESC")
    fun getAllCheckIns(): Flow<List<CheckIn>>
}
