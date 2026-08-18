package team.inreok.getiserver.domain.member.service.impl

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.junit.jupiter.MockitoExtension
import team.inreok.getiserver.domain.member.entity.type.AcademicStatus
import team.inreok.getiserver.domain.member.entity.type.MemberStatus
import team.inreok.getiserver.domain.member.entity.type.RoleType
import team.inreok.getiserver.domain.member.repository.MemberRepository

@ExtendWith(MockitoExtension::class)
class RecommendationAudienceQueryPortImplTest {
    @Mock
    private lateinit var memberRepository: MemberRepository

    private val port by lazy { RecommendationAudienceQueryPortImpl(memberRepository) }

    @Test
    fun `memberIds가 비어 있으면 Repository를 호출하지 않고 빈 목록을 반환한다`() {
        val result = port.filterEligibleStudentIds(emptyList())

        assertThat(result).isEmpty()
        verifyNoInteractions(memberRepository)
    }

    @Test
    fun `ACTIVE STUDENT ENROLLED 조건으로 Repository에 위임하고 결과를 그대로 반환한다`() {
        val memberIds = listOf(1L, 2L, 3L)
        given(
            memberRepository.findIdsByIdInAndStatusAndAcademicStatusAndRole(
                memberIds,
                MemberStatus.ACTIVE,
                AcademicStatus.ENROLLED,
                RoleType.STUDENT,
            ),
        ).willReturn(listOf(1L, 3L))

        val result = port.filterEligibleStudentIds(memberIds)

        assertThat(result).containsExactly(1L, 3L)
    }
}
