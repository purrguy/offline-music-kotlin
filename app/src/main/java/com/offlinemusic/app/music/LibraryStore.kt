package com.offlinemusic.app.music

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
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "tracks")
data class SavedTrack(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val source: String, // audius | jamendo | upload
    val fileName: String, // under filesDir/tracks/
    val mime: String,
    val artwork: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val trimStartMs: Long? = null,
    val trimEndMs: Long? = null,
)

@Dao
interface TrackDao {
    @Query("SELECT * FROM tracks ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<SavedTrack>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(t: SavedTrack)

    @Query("DELETE FROM tracks WHERE id = :id")
    suspend fun delete(id: String)
}

@Database(entities = [SavedTrack::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun tracks(): TrackDao

    companion object {
        @Volatile private var inst: AppDb? = null
        fun get(ctx: Context): AppDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, AppDb::class.java, "offline-music.db").build()
                .also { inst = it }
        }
    }
}
