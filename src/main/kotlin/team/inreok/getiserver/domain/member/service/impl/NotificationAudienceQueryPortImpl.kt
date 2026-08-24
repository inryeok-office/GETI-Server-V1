package team.inreok.getiserver.domain.member.service.impl

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.member.entity.type.AcademicStatus
import team.inreok.getiserver.domain.member.entity.type.MemberStatus
import team.inreok.getiserver.domain.member.entity.type.RoleType
import team.inreok.getiserver.domain.member.query.NotificationAudienceQueryPort
import team.inreok.getiserver.domain.member.repository.MemberRepository

/** [NotificationAudienceQueryPort]의 구현이다(Issue #191). */
@Service
class NotificationAudienceQueryPortImpl(
    private val memberRepository: MemberRepository,
) : NotificationAudienceQueryPort {
    @Transactional(readOnly = true)
    override fun findEligibleStudentIds(targetGrades: Set<Int>): List<Long> =
        if (targetGrades.isEmpty()) {
            memberRepository.findIdsByStatusAndAcademicStatusAndRole(
                status = MemberStatus.ACTIVE,
                academicStatus = AcademicStatus.ENROLLED,
                role = RoleType.STUDENT,
            )
        } else {
            memberRepository.findIdsByStatusAndAcademicStatusAndRoleAndGradeIn(
                status = MemberStatus.ACTIVE,
                academicStatus = AcademicStatus.ENROLLED,
                role = RoleType.STUDENT,
                targetGrades = targetGrades,
            )
        }
}
