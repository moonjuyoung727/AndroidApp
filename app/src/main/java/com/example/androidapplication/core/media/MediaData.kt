package com.example.androidapplication.core.media

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.example.androidapplication.core.data.PrivacyZoneDao
import com.example.androidapplication.core.data.PrivacyZoneEntity
import kotlinx.coroutines.flow.Flow

enum class MediaType { IMAGE, VIDEO }
enum class MediaSource { SNAPSHOT, RECORDING, EVENT_DOWNLOAD }

@Entity(
    tableName = "media",
    indices = [Index("ownerUserId"), Index("capturedAtUtc"), Index("recordingSessionId")]
)
data class MediaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ownerUserId: String,
    val type: MediaType,
    val source: MediaSource,
    val mac: String,
    val deviceId: String,
    val cameraName: String,
    val location: String,
    val capturedAtUtc: Long,
    val durationMs: Long = 0,
    val width: Int = 0,
    val height: Int = 0,
    val sizeBytes: Long,
    val filePath: String,
    val thumbnailPath: String? = null,
    val recordingSessionId: String? = null,
    val maskedZoneCount: Int = 0,
    val eventId: String? = null,
    val eventType: String? = null
) {
    val fileName: String get() = filePath.substringAfterLast('/')
    val format: String get() = fileName.substringAfterLast('.', "").uppercase()
}

@Dao
interface MediaDao {
    @Query("SELECT * FROM media WHERE ownerUserId = :owner ORDER BY capturedAtUtc DESC")
    fun observe(owner: String): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media WHERE ownerUserId = :owner")
    suspend fun listOwner(owner: String): List<MediaEntity>

    @Query("SELECT * FROM media")
    suspend fun listAll(): List<MediaEntity>

    @Query("SELECT * FROM media WHERE id = :id AND ownerUserId = :owner")
    fun observeOne(id: Long, owner: String): Flow<MediaEntity?>

    @Query("SELECT * FROM media WHERE id IN (:ids) AND ownerUserId = :owner")
    suspend fun byIds(ids: List<Long>, owner: String): List<MediaEntity>

    @Query("SELECT * FROM media WHERE eventId = :eventId AND ownerUserId = :owner AND type = :type LIMIT 1")
    suspend fun findEvent(eventId: String, owner: String, type: MediaType): MediaEntity?

    @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM media")
    suspend fun totalBytes(): Long

    @Insert
    suspend fun insert(item: MediaEntity): Long

    @Query("DELETE FROM media WHERE id IN (:ids)")
    suspend fun delete(ids: List<Long>)
}

class AppConverters {
    @TypeConverter fun fromType(v: MediaType): String = v.name
    @TypeConverter fun toType(v: String): MediaType = MediaType.valueOf(v)
    @TypeConverter fun fromSource(v: MediaSource): String = v.name
    @TypeConverter fun toSource(v: String): MediaSource = MediaSource.valueOf(v)
}

@Database(entities = [MediaEntity::class, PrivacyZoneEntity::class], version = 1, exportSchema = false)
@TypeConverters(AppConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun media(): MediaDao
    abstract fun privacyZones(): PrivacyZoneDao
}
