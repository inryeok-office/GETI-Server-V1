package team.inreok.getiserver.domain.member.entity.type

import org.springframework.modulith.NamedInterface

/**
 * 지금까지는 `domain.member` 내부에서만 쓰였지만, 관리자 지원자 목록 필터(Issue #181)가 이 Enum을
 * `application` 도메인의 Controller/Service Query Parameter 계약에 그대로 사용하며 처음으로 Module
 * 경계를 넘는다. 그래서 `RoleType`과 동일한 이유로 `@NamedInterface`를 직접 붙인다
 * (`docs/architecture/modularity.md`의 "Enum만 공개할 때" 방식).
 */
@NamedInterface
enum class DepartmentType {
    SW_DEVELOPMENT,
    SMART_IOT,
    AI,
}
