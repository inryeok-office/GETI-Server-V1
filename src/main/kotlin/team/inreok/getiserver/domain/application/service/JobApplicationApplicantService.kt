package team.inreok.getiserver.domain.application.service

import org.springframework.data.domain.Pageable
import team.inreok.getiserver.domain.application.dto.JobApplicationApplicantListResponse

/**
 * 공고별 공개 신청자 목록이다(Application Phase 9, Issue #137). "누가 볼 수 있는가"는
 * `JobApplicationAdminServiceImpl.requireManagerOrDeveloper`/`JobApplicationExportServiceImpl`과
 * 동일하게 해당 공고의 등록자·담당 교사, 또는 개발자로 한정한다(Issue #137 정책 확정).
 */
interface JobApplicationApplicantService {
    /**
     * @throws team.inreok.getiserver.domain.application.exception.JobNotFoundException 공고가 없음
     * @throws team.inreok.getiserver.domain.application.exception.ApplicationReviewForbiddenException
     *   요청자가 이 공고의 등록자·담당 교사·개발자가 아님
     */
    fun list(
        jobId: Long,
        requesterMemberId: Long,
        isDeveloper: Boolean,
        pageable: Pageable,
    ): JobApplicationApplicantListResponse
}
