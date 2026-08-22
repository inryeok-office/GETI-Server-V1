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

    /**
     * 관리자 Discord 전달 목록에 표시할 공고 제목을 배치로 읽는다(Issue #206). 존재하지 않는
     * id는 결과 Map에서 빠진다.
     *
     * [findById]와 같은 이유로 삭제된 공고도 포함한다 -- 실패한 `DELETE_NOTICE` 전달이 목록에
     * 남아 있는데 이름이 사라지면 관리자가 어떤 공고인지 식별할 수 없다.
     *
     * [JobDiscordPayloadSnapshot] 전체가 아니라 표시 이름만 돌려준다. 목록은 제목 외의 값을 쓰지
     * 않고, 목록 한 Page는 최대 100건이라 Snapshot을 그대로 실으면 쓰지 않을 값까지 함께 따라온다.
     * 목록 API가 한 번에 최대 100건을 반환하므로 단건이 아닌 배치 조회로 둔다(N+1 방지).
     */
    fun findDisplayNamesByIds(jobIds: Set<Long>): Map<Long, String>
}

/**
 * GETI-Bot-V1의 Job Template `data` Schema가 받는 필드에 대응한다. Bot Schema는 `.strict()`라
 * 여기 없는 값을 넘길 수 없고, 넘기면 `400 INVALID_REQUEST`(재시도 불가)로 거절된다.
 *
 * Bot Schema의 선택 필드 `location`과 `employmentType`은 아직 보내지 않는다. `jobs`에 해당
 * Column이 아예 없어서 채울 값이 없었으나(V19 시점 실측), Issue #169에서 `jobs.location`과
 * `jobs.employment_type`을 추가해 이제는 값이 존재한다. 그래도 이번 범위에서 보내지 않는
 * 이유는 Bot의 `jobPublishedDataSchema`가 이 두 필드를 실제로 받는지 이 저장소에서 확인할 수
 * 없기 때문이다 — Schema는 `.strict()`라 받지 않는 필드를 보내면 `400 INVALID_REQUEST`
 * (`retryable=false`)로 즉시 FAILED가 되어 게시 알림이 영구히 누락된다. Bot Schema를 실측한
 * 뒤 별도 작업에서 추가한다.
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
