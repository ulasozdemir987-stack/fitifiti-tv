package com.fitifiti.tv.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: String,
    val name: String,
    val avatar: Int = 0,
    val pin: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

/** İzleme ilerlemesi. key: "movie-<id>" / "episode-<id>"; süre 0 = "sıradaki bölüm" yer tutucusu (sitedeki gibi). */
@Entity(tableName = "progress", primaryKeys = ["profileId", "key"])
data class ProgressEntity(
    val profileId: Long,
    val key: String,
    val kind: String, // movie | episode
    val itemId: String, // film: stream id, bölüm: episode id
    val seriesId: Int? = null,
    val season: Int? = null,
    val episodeNum: Int? = null,
    val title: String,
    val subtitle: String? = null,
    val image: String? = null,
    val ext: String? = null,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val fraction: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val finished: Boolean get() = durationMs > 0 && fraction >= 0.96f
    val isUpNext: Boolean get() = durationMs <= 0L && positionMs <= 0L
}

/** Listem. key: "movie-<id>" / "series-<id>" */
@Entity(tableName = "favorites", primaryKeys = ["profileId", "key"])
data class FavoriteEntity(
    val profileId: Long,
    val key: String,
    val kind: String, // movie | series
    val itemId: Int,
    val title: String,
    val image: String? = null,
    val addedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "recent_channels", primaryKeys = ["profileId", "channelId"])
data class RecentChannelEntity(val profileId: Long, val channelId: Int, val at: Long = System.currentTimeMillis())

@Entity(tableName = "recent_searches", primaryKeys = ["profileId", "query"])
data class RecentSearchEntity(val profileId: Long, val query: String, val at: Long = System.currentTimeMillis())

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles WHERE accountId = :accountId ORDER BY createdAt")
    fun observe(accountId: String): Flow<List<ProfileEntity>>
    @Query("SELECT * FROM profiles WHERE id = :id") suspend fun get(id: Long): ProfileEntity?
    @Upsert suspend fun upsert(p: ProfileEntity): Long
    @Query("DELETE FROM profiles WHERE id = :id") suspend fun delete(id: Long)
}

@Dao
interface ProgressDao {
    @Query("SELECT * FROM progress WHERE profileId = :p ORDER BY updatedAt DESC LIMIT :limit")
    fun observeRecent(p: Long, limit: Int = 60): Flow<List<ProgressEntity>>
    @Query("SELECT * FROM progress WHERE profileId = :p") fun observeAll(p: Long): Flow<List<ProgressEntity>>
    @Query("SELECT * FROM progress WHERE profileId = :p AND `key` = :key") suspend fun get(p: Long, key: String): ProgressEntity?
    @Query("SELECT * FROM progress WHERE profileId = :p AND seriesId = :seriesId ORDER BY updatedAt DESC")
    suspend fun forSeries(p: Long, seriesId: Int): List<ProgressEntity>
    @Upsert suspend fun upsert(e: ProgressEntity)
    @Query("DELETE FROM progress WHERE profileId = :p AND `key` = :key") suspend fun delete(p: Long, key: String)
    @Query("DELETE FROM progress WHERE profileId = :p") suspend fun clear(p: Long)
}

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites WHERE profileId = :p ORDER BY addedAt DESC") fun observe(p: Long): Flow<List<FavoriteEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun add(e: FavoriteEntity)
    @Query("DELETE FROM favorites WHERE profileId = :p AND `key` = :key") suspend fun remove(p: Long, key: String)
}

@Dao
interface RecentDao {
    @Query("SELECT * FROM recent_channels WHERE profileId = :p ORDER BY at DESC LIMIT 20") fun channels(p: Long): Flow<List<RecentChannelEntity>>
    @Upsert suspend fun touchChannel(e: RecentChannelEntity)
    @Query("SELECT * FROM recent_searches WHERE profileId = :p ORDER BY at DESC LIMIT 8") fun searches(p: Long): Flow<List<RecentSearchEntity>>
    @Upsert suspend fun addSearch(e: RecentSearchEntity)
    @Query("DELETE FROM recent_searches WHERE profileId = :p") suspend fun clearSearches(p: Long)
}

@Database(
    entities = [ProfileEntity::class, ProgressEntity::class, FavoriteEntity::class, RecentChannelEntity::class, RecentSearchEntity::class],
    version = 1, exportSchema = true,
)
abstract class AppDb : RoomDatabase() {
    abstract fun profiles(): ProfileDao
    abstract fun progress(): ProgressDao
    abstract fun favorites(): FavoriteDao
    abstract fun recent(): RecentDao

    companion object {
        fun create(ctx: Context) = Room.databaseBuilder(ctx, AppDb::class.java, "fitifiti.db").fallbackToDestructiveMigration().build()
    }
}
