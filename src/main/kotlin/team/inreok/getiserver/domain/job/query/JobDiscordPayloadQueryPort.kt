package team.inreok.getiserver.domain.job.query

import org.springframework.modulith.NamedInterface
import java.time.LocalDateTime

/**
 * `notification` Module이 Discord로 보낼 공고 의미 데이터를 읽는 공개 계약이다(후속 요구사항
 * 문서 §19·§26). `notification`은 이 Interface를 통해서만 Job을 읽고 `Job` Entity나
 * `JobRepository`를 직접 참조하지 않는다.
 *
 * 계약이 `notification`이 아니라 `job`에 있는 이유는 [JobNotificationTargetQueryPort]와 같다 --
 * 반대 방향으로 두면 `notification`이 `job.event`를 구독하는 순간 순환 의존이 되어
 * `ModularityTest`가 실패한다.
 *
 * **Delivery는 실패 당시 Payload를 저장하지 않는다.** 재시도할 때마다 이 Port로 최신 상태를
 * 다시 읽어 의미 데이터를 새로 만든다(§19) -- 실패 후 제목이 수정됐다면 재시도는 새 제목으로
 * 나가야 하기 때문이다.
 */
@NamedInterface
interface JobDiscordPayloadQueryPort {
    /**
     * 존재하지 않으면 null을 반환한다.
     *
     * **삭제된 공고도 돌려준다** -- `DELETE_NOTICE`는 이미 삭제된 공고의 제목으로 Discord
     * 메시지를 "삭제됨" 상태로 바꿔야 하므로, Soft Delete를 걸러내면 그 작업 자체가 불가능해진다.
     * 공개 여부 판정도 하지 않는다(어떤 Delivery를 만들지는 이미 결정된 뒤 호출된다).
     *
     * Worker가 Delivery를 한 건씩 처리하므로 배치가 아닌 단건 조회다.
     */
    fun findById(jobId: Long): JobDiscordPayloadSnapshot?
}

/**
 * GETI-Bot-V1의 Job Template `data` Schema가 받는 필드에 대응한다. Bot Schema는 `.strict()`라
 * 여기 없는 값을 넘길 수 없고, 넘기면 `400 INVALID_REQUEST`(재시도 불가)로 거절된다.
 *
 * `jobs`에는 근무지·고용형태 Column이 없어(V19 시점 실측) Bot Schema의 선택 필드 `location`과
 * `employmentType`에 채울 값이 없다. 두 필드 모두 optional이므로 보내지 않는다.
 */
@NamedInterface
data class JobDiscordPayloadSnapshot(
    val jobId: Long,
    val title: String,
    val companyId: Long,
    /** 삭제된 기업이면 null이다. Bot Schema에서 `companyName`은 선택 필드다. */
    val companyName: String?,
    /** 모집 마감 시각. Bot Schema의 `deadline`에 대응한다. */
    val recruitmentEndedAt: LocalDateTime?,
    /** `Job.discordChannelKey`(논리 Key). `DiscordChannelResolver`로 물리 채널을 해석한다. */
    val discordChannelKey: String?,
    /** Mention Role 계산에 쓰는 지원 대상 학년. 전 학년 대상이면 null이다. */
    val targetGrade: Int?,
    /**
     * `discord-delivery-plan.md` §6.3의 UPDATE Idempotency Key(`{targetType}:{targetId}:UPDATE:
     * {원본 updatedAt epochMilli}`)를 만드는 데 쓴다. 저장된 Job은 `@UpdateTimestamp`가 항상
     * 채우므로 non-null이다.
     */
    val updatedAt: LocalDateTime,
)
