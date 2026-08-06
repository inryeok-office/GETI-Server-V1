package team.inreok.getiserver.domain.member.query

import org.springframework.modulith.NamedInterface

/**
 * 다른 Domain Module이 학생 지원자 정보를 읽는 유일한 공개 계약이다. 원래 `application`
 * Module이 지원서 초안 자동입력(요구사항 8절)에 필요해 만들었고(Application Epic #75, Issue
 * #78), `program` Module도 학생 지원 가능 여부 판정(재학 여부·학년, GETI-Server Program 도메인
 * 전체 개발 요구사항 10절)에 그대로 재사용한다. 두 Module 모두 이 Interface를 통해서만 Member를
 * 읽고, `Member` Entity나 `MemberRepository`를 직접 참조하지 않는다.
 *
 * 학번(studentNumber)은 포함하지 않는다 — 요구사항 8절이 자동입력 항목으로 요구하지만
 * `members` 실제 스키마에 해당 Column이 없어 이번 Phase 범위에서 제외했다(사용자 확인 완료,
 * `docs/application/application-domain-plan.md` §4 6번, §6.1).
 */
@NamedInterface
interface MemberApplicantSnapshotQueryPort {
    /** 존재하지 않으면 null을 반환한다. */
    fun findById(memberId: Long): MemberApplicantSnapshot?
}

@NamedInterface
data class MemberApplicantSnapshot(
    val memberId: Long,
    val name: String?,
    val email: String,
    val phone: String?,
    /** `AcademicStatus.name` */
    val academicStatus: String?,
    val grade: Int?,
    val cohort: Int?,
    /** `DepartmentType.name` */
    val department: String?,
    val majors: List<String>,
    val techStacks: List<String>,
    val desiredJob: String?,
)
