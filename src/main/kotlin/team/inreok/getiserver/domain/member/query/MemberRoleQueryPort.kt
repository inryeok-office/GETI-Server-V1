package team.inreok.getiserver.domain.member.query

import org.springframework.modulith.NamedInterface
import team.inreok.getiserver.domain.member.entity.type.RoleType

/**
 * 다른 Domain Module이 회원의 역할 집합만 필요할 때 쓰는 범용 공개 계약이다. `program`이 첨부파일
 * 다운로드 권한(`ProgramFileAccessChecker`) 판정에 "이 회원이 DEVELOPER인가"를 확인하려고 처음
 * 추가했다(Issue #127).
 *
 * [InquiryAssigneeCandidateQueryPort]와 합치지 않는다 -- 그 Port는 "문의 담당자로 지정할 수
 * 있는가"라는 Inquiry 전용 판정에 필요한 `status`(ACTIVE 여부)까지 함께 반환하도록 설계됐고,
 * `roles`도 `RoleType.name`(String)만 노출한다(그 Port를 만들 당시엔 `RoleType` 자체를 Module
 * 경계 밖에 공개하지 않기로 했다). 이 Port는 그와 달리 역할 집합만 필요한 범용 소비자를 위해
 * `RoleType` Enum 그대로("이 회원의 Role은 무엇인가"라는 순수 조회) 반환한다. 두 계약을 하나로
 * 합치면 Inquiry 전용 필드(`status`)가 매번 따라와 이름과 실제 계약이 어긋난다.
 */
@NamedInterface
interface MemberRoleQueryPort {
    /**
     * [memberId]의 역할 집합을 반환한다. 존재하지 않는 회원은 빈 Set이다 -- 이 Port의 소비자는
     * 항상 "특정 역할을 가졌는가"만 확인하므로, "회원이 없음"과 "역할이 없음"을 구분할 필요가
     * 없다(`InquiryAssigneeCandidateQueryPort`처럼 존재 여부 자체가 별도로 중요한 경우와 다르다).
     */
    fun findRoles(memberId: Long): Set<RoleType>
}
