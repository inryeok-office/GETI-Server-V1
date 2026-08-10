package team.inreok.getiserver.domain.program.query

import org.springframework.modulith.NamedInterface
import java.time.LocalDateTime

/**
 * `notification` Module이 Discord로 보낼 프로그램 의미 데이터를 읽는 공개 계약이다(후속
 * 요구사항 문서 §19·§27). 방향과 이유는
 * [team.inreok.getiserver.domain.job.query.JobDiscordPayloadQueryPort]와 같다.
 *
 * `notification`이 `program`의 Discord 상태를 대신 소유하면서(§32·§33) `program.entity.type.
 * DiscordDeliveryStatus(SUCCESS/FAILED/SKIPPED)`와 `dto.DiscordDeliveryResult`는 제거됐고
 * 세 응답 DTO의 `discordDelivery` 필드도 사라졌다(`discord-event-wiring-plan.md` §6.1,
 * Breaking Change). `discord_channel_id`는 여전히 클라이언트가 채널을 지정하는 입력값이라
 * 유지한다. 이 Port는 그 정리와 무관하게 "Discord에 보여줄 프로그램 내용"만 읽는다.
 */
@NamedInterface
interface ProgramDiscordPayloadQueryPort {
    /**
     * 존재하지 않으면 null을 반환한다. 삭제된 프로그램도 돌려준다 -- `DELETE_NOTICE`가 이미
     * 삭제된 프로그램의 제목으로 기존 메시지를 수정해야 하기 때문이다.
     */
    fun findById(programId: Long): ProgramDiscordPayloadSnapshot?
}

/**
 * GETI-Bot-V1의 Program Template `data` Schema가 받는 필드에 대응한다.
 *
 * [eventStartedAt]/[eventEndedAt]을 Bot Schema의 `startAt`/`endAt`에 싣는다. `programs`에는
 * 행사 기간(`event_*`)과 신청 기간(`application_*`)이 모두 있는데, Discord 공지는 "언제 열리는
 * 행사인가"를 알리는 것이므로 행사 기간을 쓴다.
 */
@NamedInterface
data class ProgramDiscordPayloadSnapshot(
    val programId: Long,
    val title: String,
    /** `programs.body_markdown`. Bot Schema의 선택 필드 `description`에 대응한다. */
    val bodyMarkdown: String?,
    val eventStartedAt: LocalDateTime?,
    val eventEndedAt: LocalDateTime?,
    /** `Program.discordChannelId`(원시 Snowflake, 클라이언트가 등록 시 지정한 값). */
    val discordChannelId: String?,
    /** Mention Role 계산에 쓰는 대상 학년 목록. `program_target_grades` Table을 조회한 값이다. */
    val targetGrades: List<Int>,
    /**
     * `discord-delivery-plan.md` §6.3의 UPDATE Idempotency Key를 만드는 데 쓴다. 저장된 Program은
     * `@UpdateTimestamp`가 항상 채우므로 non-null이다.
     */
    val updatedAt: LocalDateTime,
)
