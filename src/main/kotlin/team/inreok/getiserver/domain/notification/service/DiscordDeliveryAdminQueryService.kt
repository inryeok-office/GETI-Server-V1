package team.inreok.getiserver.domain.notification.service

import org.springframework.data.domain.Pageable
import team.inreok.getiserver.domain.notification.dto.DiscordDeliveryListResponse
import team.inreok.getiserver.domain.notification.entity.type.DiscordDeliveryStatus
import java.time.LocalDateTime

/**
 * 관리자 Discord 전달 관리 화면이 쓰는 읽기 전용 조회 계약이다(Issue #206).
 *
 * [DiscordDeliveryService]와 분리한 이유는 관심사가 다르기 때문이다 -- 그쪽은 전달을 예약하고
 * Bot으로 보내고 재시도하는 **전송 파이프라인**이고, 이쪽은 대상 이름 해석과 재시도 가능 여부
 * 판정을 곁들인 **관리자 읽기 모델**이다. 이 Interface의 메서드는 어떤 상태도 바꾸지 않는다.
 */
interface DiscordDeliveryAdminQueryService {
    /**
     * 대상 종류(JOB/PROGRAM/INQUIRY)를 가리지 않고 최근 Discord 전달 내역을 조회한다.
     *
     * [status]가 null이면 전체를 조회한다. 정렬은 최신순으로 고정되어 [pageable]의 Sort는
     * 무시하고 Page 정보만 사용한다.
     */
    fun listRecent(
        status: DiscordDeliveryStatus?,
        pageable: Pageable,
        startAt: LocalDateTime? = null,
        endAt: LocalDateTime? = null,
    ): DiscordDeliveryListResponse
}
