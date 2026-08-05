package team.inreok.getiserver.domain.application.service

import team.inreok.getiserver.domain.application.dto.CreateJobApplicationRequest
import team.inreok.getiserver.domain.application.dto.JobApplicationDraftResponse
import team.inreok.getiserver.domain.application.dto.JobEligibilityResponse
import team.inreok.getiserver.domain.application.dto.SaveJobApplicationDraftRequest

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
}
