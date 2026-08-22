package team.inreok.getiserver.domain.application.service.impl

import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.application.dto.ApplicationAnswer
import team.inreok.getiserver.domain.application.dto.ApplicationExportMaterialType
import team.inreok.getiserver.domain.application.entity.JobApplicationSubmission
import team.inreok.getiserver.domain.application.exception.ApplicationReviewForbiddenException
import team.inreok.getiserver.domain.application.exception.JobNotFoundException
import team.inreok.getiserver.domain.application.repository.FormVersionRepository
import team.inreok.getiserver.domain.application.repository.JobApplicationRepository
import team.inreok.getiserver.domain.application.repository.JobApplicationSubmissionRepository
import team.inreok.getiserver.domain.application.service.ApplicationAnswerExportRow
import team.inreok.getiserver.domain.application.service.ApplicationAnswersExportData
import team.inreok.getiserver.domain.application.service.ApplicationExportDocumentWriter
import team.inreok.getiserver.domain.application.service.ApplicationProfileExportData
import team.inreok.getiserver.domain.application.service.JobApplicationExport
import team.inreok.getiserver.domain.application.service.JobApplicationExportService
import team.inreok.getiserver.domain.file.archive.FileArchiveEntry
import team.inreok.getiserver.domain.file.archive.FileArchivePort
import team.inreok.getiserver.domain.file.link.FileLinkPort
import team.inreok.getiserver.domain.job.query.JobApplicationSnapshotQueryPort
import tools.jackson.databind.ObjectMapper
import java.io.OutputStream

/**
 * [JobApplicationExportService]의 구현이다(Issue #138).
 */
@Service
class JobApplicationExportServiceImpl(
    private val jobApplicationRepository: JobApplicationRepository,
    private val jobApplicationSubmissionRepository: JobApplicationSubmissionRepository,
    private val formVersionRepository: FormVersionRepository,
    private val jobApplicationSnapshotQueryPort: JobApplicationSnapshotQueryPort,
    private val fileLinkPort: FileLinkPort,
    private val fileArchivePort: FileArchivePort,
    private val objectMapper: ObjectMapper,
    private val documentWriter: ApplicationExportDocumentWriter,
) : JobApplicationExportService {
    @Transactional(readOnly = true)
    override fun buildExportMaterials(
        jobId: Long,
        requesterMemberId: Long,
        isDeveloper: Boolean,
        applicationIds: List<Long>?,
        materialTypes: Set<ApplicationExportMaterialType>,
    ): JobApplicationExport {
        requireManagerOrDeveloper(jobId, requesterMemberId, isDeveloper)
        if (applicationIds != null && applicationIds.isEmpty()) return JobApplicationExport(emptyList(), emptyList())

        val applications = findApplications(jobId, applicationIds)
        val applicationIdsInResult = applications.mapNotNull { it.id }.toSet()
        if (applicationIdsInResult.isEmpty()) return JobApplicationExport(emptyList(), emptyList())

        val latestSubmissionByApplicationId =
            jobApplicationSubmissionRepository
                .findLatestByApplicationIdIn(applicationIdsInResult)
                .associateBy { it.applicationId }
        val submissions = latestSubmissionByApplicationId.values.toList()
        val formVersions =
            if (ApplicationExportMaterialType.ANSWERS in materialTypes) {
                formVersionRepository
                    .findByFormIdIn(submissions.mapNotNull { it.formId }.toList())
                    .associateBy { it.formId to it.version }
            } else {
                emptyMap()
            }

        val fileEntries = mutableListOf<FileArchiveEntry>()
        val contentEntries = mutableListOf<team.inreok.getiserver.domain.file.archive.FileArchiveContentEntry>()
        applications.forEach { application ->
            val applicationId = requireNotNull(application.id)
            val submission = latestSubmissionByApplicationId[applicationId] ?: return@forEach
            if (ApplicationExportMaterialType.PROFILE in materialTypes) {
                contentEntries +=
                    documentWriter.writeProfile(
                        ApplicationProfileExportData(
                            applicationId = applicationId,
                            applicantName = application.applicantName,
                            contactEmail = application.contactEmail,
                            contactPhone = application.contactPhone,
                            applicantCohort = application.applicantCohort,
                            applicantDepartment = application.applicantDepartment,
                        ),
                    )
            }
            if (ApplicationExportMaterialType.ANSWERS in materialTypes) {
                contentEntries +=
                    documentWriter.writeAnswers(
                        ApplicationAnswersExportData(
                            applicationId = applicationId,
                            rows = answerRows(submission, formVersions[submission.formId to submission.formVersion]),
                        ),
                    )
            }
            if (ApplicationExportMaterialType.ATTACHMENTS in materialTypes) {
                val fileIds = fileIdsOf(submission)
                if (fileIds.isNotEmpty()) {
                    val snapshots = fileLinkPort.snapshotsOf(fileIds.toSet())
                    val applicantLabel = application.applicantName ?: "지원자$applicationId"
                    fileIds.forEach { fileId ->
                        snapshots[fileId]?.let { snapshot ->
                            fileEntries +=
                                FileArchiveEntry(
                                    fileId = fileId,
                                    displayName =
                                        "application-${applicationId}_${applicantLabel}_${snapshot.originalName}",
                                )
                        }
                    }
                }
            }
        }
        return JobApplicationExport(fileEntries, contentEntries)
    }

    @Transactional(readOnly = true)
    override fun buildExportEntries(
        jobId: Long,
        requesterMemberId: Long,
        isDeveloper: Boolean,
        applicationIds: List<Long>?,
    ): List<FileArchiveEntry> {
        requireManagerOrDeveloper(jobId, requesterMemberId, isDeveloper)

        // Issue #138 요구사항 "현재 지원서가 아닌 제출 Snapshot 기준" -- 제출된(비 DRAFT) 지원서마다
        // 가장 최근 제출 Snapshot(JobApplicationSubmission, Issue #133)의 답변에 담긴 fileId를
        // 수집한다. Submission의 answers는 제출 시점에 고정되므로, 이후 학생이 saveDraft로 답변을
        // 바꾸거나 재제출로 File 연결이 해제되어도(JobApplicationFileSync의 "전체 해제 후 재연결")
        // 이 Snapshot이 가리키는 fileId 자체는 바뀌지 않는다. 연결이 해제된 File도 삭제되지는
        // 않아(status는 UPLOADED로 돌아갈 뿐) isVisible()은 계속 true이므로 여전히 내려받을 수
        // 있다(FileArchivePort.writeZip이 이 판정을 담당).
        //
        // Issue #203: applicationIds가 지정되면 이 공고의 전체 지원자 중 선택된 지원서만 남긴다.
        // In-Memory Filter가 아니라 search()에 그대로 전달해 DB 수준(JPQL)에서 걸러낸다(PR #211의
        // hasJobFilter/jobIds와 동일한 관례). search()가 이미 jobId로 걸러낸 결과와 AND로
        // 결합되므로, 다른 공고 소속이거나 존재하지 않는 ID는 이 교집합에서 자연히 빠진다(정책
        // 확정: 오류 없이 무시).
        //
        // applicationIds가 null이 아니면서 비어 있으면(예: ?applicationIds= 처럼 Query Parameter
        // 값이 빈 문자열이면 Spring이 크기 0인 List로 Binding한다) 무엇을 선택해도 일치할 수 없어
        // 결과가 항상 빈 목록이다. `a.id IN ()`으로 DB에 다녀오지 않고 바로 빈 목록으로 대응한다
        // (JobApplicationAdminServiceImpl.list의 hasJobFilter && filterJobIds.isEmpty() Guard와
        // 동일한 관례, PR #211 코드리뷰 반영).
        val applications =
            if (applicationIds != null && applicationIds.isEmpty()) {
                emptyList()
            } else {
                jobApplicationRepository
                    .search(
                        jobId = jobId,
                        status = null,
                        hasApplicantName = false,
                        applicantName = "",
                        cohort = null,
                        department = null,
                        hasJobFilter = false,
                        // JobApplicationAdminServiceImpl.filterJobIds와 동일하게 emptySet()으로
                        // 통일한다(PR #211 코드리뷰 반영 -- Collection 타입이 emptyList()/emptySet()로
                        // 갈라지면 Mockito Stub이 값 일치로 매칭돼 한쪽만 나중에 고치면 조용히 깨진다).
                        jobIds = emptySet(),
                        hasApplicationIds = applicationIds != null,
                        applicationIds = applicationIds ?: emptySet(),
                        pageable = Pageable.unpaged(),
                    ).content
            }

        // 지원서마다 단건 조회를 반복하면 지원자 수만큼 Query가 느는 N+1이 되므로(PR #157
        // 코드리뷰 반영), applicationId 전체에 대해 최신 제출 Snapshot을 한 Query로 배치 조회한다.
        // Issue #203으로 추가된 파라미터 applicationIds(요청 Filter)와 이름이 겹치지 않게
        // matchedApplicationIds로 구분한다(search()가 이미 필터링해 반환한 결과의 id 집합이라
        // 요청 Filter와는 다른 값이다 -- 같은 이름이면 이 함수를 이후에 수정할 때 요청 Filter를
        // 다시 참조하려다 조용히 이 값을 대신 읽는 실수로 이어질 수 있다).
        val matchedApplicationIds = applications.mapNotNull { it.id }.toSet()
        val latestSubmissionByApplicationId =
            jobApplicationSubmissionRepository
                .findLatestByApplicationIdIn(matchedApplicationIds)
                .associateBy { it.applicationId }
        val fileIdsByApplication =
            applications
                .associateWith { fileIdsOf(latestSubmissionByApplicationId[it.id]) }
                .filterValues { it.isNotEmpty() }
        if (fileIdsByApplication.isEmpty()) return emptyList()

        val allFileIds = fileIdsByApplication.values.flatten().toSet()
        val snapshotsByFileId = fileLinkPort.snapshotsOf(allFileIds)

        return fileIdsByApplication.flatMap { (application, fileIds) ->
            // applicantName은 Column상 nullable이다(초안 생성 시점 Snapshot). 비어 있어도 Entry
            // 이름 자체가 만들어지지 않는 일은 없게 지원서 ID로 대체한다.
            val applicantLabel = application.applicantName ?: "지원자${application.id}"
            fileIds.mapNotNull { fileId ->
                val snapshot = snapshotsByFileId[fileId] ?: return@mapNotNull null
                FileArchiveEntry(fileId = fileId, displayName = "${applicantLabel}_${snapshot.originalName}")
            }
        }
    }

    // FileArchivePort.writeZip의 얇은 위임이다. 별도 Transaction을 열지 않는다 -- Storage
    // Streaming은 느린 외부 I/O라 Transaction 밖에서 실행해야 한다(buildExportEntries와 분리한
    // 이유는 JobApplicationExportService KDoc 참고).
    override fun writeZip(
        entries: List<FileArchiveEntry>,
        outputStream: OutputStream,
    ) {
        // 대상 fileId가 하나도 없어도(entries가 비어 있어도) 여기서 막지 않는다 --
        // FileArchivePort.writeZip이 같은 상황(FileArchiveEmptyException)과 개수·용량 상한
        // 초과를 이미 판정하므로 같은 검증을 중복하지 않는다(Issue #154 참고).
        fileArchivePort.writeZip(entries, outputStream)
    }

    override fun writeZip(
        export: JobApplicationExport,
        outputStream: OutputStream,
    ) {
        fileArchivePort.writeZip(export.fileEntries, export.contentEntries, outputStream)
    }

    private fun findApplications(
        jobId: Long,
        applicationIds: List<Long>?,
    ) = jobApplicationRepository
        .search(
            jobId = jobId,
            status = null,
            hasApplicantName = false,
            applicantName = "",
            cohort = null,
            department = null,
            hasJobFilter = false,
            jobIds = emptySet(),
            hasApplicationIds = applicationIds != null,
            applicationIds = applicationIds ?: emptySet(),
            pageable = Pageable.unpaged(),
        ).content

    private fun answerRows(
        submission: JobApplicationSubmission,
        formVersion: team.inreok.getiserver.domain.application.entity.FormVersion?,
    ): List<ApplicationAnswerExportRow> {
        if (formVersion == null) return emptyList()
        val schemaByKey =
            objectMapper
                .readValue(
                    formVersion.schemaData,
                    Array<team.inreok.getiserver.domain.application.dto.FormFieldSchema>::class.java,
                ).associateBy { it.key }
        return objectMapper
            .readValue(submission.answers, Array<ApplicationAnswer>::class.java)
            .mapNotNull { answer ->
                val schema = schemaByKey[answer.fieldId] ?: return@mapNotNull null
                if (schema.type == team.inreok.getiserver.domain.application.entity.type.FormFieldType.FILE) {
                    return@mapNotNull null
                }
                ApplicationAnswerExportRow(
                    fieldId = answer.fieldId,
                    question = schema.label,
                    answer = answer.value?.let { if (it.isString) it.asString() else it.toString() }.orEmpty(),
                )
            }.sortedBy { schemaByKey.getValue(it.fieldId).order }
    }

    private fun fileIdsOf(submission: JobApplicationSubmission?): List<Long> {
        if (submission == null || submission.answers.isBlank()) return emptyList()
        return objectMapper
            .readValue(submission.answers, Array<ApplicationAnswer>::class.java)
            .flatMap { it.fileIds.orEmpty() }
            .distinct()
    }

    // JobApplicationAdminServiceImpl.requireManagerOrDeveloper와 같은 권한 판정이지만(Issue #138
    // 요구사항 "Phase 4의 requireManagerOrDeveloper와 동일한 권한 판정 재사용"), 그 Method는
    // 지원서 1건(JobApplication)을 기준으로 판정하는 private Method라 공고(jobId) 기준인 이
    // 요청에는 그대로 재사용할 수 없다 -- 같은 판정 로직을 공고 기준으로 다시 적었다.
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
