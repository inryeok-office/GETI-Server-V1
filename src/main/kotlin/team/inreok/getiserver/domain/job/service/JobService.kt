package team.inreok.getiserver.domain.job.service

import org.springframework.data.domain.Pageable
import team.inreok.getiserver.domain.job.dto.JobAdminDetailResponse
import team.inreok.getiserver.domain.job.dto.JobAdminListResponse
import team.inreok.getiserver.domain.job.dto.JobCreateRequest
import team.inreok.getiserver.domain.job.dto.JobDetailResponse
import team.inreok.getiserver.domain.job.dto.JobStatusUpdateRequest
import team.inreok.getiserver.domain.job.dto.JobUpdateRequest
import team.inreok.getiserver.domain.job.entity.type.JobStatus

interface JobService {
    /** 관리자 공고 목록. 상태를 생략하면 삭제되지 않은 공고만, 지정하면 해당 상태를 조회한다. */
    fun listForAdmin(
        query: String?,
        status: JobStatus?,
        pageable: Pageable,
        requesterId: Long,
    ): JobAdminListResponse

    /** 공고를 등록하거나 임시저장한다. `status = PUBLISHED`면 게시 필수값을 모두 검증한다. */
    fun create(
        request: JobCreateRequest,
        createdByMemberId: Long,
    ): JobDetailResponse

    /**
     * 전달된 Field만 수정한다. 대상이 이미 게시된 공고라면 수정 결과가 게시 필수값을 계속
     * 만족하는지 다시 검증한다.
     *
     * [requesterId]는 응답의 `company.logoUrl`을 발급할 때 쓰는 요청자 ID다(Issue #92,
     * `CompanyQuery.findActiveSummary`).
     */
    fun update(
        jobId: Long,
        request: JobUpdateRequest,
        requesterId: Long,
    ): JobDetailResponse

    /** 공고 상태를 변경한다. 삭제는 Soft Delete로 처리해 이력을 보존한다. [requesterId]는 [update]와 같다. */
    fun changeStatus(
        jobId: Long,
        request: JobStatusUpdateRequest,
        requesterId: Long,
    ): JobDetailResponse

    /** 관리자용 상세 조회. 모든 상태를 볼 수 있고 조회수를 증가시키지 않는다. [requesterId]는 [update]와 같다. */
    fun getForAdmin(
        jobId: Long,
        requesterId: Long,
    ): JobAdminDetailResponse

    /** 공개 상세 조회. 조회할 때마다 조회수를 1 증가시킨다. [requesterId]는 [update]와 같다. */
    fun getPublicDetail(
        jobId: Long,
        requesterId: Long,
    ): JobDetailResponse
}
