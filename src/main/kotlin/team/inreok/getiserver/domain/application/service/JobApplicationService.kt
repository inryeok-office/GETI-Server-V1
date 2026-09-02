package team.inreok.getiserver.domain.application.service

import org.springframework.data.domain.Pageable
import team.inreok.getiserver.domain.application.dto.CreateJobApplicationRequest
import team.inreok.getiserver.domain.application.dto.JobApplicationActionRequest
import team.inreok.getiserver.domain.application.dto.JobApplicationDraftResponse
import team.inreok.getiserver.domain.application.dto.JobApplicationStatusHistoryResponse
import team.inreok.getiserver.domain.application.dto.JobEligibilityResponse
import team.inreok.getiserver.domain.application.dto.MyJobApplicationListResponse
import team.inreok.getiserver.domain.application.dto.SaveJobApplicationDraftRequest
import team.inreok.getiserver.domain.application.entity.type.JobApplicationStatus

interface JobApplicationService {
    /** 서버가 계산한 지원 가능 여부를 반환한다(요구사항 7절). Job이나 학생 정보를 찾지 못해도
     * 예외를 던지지 않고 JOB_NOT_PUBLISHED/NOT_ENROLLED 계열로 반환한다. */
    fun checkEligibility(
        jobId: Long,
        studentMemberId: Long,
    ): JobEligibilityResponse

    /** 지원서 초안을 생성한다(요구사항 8절). 지원 불가 상태면 JobNotApplicableException,
     * 이미 활성 지원서가 있으면 ActiveApplicationExistsException. */
    fun createDraft(
        jobId: Long,
        studentMemberId: Long,
        request: CreateJobApplicationRequest,
    ): JobApplicationDraftResponse

    /** 지원서를 임시저장한다(요구사항 9절). 본인 소유가 아니면 ApplicationAccessForbiddenException,
     * DRAFT 상태가 아니면 ApplicationActionNotAvailableException. */
    fun saveDraft(
        applicationId: Long,
        studentMemberId: Long,
        request: SaveJobApplicationDraftRequest,
    ): JobApplicationDraftResponse

    /** 학생 지원서 Action(SUBMIT/REQUEST_EDIT/RESUBMIT/WITHDRAW)을 수행한다(Issue #124).
     * 지원서가 없으면 ApplicationNotFoundException, 본인 소유가 아니면
     * ApplicationAccessForbiddenException, 현재 상태에서 허용되지 않는 Action이면
     * ApplicationActionNotAvailableException, SUBMIT/RESUBMIT 시 필수 답변이 비어 있으면
     * ApplicationRequiredAnswerMissingException. */
    fun executeAction(
        applicationId: Long,
        studentMemberId: Long,
        request: JobApplicationActionRequest,
    ): JobApplicationDraftResponse

    /** 본인 지원서의 상태 변경 이력을 오래된 순으로 반환한다(Issue #133). 지원서가 없으면
     * ApplicationNotFoundException, 본인 소유가 아니면 ApplicationAccessForbiddenException. */
    fun getHistory(
        applicationId: Long,
        studentMemberId: Long,
    ): List<JobApplicationStatusHistoryResponse>

    /** 본인 지원 목록을 조회한다(Issue #184). status를 지정하면 그 상태만 필터하고, 지정하지
     * 않으면 DRAFT를 포함한 모든 상태를 반환한다(admin 목록과 달리 본인의 임시저장도 본인에게는
     * 보여야 이어서 작성할 수 있다). */
    fun list(
        studentMemberId: Long,
        status: JobApplicationStatus?,
        pageable: Pageable,
    ): MyJobApplicationListResponse

    /** 본인 지원서 상세를 조회한다(Issue #184). 지원서가 없으면 ApplicationNotFoundException,
     * 본인 소유가 아니면 ApplicationAccessForbiddenException. admin 상세(getDetail)와 달리 DRAFT도
     * 조회할 수 있다(본인의 임시저장 확인 용도). */
    fun getDetail(
        applicationId: Long,
        studentMemberId: Long,
    ): JobApplicationDraftResponse
}
