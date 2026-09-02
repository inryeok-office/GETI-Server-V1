package team.inreok.getiserver.domain.notification.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import team.inreok.getiserver.domain.notification.entity.NotificationSetting

interface NotificationSettingRepository : JpaRepository<NotificationSetting, Long> {
    /**
     * ON/OFF 설정을 원자적으로 Upsert한다(`RecommendationPreferenceRepository.upsert`와 같은
     * 이유·같은 방식). 아직 설정한 적 없는 회원이 `PATCH /settings`를 거의 동시에 두 번 보내도
     * (더블 클릭·재시도) `uk`(memberId를 그대로 PK로 사용) 위반 없이 하나의 Row로 수렴한다.
     */
    @Modifying(clearAutomatically = true)
    @Query(
        value = """
            INSERT INTO notification_settings (member_id, push_enabled, updated_at)
            VALUES (:memberId, :pushEnabled, now())
            ON CONFLICT (member_id) DO UPDATE SET push_enabled = :pushEnabled, updated_at = now()
            """,
        nativeQuery = true,
    )
    fun upsert(
        @Param("memberId") memberId: Long,
        @Param("pushEnabled") pushEnabled: Boolean,
    )
}
