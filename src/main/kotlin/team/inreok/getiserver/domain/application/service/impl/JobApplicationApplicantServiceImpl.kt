package team.inreok.getiserver.domain.application.service.impl

import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.application.dto.JobApplicationApplicantListResponse
import team.inreok.getiserver.domain.application.dto.JobApplicationApplicantResponse
import team.inreok.getiserver.domain.application.entity.JobApplication
import team.inreok.getiserver.domain.application.exception.ApplicationReviewForbiddenException
import team.inreok.getiserver.domain.application.exception.JobNotFoundException
import team.inreok.getiserver.domain.application.repository.JobApplicationRepository
import team.inreok.getiserver.domain.application.service.JobApplicationApplicantService
import team.inreok.getiserver.domain.file.link.FileUrlPort
import team.inreok.getiserver.domain.job.query.JobApplicationSnapshotQueryPort
import team.inreok.getiserver.domain.member.query.ApplicationApplicantProfile
import team.inreok.getiserver.domain.member.query.ApplicationApplicantProfileQueryPort

/**
 * [JobApplicationApplicantService]의 구현이다(Issue #137).
 */
@Service
class JobApplicationApplicantServiceImpl(
    private val jobApplicationRepository: JobApplicationRepository,
    private val jobApplicationSnapshotQueryPort: JobApplicationSnapshotQueryPort,
    private val applicationApplicantProfileQueryPort: ApplicationApplicantProfileQueryPort,
    private val fileUrlPort: FileUrlPort,
) : JobApplicationApplicantService {
    @Transactional(readOnly = true)
    override fun list(
        jobId: Long,
        requesterMemberId: Long,
        isDeveloper: Boolean,
        pageable: Pageable,
    ): JobApplicationApplicantListResponse {
        requireManagerOrDeveloper(jobId, requesterMemberId, isDeveloper)

        val page = jobApplicationRepository.findApplicantsOf(jobId, pageable)

        // 지원자마다 Member/File을 개별 조회하면 Page 크기만큼 Query가 늘어난다(N+1). 이번
        // Page에 등장하는 모든 회원 id를 한 번에 모아 배치로 조회한다(JobApplicationExportServiceImpl,
        // InquiryServiceImpl.listAdmin과 동일한 원칙).
        val applicantMemberIds = page.content.map { it.applicantMemberId }.toSet()
        val profiles =
            if (applicantMemberIds.isEmpty()) {
                emptyMap()
            } else {
                applicationApplicantProfileQueryPort.findAllByIds(applicantMemberIds)
            }
        val imageFileIds = profiles.values.mapNotNull { it.profileImageFileId }.toSet()
        val imageUrls =
            if (imageFileIds.isEmpty()) emptyMap() else fileUrlPort.presignedImageUrls(requesterMemberId, imageFileIds)

        return JobApplicationApplicantListResponse(
            content = page.content.map { it.toApplicantResponse(profiles, imageUrls) },
            page = page.number,
            size = page.size,
            totalElements = page.totalElements,
            totalPages = page.totalPages,
            first = page.isFirst,
            last = page.isLast,
        )
    }

    private fun JobApplication.toApplicantResponse(
        profiles: Map<Long, ApplicationApplicantProfile>,
        imageUrls: Map<Long, String>,
    ): JobApplicationApplicantResponse {
        val profile = profiles[applicantMemberId]
        return JobApplicationApplicantResponse(
            applicationId = requireNotNull(id) { "저장된 JobApplication은 id를 가져야 합니다." },
            memberId = applicantMemberId,
            name = profile?.name,
            profileImageUrl = profile?.profileImageFileId?.let { imageUrls[it] },
            cohort = profile?.cohort,
            department = profile?.department,
        )
    }

    // JobApplicationAdminServiceImpl.requireManagerOrDeveloper와 같은 권한 판정이지만 그 Method는
    // 지원서 1건(JobApplication)을 기준으로 판정하는 private Method라 공고(jobId) 기준인 이
    // 요청에는 그대로 재사용할 수 없다 -- JobApplicationExportServiceImpl과 동일하게 공고 기준으로
    // 다시 적었다.
    private fun requireManagerOrDeveloper(
        jobId: Long,
        requesterMemberId: Long,
        isDeveloper: Boolean,
    ) {
        if (isDeveloper) return
        val job = jobApplicationSnapshotQueryPort.findById(jobId) ?: throw JobNotFoundException(jobId)
        val isManager = requesterMemberId == job.createdByMemberId || requesterMemberId == job.managerMemberId
        if (!isManager) throw ApplicationReviewForbiddenException()
    }
}
