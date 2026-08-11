package team.inreok.getiserver.domain.member.service.impl

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import team.inreok.getiserver.domain.member.entity.Member
import team.inreok.getiserver.domain.member.entity.MemberRole
import team.inreok.getiserver.domain.member.entity.MemberRoleId
import team.inreok.getiserver.domain.member.entity.type.MemberStatus
import team.inreok.getiserver.domain.member.entity.type.OAuthProvider
import team.inreok.getiserver.domain.member.entity.type.RoleType
import team.inreok.getiserver.domain.member.exception.OAuthEmailAlreadyRegisteredException
import team.inreok.getiserver.domain.member.repository.MemberRepository
import team.inreok.getiserver.domain.member.repository.MemberRoleRepository

@ExtendWith(MockitoExtension::class)
class OAuthMemberPortImplTest {
    @Mock
    private lateinit var memberRepository: MemberRepository

    @Mock
    private lateinit var memberRoleRepository: MemberRoleRepository

    private val port: OAuthMemberPortImpl by lazy { OAuthMemberPortImpl(memberRepository, memberRoleRepository) }

    private fun member(
        id: Long? = 1L,
        provider: OAuthProvider = OAuthProvider.GOOGLE,
        subject: String = "subject-1",
        email: String = "user@example.com",
        status: MemberStatus = MemberStatus.PENDING,
    ): Member =
        Member(oauthProvider = provider, oauthSubject = subject, email = email, status = status).also { it.id = id }

    // Kotlin non-null 파라미터에 bare any()를 쓰면 null 반환으로 NPE가 나므로 non-null Fallback을 둔다.
    private fun anyMember(): Member = org.mockito.ArgumentMatchers.any(Member::class.java) ?: member()

    @Test
    fun `기존 회원이면 생성하지 않고 현재 상태와 역할을 반환한다`() {
        given(memberRepository.findByOauthProviderAndOauthSubject(OAuthProvider.GOOGLE, "subject-1"))
            .willReturn(member(id = 5L, status = MemberStatus.ACTIVE))
        given(memberRoleRepository.findAllByIdMemberId(5L))
            .willReturn(listOf(MemberRole(MemberRoleId(memberId = 5L, role = RoleType.DEVELOPER))))

        val result = port.findOrCreateByOAuth("google", "subject-1", "user@example.com")

        assertThat(result.memberId).isEqualTo(5L)
        assertThat(result.status).isEqualTo("ACTIVE")
        assertThat(result.roles).containsExactly("DEVELOPER")
        assertThat(result.isNewMember).isFalse()
        verify(memberRepository, never()).saveAndFlush(anyMember())
    }

    // 저장 시 전달된 Member에 id를 붙여 그대로 돌려준다(생성된 status를 결과가 그대로 반영하도록).
    private fun savedWithId(id: Long) =
        org.mockito.BDDMockito.willAnswer { inv -> (inv.arguments[0] as Member).also { it.id = id } }

    @Test
    fun `Google 신규 교직원은 PENDING으로 생성되고 승인 전이라 Role을 부여하지 않는다`() {
        given(memberRepository.findByOauthProviderAndOauthSubject(OAuthProvider.GOOGLE, "subject-1")).willReturn(null)
        given(memberRepository.findByEmail("user@example.com")).willReturn(null)
        savedWithId(10L).given(memberRepository).saveAndFlush(anyMember())

        val result = port.findOrCreateByOAuth("google", "subject-1", "user@example.com")

        assertThat(result.memberId).isEqualTo(10L)
        assertThat(result.status).isEqualTo("PENDING")
        assertThat(result.roles).isEmpty()
        assertThat(result.isNewMember).isTrue()
        // PENDING 회원에게 Role을 부여하면 status를 보지 않는 인가에서 승인을 우회하므로 부여하지 않는다.
        verify(memberRoleRepository, never()).save(org.mockito.ArgumentMatchers.any(MemberRole::class.java))
    }

    @Test
    fun `DG 신규 학생은 ACTIVE로 자동 가입되고 STUDENT 역할이 부여된다`() {
        given(memberRepository.findByOauthProviderAndOauthSubject(OAuthProvider.DG, "dg-subject")).willReturn(null)
        given(memberRepository.findByEmail("student@dgsw.hs.kr")).willReturn(null)
        savedWithId(11L).given(memberRepository).saveAndFlush(anyMember())

        val result = port.findOrCreateByOAuth("dg", "dg-subject", "student@dgsw.hs.kr")

        assertThat(result.status).isEqualTo("ACTIVE")
        assertThat(result.roles).containsExactly("STUDENT")

        val savedMember = ArgumentCaptor.forClass(Member::class.java)
        verify(memberRepository).saveAndFlush(savedMember.capture())
        assertThat(savedMember.value.status).isEqualTo(MemberStatus.ACTIVE)

        val savedRole = ArgumentCaptor.forClass(MemberRole::class.java)
        verify(memberRoleRepository).save(savedRole.capture())
        assertThat(savedRole.value.id.role).isEqualTo(RoleType.STUDENT)
        assertThat(savedRole.value.id.memberId).isEqualTo(11L)
    }

    @Test
    fun `같은 이메일이 다른 OAuth 계정으로 이미 있으면 예외를 던지고 생성하지 않는다`() {
        given(memberRepository.findByOauthProviderAndOauthSubject(OAuthProvider.GOOGLE, "subject-1")).willReturn(null)
        given(memberRepository.findByEmail("user@example.com")).willReturn(member(id = 3L))

        assertThatThrownBy { port.findOrCreateByOAuth("google", "subject-1", "user@example.com") }
            .isInstanceOf(OAuthEmailAlreadyRegisteredException::class.java)
        verify(memberRepository, never()).saveAndFlush(anyMember())
    }

    @Test
    fun `getRoles는 회원의 역할 이름 목록을 반환한다`() {
        given(memberRoleRepository.findAllByIdMemberId(1L))
            .willReturn(
                listOf(
                    MemberRole(MemberRoleId(memberId = 1L, role = RoleType.TEACHER)),
                    MemberRole(MemberRoleId(memberId = 1L, role = RoleType.DEVELOPER)),
                ),
            )

        assertThat(port.getRoles(1L)).containsExactly("TEACHER", "DEVELOPER")
    }
}
