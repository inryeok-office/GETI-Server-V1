package team.inreok.getiserver.domain.member.query

import org.springframework.modulith.NamedInterface

/**
 * `inquiry` Module이 담당 개발자 지정 대상을 검증할 때 쓰는 공개 계약이다(GETI Inquiry 도메인
 * 개발 요구사항 32절). 담당자로 지정할 수 있으려면 `role=DEVELOPER`이면서 `status=ACTIVE`여야
 * 하는데, 이 판정은 `inquiry`의 비즈니스 규칙이라 Port는 원시 값(역할 집합, 상태)만 반환하고
 * 최종 판정은 하지 않는다([team.inreok.getiserver.domain.program.query.ProgramNotificationTargetQueryPort]와
 * 같은 "원시 상태만 반환" 원칙).
 *
 * `application` Module도 지원서 첨부파일 다운로드 권한 판정(교사·개발자 여부, Issue #134)에 그대로
 * 재사용한다 -- "이 회원의 역할이 무엇인가"라는 같은 조회이므로 거의 동일한 목적의 Named
 * Interface를 또 만들지 않았다(`MemberApplicantSnapshotQueryPort`가 `application`/`program`
 * 양쪽에서 재사용되는 것과 같은 판단).
 */
@NamedInterface
interface InquiryAssigneeCandidateQueryPort {
    /** 존재하지 않으면 null을 반환한다. */
    fun findById(memberId: Long): InquiryAssigneeCandidate?
}

@NamedInterface
data class InquiryAssigneeCandidate(
    val memberId: Long,
    val name: String?,
    /** `RoleType.name` 집합. */
    val roles: Set<String>,
    /** `MemberStatus.name` */
    val status: String,
)
