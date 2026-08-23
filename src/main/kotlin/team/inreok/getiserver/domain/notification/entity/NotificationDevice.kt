package team.inreok.getiserver.domain.notification.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import team.inreok.getiserver.domain.notification.entity.type.PushPlatform
import java.time.LocalDateTime

/**
 * 회원이 등록한 푸시 기기다(Issue #189). `deviceKey`(안정적인 기기/앱 설치 식별자)는 회원이 아니라
 * 기기 단위로 Global Unique하다 -- 같은 기기를 다른 회원이 등록하면 그 회원으로 소유권이 이전된다
 * (이전 소유자에게 별도 알림·거부 절차는 두지 않는 확정 정책). 실제 발송에 쓰이는 `pushToken`
 * (FCM/APNs Token)은 만료·재발급될 수 있어 `deviceKey`와 분리한 별도 Column이다.
 *
 * 등록/재등록/소유권 이전은 모두 [team.inreok.getiserver.domain.notification.repository.NotificationDeviceRepository.upsert]
 * (`INSERT ... ON CONFLICT (device_key) DO UPDATE`)로 원자적으로 처리한다 -- 같은 deviceKey로
 * 동시에 도착하는 등록 요청이 Unique 제약 위반 없이 하나의 Row로 수렴하게 하기 위해서다.
 *
 * `pushToken`은 Secret에 준하는 값이라 어떤 API 응답에도 Echo하지 않는다
 * ([team.inreok.getiserver.domain.notification.dto.NotificationDeviceResponse] 참고). 이 Class가
 * `data class`가 아니라 일반 Class인 것도 같은 이유다 -- `data class`의 자동 생성 `toString()`은
 * 모든 생성자 프로퍼티를 그대로 출력해 Log에 Token이 새어 나갈 수 있다. [toString]을 직접
 * 재정의해 `pushToken`을 절대 출력하지 않는다.
 */
@Entity
@Table(
    name = "notification_devices",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_notification_devices_device_key", columnNames = ["device_key"]),
    ],
)
class NotificationDevice(
    @Column(name = "member_id", nullable = false)
    var memberId: Long,
    @Column(name = "device_key", nullable = false, length = 255)
    var deviceKey: String,
    @Column(name = "push_token", nullable = false, length = 1000)
    var pushToken: String,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var platform: PushPlatform,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime? = null

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime? = null

    // pushToken은 절대 포함하지 않는다(Class KDoc 참고).
    override fun toString(): String =
        "NotificationDevice(id=$id, memberId=$memberId, deviceKey='$deviceKey', platform=$platform, " +
            "createdAt=$createdAt, updatedAt=$updatedAt)"
}
