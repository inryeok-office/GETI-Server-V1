package team.inreok.getiserver.domain.notification.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.UpdateTimestamp
import java.time.LocalDateTime

/**
 * 회원 1명의 푸시 알림 수신 설정이다(Issue #189). `NotificationType`별로 나누지 않고
 * `pushEnabled` 하나만 회원당 1개 유지한다(확정 계약). `job_ai_analyses`(job_id를 공유 PK로
 * 사용)와 같은 방식으로 `memberId`를 그대로 PK로 쓴다 -- 회원 1명당 정확히 0개 또는 1개 Row만
 * 존재하는 순수 1:1 관계라 대리 PK가 필요 없다.
 *
 * Row가 없으면 아직 설정한 적 없는 회원이라 Service가 기본값(true)으로 해석한다 -- 가입 시 전체
 * 회원에게 Row를 만들어 두지 않고, 실제로 설정을 바꾸는 시점에만 만든다(Lazy Create,
 * `RecommendationPreference`와 같은 방식).
 *
 * `pushEnabled=false`여도 이 값은 [team.inreok.getiserver.domain.notification.entity.NotificationDevice]
 * Row를 지우지 않는다 -- Push 발송만 멈춘다(발송 여부 판단은 Issue #190의 책임).
 */
@Entity
@Table(name = "notification_settings")
class NotificationSetting(
    @Id
    @Column(name = "member_id")
    val memberId: Long,
    @Column(name = "push_enabled", nullable = false)
    var pushEnabled: Boolean,
) {
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime? = null
}
