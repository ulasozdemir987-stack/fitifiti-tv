package com.fitifiti.tv.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.fitifiti.tv.data.local.entity.Profile
import com.fitifiti.tv.data.local.dao.ProfileDao

@Database(entities = [Profile::class], version = 1, exportSchema = false)
abstract class FitifitiDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
}
