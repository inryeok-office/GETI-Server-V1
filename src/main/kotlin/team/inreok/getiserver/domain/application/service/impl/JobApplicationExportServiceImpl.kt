package team.inreok.getiserver.domain.application.service.impl

import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.application.dto.ApplicationAnswer
import team.inreok.getiserver.domain.application.entity.JobApplicationSubmission
import team.inreok.getiserver.domain.application.exception.ApplicationReviewForbiddenException
import team.inreok.getiserver.domain.application.exception.JobNotFoundException
import team.inreok.getiserver.domain.application.repository.JobApplicationRepository
import team.inreok.getiserver.domain.application.repository.JobApplicationSubmissionRepository
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
    private val jobApplicationSnapshotQueryPort: JobApplicationSnapshotQueryPort,
    private val fileLinkPort: FileLinkPort,
    private val fileArchivePort: FileArchivePort,
    private val objectMapper: ObjectMapper,
) : JobApplicationExportService {
    @Transactional(readOnly = true)
    override fun buildExportEntries(
        jobId: Long,
        requesterMemberId: Long,
        isDeveloper: Boolean,
    ): List<FileArchiveEntry> {
        requireManagerOrDeveloper(jobId, requesterMemberId, isDeveloper)

        // Issue #138 요구사항 "현재 지원서가 아닌 제출 Snapshot 기준" -- 제출된(비 DRAFT) 지원서마다
        // 가장 최근 제출 Snapshot(JobApplicationSubmission, Issue #133)의 답변에 담긴 fileId를
        // 수집한다. Submission의 answers는 제출 시점에 고정되므로, 이후 학생이 saveDraft로 답변을
        // 바꾸거나 재제출로 File 연결이 해제되어도(JobApplicationFileSync의 "전체 해제 후 재연결")
        // 이 Snapshot이 가리키는 fileId 자체는 바뀌지 않는다. 연결이 해제된 File도 삭제되지는
        // 않아(status는 UPLOADED로 돌아갈 뿐) isVisible()은 계속 true이므로 여전히 내려받을 수
        // 있다(FileArchivePort.writeZip이 이 판정을 담당).
        val applications = jobApplicationRepository.search(jobId, null, Pageable.unpaged()).content

        // 지원서마다 단건 조회를 반복하면 지원자 수만큼 Query가 느는 N+1이 되므로(PR #157
        // 코드리뷰 반영), applicationId 전체에 대해 최신 제출 Snapshot을 한 Query로 배치 조회한다.
        val applicationIds = applications.mapNotNull { it.id }.toSet()
        val latestSubmissionByApplicationId =
            jobApplicationSubmissionRepository
                .findLatestByApplicationIdIn(applicationIds)
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
