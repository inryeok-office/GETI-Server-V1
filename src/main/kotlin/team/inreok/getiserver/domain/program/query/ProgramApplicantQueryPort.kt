package team.inreok.getiserver.domain.program.query

import org.springframework.modulith.NamedInterface

/**
 * `notification` Module이 프로그램 알림의 수신자(신청자)를 읽는 공개 계약이다(Issue #118).
 * 의존 방향과 이유는 [ProgramNotificationTargetQueryPort]와 같다 -- 소유 Domain인 `program`이
 * 공개하고 `notification`이 참조하므로, `notification -> program` 단방향만 생겨
 * `ModularityTest`의 순환 검증에 걸리지 않는다.
 */
@NamedInterface
interface ProgramApplicantQueryPort {
    /**
     * 활성 신청(`APPLIED`) 상태인 신청자의 회원 id다. 취소한 신청자는 알림 대상이 아니므로
     * 제외한다. 신청자가 없거나 프로그램이 없으면 빈 목록을 반환한다.
     *
     * 프로그램 삭제는 Soft Delete라 신청 Row가 그대로 남으므로, 삭제 Transaction이 Commit된
     * 뒤에 호출해도 신청자를 그대로 조회할 수 있다.
     */
    fun findActiveApplicantMemberIds(programId: Long): List<Long>
}
