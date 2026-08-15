package team.inreok.getiserver.domain.member.service.impl

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import team.inreok.getiserver.domain.member.entity.Member
import team.inreok.getiserver.domain.member.entity.MemberTechStack
import team.inreok.getiserver.domain.member.entity.MemberTechStackId
import team.inreok.getiserver.domain.member.entity.type.MemberStatus
import team.inreok.getiserver.domain.member.entity.type.OAuthProvider
import team.inreok.getiserver.domain.member.repository.MemberRepository
import team.inreok.getiserver.domain.member.repository.MemberTechStackRepository
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class RecommendationMemberProfileQueryPortImplTest {
    @Mock
    private lateinit var memberRepository: MemberRepository

    @Mock
    private lateinit var memberTechStackRepository: MemberTechStackRepository

    private val port by lazy { RecommendationMemberProfileQueryPortImpl(memberRepository, memberTechStackRepository) }

    private fun memberOf(id: Long = 1L): Member =
        Member(
            oauthProvider = OAuthProvider.DG,
            oauthSubject = "subject-$id",
            email = "student$id@example.com",
            status = MemberStatus.ACTIVE,
            profilePublic = true,
        ).apply {
            this.id = id
            grade = 3
        }

    @Test
    fun `존재하지 않는 Member는 null을 반환한다`() {
        given(memberRepository.findById(1L)).willReturn(Optional.empty())

        val result = port.findById(1L)

        assertThat(result).isNull()
    }

    @Test
    fun `Member의 기술 스택 ID를 중복 없이 Set으로 반환한다`() {
        given(memberRepository.findById(1L)).willReturn(Optional.of(memberOf(1L)))
        given(memberTechStackRepository.findAllByIdMemberId(1L)).willReturn(
            listOf(
                MemberTechStack(MemberTechStackId(memberId = 1L, techStackId = 10L)),
                MemberTechStack(MemberTechStackId(memberId = 1L, techStackId = 20L)),
            ),
        )

        val result = port.findById(1L)

        assertThat(result).isNotNull
        assertThat(result!!.memberId).isEqualTo(1L)
        assertThat(result.status).isEqualTo("ACTIVE")
        assertThat(result.grade).isEqualTo(3)
        assertThat(result.techStackIds).containsExactlyInAnyOrder(10L, 20L)
    }
}
