package team.inreok.getiserver.domain.program.event

import org.springframework.modulith.NamedInterface

/**
 * 학생이 프로그램 신청을 취소했음을 알린다(Issue #191, 제품 계약 확정 "취소 학생 본인 + 담당
 * 교사"). `domain.notification`이 이 Event를 구독해 `PROGRAM_APPLICATION_CANCELED` 알림을
 * 만든다. Program 계약상 취소 사유를 별도로 받지 않으므로 이 Event도, 그 알림도 사유 Field를
 * 두지 않는다(Issue #191 명시 확정).
 *
 * [ProgramApplicationAppliedEvent]와 같은 이유로 [applicationId]를 담는다 -- 이 Event는 취소된
 * 기존 `ProgramApplication` Row(신청 시 생긴 것과 같은 Row)를 가리키므로, 그 신청 건의
 * `applicationId`가 안정적인 식별자다. `sourceEventType`이 `ProgramApplicationAppliedEvent`와
 * 다르므로(Notification Idempotency Identity, Issue #193) 같은 `applicationId`를 공유해도 신청
 * 알림과 취소 알림이 서로를 중복으로 오판하지 않는다.
 *
 * 알림 문구에 필요한 [programTitle]을 함께 담아 구독 측이 다시 조회하지 않게 한다
 * ([ProgramApplicationAppliedEvent]와 같은 이유).
 */
@NamedInterface
data class ProgramApplicationCanceledEvent(
    val programId: Long,
    val applicationId: Long,
    val applicantMemberId: Long,
    val programTitle: String,
)
