package team.inreok.getiserver.domain.program.event

import org.springframework.modulith.NamedInterface

/**
 * 프로그램이 삭제(Soft Delete)됐음을 알리는 최소 계약이다(Issue #118, 제품 계약 Notification
 * §16.1의 "Program 삭제" 최소 인앱 알림 대상). `domain.notification`이 이 Event를 구독해 해당
 * 프로그램에 신청한 학생들에게 `PROGRAM_DELETED` 알림을 만든다.
 *
 * [ProgramDiscordEvent]와 별도로 둔다 -- 그 Event는 Discord 채널 메시지 관리 전용이고(DRAFT는
 * 지울 메시지가 없어 발행 대상에서 빠진다), 이 Event는 "신청자에게 알려야 한다"라는 다른 목적과
 * 다른 발행 조건을 가진다. 한 Event를 두 용도로 겹쳐 쓰면 한쪽 조건이 바뀔 때 다른 쪽이 조용히
 * 영향을 받는다.
 *
 * 알림 문구에 필요한 [title]을 함께 담아 구독 측이 다시 조회하지 않게 한다
 * ([team.inreok.getiserver.domain.inquiry.event.InquiryAnsweredEvent]가 `inquiryTitle`을 담는 것과
 * 같은 이유다). 수신자 목록은 건수가 정해져 있지 않아 Event에 담지 않고 구독 측이
 * [team.inreok.getiserver.domain.program.query.ProgramApplicantQueryPort]로 조회한다.
 */
@NamedInterface
data class ProgramDeletedEvent(
    val programId: Long,
    val title: String,
)
