package team.inreok.getiserver.domain.application.service.impl

import team.inreok.getiserver.domain.application.entity.JobApplication
import team.inreok.getiserver.domain.application.entity.JobApplicationStatusHistory
import team.inreok.getiserver.domain.application.entity.JobApplicationSubmission
import team.inreok.getiserver.domain.application.entity.type.JobApplicationStatus
import team.inreok.getiserver.domain.application.repository.JobApplicationStatusHistoryRepository
import team.inreok.getiserver.domain.application.repository.JobApplicationSubmissionRepository

/**
 * 상태 전이 성공 직후 이력·Snapshot을 남기는 공용 함수다(Issue #133). 학생 Action
 * (`JobApplicationServiceImpl`)과 교사 Action(`JobApplicationAdminServiceImpl`)이 모두
 * 호출하므로, 두 Service 클래스의 Method 개수를 detekt TooManyFunctions 한도 안에서 유지하기
 * 위해 이 파일의 Top-level 함수로 분리했다(JobApplicationEligibility.kt와 같은 이유). 두 Action
 * Enum(`JobApplicationAction`/`JobApplicationAdminAction`)이 서로 달라 [action]은 `.name`
 * 문자열로 받는다. 호출부가 이미 상태 전이(`applyJobApplicationAction`/
 * `applyJobApplicationAdminAction`)와 같은 Transaction 안에서 호출해야 원자성이 보장된다
 * (요구사항 "Transaction 일관성" 절 — 별도 Method로 다시 Transaction을 열지 않는다).
 *
 * 저장된 이력을 반환한다(Issue #193) -- `JobApplicationAdminServiceImpl.executeAction`이 이
 * 이력의 [JobApplicationStatusHistory.id]를 `JobApplicationReviewedEvent.historyId`(Notification
 * Idempotency의 `sourceEventId`)로 쓴다. 같은 지원서가 여러 번 검토되면(REQUEST_REVISION 뒤
 * APPROVE 등) 매번 새 이력 Row가 생겨 id도 매번 달라지므로, 서로 다른 검토 결과가 하나의 알림으로
 * 뭉개지지 않는다. 학생 Action 호출부(`JobApplicationServiceImpl`)는 반환값을 쓰지 않는다.
 */
fun recordStatusHistory(
    jobApplicationStatusHistoryRepository: JobApplicationStatusHistoryRepository,
    application: JobApplication,
    fromStatus: JobApplicationStatus,
    action: String,
    actorMemberId: Long,
    reason: String?,
): JobApplicationStatusHistory =
    jobApplicationStatusHistoryRepository.save(
        JobApplicationStatusHistory(
            applicationId = requireNotNull(application.id),
            fromStatus = fromStatus,
            toStatus = application.status,
            action = action,
            actorMemberId = actorMemberId,
            reason = reason,
        ),
    )

// SUBMIT/RESUBMIT이 성공해 application.answers/submittedAt이 이미 확정된 뒤에만 호출해야 한다
// (요구사항 "Snapshot" 절 — 제출 시점 답변을 그대로 고정).
fun recordSubmissionSnapshot(
    jobApplicationSubmissionRepository: JobApplicationSubmissionRepository,
    application: JobApplication,
) {
    val applicationId = requireNotNull(application.id)
    val nextSubmissionNumber =
        (
            jobApplicationSubmissionRepository
                .findTopByApplicationIdOrderBySubmissionNumberDesc(
                    applicationId,
                )?.submissionNumber
                ?: 0
        ) +
            1
    jobApplicationSubmissionRepository.save(
        JobApplicationSubmission(
            applicationId = applicationId,
            submissionNumber = nextSubmissionNumber,
            formId = application.formId,
            formVersion = application.formVersion,
            answers = application.answers,
            submittedAt = requireNotNull(application.submittedAt),
        ),
    )
}
