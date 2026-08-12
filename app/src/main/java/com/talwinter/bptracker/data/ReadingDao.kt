package com.talwinter.bptracker.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingDao {

    @Query("SELECT * FROM readings ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<Reading>>

    @Query("SELECT * FROM readings WHERE timestamp >= :since ORDER BY timestamp ASC")
    fun observeSince(since: Long): Flow<List<Reading>>

    @Query("SELECT * FROM readings WHERE timestamp BETWEEN :from AND :to ORDER BY timestamp ASC")
    suspend fun between(from: Long, to: Long): List<Reading>

    @Query("SELECT * FROM readings WHERE id = :id")
    suspend fun byId(id: Long): Reading?

    @Query("SELECT * FROM readings ORDER BY timestamp DESC LIMIT 1")
    fun observeLatest(): Flow<Reading?>

    @Query("SELECT COUNT(*) FROM readings")
    fun observeCount(): Flow<Int>

    @Insert
    suspend fun insert(reading: Reading): Long

    @Update
    suspend fun update(reading: Reading)

    @Delete
    suspend fun delete(reading: Reading)
}
