package team.inreok.getiserver.domain.member.query

import org.springframework.modulith.NamedInterface

/**
 * `application` Module이 Phase 9(공고별 지원자 목록, Issue #137)에서 지원자를 화면에 보여줄 때
 * 읽는 공개 계약이다. `application`은 이 Interface를 통해서만 지원자 표시 정보를 읽고, `Member`
 * Entity나 `MemberRepository`를 직접 참조하지 않는다.
 *
 * 이 목록은 대상 공고의 등록자·담당 교사·개발자만 조회할 수 있다(Issue #137 정책 확정 코멘트) --
 * 항상 [team.inreok.getiserver.domain.member.access.PrivilegedProfileViewer]가 비공개 프로필까지
 * 허용하는 대상과 같은 Role이므로, 이 Snapshot은 `profilePublic` 여부로 값을 가리지 않는다.
 *
 * 이미 있는 [MemberApplicantSnapshotQueryPort]를 재사용하지 않는 이유는, 그 계약이 지원서
 * 자동입력(요구사항 8절)에 필요한 email/phone/academicStatus/majors/techStacks 같은 필드까지
 * 담고 있어서다 -- 이 목록은 최소 정보(이름·프로필 사진·기수·학과)만 노출한다(Issue #137 "최소
 * 정보만 노출" 원칙, 사용자 확인 완료). [InquiryMemberSnapshotQueryPort]와 형태가 같지만 그
 * 계약은 `inquiry` 전용으로 문서화되어 있어 그대로 재사용하지 않고 새로 둔다.
 */
@NamedInterface
interface ApplicationApplicantProfileQueryPort {
    /**
     * 존재하지 않는 id는 결과 Map에서 빠진다. 목록 응답이 여러 지원자를 함께 보여주므로 배치로
     * 둔다(N+1 방지).
     */
    fun findAllByIds(memberIds: Set<Long>): Map<Long, ApplicationApplicantProfile>
}

@NamedInterface
data class ApplicationApplicantProfile(
    val memberId: Long,
    val name: String?,
    /** 표시용 URL이 아니라 원본 File ID다 -- URL 변환은 `domain.file.link.FileUrlPort`가 담당한다. */
    val profileImageFileId: Long?,
    val cohort: Int?,
    /** `DepartmentType.name` */
    val department: String?,
)
