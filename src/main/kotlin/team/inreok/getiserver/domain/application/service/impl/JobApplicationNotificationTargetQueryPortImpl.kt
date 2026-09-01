package team.inreok.getiserver.domain.application.service.impl

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.application.query.JobApplicationNotificationTargetQueryPort
import team.inreok.getiserver.domain.application.query.JobApplicationNotificationTargetSnapshot
import team.inreok.getiserver.domain.application.repository.JobApplicationRepository

/**
 * 다른 Domain Module(notification)에 공개된 조회 계약
 * ([JobApplicationNotificationTargetQueryPort])의 구현이다. `ProgramFormLinkQueryPortImpl`과 같은
 * 이유로 Service 구현에 합치지 않고 분리한다.
 */
@Service
class JobApplicationNotificationTargetQueryPortImpl(
    private val jobApplicationRepository: JobApplicationRepository,
) : JobApplicationNotificationTargetQueryPort {
    @Transactional(readOnly = true)
    override fun findAllByIds(applicationIds: Set<Long>): Map<Long, JobApplicationNotificationTargetSnapshot> {
        if (applicationIds.isEmpty()) return emptyMap()
        return jobApplicationRepository
            .findAllById(applicationIds)
            .mapNotNull { application ->
                val applicationId = application.id ?: return@mapNotNull null
                applicationId to
                    JobApplicationNotificationTargetSnapshot(
                        applicationId = applicationId,
                        applicantMemberId = application.applicantMemberId,
                    )
            }.toMap()
    }
}
