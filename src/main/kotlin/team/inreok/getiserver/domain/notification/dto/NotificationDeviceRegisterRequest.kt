package team.inreok.getiserver.domain.notification.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import team.inreok.getiserver.domain.notification.entity.type.PushPlatform

/**
 * 푸시 기기 등록/갱신 요청이다(Issue #189). 같은 `deviceKey`로 다시 보내면 기존 Row를 갱신하고,
 * 다른 회원이 이미 등록한 `deviceKey`면 이 요청의 회원으로 소유권이 이전된다.
 */
@Schema(description = "푸시 기기 등록/갱신 요청")
data class NotificationDeviceRegisterRequest(
    @field:NotBlank(message = "deviceKey는 비어 있을 수 없습니다.")
    @field:Size(max = 255, message = "deviceKey는 255자를 넘을 수 없습니다.")
    @param:Schema(
        description = "안정적인 기기/앱 설치 식별자(필수, 최대 255자). 같은 값으로 다시 등록하면 기존 Row가 갱신된다.",
        example = "a1b2c3d4-5678-90ab-cdef-device-uuid",
        maxLength = 255,
    )
    val deviceKey: String,
    @field:NotBlank(message = "pushToken은 비어 있을 수 없습니다.")
    @field:Size(max = 1000, message = "pushToken은 1000자를 넘을 수 없습니다.")
    @param:Schema(
        description = "FCM/APNs Push Token(필수, 최대 1000자). 만료·재발급될 수 있어 deviceKey와 분리된 값이며, 어떤 응답에도 노출되지 않는다.",
        example = "fcm-or-apns-push-token-value",
        maxLength = 1000,
    )
    val pushToken: String,
    @param:Schema(description = "기기 플랫폼(필수)", example = "ANDROID")
    val platform: PushPlatform,
) {
    // pushToken은 절대 포함하지 않는다 -- data class의 자동 생성 toString()을 그대로 두면 Log에
    // Token이 새어 나갈 수 있어 직접 재정의한다(NotificationDevice Class KDoc과 같은 이유).
    override fun toString(): String =
        "NotificationDeviceRegisterRequest(deviceKey='$deviceKey', pushToken=****, platform=$platform)"
}
