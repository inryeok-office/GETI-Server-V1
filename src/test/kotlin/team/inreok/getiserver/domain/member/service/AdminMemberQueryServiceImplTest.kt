package team.inreok.getiserver.domain.member.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import team.inreok.getiserver.domain.member.entity.Member
import team.inreok.getiserver.domain.member.entity.type.MemberStatus
import team.inreok.getiserver.domain.member.entity.type.OAuthProvider
import team.inreok.getiserver.domain.member.entity.type.RoleType
import team.inreok.getiserver.domain.member.repository.MemberRepository
import team.inreok.getiserver.domain.member.service.impl.AdminMemberQueryServiceImpl

@ExtendWith(MockitoExtension::class)
class AdminMemberQueryServiceImplTest {
    @Mock
    private lateinit var memberRepository: MemberRepository

    private val service by lazy { AdminMemberQueryServiceImpl(memberRepository) }

    @Test
    fun `ACTIVE인 해당 Role 회원을 memberId와 name만 담아 순서대로 돌려준다`() {
        given(memberRepository.findAllByStatusAndRoleOrderByName(MemberStatus.ACTIVE, RoleType.TEACHER))
            .willReturn(listOf(teacher(id = 1L, name = "강교사"), teacher(id = 2L, name = "김교사")))

        val response = service.listActiveMembersByRole(RoleType.TEACHER)

        assertThat(response.members).hasSize(2)
        assertThat(response.members[0].memberId).isEqualTo(1L)
        assertThat(response.members[0].name).isEqualTo("강교사")
        assertThat(response.members[1].memberId).isEqualTo(2L)
        assertThat(response.members[1].name).isEqualTo("김교사")
    }

    @Test
    fun `대상이 없으면 빈 목록을 돌려준다`() {
        given(memberRepository.findAllByStatusAndRoleOrderByName(MemberStatus.ACTIVE, RoleType.TEACHER))
            .willReturn(emptyList())

        val response = service.listActiveMembersByRole(RoleType.TEACHER)

        assertThat(response.members).isEmpty()
    }

    private fun teacher(
        id: Long,
        name: String,
    ) = Member(
        oauthProvider = OAuthProvider.GOOGLE,
        oauthSubject = "sub-$id",
        email = "teacher$id@school.example",
        status = MemberStatus.ACTIVE,
    ).apply {
        this.id = id
        this.name = name
    }
}
