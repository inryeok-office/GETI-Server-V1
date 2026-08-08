package team.inreok.getiserver.domain.member.query

import org.springframework.modulith.NamedInterface

/**
 * `inquiry` Module이 문의 작성자·담당자를 화면에 보여줄 때 쓰는 공개 계약이다(GETI Inquiry
 * 도메인 개발 요구사항 18절). `inquiry`는 이 Interface를 통해서만 Member를 읽고, `Member`
 * Entity나 `MemberRepository`를 직접 참조하지 않는다.
 *
 * 이미 있는 [MemberApplicantSnapshotQueryPort]를 재사용하지 않고 새로 만든 이유는, 그 계약이
 * `email`/`phone`처럼 문의 응답에는 필요 없는 민감한 필드까지 담고 있어서다(요구사항 19절 "민감한
 * Member 필드를 Inquiry DTO에 추가하지 않는다"). `profileImageFileId`는 Snapshot 자체가 아니라
 * `inquiry`가 [team.inreok.getiserver.domain.file.link.FileUrlPort]로 표시용 URL을 만들 때
 * 넘기는 원본 값이다.
 */
@NamedInterface
interface InquiryMemberSnapshotQueryPort {
    /**
     * 존재하지 않는 id는 결과 Map에서 빠진다. 목록 응답이 여러 명을 함께 보여주므로 배치로
     * 둔다(N+1 방지, 요구사항 46절).
     */
    fun findAllByIds(memberIds: Set<Long>): Map<Long, InquiryMemberSnapshot>
}

@NamedInterface
data class InquiryMemberSnapshot(
    val memberId: Long,
    val name: String?,
    val profileImageFileId: Long?,
    val cohort: Int?,
    /** `DepartmentType.name` */
    val department: String?,
    val isPublic: Boolean,
)
