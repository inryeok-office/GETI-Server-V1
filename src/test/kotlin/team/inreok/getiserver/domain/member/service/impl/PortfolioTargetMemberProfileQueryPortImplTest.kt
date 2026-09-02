package team.inreok.getiserver.domain.member.service.impl

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.junit.jupiter.MockitoExtension
import team.inreok.getiserver.domain.member.entity.Member
import team.inreok.getiserver.domain.member.entity.type.DepartmentType
import team.inreok.getiserver.domain.member.entity.type.OAuthProvider
import team.inreok.getiserver.domain.member.repository.MemberRepository

@ExtendWith(MockitoExtension::class)
class PortfolioTargetMemberProfileQueryPortImplTest {
    @Mock
    private lateinit var memberRepository: MemberRepository

    private val port by lazy { PortfolioTargetMemberProfileQueryPortImpl(memberRepository) }

    @Test
    fun `존재하는 회원의 이름 기수 학과를 memberId 기준 Map으로 돌려준다`() {
        given(memberRepository.findAllById(setOf(1L, 2L)))
            .willReturn(
                listOf(
                    member(1L, name = "홍길동", cohort = 6, department = DepartmentType.SW_DEVELOPMENT),
                    member(2L, name = null, cohort = null, department = null),
                ),
            )

        val result = port.findProfiles(setOf(1L, 2L))

        assertThat(result).hasSize(2)
        assertThat(result[1L]?.name).isEqualTo("홍길동")
        assertThat(result[1L]?.cohort).isEqualTo(6)
        assertThat(result[1L]?.department).isEqualTo("SW_DEVELOPMENT")
        assertThat(result[2L]?.name).isNull()
        assertThat(result[2L]?.department).isNull()
    }

    @Test
    fun `비어 있으면 Repository를 호출하지 않고 빈 Map을 돌려준다`() {
        val result = port.findProfiles(emptySet())

        assertThat(result).isEmpty()
        verifyNoInteractions(memberRepository)
    }

    private fun member(
        id: Long,
        name: String?,
        cohort: Int?,
        department: DepartmentType?,
    ) = Member(
        oauthProvider = OAuthProvider.DG,
        oauthSubject = "sub-$id",
        email = "member$id@test.dev",
    ).apply {
        this.id = id
        this.name = name
        this.cohort = cohort
        this.department = department
    }
}
