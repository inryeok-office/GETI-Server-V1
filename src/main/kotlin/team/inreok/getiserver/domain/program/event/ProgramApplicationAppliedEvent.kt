package team.inreok.getiserver.domain.program.event

import org.springframework.modulith.NamedInterface

/**
 * 학생이 프로그램에 신청했음을 알린다(Issue #191, 제품 계약 확정 "신청 학생 본인 + 담당 교사").
 * `domain.notification`이 이 Event를 구독해 `PROGRAM_APPLICATION_APPLIED` 알림을 만든다.
 *
 * [ProgramDiscordEvent]/[ProgramDeletedEvent]와 별도로 둔다 -- 이 Event는 신청 성공이라는 다른
 * 발행 조건과 다른 목적(신청자 본인 + 담당 교사에게 알려야 한다)을 가진다.
 *
 * [applicationId]는 새로 생긴 `ProgramApplication` Row의 id다(Notification Idempotency Identity,
 * Issue #193). 취소 후 재신청하면 `uk_program_applications_active_singleton`(V15)이 활성 신청은
 * Program당 1건만 허용하지만, 취소된 신청 Row는 남아 있고 재신청은 항상 새 Row로 저장되므로
 * `applicationId`가 매 신청마다 새로 생기는 안정적인 식별자다 -- `programId`나 `applicantMemberId`
 * 조합만 쓰면 재신청이 중복으로 처리(dedup)되어 두 번째 알림이 조용히 사라진다.
 *
 * 알림 문구에 필요한 [programTitle]을 함께 담아 구독 측이 다시 조회하지 않게 한다
 * ([ProgramDeletedEvent]와 같은 이유).
 */
@NamedInterface
data class ProgramApplicationAppliedEvent(
    val programId: Long,
    val applicationId: Long,
    val applicantMemberId: Long,
    val programTitle: String,
)
