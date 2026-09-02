package team.inreok.getiserver.domain.portfolio.service.impl

import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.file.archive.FileArchiveEntry
import team.inreok.getiserver.domain.file.archive.FileArchivePort
import team.inreok.getiserver.domain.file.entity.type.FileOwnerType
import team.inreok.getiserver.domain.file.link.FileLinkPort
import team.inreok.getiserver.domain.file.link.FileSnapshot
import team.inreok.getiserver.domain.member.query.PortfolioTargetMemberProfile
import team.inreok.getiserver.domain.member.query.PortfolioTargetMemberProfileQueryPort
import team.inreok.getiserver.domain.portfolio.dto.PortfolioSubmissionStatusListResponse
import team.inreok.getiserver.domain.portfolio.dto.PortfolioSubmissionStatusResponse
import team.inreok.getiserver.domain.portfolio.entity.PortfolioSubmission
import team.inreok.getiserver.domain.portfolio.entity.type.PortfolioMaterialType
import team.inreok.getiserver.domain.portfolio.entity.type.PortfolioSubmissionStatus
import team.inreok.getiserver.domain.portfolio.exception.NoSubmissionsToExportException
import team.inreok.getiserver.domain.portfolio.exception.PortfolioRequestNotFoundException
import team.inreok.getiserver.domain.portfolio.repository.PortfolioRequestRepository
import team.inreok.getiserver.domain.portfolio.repository.PortfolioRequestTargetRepository
import team.inreok.getiserver.domain.portfolio.repository.PortfolioSubmissionRepository
import team.inreok.getiserver.domain.portfolio.service.PortfolioSubmissionAdminService
import java.io.OutputStream

@Service
class PortfolioSubmissionAdminServiceImpl(
    private val requestRepository: PortfolioRequestRepository,
    private val targetRepository: PortfolioRequestTargetRepository,
    private val submissionRepository: PortfolioSubmissionRepository,
    private val profileQueryPort: PortfolioTargetMemberProfileQueryPort,
    private val fileLinkPort: FileLinkPort,
    private val fileArchivePort: FileArchivePort,
) : PortfolioSubmissionAdminService {
    @Transactional(readOnly = true)
    override fun getSubmissionStatuses(
        requestId: Long,
        submitted: Boolean?,
        name: String?,
        pageable: Pageable,
    ): PortfolioSubmissionStatusListResponse {
        requireNotDeleted(requestId)

        val targetMemberIds = targetRepository.findAllByRequestId(requestId).map { it.studentMemberId }
        val rows = buildRows(requestId, targetMemberIds)
        val filtered = applyFilters(rows, submitted, name).sortedWith(ROW_ORDER)
        return PortfolioSubmissionStatusListResponse.from(paginate(filtered, pageable))
    }

    @Transactional(readOnly = true)
    override fun buildExportEntries(
        requestId: Long,
        submittedOnly: Boolean,
    ): List<FileArchiveEntry> {
        requireNotDeleted(requestId)

        val submissions =
            submissionRepository
                .findAllByRequestId(requestId)
                .filter { !submittedOnly || it.status == PortfolioSubmissionStatus.SUBMITTED }
        if (submissions.isEmpty()) throw NoSubmissionsToExportException()

        val filesBySubmissionId = linkedFilesBySubmissionId(submissions)
        val profiles = profileQueryPort.findProfiles(submissions.map { it.memberId }.toSet())

        // 같은 이름 학생이 있어도 ZIP 안에서 파일이 섞이지 않게 memberId를 파일명에 넣어 안정적으로
        // 구분한다(FileArchivePort가 동일 이름은 (2)로 뒤에 번호를 붙이지만, 학생을 식별할 수 있는
        // 이름이 먼저 필요하다). memberId 오름차순으로 정렬해 실행마다 순서가 흔들리지 않게 한다.
        return submissions
            .sortedBy { it.memberId }
            .flatMap { submission ->
                val label = profiles[submission.memberId]?.name ?: "학생${submission.memberId}"
                filesBySubmissionId[submission.id].orEmpty().map { file ->
                    FileArchiveEntry(
                        fileId = file.fileId,
                        displayName = "student-${submission.memberId}_${label}_${file.originalName}",
                    )
                }
            }.ifEmpty { throw NoSubmissionsToExportException() }
    }

    // FileArchivePort.writeZip의 얇은 위임이다. 별도 Transaction을 열지 않는다 -- Storage Streaming은
    // 느린 외부 I/O라 Transaction 밖에서 실행해야 한다(JobApplicationExportServiceImpl.writeZip과 동일).
    override fun writeZip(
        entries: List<FileArchiveEntry>,
        outputStream: OutputStream,
    ) {
        fileArchivePort.writeZip(entries, outputStream)
    }

    private fun requireNotDeleted(requestId: Long) {
        requestRepository.findByIdAndDeletedAtIsNull(requestId) ?: throw PortfolioRequestNotFoundException(requestId)
    }

    /**
     * 대상 학생 전체를 기준으로 현황 Row를 만든다. 대상·제출물·프로필·연결 파일을 각각 한 번씩
     * 배치로 조회해(§35 N+1 방지), 제출물이 없는 학생은 "미제출"(submitted=false, status/materialType
     * null)로 채운다.
     */
    private fun buildRows(
        requestId: Long,
        targetMemberIds: List<Long>,
    ): List<PortfolioSubmissionStatusResponse> {
        if (targetMemberIds.isEmpty()) return emptyList()

        val profiles = profileQueryPort.findProfiles(targetMemberIds.toSet())
        val submissionsByMemberId = submissionRepository.findAllByRequestId(requestId).associateBy { it.memberId }
        val filesBySubmissionId = linkedFilesBySubmissionId(submissionsByMemberId.values)

        return targetMemberIds.map { memberId ->
            toRow(memberId, profiles[memberId], submissionsByMemberId[memberId], filesBySubmissionId)
        }
    }

    private fun toRow(
        memberId: Long,
        profile: PortfolioTargetMemberProfile?,
        submission: PortfolioSubmission?,
        filesBySubmissionId: Map<Long, List<FileSnapshot>>,
    ): PortfolioSubmissionStatusResponse {
        val hasFiles = submission?.id?.let { filesBySubmissionId[it].orEmpty().isNotEmpty() } ?: false
        val hasUrl = submission?.portfolioUrl != null
        return PortfolioSubmissionStatusResponse(
            memberId = memberId,
            studentName = profile?.name,
            cohort = profile?.cohort,
            department = profile?.department,
            submitted = submission?.status == PortfolioSubmissionStatus.SUBMITTED,
            status = submission?.status,
            materialType = submission?.let { materialTypeOf(hasFiles, hasUrl) },
            submittedAt = submission?.submittedAt,
        )
    }

    private fun materialTypeOf(
        hasFiles: Boolean,
        hasUrl: Boolean,
    ): PortfolioMaterialType? =
        when {
            hasFiles && hasUrl -> PortfolioMaterialType.BOTH
            hasFiles -> PortfolioMaterialType.FILE
            hasUrl -> PortfolioMaterialType.URL
            else -> null
        }

    private fun applyFilters(
        rows: List<PortfolioSubmissionStatusResponse>,
        submitted: Boolean?,
        name: String?,
    ): List<PortfolioSubmissionStatusResponse> {
        val keyword = name?.trim()?.takeIf { it.isNotEmpty() }
        return rows
            .filter { submitted == null || it.submitted == submitted }
            .filter { keyword == null || it.studentName?.contains(keyword, ignoreCase = true) == true }
    }

    private fun paginate(
        rows: List<PortfolioSubmissionStatusResponse>,
        pageable: Pageable,
    ): Page<PortfolioSubmissionStatusResponse> {
        val fromIndex = pageable.offset.toInt().coerceAtMost(rows.size)
        val toIndex = (fromIndex + pageable.pageSize).coerceAtMost(rows.size)
        return PageImpl(rows.subList(fromIndex, toIndex), pageable, rows.size.toLong())
    }

    private fun linkedFilesBySubmissionId(
        submissions: Collection<PortfolioSubmission>,
    ): Map<Long, List<FileSnapshot>> {
        val submissionIds = submissions.mapNotNull { it.id }
        if (submissionIds.isEmpty()) return emptyMap()
        return fileLinkPort.linkedFilesOf(FileOwnerType.PORTFOLIO_SUBMISSION, submissionIds)
    }

    companion object {
        // 이름 오름차순(대소문자 무시), 이름이 없으면 뒤로, 동명이인은 memberId로 안정 정렬한다.
        private val ROW_ORDER =
            compareBy<PortfolioSubmissionStatusResponse>(
                { it.studentName?.lowercase() == null },
                { it.studentName?.lowercase() },
                { it.memberId },
            )
    }
}
