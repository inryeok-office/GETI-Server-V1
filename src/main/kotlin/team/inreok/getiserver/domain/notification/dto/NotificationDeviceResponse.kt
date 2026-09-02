package team.inreok.getiserver.domain.notification.dto

import io.swagger.v3.oas.annotations.media.Schema
import team.inreok.getiserver.domain.notification.entity.type.PushPlatform
import java.time.LocalDateTime

// pushToken은 절대 포함하지 않는다(Issue #189 확정 제약) -- Secret에 준하는 값이라 응답 Body에
// Echo하지 않는다.
@Schema(description = "푸시 기기 등록/갱신 결과. pushToken은 응답에 포함하지 않는다.")
data class NotificationDeviceResponse(
    @param:Schema(description = "기기 식별자", example = "a1b2c3d4-5678-90ab-cdef-device-uuid")
    val deviceKey: String,
    @param:Schema(description = "기기 플랫폼", example = "ANDROID")
    val platform: PushPlatform,
    @param:Schema(description = "등록/갱신이 반영된 시각")
    val updatedAt: LocalDateTime,
)
