package com.example.androidapplication.core.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import com.example.androidapplication.core.common.READ_TIMEOUT_MS
import com.example.androidapplication.core.config.FeatureFlags
import com.example.androidapplication.core.network.ApiCaller
import com.example.androidapplication.core.network.PrivacyZoneApi
import com.example.androidapplication.core.network.PrivacyZoneBody
import com.example.androidapplication.core.network.PrivacyZoneDto
import com.example.androidapplication.core.network.PrivacyZonesRequest
import com.example.androidapplication.core.network.ZoneCoordinatesDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** 저장 요청이 실패했을 때의 일반 문구. 보드 적용 여부는 [ZoneApplyStatus]로 따로 보여 준다. */
const val ZONE_APPLY_FAILED = "프라이버시 존 저장에 실패했습니다."

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

    /** zone_id는 존이 가진 ID를 그대로 보낸다. 앱이 새로 만든 UUID는 서버가 모르는 ID라서 서버가 새로 발급한다. */
    fun toBody(zone: PrivacyZone): PrivacyZoneBody = PrivacyZoneBody(
        zoneId = zone.id.ifBlank { null },
        zoneName = zone.name,
        isActive = zone.enabled,
        color = zone.color,
        coordinates = ZoneCoordinatesDto(
            ratio(zone.x.toDouble()),
            ratio(zone.y.toDouble()),
            ratio(zone.x.toDouble() + zone.width.toDouble()),
            ratio(zone.y.toDouble() + zone.height.toDouble())
        )
    )

    /** 소수 6자리로 반올림한 뒤 0~1로 보정한다. x + width가 1.0000001이 되면 서버가 x2 ≤ 1 위반으로 400을 낸다. */
    fun ratio(value: Double): Float = (Math.round(value * 1e6) / 1e6).coerceIn(0.0, 1.0).toFloat()

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
            val remote = caller.call("프라이버시 존 조회 실패", timeoutMs = READ_TIMEOUT_MS) { api.list(mac) }.map(PrivacyZoneMapper::fromDto)
            cache(mac, remote)
            return remote
        }
        return dao.list(mac).map(PrivacyZoneMapper::fromEntity)
    }

    /**
     * PUT 한 번으로 추가·수정·삭제를 반영하고 서버가 돌려준 목록(서버 ID, 서버 순서)을 돌려준다.
     * 보드가 오프라인이어도 저장한다. 적용은 보드가 돌아온 뒤에 된다 (UC1-E4).
     */
    override suspend fun save(mac: String, zones: List<PrivacyZone>): List<PrivacyZone> {
        val saved = if (FeatureFlags.privacyZones) {
            caller.call(ZONE_APPLY_FAILED, statusFallback = mapOf(400 to ZoneSaveErrors.BAD_REQUEST_MESSAGE)) {
                api.replace(mac, PrivacyZonesRequest(zones.map(PrivacyZoneMapper::toBody)))
            }.map(PrivacyZoneMapper::fromDto)
        } else {
            zones
        }
        cache(mac, saved)
        return saved
    }

    // 서버 응답 순서를 그대로 position으로 둔다 (position은 캐시 정렬에만 쓴다)
    private suspend fun cache(mac: String, zones: List<PrivacyZone>) =
        dao.replace(mac, zones.mapIndexed { i, z -> PrivacyZoneMapper.toEntity(mac, z, i) })
}
