package team.inreok.getiserver.domain.notification.service.impl

import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.inquiry.query.InquiryDiscordPayloadQueryPort
import team.inreok.getiserver.domain.job.query.JobDiscordPayloadQueryPort
import team.inreok.getiserver.domain.notification.config.DiscordBotProperties
import team.inreok.getiserver.domain.notification.dto.DiscordDeliveryListItemResponse
import team.inreok.getiserver.domain.notification.dto.DiscordDeliveryListResponse
import team.inreok.getiserver.domain.notification.entity.DiscordDelivery
import team.inreok.getiserver.domain.notification.entity.type.DiscordDeliveryStatus
import team.inreok.getiserver.domain.notification.entity.type.DiscordDeliveryTargetType
import team.inreok.getiserver.domain.notification.repository.DiscordDeliveryRepository
import team.inreok.getiserver.domain.notification.service.DiscordDeliveryAdminQueryService
import team.inreok.getiserver.domain.notification.service.DiscordDeliveryRetryPolicy
import team.inreok.getiserver.domain.program.query.ProgramDiscordPayloadQueryPort
import java.time.LocalDateTime

/**
 * 관리자 Discord 전달 목록 조회 구현이다(Issue #206).
 *
 * 대상 이름은 저장돼 있지 않아 조회 시점에 원본 Domain에서 다시 읽는다(Notification 요구사항
 * §19·§39: Delivery는 Payload Snapshot을 저장하지 않는다). 다른 Domain의 Repository를 직접 쓰지
 * 않고 각 Domain이 공개한 Query Port만 사용하며, 한 Page에 최대 100건이 실릴 수 있으므로 대상을
 * [DiscordDeliveryTargetType]별로 모아 **Domain당 최대 한 번만** 조회한다(N+1 방지,
 * [NotificationTargetResolver][team.inreok.getiserver.domain.notification.service.NotificationTargetResolver]와
 * 같은 방식).
 */
@Service
class DiscordDeliveryAdminQueryServiceImpl(
    private val deliveryRepository: DiscordDeliveryRepository,
    private val jobPayloadQueryPort: JobDiscordPayloadQueryPort,
    private val programPayloadQueryPort: ProgramDiscordPayloadQueryPort,
    private val inquiryPayloadQueryPort: InquiryDiscordPayloadQueryPort,
    private val retryPolicy: DiscordDeliveryRetryPolicy,
    private val properties: DiscordBotProperties,
) : DiscordDeliveryAdminQueryService {
    @Transactional(readOnly = true)
    override fun listRecent(
        status: DiscordDeliveryStatus?,
        pageable: Pageable,
        startAt: LocalDateTime?,
        endAt: LocalDateTime?,
    ): DiscordDeliveryListResponse {
        // 정렬은 Repository Query가 id DESC로 고정한다. 클라이언트가 보낸 Sort를 그대로 넘기면
        // JPQL의 ORDER BY와 충돌하므로 Page 정보만 남긴다(NotificationServiceImpl.list와 동일).
        val page =
            deliveryRepository.findRecent(
                status,
                startAt,
                endAt,
                PageRequest.of(pageable.pageNumber, pageable.pageSize),
            )
        if (page.isEmpty) return page.toListResponse(emptyMap(), emptySet())

        val targetNames = loadTargetNames(page.content)
        val latestDeliveryIds = loadLatestDeliveryIds(page.content)
        return page.toListResponse(targetNames, latestDeliveryIds)
    }

    /**
     * Page에 실린 대상들의 표시 이름을 대상 Domain별로 한 번씩만 읽는다. 해당 종류의 대상이 하나도
     * 없으면 그 Port는 아예 호출하지 않는다 -- 다른 Module에 빈 질의를 보내지 않기 위해서다.
     */
    private fun loadTargetNames(deliveries: List<DiscordDelivery>): Map<TargetRef, String> {
        val jobIds = deliveries.targetIdsOf(DiscordDeliveryTargetType.JOB)
        val programIds = deliveries.targetIdsOf(DiscordDeliveryTargetType.PROGRAM)
        val inquiryIds = deliveries.targetIdsOf(DiscordDeliveryTargetType.INQUIRY)
        return buildMap {
            if (jobIds.isNotEmpty()) {
                putNames(DiscordDeliveryTargetType.JOB, jobPayloadQueryPort.findDisplayNamesByIds(jobIds))
            }
            if (programIds.isNotEmpty()) {
                putNames(DiscordDeliveryTargetType.PROGRAM, programPayloadQueryPort.findDisplayNamesByIds(programIds))
            }
            if (inquiryIds.isNotEmpty()) {
                putNames(DiscordDeliveryTargetType.INQUIRY, inquiryPayloadQueryPort.findDisplayNamesByIds(inquiryIds))
            }
        }
    }

    /**
     * Page에 실린 대상들의 "가장 최근 Delivery" id 집합이다. 수동 재시도 API가 대상별 최신
     * Delivery만 다시 시도하므로, 이 집합에 없는 항목은 FAILED여도 재시도할 수 없다.
     */
    private fun loadLatestDeliveryIds(deliveries: List<DiscordDelivery>): Set<Long> =
        deliveryRepository
            .findLatestDeliveryIds(
                targetTypes = deliveries.mapTo(mutableSetOf()) { it.targetType },
                targetIds = deliveries.mapTo(mutableSetOf()) { it.targetId },
            ).toSet()

    private fun Page<DiscordDelivery>.toListResponse(
        targetNames: Map<TargetRef, String>,
        latestDeliveryIds: Set<Long>,
    ) = DiscordDeliveryListResponse(
        content = content.map { it.toListItemResponse(targetNames, latestDeliveryIds) },
        page = number,
        size = size,
        totalElements = totalElements,
        totalPages = totalPages,
        first = isFirst,
        last = isLast,
    )

    private fun DiscordDelivery.toListItemResponse(
        targetNames: Map<TargetRef, String>,
        latestDeliveryIds: Set<Long>,
    ): DiscordDeliveryListItemResponse {
        val deliveryId = requireNotNull(id) { "저장된 DiscordDelivery는 id를 가져야 합니다." }
        return DiscordDeliveryListItemResponse(
            deliveryId = deliveryId,
            targetType = targetType,
            targetId = targetId,
            // 원본 Row가 이미 완전히 사라졌으면 null이다. Soft Delete는 Port가 걸러내지 않으므로
            // 삭제된 공고·프로그램의 제목은 그대로 표시된다.
            targetName = targetNames[TargetRef(targetType, targetId)],
            action = action,
            channelId = channelId,
            messageId = discordMessageId,
            status = status,
            automaticRetryCount = automaticRetryCount,
            manualRetryCount = manualRetryCount,
            maxAutomaticRetryCount = properties.maxAutomaticRetryCount,
            maxManualRetryCount = properties.maxManualRetryCount,
            // 단일 조회(DiscordDeliveryServiceImpl.toStatusResponse)의 판정에 "이 Row가 대상의
            // 최신 Row"라는 조건이 하나 더 붙는다 -- 목록만 대상당 여러 Row를 보여주는데, 재시도
            // API는 최신 Row 하나만 다시 시도하기 때문이다. 단일 조회는 애초에 최신 Row만
            // 반환하므로 두 계산은 서로 모순되지 않는다.
            canRetry =
                deliveryId in latestDeliveryIds &&
                    status == DiscordDeliveryStatus.FAILED &&
                    retryPolicy.canRetryManually(manualRetryCount),
            failureCode = lastErrorCode,
            failureReason = lastErrorMessage,
            requestedAt = requireNotNull(createdAt) { "저장된 DiscordDelivery는 createdAt을 가져야 합니다." },
            lastSyncedAt = lastAttemptAt,
        )
    }

    private fun List<DiscordDelivery>.targetIdsOf(targetType: DiscordDeliveryTargetType): Set<Long> =
        filter { it.targetType == targetType }.mapTo(mutableSetOf()) { it.targetId }

    private fun MutableMap<TargetRef, String>.putNames(
        targetType: DiscordDeliveryTargetType,
        names: Map<Long, String>,
    ) = names.forEach { (targetId, name) -> put(TargetRef(targetType, targetId), name) }

    /**
     * 대상 이름 Map의 Key다. `targetId`만으로는 부족하다 -- 공고 1번과 프로그램 1번이 같은 Page에
     * 함께 실리면 서로의 이름을 덮어쓴다.
     */
    private data class TargetRef(
        val targetType: DiscordDeliveryTargetType,
        val targetId: Long,
    )
}
