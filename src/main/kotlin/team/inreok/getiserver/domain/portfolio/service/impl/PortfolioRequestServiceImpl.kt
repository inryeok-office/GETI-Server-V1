package team.inreok.getiserver.domain.portfolio.service.impl

import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.member.query.PortfolioTargetMemberQueryPort
import team.inreok.getiserver.domain.portfolio.dto.PortfolioRequestCreateRequest
import team.inreok.getiserver.domain.portfolio.dto.PortfolioRequestListResponse
import team.inreok.getiserver.domain.portfolio.dto.PortfolioRequestResponse
import team.inreok.getiserver.domain.portfolio.dto.PortfolioRequestStatusUpdateRequest
import team.inreok.getiserver.domain.portfolio.dto.PortfolioRequestSummaryResponse
import team.inreok.getiserver.domain.portfolio.dto.PortfolioRequestUpdateRequest
import team.inreok.getiserver.domain.portfolio.entity.PortfolioRequest
import team.inreok.getiserver.domain.portfolio.entity.PortfolioRequestTarget
import team.inreok.getiserver.domain.portfolio.entity.type.PortfolioRequestStatus
import team.inreok.getiserver.domain.portfolio.entity.type.PortfolioSubmissionStatus
import team.inreok.getiserver.domain.portfolio.exception.InvalidTargetStudentException
import team.inreok.getiserver.domain.portfolio.exception.NotRequestTargetException
import team.inreok.getiserver.domain.portfolio.exception.PortfolioRequestNotEditableException
import team.inreok.getiserver.domain.portfolio.exception.PortfolioRequestNotFoundException
import team.inreok.getiserver.domain.portfolio.exception.PortfolioStatusTransitionInvalidException
import team.inreok.getiserver.domain.portfolio.exception.TargetStudentRequiredException
import team.inreok.getiserver.domain.portfolio.repository.PortfolioRequestRepository
import team.inreok.getiserver.domain.portfolio.repository.PortfolioRequestTargetRepository
import team.inreok.getiserver.domain.portfolio.repository.PortfolioSubmissionRepository
import team.inreok.getiserver.domain.portfolio.repository.RequestCountProjection
import team.inreok.getiserver.domain.portfolio.service.PortfolioRequestService
import team.inreok.getiserver.global.error.BusinessException
import team.inreok.getiserver.global.error.CommonErrorCode
import java.time.LocalDateTime

@Service
class PortfolioRequestServiceImpl(
    private val requestRepository: PortfolioRequestRepository,
    private val targetRepository: PortfolioRequestTargetRepository,
    private val submissionRepository: PortfolioSubmissionRepository,
    private val targetMemberQueryPort: PortfolioTargetMemberQueryPort,
) : PortfolioRequestService {
    @Transactional
    override fun create(
        request: PortfolioRequestCreateRequest,
        createdByMemberId: Long,
    ): PortfolioRequestResponse {
        val targetIds = validatedTargetIds(request.targetStudentIds)

        val saved =
            requestRepository.saveAndFlush(
                PortfolioRequest(
                    createdByMemberId = createdByMemberId,
                    title = request.title.trim(),
                    dueAt = request.dueAt,
                    status = PortfolioRequestStatus.DRAFT,
                ).apply { description = request.description },
            )
        replaceTargets(requireNotNull(saved.id), targetIds)
        return detailOf(saved)
    }

    @Transactional
    override fun update(
        requestId: Long,
        request: PortfolioRequestUpdateRequest,
    ): PortfolioRequestResponse {
        val entity = findNotDeleted(requestId)
        requireMetadataEditable(entity)

        request.title?.let { entity.title = normalizedTitle(it) }
        request.description?.let { entity.description = it }
        request.dueAt?.let { entity.dueAt = it }
        request.targetStudentIds?.let { applyTargetReplacement(entity, requestId, it) }

        requestRepository.flush()
        return detailOf(entity)
    }

    /**
     * 대상 교체는 이미 제출한 학생을 대상에서 빼는 고아 Submission 문제(§27)를 피하려고 제출이
     * 시작되기 전인 DRAFT에서만 허용한다(Phase 1 기본값). 나머지 상태에서는 지정 자체를 거부한다.
     */
    private fun applyTargetReplacement(
        entity: PortfolioRequest,
        requestId: Long,
        newTargets: List<Long>,
    ) {
        if (entity.status != PortfolioRequestStatus.DRAFT) {
            throw PortfolioRequestNotEditableException(entity.status, "대상 학생은 DRAFT 상태에서만 변경할 수 있습니다.")
        }
        replaceTargets(requestId, validatedTargetIds(newTargets))
    }

    @Transactional
    override fun changeStatus(
        requestId: Long,
        request: PortfolioRequestStatusUpdateRequest,
    ): PortfolioRequestResponse {
        val entity = findNotDeleted(requestId)
        val target = request.status
        if (target !in allowedTransitions(entity.status)) {
            throw PortfolioStatusTransitionInvalidException(entity.status, target)
        }

        when (target) {
            // 공개하려면 대상 학생이 최소 한 명 있어야 한다(§26 "Request 공개 전 Target 최소 1명 보장").
            PortfolioRequestStatus.PUBLISHED -> {
                if (targetRepository.countByRequestId(requestId) == 0L) throw TargetStudentRequiredException()
            }

            // Soft Delete: 실제 행을 지우지 않아 이력이 보존된다. status와 deletedAt을 같은
            // Transaction에서 함께 바꿔 두 값이 어긋나지 않게 한다.
            PortfolioRequestStatus.DELETED -> {
                entity.deletedAt = LocalDateTime.now()
            }

            PortfolioRequestStatus.CLOSED, PortfolioRequestStatus.DRAFT -> {
                Unit
            }
        }
        entity.status = target

        requestRepository.flush()
        return detailOf(entity)
    }

    @Transactional(readOnly = true)
    override fun getDetail(
        requestId: Long,
        requesterId: Long,
        isAdmin: Boolean,
    ): PortfolioRequestResponse {
        val entity = findNotDeleted(requestId)
        if (!isAdmin) {
            if (!targetRepository.existsByRequestIdAndStudentMemberId(requestId, requesterId)) {
                throw NotRequestTargetException()
            }
            // 대상이더라도 아직 공개되지 않은(DRAFT) 요청은 학생에게 존재하지 않는 것으로 취급한다.
            if (entity.status == PortfolioRequestStatus.DRAFT) throw PortfolioRequestNotFoundException(requestId)
        }
        return detailOf(entity)
    }

    @Transactional(readOnly = true)
    override fun getList(
        status: PortfolioRequestStatus?,
        requesterId: Long,
        isAdmin: Boolean,
        pageable: Pageable,
    ): PortfolioRequestListResponse {
        val page =
            if (isAdmin) {
                requestRepository.findAllForAdmin(status, pageable)
            } else {
                requestRepository.findAllForStudent(requesterId, STUDENT_VISIBLE_STATUSES, status, pageable)
            }

        val requestIds = page.content.mapNotNull { it.id }
        val targetCounts =
            if (requestIds.isEmpty()) {
                emptyMap()
            } else {
                countsByRequestId(targetRepository.countGroupedByRequestId(requestIds))
            }
        val submittedCounts =
            if (requestIds.isEmpty()) {
                emptyMap()
            } else {
                countsByRequestId(
                    submissionRepository
                        .countGroupedByRequestIdAndStatus(requestIds, PortfolioSubmissionStatus.SUBMITTED),
                )
            }

        return PortfolioRequestListResponse.from(
            page.map {
                val id = requireNotNull(it.id)
                PortfolioRequestSummaryResponse.from(it, targetCounts[id] ?: 0L, submittedCounts[id] ?: 0L)
            },
        )
    }

    private fun findNotDeleted(requestId: Long): PortfolioRequest =
        requestRepository.findByIdAndDeletedAtIsNull(requestId) ?: throw PortfolioRequestNotFoundException(requestId)

    /** 중복을 제거하고 존재·STUDENT 여부를 검증한 대상 id 집합을 돌려준다(§26). */
    private fun validatedTargetIds(rawTargetIds: List<Long>): Set<Long> {
        val targetIds = rawTargetIds.toSet()
        if (targetIds.isEmpty()) throw TargetStudentRequiredException()
        val validStudentIds = targetMemberQueryPort.findStudentMemberIds(targetIds)
        if (validStudentIds.size != targetIds.size) throw InvalidTargetStudentException()
        return targetIds
    }

    /** 요청의 대상 학생 집합을 통째로 교체한다. 기존 대상을 먼저 지우고(Flush) 새 대상을 넣어 UNIQUE 충돌을 피한다. */
    private fun replaceTargets(
        requestId: Long,
        targetIds: Set<Long>,
    ) {
        targetRepository.deleteByRequestId(requestId)
        targetRepository.flush()
        targetRepository.saveAll(targetIds.map { PortfolioRequestTarget(requestId = requestId, studentMemberId = it) })
    }

    private fun detailOf(entity: PortfolioRequest): PortfolioRequestResponse {
        val requestId = requireNotNull(entity.id)
        return PortfolioRequestResponse.from(
            request = entity,
            targetCount = targetRepository.countByRequestId(requestId),
            submittedCount =
                submissionRepository.countByRequestIdAndStatus(requestId, PortfolioSubmissionStatus.SUBMITTED),
        )
    }

    companion object {
        private val STUDENT_VISIBLE_STATUSES =
            listOf(PortfolioRequestStatus.PUBLISHED, PortfolioRequestStatus.CLOSED)
    }
}

private fun allowedTransitions(current: PortfolioRequestStatus): Set<PortfolioRequestStatus> =
    when (current) {
        PortfolioRequestStatus.DRAFT -> setOf(PortfolioRequestStatus.PUBLISHED, PortfolioRequestStatus.DELETED)

        PortfolioRequestStatus.PUBLISHED -> setOf(PortfolioRequestStatus.CLOSED, PortfolioRequestStatus.DELETED)

        PortfolioRequestStatus.CLOSED -> setOf(PortfolioRequestStatus.DELETED)

        // findNotDeleted가 먼저 걸러내므로 실제로는 도달하지 않지만, 전이표를 한곳에서 읽을 수 있게 남겨둔다.
        PortfolioRequestStatus.DELETED -> emptySet()
    }

private fun countsByRequestId(rows: List<RequestCountProjection>): Map<Long, Long> =
    rows.associate { it.requestId to it.count }

private fun requireMetadataEditable(entity: PortfolioRequest) {
    if (entity.status == PortfolioRequestStatus.CLOSED || entity.status == PortfolioRequestStatus.DELETED) {
        throw PortfolioRequestNotEditableException(entity.status)
    }
}

private fun normalizedTitle(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) throw BusinessException(CommonErrorCode.VALIDATION_FAILED, "수합 요청 제목은 비어 있을 수 없습니다.")
    return trimmed
}
