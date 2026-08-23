package team.inreok.getiserver.domain.notification.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import team.inreok.getiserver.domain.notification.entity.NotificationDevice

interface NotificationDeviceRepository : JpaRepository<NotificationDevice, Long> {
    fun findByDeviceKey(deviceKey: String): NotificationDevice?

    /**
     * 기기 등록·재등록·소유권 이전을 원자적으로 Upsert한다
     * (`RecommendationPreferenceRepository.upsert`와 같은 이유·같은 방식).
     *
     * `deviceKey`는 회원이 아니라 물리/논리 기기 단위로 Global Unique다(Issue #189 확정 계약) --
     * 다른 회원이 같은 `deviceKey`로 등록하면 이 Query가 `memberId`/`pushToken`/`platform`을 새
     * 값으로 덮어써 소유권을 이전한다. 같은 회원의 단순 재등록(Token 재발급 등)과 다른 회원의
     * 등록을 이 Query 수준에서 구분하지 않는다 -- 이전 소유자에게 별도 알림·거부 절차를 두지 않는
     * 것이 확정 정책이기 때문이다.
     *
     * find-then-save 2단계 대신 `INSERT ... ON CONFLICT` 한 Statement로 처리해, 같은 `deviceKey`로
     * 동시에 도착하는 등록 요청(재시도, 앱 재실행 직후 중복 호출 등)이
     * `uk_notification_devices_device_key` 위반 없이 하나의 Row로 안전하게 수렴하도록 한다.
     *
     * `clearAutomatically = true`가 필요한 이유는 `RecommendationPreferenceRepository.upsert`
     * KDoc과 같다 -- Native Query라 Hibernate가 변경을 추적하지 못해, 같은 Transaction의
     * Persistence Context에 이전 Entity가 남아 있으면 그 뒤의 조회가 DB의 새 값 대신 Context의
     * 이전 값을 돌려줄 수 있다.
     */
    @Modifying(clearAutomatically = true)
    @Query(
        value = """
            INSERT INTO notification_devices (member_id, device_key, push_token, platform, created_at, updated_at)
            VALUES (:memberId, :deviceKey, :pushToken, :platform, now(), now())
            ON CONFLICT (device_key) DO UPDATE
                SET member_id = :memberId, push_token = :pushToken, platform = :platform, updated_at = now()
            """,
        nativeQuery = true,
    )
    fun upsert(
        @Param("memberId") memberId: Long,
        @Param("deviceKey") deviceKey: String,
        @Param("pushToken") pushToken: String,
        @Param("platform") platform: String,
    )
}
