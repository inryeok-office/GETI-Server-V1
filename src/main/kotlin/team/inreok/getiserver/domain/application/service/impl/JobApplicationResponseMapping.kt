package team.inreok.getiserver.domain.application.service.impl

import team.inreok.getiserver.domain.application.dto.ApplicationAnswer
import team.inreok.getiserver.domain.application.dto.FormFieldResponse
import team.inreok.getiserver.domain.application.dto.JobApplicationDraftResponse
import team.inreok.getiserver.domain.application.dto.JobApplicationFileResponse
import team.inreok.getiserver.domain.application.dto.JobApplicationStatusHistoryResponse
import team.inreok.getiserver.domain.application.entity.JobApplication
import team.inreok.getiserver.domain.application.entity.JobApplicationStatusHistory
import team.inreok.getiserver.domain.application.repository.FormVersionRepository
import team.inreok.getiserver.domain.file.link.FileSnapshot
import tools.jackson.databind.ObjectMapper

/**
 * `JobApplication` Entity를 응답 DTO로 변환한다. 학생용 초안·임시저장·Action 결과
 * (`JobApplicationServiceImpl`)와 교사용 상세·검토 Action 결과(`JobApplicationAdminServiceImpl`,
 * Issue #125)가 모두 같은 모양의 응답을 반환해 이 변환 로직을 공유한다. detekt
 * TooManyFunctions 한도 안에서 각 Service 클래스를 유지하기 위해 순수 함수로 분리했다
 * (JobApplicationEligibility.kt의 computeEligibilityReason과 같은 이유).
 *
 * [files]는 호출부가 `FileLinkPort.linkedFilesOf(FileOwnerType.JOB_APPLICATION, applicationId)`로
 * 미리 조회해 전달한다(Issue #134) -- 이 함수를 순수 함수로 유지하기 위해 Port 호출 자체는
 * 여기서 하지 않는다. 방금 생성된 초안처럼 애초에 연결된 파일이 있을 수 없는 호출부는 빈 목록을
 * 그대로 넘긴다(불필요한 조회를 피함, `InquiryServiceImpl.create`와 동일한 판단).
 *
 * [jobTitle]/[companyName]/[managerMemberId]/[managerName]은 교사·개발자용 조회
 * (`JobApplicationAdminServiceImpl`, Issue #172)에서만 실제 값을 채워 넘긴다 -- 학생용 초안·Action
 * 결과(`JobApplicationServiceImpl`)는 이 함수를 그대로 재사용하되 기본값 null을 그대로 두어, 이번
 * Issue 범위가 아닌 학생 조회 경로에 불필요한 Job/Company/Member 조회를 추가하지 않는다.
 *
 * [availableActions]는 반대로 학생 본인 상세 조회(`JobApplicationServiceImpl.getDetail`, Issue
 * #184)에서만 채워 넘긴다 -- `availableStudentActionNames`(`JobApplicationServiceImpl.kt`)가 계산하는
 * "학생이 다음에 수행할 수 있는 Action" 목록이라 교사·개발자용 조회에는 의미가 없다.
 *
 * [questions]는 학생용 응답 호출부가 지원서에 저장된 `formId`/`formVersion`으로 조회해 넘긴다.
 * 교사·개발자 응답은 기존 계약을 유지하기 위해 기본값(emptyList)을 사용한다.
 */
fun toJobApplicationDraftResponse(
    objectMapper: ObjectMapper,
    application: JobApplication,
    files: List<FileSnapshot>,
    jobTitle: String? = null,
    companyName: String? = null,
    managerMemberId: Long? = null,
    managerName: String? = null,
    availableActions: List<String> = emptyList(),
    questions: List<FormFieldResponse> = emptyList(),
): JobApplicationDraftResponse =
    JobApplicationDraftResponse(
        applicationId = requireNotNull(application.id),
        jobId = application.jobId,
        jobTitle = jobTitle,
        companyName = companyName,
        managerMemberId = managerMemberId,
        managerName = managerName,
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
        files = files.map(FileSnapshot::toJobApplicationFileResponse),
        submittedAt = application.submittedAt,
        withdrawnAt = application.withdrawnAt,
        createdAt = requireNotNull(application.createdAt),
        updatedAt = requireNotNull(application.updatedAt),
        availableActions = availableActions,
        questions = questions,
    )

/**
 * 지원서가 생성될 당시 저장한 Form Version의 문항 구조를 반환한다. Form의 currentVersion이나
 * 최신 Version을 조회하지 않아, 이후 Form이 수정되어도 기존 지원서의 Schema Snapshot을 유지한다.
 * Form ID와 Version 중 하나만 누락된 데이터는 Snapshot 정합성 오류로 드러낸다.
 */
internal fun formFieldResponsesOf(
    objectMapper: ObjectMapper,
    formVersionRepository: FormVersionRepository,
    application: JobApplication,
): List<FormFieldResponse> {
    if (application.formId == null && application.formVersion == null) return emptyList()

    val formId = requireNotNull(application.formId) { "지원서에 Form ID가 없습니다." }
    val formVersion = requireNotNull(application.formVersion) { "지원서에 Form Version이 없습니다." }
    val version =
        formVersionRepository.findByFormIdAndVersion(formId, formVersion)
            ?: error("지원서에 저장된 Form Version을 찾을 수 없습니다.")

    return readFormFieldResponses(objectMapper, version.schemaData)
}

private fun FileSnapshot.toJobApplicationFileResponse(): JobApplicationFileResponse =
    JobApplicationFileResponse(
        fileId = fileId,
        originalName = originalName,
        contentType = contentType,
        size = size,
        downloadUrl = "/api/v1/files/$fileId/download",
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
