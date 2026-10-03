package com.fitifiti.tv.di

import android.content.Context
import androidx.room.Room
import com.fitifiti.tv.data.local.FitifitiDatabase
import com.fitifiti.tv.data.local.dao.ProfileDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): FitifitiDatabase {
        return Room.databaseBuilder(
            context,
            FitifitiDatabase::class.java,
            "fitifiti_db"
        ).build()
    }

    @Provides
    fun provideProfileDao(database: FitifitiDatabase): ProfileDao {
        return database.profileDao()
    }
}
