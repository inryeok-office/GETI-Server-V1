package team.inreok.getiserver.domain.application.service.impl

import team.inreok.getiserver.domain.application.dto.ApplicationAnswer
import team.inreok.getiserver.domain.application.dto.JobApplicationDraftResponse
import team.inreok.getiserver.domain.application.dto.JobApplicationStatusHistoryResponse
import team.inreok.getiserver.domain.application.entity.JobApplication
import team.inreok.getiserver.domain.application.entity.JobApplicationStatusHistory
import tools.jackson.databind.ObjectMapper

/**
 * `JobApplication` Entity를 응답 DTO로 변환한다. 학생용 초안·임시저장·Action 결과
 * (`JobApplicationServiceImpl`)와 교사용 상세·검토 Action 결과(`JobApplicationAdminServiceImpl`,
 * Issue #125)가 모두 같은 모양의 응답을 반환해 이 변환 로직을 공유한다. detekt
 * TooManyFunctions 한도 안에서 각 Service 클래스를 유지하기 위해 순수 함수로 분리했다
 * (JobApplicationEligibility.kt의 computeEligibilityReason과 같은 이유).
 */
fun toJobApplicationDraftResponse(
    objectMapper: ObjectMapper,
    application: JobApplication,
): JobApplicationDraftResponse =
    JobApplicationDraftResponse(
        applicationId = requireNotNull(application.id),
        jobId = application.jobId,
        formId = application.formId,
        formVersion = application.formVersion,
        status = application.status,
        statusReason = application.statusReason,
        contactEmail = application.contactEmail,
        contactPhone = application.contactPhone,
        privacyConsent = application.privacyConsent,
        applicantName = application.applicantName,
        applicantCohort = application.applicantCohort,
        applicantDepartment = application.applicantDepartment,
        applicantMajors = readJsonStringList(objectMapper, application.applicantMajors),
        applicantDesiredJob = application.applicantDesiredJob,
        applicantTechStacks = readJsonStringList(objectMapper, application.applicantTechStacks),
        answers = readJsonAnswers(objectMapper, application.answers),
        submittedAt = application.submittedAt,
        withdrawnAt = application.withdrawnAt,
        createdAt = requireNotNull(application.createdAt),
        updatedAt = requireNotNull(application.updatedAt),
    )

// 학생용(JobApplicationServiceImpl)·교사용(JobApplicationAdminServiceImpl) 이력 조회가 모두
// 같은 모양의 응답을 반환해 이 변환도 함께 공유한다(Issue #133).
fun toJobApplicationStatusHistoryResponse(history: JobApplicationStatusHistory): JobApplicationStatusHistoryResponse =
    JobApplicationStatusHistoryResponse(
        historyId = requireNotNull(history.id),
        fromStatus = history.fromStatus,
        toStatus = history.toStatus,
        action = history.action,
        actorMemberId = history.actorMemberId,
        reason = history.reason,
        createdAt = requireNotNull(history.createdAt),
    )

private fun readJsonStringList(
    objectMapper: ObjectMapper,
    json: String?,
): List<String> {
    if (json.isNullOrBlank()) return emptyList()
    return objectMapper.readValue(json, Array<String>::class.java).toList()
}

private fun readJsonAnswers(
    objectMapper: ObjectMapper,
    json: String,
): List<ApplicationAnswer> {
    if (json.isBlank()) return emptyList()
    return objectMapper.readValue(json, Array<ApplicationAnswer>::class.java).toList()
}
