package team.inreok.getiserver.domain.notification.service.impl

import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.job.query.JobDiscordSendTargetQueryPort
import team.inreok.getiserver.domain.job.query.JobDiscordSendTargetSnapshot
import team.inreok.getiserver.domain.notification.dto.DiscordSendTargetDeliveryResponse
import team.inreok.getiserver.domain.notification.dto.DiscordSendTargetListResponse
import team.inreok.getiserver.domain.notification.dto.DiscordSendTargetResponse
import team.inreok.getiserver.domain.notification.entity.DiscordDelivery
import team.inreok.getiserver.domain.notification.entity.type.DiscordDeliveryAction
import team.inreok.getiserver.domain.notification.entity.type.DiscordDeliveryStatus
import team.inreok.getiserver.domain.notification.entity.type.DiscordDeliveryTargetType
import team.inreok.getiserver.domain.notification.entity.type.DiscordSendTargetType
import team.inreok.getiserver.domain.notification.exception.DiscordSendTargetInvalidTargetGradeException
import team.inreok.getiserver.domain.notification.exception.DiscordSendTargetPageTooDeepException
import team.inreok.getiserver.domain.notification.repository.DiscordDeliveryRepository
import team.inreok.getiserver.domain.notification.service.DiscordDeliveryRetryPolicy
import team.inreok.getiserver.domain.notification.service.DiscordSendTargetQueryService
import team.inreok.getiserver.domain.program.query.ProgramDiscordSendTargetQueryPort
import team.inreok.getiserver.domain.program.query.ProgramDiscordSendTargetSnapshot
import java.time.LocalDateTime

@Service
@Suppress("TooManyFunctions")
class DiscordSendTargetQueryServiceImpl(
    private val jobQueryPort: JobDiscordSendTargetQueryPort,
    private val programQueryPort: ProgramDiscordSendTargetQueryPort,
    private val deliveryRepository: DiscordDeliveryRepository,
    private val retryPolicy: DiscordDeliveryRetryPolicy,
) : DiscordSendTargetQueryService {
    @Transactional(readOnly = true)
    override fun list(
        targetType: DiscordSendTargetType?,
        targetName: String?,
        targetGrade: Int?,
        pageable: Pageable,
    ): DiscordSendTargetListResponse {
        validateTargetGrade(targetGrade)
        val normalizedName = targetName?.trim()?.takeIf { it.isNotEmpty() }
        val totalElements = count(targetType, normalizedName, targetGrade)
        val pageRequest = PageRequest.of(pageable.pageNumber, normalizedPageSize(pageable))
        if (pageRequest.offset >= totalElements) {
            return emptyPage(pageRequest, totalElements)
        }
        if (pageRequest.pageNumber > MAX_PAGE_NUMBER) {
            throw DiscordSendTargetPageTooDeepException()
        }

        val rows = mergePage(targetType, normalizedName, targetGrade, pageRequest)
        val deliveries = resolveDeliveries(rows)
        val page = PageImpl(rows.map { it.toResponse(deliveries) }, pageRequest, totalElements)
        return page.toResponse()
    }

    private fun count(
        targetType: DiscordSendTargetType?,
        targetName: String?,
        targetGrade: Int?,
    ): Long =
        when (targetType) {
            DiscordSendTargetType.JOB -> {
                jobQueryPort.countPublished(targetName, targetGrade)
            }

            DiscordSendTargetType.PROGRAM -> {
                programQueryPort.countPublished(targetName, targetGrade)
            }

            null -> {
                jobQueryPort.countPublished(targetName, targetGrade) +
                    programQueryPort.countPublished(targetName, targetGrade)
            }
        }

    private fun mergePage(
        targetType: DiscordSendTargetType?,
        targetName: String?,
        targetGrade: Int?,
        pageable: Pageable,
    ): List<TargetRow> {
        val cursors =
            buildList {
                if (targetType == null || targetType == DiscordSendTargetType.JOB) {
                    add(
                        TargetCursor(
                            fetch = { afterCreatedAt, afterId, limit ->
                                jobQueryPort.findPublished(targetName, targetGrade, afterCreatedAt, afterId, limit)
                            },
                            toRow = { it.toRow() },
                        ),
                    )
                }
                if (targetType == null || targetType == DiscordSendTargetType.PROGRAM) {
                    add(
                        TargetCursor(
                            fetch = { afterCreatedAt, afterId, limit ->
                                programQueryPort.findPublished(targetName, targetGrade, afterCreatedAt, afterId, limit)
                            },
                            toRow = { it.toRow() },
                        ),
                    )
                }
            }
        val offset = pageable.offset
        var skipped = 0L
        while (skipped < offset) {
            if (takeNext(cursors) == null) return emptyList()
            skipped += 1
        }

        return buildList(pageable.pageSize) {
            repeat(pageable.pageSize) {
                takeNext(cursors)?.let(::add) ?: return@buildList
            }
        }
    }

    private fun takeNext(cursors: List<TargetCursor<*>>): TargetRow? {
        val next = cursors.mapNotNull { it.peek() }.minWithOrNull(CURSOR_ORDER) ?: return null
        next.cursor.pop()
        return next.row
    }

    private fun resolveDeliveries(rows: List<TargetRow>): ResolvedDeliveries {
        if (rows.isEmpty()) return ResolvedDeliveries(emptyMap(), emptyMap())
        val targetTypes = rows.mapTo(mutableSetOf()) { it.deliveryTargetType }
        val targetIds = rows.mapTo(mutableSetOf()) { it.targetId }
        val latestCreate =
            deliveryRepository.findLatestCreateDeliveries(
                targetTypes,
                targetIds,
                DiscordDeliveryAction.CREATE,
            )
        val latestAnyDeliveryIds =
            deliveryRepository
                .findLatestDeliveries(targetTypes, targetIds)
                .associate { TargetKey(it.targetType, it.targetId) to requireNotNull(it.id) }
        return ResolvedDeliveries(
            latestCreate.associateBy { TargetKey(it.targetType, it.targetId) },
            latestAnyDeliveryIds,
        )
    }

    private fun TargetRow.toResponse(deliveries: ResolvedDeliveries): DiscordSendTargetResponse {
        val delivery = deliveries.latestCreate[TargetKey(deliveryTargetType, targetId)]
        return DiscordSendTargetResponse(
            targetType = targetType,
            targetId = targetId,
            targetName = targetName,
            targetGrades = targetGrades,
            delivery = delivery?.toResponse(deliveries.latestAnyDeliveryIds),
        )
    }

    private fun DiscordDelivery.toResponse(
        latestAnyDeliveryIds: Map<TargetKey, Long>,
    ): DiscordSendTargetDeliveryResponse =
        DiscordSendTargetDeliveryResponse(
            deliveryId = requireNotNull(id) { "저장된 DiscordDelivery는 id를 가져야 합니다." },
            status = status,
            requestedAt = requireNotNull(createdAt) { "저장된 DiscordDelivery는 createdAt을 가져야 합니다." },
            manualRetryCount = manualRetryCount,
            canRetry =
                latestAnyDeliveryIds[TargetKey(targetType, targetId)] == id &&
                    status == DiscordDeliveryStatus.FAILED &&
                    retryPolicy.canRetryManually(manualRetryCount),
        )

    private fun emptyPage(
        pageable: PageRequest,
        totalElements: Long,
    ): DiscordSendTargetListResponse =
        PageImpl<DiscordSendTargetResponse>(emptyList(), pageable, totalElements).toResponse()

    private fun Page<DiscordSendTargetResponse>.toResponse() =
        DiscordSendTargetListResponse(
            content = content,
            page = number,
            size = size,
            totalElements = totalElements,
            totalPages = totalPages,
            first = isFirst,
            last = isLast,
        )

    private fun validateTargetGrade(targetGrade: Int?) {
        if (targetGrade != null && targetGrade !in MIN_TARGET_GRADE..MAX_TARGET_GRADE) {
            throw DiscordSendTargetInvalidTargetGradeException()
        }
    }

    private fun normalizedPageSize(pageable: Pageable): Int = pageable.pageSize.coerceIn(1, MAX_PAGE_SIZE)

    private data class TargetRow(
        val targetType: DiscordSendTargetType,
        val targetId: Long,
        val targetName: String,
        val targetGrades: List<Int>?,
        val createdAt: LocalDateTime,
    ) {
        val deliveryTargetType: DiscordDeliveryTargetType
            get() = DiscordDeliveryTargetType.valueOf(targetType.name)
    }

    private data class TargetKey(
        val targetType: DiscordDeliveryTargetType,
        val targetId: Long,
    )

    private data class ResolvedDeliveries(
        val latestCreate: Map<TargetKey, DiscordDelivery>,
        val latestAnyDeliveryIds: Map<TargetKey, Long>,
    )

    private class TargetCursor<T>(
        private val fetch: (LocalDateTime?, Long?, Int) -> List<T>,
        private val toRow: (T) -> TargetRow,
    ) {
        private val buffer = ArrayDeque<T>()
        private var afterCreatedAt: LocalDateTime? = null
        private var afterId: Long? = null
        private var exhausted = false

        fun peek(): CursorRow? {
            fillIfNeeded()
            return buffer.firstOrNull()?.let { CursorRow(this, toRow(it)) }
        }

        fun pop() {
            buffer.removeFirst()
        }

        private fun fillIfNeeded() {
            if (buffer.isNotEmpty() || exhausted) return
            val batch = fetch(afterCreatedAt, afterId, BATCH_SIZE)
            if (batch.isEmpty()) {
                exhausted = true
                return
            }
            buffer.addAll(batch)
            val last = toRow(batch.last())
            afterCreatedAt = last.createdAt
            afterId = last.targetId
            exhausted = batch.size < BATCH_SIZE
        }
    }

    private data class CursorRow(
        val cursor: TargetCursor<*>,
        val row: TargetRow,
    )

    private companion object {
        const val MIN_TARGET_GRADE = 1
        const val MAX_TARGET_GRADE = 3
        const val MAX_PAGE_SIZE = 100
        const val MAX_PAGE_NUMBER = 100
        const val BATCH_SIZE = 100

        val CURSOR_ORDER =
            compareBy<CursorRow> { it.row.createdAt }
                .reversed()
                .thenByDescending { it.row.targetId }
                .thenBy { it.row.targetType }
    }

    private fun JobDiscordSendTargetSnapshot.toRow() =
        TargetRow(
            targetType = DiscordSendTargetType.JOB,
            targetId = jobId,
            targetName = title,
            targetGrades = targetGrades,
            createdAt = createdAt,
        )

    private fun ProgramDiscordSendTargetSnapshot.toRow() =
        TargetRow(
            targetType = DiscordSendTargetType.PROGRAM,
            targetId = programId,
            targetName = title,
            targetGrades = targetGrades,
            createdAt = createdAt,
        )
}
