package team.inreok.getiserver.domain.portfolio.service.impl

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.file.entity.type.FileOwnerType
import team.inreok.getiserver.domain.file.entity.type.FilePurpose
import team.inreok.getiserver.domain.file.link.FileLinkPort
import team.inreok.getiserver.domain.file.link.FileSnapshot
import team.inreok.getiserver.domain.portfolio.dto.PortfolioSubmissionResponse
import team.inreok.getiserver.domain.portfolio.dto.PortfolioSubmissionUpsertRequest
import team.inreok.getiserver.domain.portfolio.entity.PortfolioRequest
import team.inreok.getiserver.domain.portfolio.entity.PortfolioSubmission
import team.inreok.getiserver.domain.portfolio.entity.type.PortfolioRequestStatus
import team.inreok.getiserver.domain.portfolio.entity.type.PortfolioSubmissionStatus
import team.inreok.getiserver.domain.portfolio.exception.NotRequestTargetException
import team.inreok.getiserver.domain.portfolio.exception.PortfolioRequestNotFoundException
import team.inreok.getiserver.domain.portfolio.exception.SubmissionClosedException
import team.inreok.getiserver.domain.portfolio.repository.PortfolioRequestRepository
import team.inreok.getiserver.domain.portfolio.repository.PortfolioRequestTargetRepository
import team.inreok.getiserver.domain.portfolio.repository.PortfolioSubmissionRepository
import team.inreok.getiserver.domain.portfolio.service.PortfolioSubmissionService
import java.time.LocalDateTime

@Service
class PortfolioSubmissionServiceImpl(
    private val requestRepository: PortfolioRequestRepository,
    private val targetRepository: PortfolioRequestTargetRepository,
    private val submissionRepository: PortfolioSubmissionRepository,
    private val fileLinkPort: FileLinkPort,
) : PortfolioSubmissionService {
    /**
     * 상태 전이와 File 연결을 같은 Transaction 안에서 처리한다 -- 파일 연결만 성공하고 제출물 상태
     * 저장이 Rollback되는(또는 그 반대) 어긋난 상태를 만들지 않기 위해서다(JobApplication File Sync와
     * 같은 이유). File I/O는 외부 Storage가 아니라 DB의 files 행 상태만 바꾸므로 이 Transaction에
     * 포함해도 §17(느린 I/O를 Transaction 밖에 두기) 원칙과 충돌하지 않는다.
     *
     * 요청 Row를 [PortfolioRequestRepository.findByIdAndDeletedAtIsNullForUpdate]로 잠근 뒤
     * 제출물을 Upsert한다. 같은 학생이 거의 동시에 두 번 제출해도 요청 Lock으로 순차화되어 두 번째
     * 요청이 첫 번째가 만든 Row를 보고 정상 Update를 타므로, UNIQUE 제약 위반(500)이 발생하지 않는다
     * (Program 신청 동시성과 같은 관례).
     */
    @Transactional
    override fun submit(
        requestId: Long,
        requesterId: Long,
        request: PortfolioSubmissionUpsertRequest,
    ): PortfolioSubmissionResponse {
        val entity =
            requestRepository.findByIdAndDeletedAtIsNullForUpdate(requestId)
                ?: throw PortfolioRequestNotFoundException(requestId)
        requireStudentTarget(entity, requestId, requesterId)
        if (!isWithinSubmissionWindow(entity)) throw SubmissionClosedException()

        val submission =
            submissionRepository.findByRequestIdAndMemberId(requestId, requesterId)
                ?: PortfolioSubmission(requestId = requestId, memberId = requesterId)
        applySubmission(submission, request)
        val saved = submissionRepository.saveAndFlush(submission)

        val files = syncFiles(requesterId, requireNotNull(saved.id), request.fileIds)
        return PortfolioSubmissionResponse.from(saved, files)
    }

    /**
     * 학생이 이 요청을 볼 수 있는 대상인지 검증한다. 순서가 중요하다 -- 공개 전(DRAFT) 판정을 대상
     * 여부보다 먼저 둬야, 대상이 아닌 학생이 DRAFT 요청 id를 넣었을 때 403(대상 아님)으로 "그 요청이
     * 존재한다"는 사실이 새어 나가지 않는다(getDetail과 동일한 순서/이유).
     */
    private fun requireStudentTarget(
        entity: PortfolioRequest,
        requestId: Long,
        requesterId: Long,
    ) {
        if (entity.status == PortfolioRequestStatus.DRAFT) throw PortfolioRequestNotFoundException(requestId)
        if (!targetRepository.existsByRequestIdAndStudentMemberId(requestId, requesterId)) {
            throw NotRequestTargetException()
        }
    }

    /**
     * 지금 이 요청에 제출할 수 있는 시점인지 판정한다. 여기 도달했다면 상태는 PUBLISHED 또는
     * CLOSED다(DRAFT는 존재 비노출로 404, DELETED는 조회 단계에서 걸러졌다). 공개 중이고 마감 시각
     * 전이어야만 제출할 수 있다.
     */
    private fun isWithinSubmissionWindow(entity: PortfolioRequest): Boolean =
        entity.status == PortfolioRequestStatus.PUBLISHED && !LocalDateTime.now().isAfter(entity.dueAt)

    private fun applySubmission(
        submission: PortfolioSubmission,
        request: PortfolioSubmissionUpsertRequest,
    ) {
        submission.portfolioUrl = request.portfolioUrl?.trim()?.takeIf { it.isNotEmpty() }
        submission.note = request.note?.takeIf { it.isNotBlank() }
        submission.status = request.status
        if (request.status == PortfolioSubmissionStatus.SUBMITTED) {
            // 최초 제출 시각만 기록하고 이후 재제출에서는 유지한다 -- "언제 처음 제출했는가"가 현황의
            // 기준이기 때문이다.
            submission.submittedAt = submission.submittedAt ?: LocalDateTime.now()
        }
    }

    /**
     * 제출물에 연결된 파일 집합을 [fileIds]가 나타내는 최종 상태로 맞춘다(desired-set 동기화).
     *
     * `FileLinkPort`는 개별 해제를 제공하지 않으므로 "전체 해제 후 재연결" 패턴을 쓴다
     * (JobApplication File Sync와 동일). 원하는 집합이 이미 연결된 집합과 같으면 File 도메인을 다시
     * 호출하지 않아 매 저장마다 불필요한 해제·재연결로 Lock과 로그를 남기지 않는다. 소유권·목적·상태·
     * 개수 검증은 연결 시점의 [FileLinkPort.validateAndLink]가 수행한다(§14 보안 요구사항).
     */
    private fun syncFiles(
        requesterId: Long,
        submissionId: Long,
        fileIds: List<Long>,
    ): List<FileSnapshot> {
        val desired = fileIds.distinct()
        val currentIds =
            fileLinkPort.linkedFilesOf(FileOwnerType.PORTFOLIO_SUBMISSION, submissionId).map { it.fileId }.toSet()
        return if (desired.toSet() == currentIds) {
            snapshotsInOrder(desired)
        } else {
            relink(requesterId, submissionId, desired, hadLinks = currentIds.isNotEmpty())
        }
    }

    /** 변경이 없을 때 이미 연결된 파일을 [fileIds] 순서 그대로 돌려준다. */
    private fun snapshotsInOrder(fileIds: List<Long>): List<FileSnapshot> {
        if (fileIds.isEmpty()) return emptyList()
        val snapshots = fileLinkPort.snapshotsOf(fileIds)
        return fileIds.mapNotNull { snapshots[it] }
    }

    private fun relink(
        requesterId: Long,
        submissionId: Long,
        desired: List<Long>,
        hadLinks: Boolean,
    ): List<FileSnapshot> {
        if (hadLinks) fileLinkPort.unlinkAllOf(FileOwnerType.PORTFOLIO_SUBMISSION, submissionId)
        if (desired.isEmpty()) return emptyList()
        return fileLinkPort.validateAndLink(
            requesterId = requesterId,
            fileIds = desired,
            purpose = FilePurpose.PORTFOLIO,
            ownerId = submissionId,
        )
    }
}
