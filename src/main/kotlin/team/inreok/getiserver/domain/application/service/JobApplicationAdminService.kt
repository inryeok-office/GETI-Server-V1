package team.inreok.getiserver.domain.application.service

import org.springframework.data.domain.Pageable
import team.inreok.getiserver.domain.application.dto.JobApplicationAdminActionRequest
import team.inreok.getiserver.domain.application.dto.JobApplicationAdminListResponse
import team.inreok.getiserver.domain.application.dto.JobApplicationDraftResponse
import team.inreok.getiserver.domain.application.dto.JobApplicationStatusHistoryResponse
import team.inreok.getiserver.domain.application.entity.type.JobApplicationStatus
import team.inreok.getiserver.domain.member.entity.type.DepartmentType

/** 교사·개발자용 지원서 조회·검토 Service다(Issue #125). 학생 본인 Action(SUBMIT 등)은
 * [JobApplicationService]가 별도로 담당한다 -- 소유권 기반 권한 모델(학생)과 담당자 기반 권한
 * 모델(교사)이 서로 달라 Service를 분리했다. */
interface JobApplicationAdminService {
    /**
     * 모든 교사·개발자가 담당 공고 여부와 무관하게 조회할 수 있다(요구사항 "권한" 절). 모든
     * Filter는 AND로 조합하고, 지정하지 않은(null) Filter는 적용하지 않는다(Issue #181).
     * [mineOnly]가 true면 [requesterMemberId]가 담당(managerMemberId) 또는 등록
     * (createdByMemberId)한 공고의 지원서만 반환한다.
     */
    fun list(
        jobId: Long?,
        status: JobApplicationStatus?,
        applicantName: String?,
        cohort: Int?,
        department: DepartmentType?,
        companyId: Long?,
        managerMemberId: Long?,
        mineOnly: Boolean,
        requesterMemberId: Long,
        pageable: Pageable,
    ): JobApplicationAdminListResponse

    /** 지원서가 없으면 ApplicationNotFoundException. */
    fun getDetail(applicationId: Long): JobApplicationDraftResponse

    /** 교사 검토 Action(ALLOW_EDIT/REQUEST_REVISION/APPROVE/REJECT)을 수행한다. 지원서가 없으면
     * ApplicationNotFoundException, 해당 공고의 등록자·담당 교사·개발자가 아니면
     * ApplicationReviewForbiddenException, 현재 상태에서 허용되지 않는 Action이면
     * ApplicationActionNotAvailableException. */
    fun executeAction(
        applicationId: Long,
        requesterMemberId: Long,
        isDeveloper: Boolean,
        request: JobApplicationAdminActionRequest,
    ): JobApplicationDraftResponse

    /** 지원서의 상태 변경 이력을 오래된 순으로 반환한다(Issue #133). 담당 공고 여부와 무관하게
     * 모든 교사·개발자가 조회할 수 있다(조회 권한은 list/getDetail과 동일). DRAFT 지원서는
     * getDetail과 동일하게 존재하지 않는 것으로 취급되어 ApplicationNotFoundException. */
    fun getHistory(applicationId: Long): List<JobApplicationStatusHistoryResponse>
}
