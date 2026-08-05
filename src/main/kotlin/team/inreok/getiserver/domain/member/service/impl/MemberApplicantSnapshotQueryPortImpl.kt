package team.inreok.getiserver.domain.member.service.impl

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.member.entity.Member
import team.inreok.getiserver.domain.member.query.MemberApplicantSnapshot
import team.inreok.getiserver.domain.member.query.MemberApplicantSnapshotQueryPort
import team.inreok.getiserver.domain.member.repository.MemberRepository
import team.inreok.getiserver.domain.member.service.MemberSelectionQueryService
import tools.jackson.databind.ObjectMapper

/**
 * `application` Module에 공개된 조회 계약([MemberApplicantSnapshotQueryPort])의 구현이다.
 * 전공/기술스택 이름 조회는 같은 Module 안의 [MemberSelectionQueryService]를 그대로 재사용한다
 * (그 Service 자체를 Named Interface로 공개하지 않고, 이 Port 하나로 좁혀서 공개한다).
 */
@Service
class MemberApplicantSnapshotQueryPortImpl(
    private val memberRepository: MemberRepository,
    private val memberSelectionQueryService: MemberSelectionQueryService,
    private val objectMapper: ObjectMapper,
) : MemberApplicantSnapshotQueryPort {
    @Transactional(readOnly = true)
    override fun findById(memberId: Long): MemberApplicantSnapshot? {
        val member = memberRepository.findById(memberId).orElse(null) ?: return null
        return MemberApplicantSnapshot(
            memberId = requireNotNull(member.id) { "저장된 Member는 id를 가져야 합니다." },
            name = member.name,
            email = member.email,
            phone = member.phoneNumber,
            academicStatus = member.academicStatus?.name,
            grade = member.grade,
            cohort = member.cohort,
            department = member.department?.name,
            majors = memberSelectionQueryService.getMajorNames(memberId),
            techStacks = memberSelectionQueryService.getTechStackNames(memberId),
            desiredJob = readDesiredJob(member),
        )
    }

    private fun readDesiredJob(member: Member): String? {
        val json = member.desiredPositions
        if (json.isNullOrBlank()) return null
        return objectMapper.readValue(json, Array<String>::class.java).toList().firstOrNull()
    }
}
