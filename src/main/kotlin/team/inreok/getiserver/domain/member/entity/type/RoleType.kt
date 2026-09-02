package team.inreok.getiserver.domain.member.entity.type

import org.springframework.modulith.NamedInterface

/**
 * 회원의 역할이다. 지금까지는 `domain.member` 내부에서만 쓰였지만(각 도메인은 `roles: Set<String>`
 * 원시 값만 받는 `InquiryAssigneeCandidateQueryPort` 같은 계약을 통해 간접적으로만 참조했다),
 * [team.inreok.getiserver.domain.member.query.MemberRoleQueryPort]가 이 Enum 자체를 반환하기
 * 시작하며 처음으로 Module 경계를 넘는다. 그래서 `@NamedInterface`를 직접 붙인다
 * (`docs/architecture/modularity.md`의 "Enum만 공개할 때" 방식).
 */
@NamedInterface
enum class RoleType {
    STUDENT,
    TEACHER,
    DEVELOPER,
}
