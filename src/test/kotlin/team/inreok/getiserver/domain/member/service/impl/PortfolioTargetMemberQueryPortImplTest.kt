package team.inreok.getiserver.domain.member.service.impl

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.junit.jupiter.MockitoExtension
import team.inreok.getiserver.domain.member.entity.type.MemberStatus
import team.inreok.getiserver.domain.member.entity.type.RoleType
import team.inreok.getiserver.domain.member.repository.MemberRepository

@ExtendWith(MockitoExtension::class)
class PortfolioTargetMemberQueryPortImplTest {
    @Mock
    private lateinit var memberRepository: MemberRepository

    private val port by lazy { PortfolioTargetMemberQueryPortImpl(memberRepository) }

    @Test
    fun `존재하고 ACTIVE STUDENT인 id만 돌려준다`() {
        given(memberRepository.findIdsByIdInAndStatusAndRole(setOf(1L, 2L, 3L), MemberStatus.ACTIVE, RoleType.STUDENT))
            .willReturn(listOf(1L, 2L))

        val result = port.findStudentMemberIds(setOf(1L, 2L, 3L))

        assertThat(result).containsExactlyInAnyOrder(1L, 2L)
    }

    @Test
    fun `비어 있으면 Repository를 호출하지 않고 빈 Set을 돌려준다`() {
        val result = port.findStudentMemberIds(emptySet())

        assertThat(result).isEmpty()
        verifyNoInteractions(memberRepository)
    }
}
