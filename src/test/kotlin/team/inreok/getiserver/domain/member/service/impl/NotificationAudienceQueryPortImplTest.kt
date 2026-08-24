package team.inreok.getiserver.domain.member.service.impl

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.junit.jupiter.MockitoExtension
import team.inreok.getiserver.domain.member.entity.type.AcademicStatus
import team.inreok.getiserver.domain.member.entity.type.MemberStatus
import team.inreok.getiserver.domain.member.entity.type.RoleType
import team.inreok.getiserver.domain.member.repository.MemberRepository

@ExtendWith(MockitoExtension::class)
class NotificationAudienceQueryPortImplTest {
    @Mock
    private lateinit var memberRepository: MemberRepository

    private val port by lazy { NotificationAudienceQueryPortImpl(memberRepository) }

    @Test
    fun `대상 학년이 있으면 학년 조건을 포함해 조회한다`() {
        given(
            memberRepository.findIdsByStatusAndAcademicStatusAndRoleAndGradeIn(
                status = MemberStatus.ACTIVE,
                academicStatus = AcademicStatus.ENROLLED,
                role = RoleType.STUDENT,
                targetGrades = setOf(3),
            ),
        ).willReturn(listOf(10L, 11L))

        val result = port.findEligibleStudentIds(setOf(3))

        assertThat(result).containsExactly(10L, 11L)
        verify(memberRepository).findIdsByStatusAndAcademicStatusAndRoleAndGradeIn(
            status = MemberStatus.ACTIVE,
            academicStatus = AcademicStatus.ENROLLED,
            role = RoleType.STUDENT,
            targetGrades = setOf(3),
        )
        verifyNoMoreInteractions(memberRepository)
    }

    @Test
    fun `대상 학년이 없으면 학년 조건 없이 전체 재학생을 조회한다`() {
        given(
            memberRepository.findIdsByStatusAndAcademicStatusAndRole(
                status = MemberStatus.ACTIVE,
                academicStatus = AcademicStatus.ENROLLED,
                role = RoleType.STUDENT,
            ),
        ).willReturn(listOf(20L))

        val result = port.findEligibleStudentIds(emptySet())

        assertThat(result).containsExactly(20L)
        verify(memberRepository).findIdsByStatusAndAcademicStatusAndRole(
            status = MemberStatus.ACTIVE,
            academicStatus = AcademicStatus.ENROLLED,
            role = RoleType.STUDENT,
        )
        verifyNoMoreInteractions(memberRepository)
    }
}
