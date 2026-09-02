package team.inreok.getiserver.domain.member.access

import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import team.inreok.getiserver.domain.member.entity.type.RoleType

/**
 * 요청자가 학생의 비공개 프로필까지 볼 수 있는 Role(교사·개발자)인지 판정한다(Issue #114, #89 결정).
 *
 * Role을 `MemberRoleRepository`가 아니라 [SecurityContextHolder]에서 읽는 이유는 Query 수 때문이다.
 * 이 판정은 [MemberProfileImageAccessChecker]를 통해 목록 조회에서 **파일마다** 호출되는데,
 * `findAllByIdMemberId`는 파생 Query라 1차 캐시가 걸리지 않아 회원 수만큼 SELECT가 늘어난다. JWT의
 * roles Claim이 `JwtAuthenticationFilter`를 통해 이미 authorities로 채워져 있으므로 여기서 읽으면
 * 추가 Query가 없다.
 *
 * **대가는 반영 지연이다.** Access Token TTL(`app.jwt.access-token-expiration-seconds`, 기본 1800초)
 * 동안은 회수된 Role이 담긴 Token이 계속 유효하다.
 *
 * 인증 정보가 없으면(요청 Thread 밖에서 호출되는 경우) 거부한다 -- 판정 근거가 없으면 권한을 주지
 * 않는다. 현재 이 판정에 도달하는 경로는 모두 Controller 요청 Thread지만, 나중에 Scheduler나 Event
 * Listener가 같은 Service를 재사용해도 조용히 권한이 열리지 않도록 기본값을 거부로 둔다.
 */
@Component
class PrivilegedProfileViewer {
    fun canViewPrivateProfile(): Boolean {
        val authentication = SecurityContextHolder.getContext().authentication ?: return false
        return authentication.authorities.any { it.authority in PRIVILEGED_AUTHORITIES }
    }

    private companion object {
        /** Role 이름이 바뀌면 컴파일 단계에서 드러나도록 [RoleType]을 그대로 참조한다. */
        val PRIVILEGED_AUTHORITIES =
            setOf(
                "ROLE_${RoleType.TEACHER}",
                "ROLE_${RoleType.DEVELOPER}",
            )
    }
}
