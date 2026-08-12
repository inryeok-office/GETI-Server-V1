package team.inreok.getiserver.domain.member.access

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder

/**
 * Issue #114의 "교사·개발자만 비공개 프로필을 볼 수 있다"를 판정하는 지점이다. 이 판정 하나가
 * 상세 프로필 마스킹([team.inreok.getiserver.domain.member.service.impl.MemberServiceImpl])과
 * 이미지 접근([MemberProfileImageAccessChecker]) 양쪽에 함께 적용된다.
 */
class PrivilegedProfileViewerTest {
    private val viewer = PrivilegedProfileViewer()

    // SecurityContextHolder는 ThreadLocal이라 정리하지 않으면 같은 Thread를 재사용하는 다른
    // Test로 인증 정보가 새어 나간다.
    @AfterEach
    fun clearSecurityContext() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `인증 정보가 없으면 거부한다`() {
        assertThat(viewer.canViewPrivateProfile()).isFalse()
    }

    @Test
    fun `학생은 다른 회원의 비공개 프로필을 볼 수 없다`() {
        authenticateWith("STUDENT")

        assertThat(viewer.canViewPrivateProfile()).isFalse()
    }

    @Test
    fun `교사는 비공개 프로필을 볼 수 있다`() {
        authenticateWith("TEACHER")

        assertThat(viewer.canViewPrivateProfile()).isTrue()
    }

    @Test
    fun `개발자는 비공개 프로필을 볼 수 있다`() {
        authenticateWith("DEVELOPER")

        assertThat(viewer.canViewPrivateProfile()).isTrue()
    }

    @Test
    fun `학생 Role을 함께 가진 교사도 비공개 프로필을 볼 수 있다`() {
        // members_roles는 (member_id, role) 복합 키라 한 회원이 여러 Role을 가질 수 있다.
        authenticateWith("STUDENT", "TEACHER")

        assertThat(viewer.canViewPrivateProfile()).isTrue()
    }

    @Test
    fun `권한이 하나도 없는 인증은 거부한다`() {
        // 승인 대기(PENDING) 교직원은 Role을 부여받지 못한 채 로그인할 수 있다
        // (OAuthMemberPortImpl.autoGrantRoleFor).
        authenticateWith()

        assertThat(viewer.canViewPrivateProfile()).isFalse()
    }

    private fun authenticateWith(vararg roles: String) {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(
                MEMBER_ID,
                null,
                roles.map { SimpleGrantedAuthority("ROLE_$it") },
            )
    }

    private companion object {
        private const val MEMBER_ID = 1L
    }
}
