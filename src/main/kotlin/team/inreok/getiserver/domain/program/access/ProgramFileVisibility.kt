package team.inreok.getiserver.domain.program.access

import team.inreok.getiserver.domain.program.entity.Program
import team.inreok.getiserver.domain.program.entity.type.ProgramStatus

/**
 * "이 회원이 이 Program의 첨부파일(본문, `FileOwnerType.PROGRAM`)을 볼 수 있는가"를 판정하는
 * 순수 함수다. Repository/Port 호출이 없어 두 호출부가 항상 같은 규칙을 적용하도록 강제한다
 * (`ProgramEligibility.computeProgramEligibilityReason`과 동일한 방식).
 *
 * 두 곳에서 이 함수를 그대로 재사용한다.
 * 1. [ProgramFileAccessChecker.canDownload] -- 실제 다운로드(`FileController`) 권한 판정. `isDeveloper`는
 *    [team.inreok.getiserver.domain.member.query.MemberRoleQueryPort]로 DB에서 조회한 값이다.
 * 2. `ProgramServiceImpl.getDetail` -- 상세 응답의 `files` 목록 노출 여부. `isDeveloper`는 Controller가
 *    JWT Role Claim에서 계산해 넘긴 값이다(DB 조회 없이 `InquiryServiceImpl.getDetail`과 동일한 방식).
 *
 * 두 호출부가 `isDeveloper`를 서로 다른 방법(DB 조회 vs JWT)으로 구하지만, 판정 로직 자체(공개
 * 상태면 누구나, 아니면 등록자·담당 교사·개발자만)는 이 함수 하나로 고정돼 있어 어긋날 수 없다.
 *
 * PUBLISHED/CLOSED를 "공개"로 취급하는 이유는 요구사항이 그렇게 명시했기 때문이다 -- CLOSED는
 * 신청만 마감됐을 뿐 프로그램 정보 자체는 계속 조회할 수 있어야 한다(`ProgramEligibility`가
 * CLOSED를 신청 불가 사유로만 쓰고 조회 자체를 막지 않는 것과 같은 맥락).
 */
fun canViewProgramFiles(
    program: Program,
    requesterId: Long,
    isDeveloper: Boolean,
): Boolean {
    if (program.status == ProgramStatus.PUBLISHED || program.status == ProgramStatus.CLOSED) return true
    return isDeveloper || requesterId == program.createdByMemberId || requesterId == program.managerMemberId
}
