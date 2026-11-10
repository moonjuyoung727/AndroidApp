package com.example.androidapplication.core.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import com.example.androidapplication.core.common.ApiError
import com.example.androidapplication.core.config.FeatureFlags
import com.example.androidapplication.core.network.ApiCaller
import com.example.androidapplication.core.network.PrivacyZoneApi
import com.example.androidapplication.core.network.PrivacyZoneBody
import com.example.androidapplication.core.network.PrivacyZoneDto
import com.example.androidapplication.core.network.ZoneCoordinatesDto
import com.example.androidapplication.core.network.ZoneDeleteRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

const val ZONE_APPLY_FAILED = "보드 적용에 실패했습니다. 카메라 연결 상태를 확인하세요."

@Entity(tableName = "privacy_zones", indices = [Index("mac")])
data class PrivacyZoneEntity(
    @PrimaryKey val id: String,
    val mac: String,
    val name: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val enabled: Boolean,
    val color: String?,
    val position: Int
)

@Dao
interface PrivacyZoneDao {
    @Query("SELECT * FROM privacy_zones WHERE mac = :mac ORDER BY position")
    fun observe(mac: String): Flow<List<PrivacyZoneEntity>>

    @Query("SELECT * FROM privacy_zones WHERE mac = :mac ORDER BY position")
    suspend fun list(mac: String): List<PrivacyZoneEntity>

    @Query("DELETE FROM privacy_zones WHERE mac = :mac")
    suspend fun deleteAll(mac: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(zones: List<PrivacyZoneEntity>)

    @Transaction
    suspend fun replace(mac: String, zones: List<PrivacyZoneEntity>) {
        deleteAll(mac)
        insert(zones)
    }
}

/** 서버 {zone_id, coordinates:{x1,y1,x2,y2}} ↔ 앱 {x,y,width,height} */
object PrivacyZoneMapper {
    fun fromDto(dto: PrivacyZoneDto): PrivacyZone = PrivacyZone(
        id = dto.zoneId,
        name = dto.zoneName,
        x = dto.coordinates.x1,
        y = dto.coordinates.y1,
        width = dto.coordinates.x2 - dto.coordinates.x1,
        height = dto.coordinates.y2 - dto.coordinates.y1,
        enabled = dto.isActive,
        color = dto.color
    )

    fun toBody(zone: PrivacyZone): PrivacyZoneBody = PrivacyZoneBody(
        zoneName = zone.name,
        isActive = zone.enabled,
        color = zone.color,
        coordinates = ZoneCoordinatesDto(zone.x, zone.y, zone.x + zone.width, zone.y + zone.height)
    )

    fun toEntity(mac: String, zone: PrivacyZone, position: Int) = PrivacyZoneEntity(
        zone.id, mac, zone.name, zone.x, zone.y, zone.width, zone.height, zone.enabled, zone.color, position
    )

    fun fromEntity(e: PrivacyZoneEntity) = PrivacyZone(e.id, e.name, e.x, e.y, e.width, e.height, e.enabled, e.color)
}

/** 서버 API 전까지 Room에 저장한다. 플래그를 켜면 서버를 기준으로 동작하고 Room은 캐시가 된다. */
@Singleton
class PrivacyZoneRepositoryImpl @Inject constructor(
    private val dao: PrivacyZoneDao,
    private val api: PrivacyZoneApi,
    private val caller: ApiCaller
) : PrivacyZoneRepository {
    override val isServerApplied get() = FeatureFlags.privacyZones

    override fun observe(mac: String): Flow<List<PrivacyZone>> =
        dao.observe(mac).map { list -> list.map(PrivacyZoneMapper::fromEntity) }

    override suspend fun current(mac: String): List<PrivacyZone> {
        if (FeatureFlags.privacyZones) {
            val remote = caller.call("프라이버시 존 조회 실패") { api.list(mac) }.map(PrivacyZoneMapper::fromDto)
            dao.replace(mac, remote.mapIndexed { i, z -> PrivacyZoneMapper.toEntity(mac, z, i) })
            return remote
        }
        return dao.list(mac).map(PrivacyZoneMapper::fromEntity)
    }

    override suspend fun save(mac: String, zones: List<PrivacyZone>, cameraOnline: Boolean) {
        if (!cameraOnline) throw ApiError(0, null, ZONE_APPLY_FAILED)
        if (FeatureFlags.privacyZones) saveRemote(mac, zones)
        dao.replace(mac, zones.mapIndexed { i, z -> PrivacyZoneMapper.toEntity(mac, z, i) })
    }

    // 명세에 일괄 저장이 없어서 서버 목록과 비교해 나눠 호출한다
    private suspend fun saveRemote(mac: String, zones: List<PrivacyZone>) {
        caller.call(ZONE_APPLY_FAILED) {
            val server = api.list(mac).map(PrivacyZoneMapper::fromDto).associateBy { it.id }
            val removed = server.keys - zones.map { it.id }.toSet()
            if (removed.isNotEmpty()) api.delete(mac, ZoneDeleteRequest(removed.toList()))
            zones.forEach { zone ->
                val existing = server[zone.id]
                when {
                    existing == null -> api.add(mac, PrivacyZoneMapper.toBody(zone))
                    existing != zone -> api.update(mac, zone.id, PrivacyZoneMapper.toBody(zone))
                }
            }
        }
    }
}
