package team.inreok.getiserver.domain.application.service

import org.springframework.data.domain.Pageable
import team.inreok.getiserver.domain.application.dto.JobApplicationAdminActionRequest
import team.inreok.getiserver.domain.application.dto.JobApplicationAdminListResponse
import team.inreok.getiserver.domain.application.dto.JobApplicationDraftResponse
import team.inreok.getiserver.domain.application.entity.type.JobApplicationStatus

/** 교사·개발자용 지원서 조회·검토 Service다(Issue #125). 학생 본인 Action(SUBMIT 등)은
 * [JobApplicationService]가 별도로 담당한다 -- 소유권 기반 권한 모델(학생)과 담당자 기반 권한
 * 모델(교사)이 서로 달라 Service를 분리했다. */
interface JobApplicationAdminService {
    /** 모든 교사·개발자가 담당 공고 여부와 무관하게 조회할 수 있다(요구사항 "권한" 절). */
    fun list(
        jobId: Long?,
        status: JobApplicationStatus?,
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
}
