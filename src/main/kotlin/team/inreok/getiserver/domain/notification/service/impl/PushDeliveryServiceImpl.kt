package team.inreok.getiserver.domain.notification.service.impl

import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.notification.config.PushDeliveryProperties
import team.inreok.getiserver.domain.notification.entity.Notification
import team.inreok.getiserver.domain.notification.entity.PushDelivery
import team.inreok.getiserver.domain.notification.entity.type.NotificationType
import team.inreok.getiserver.domain.notification.entity.type.PushDeliveryStatus
import team.inreok.getiserver.domain.notification.repository.NotificationDeviceRepository
import team.inreok.getiserver.domain.notification.repository.NotificationRepository
import team.inreok.getiserver.domain.notification.repository.PushDeliveryRepository
import team.inreok.getiserver.domain.notification.service.NotificationSettingService
import team.inreok.getiserver.domain.notification.service.PushDeliveryRetryPolicy
import team.inreok.getiserver.domain.notification.service.PushDeliveryService
import team.inreok.getiserver.domain.notification.service.PushEligibleNotificationTypes
import team.inreok.getiserver.domain.notification.service.PushProvider
import team.inreok.getiserver.domain.notification.service.PushSendCommand
import team.inreok.getiserver.domain.notification.service.PushSendResult
import java.time.LocalDateTime

/**
 * Push 전달의 예약·전송·재시도를 담당한다(`DiscordDeliveryServiceImpl`과 같은 구조).
 *
 * ## Transaction 경계
 *
 * [enqueueForNotification]은 `REQUIRES_NEW`다 -- 이유는 Interface KDoc과
 * `NotificationInsertOperation` KDoc(Issue #118) 참고.
 *
 * [processDueDeliveries]와 그 하위 처리에는 `@Transactional`이 없다. FCM HTTP 호출을 Transaction
 * 안에 넣지 않기 위해서다(.claude/rules/spring-boot.md). 선점(`claim`), 상태 반영은 각각 짧은
 * Transaction으로 나뉜다(Repository Method 각각의 `@Transactional`).
 *
 * ## 동시성
 *
 * 여러 Instance가 같은 Row를 동시에 집어도 [PushDeliveryRepository.claim]의 조건부 UPDATE가
 * 하나만 통과시킨다(`DiscordDeliveryServiceImpl`과 같은 이유).
 */
@Service
@EnableConfigurationProperties(PushDeliveryProperties::class)
class PushDeliveryServiceImpl(
    private val pushDeliveryRepository: PushDeliveryRepository,
    private val notificationRepository: NotificationRepository,
    private val notificationDeviceRepository: NotificationDeviceRepository,
    private val notificationSettingService: NotificationSettingService,
    private val pushProvider: PushProvider,
    private val retryPolicy: PushDeliveryRetryPolicy,
    private val properties: PushDeliveryProperties,
) : PushDeliveryService {
    private val log = LoggerFactory.getLogger(PushDeliveryServiceImpl::class.java)

    @Suppress("ReturnCount")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    override fun enqueueForNotification(
        notificationId: Long,
        recipientMemberId: Long,
        type: NotificationType,
    ) {
        if (!PushEligibleNotificationTypes.isEligible(type)) return
        if (!notificationSettingService.getSettings(recipientMemberId).pushEnabled) return

        val deviceIds = notificationDeviceRepository.findIdsByMemberId(recipientMemberId)
        if (deviceIds.isEmpty()) return

        deviceIds.forEach { deviceId ->
            pushDeliveryRepository.save(
                PushDelivery(
                    notificationId = notificationId,
                    memberId = recipientMemberId,
                    deviceId = deviceId,
                ),
            )
        }
    }

    override fun processDueDeliveries(): Int {
        val now = LocalDateTime.now()
        recoverStaleProcessing(now)

        val dueIds =
            pushDeliveryRepository.findDueIds(
                status = PushDeliveryStatus.PENDING,
                now = now,
                pageable = PageRequest.of(0, properties.batchSize),
            )

        return dueIds.count { deliveryId -> processOne(deliveryId) }
    }

    private fun recoverStaleProcessing(now: LocalDateTime) {
        val recovered =
            pushDeliveryRepository.recoverStaleProcessing(
                pending = PushDeliveryStatus.PENDING,
                processing = PushDeliveryStatus.PROCESSING,
                threshold = now.minusNanos(properties.staleProcessingThresholdMs * NANOS_PER_MILLI),
                now = now,
            )
        if (recovered > 0) {
            log.warn("중단된 Push 전달 {}건을 재시도 대기로 되돌렸습니다.", recovered)
        }
    }

    @Suppress("ReturnCount", "TooGenericExceptionCaught")
    private fun processOne(deliveryId: Long): Boolean {
        val claimedAt = LocalDateTime.now()
        val claimed =
            pushDeliveryRepository.claim(
                id = deliveryId,
                pending = PushDeliveryStatus.PENDING,
                processing = PushDeliveryStatus.PROCESSING,
                now = claimedAt,
            )
        if (claimed != 1) return false

        val delivery = pushDeliveryRepository.findById(deliveryId).orElse(null) ?: return false
        return try {
            send(delivery)
            true
        } catch (ex: RuntimeException) {
            // 한 건의 예기치 못한 오류가 Sweep 전체를 멈추지 않게 한다(`DiscordDeliveryServiceImpl`
            // 과 같은 이유). 이 Row는 PROCESSING으로 남지만 Stale 회수가 임계값 이후에 되살린다.
            log.error("Push 전달 처리 중 예기치 못한 오류(deliveryId={})", deliveryId, ex)
            false
        }
    }

    private fun send(delivery: PushDelivery) {
        val notification = notificationRepository.findById(delivery.notificationId).orElse(null)
        if (notification == null || notification.deletedAt != null) {
            // 알림이 삭제됐으면 재시도해도 다시 생기지 않는다.
            finish(
                delivery,
                NOTIFICATION_UNAVAILABLE,
                "알림을 찾을 수 없어 Push 전달을 중단했습니다.",
                retryable = false,
                invalidToken = false,
            )
            return
        }

        val device = notificationDeviceRepository.findById(delivery.deviceId).orElse(null)
        if (device == null) {
            // 그 사이 회원이 기기를 해제했거나 무효 Token 정리로 이미 지워졌다.
            finish(delivery, DEVICE_NOT_FOUND, "기기를 찾을 수 없어 Push 전달을 중단했습니다.", retryable = false, invalidToken = false)
            return
        }

        val result =
            pushProvider.send(
                PushSendCommand(
                    platform = device.platform,
                    // Token은 재등록으로 언제든 바뀔 수 있어 예약 시점 값이 아니라 지금 다시 읽은
                    // 값을 쓴다(PushDelivery KDoc 참고).
                    token = device.pushToken,
                    title = notification.title,
                    body = notification.content,
                    data = notificationClickData(notification),
                ),
            )
        applyResult(delivery, result)
    }

    private fun notificationClickData(notification: Notification): Map<String, String> =
        buildMap {
            put("notificationId", requireNotNull(notification.id).toString())
            put("type", notification.type.name)
            notification.targetType?.let { put("targetType", it.name) }
            notification.targetId?.let { put("targetId", it.toString()) }
        }

    private fun applyResult(
        delivery: PushDelivery,
        result: PushSendResult,
    ) {
        when (result) {
            is PushSendResult.Success -> {
                val now = LocalDateTime.now()
                delivery.markSent(now)
                pushDeliveryRepository.save(delivery)
                log.info("Push 전달 성공(deliveryId={}, notificationId={})", delivery.id, delivery.notificationId)
            }

            is PushSendResult.Failure -> {
                finish(
                    delivery,
                    result.code,
                    result.message,
                    retryable = result.retryable,
                    invalidToken = result.invalidToken,
                )
            }
        }
    }

    /**
     * 실패를 상태에 반영한다. Token 자체가 무효하면 재시도 여부와 무관하게 즉시 FAILED로 확정하고
     * 해당 `NotificationDevice` 등록을 제거한다(Issue #190 확정 계약). 그 외에는 재시도 가능하고
     * 횟수가 남았으면 다음 시각을 예약하고, 아니면 FAILED로 확정한다.
     */
    private fun finish(
        delivery: PushDelivery,
        errorCode: String,
        errorMessage: String?,
        retryable: Boolean,
        invalidToken: Boolean,
    ) {
        val now = LocalDateTime.now()
        val retryAt = if (retryable && !invalidToken) retryPolicy.nextRetryAt(delivery.retryCount, now) else null

        if (retryAt != null) {
            delivery.markRetryScheduled(retryAt, errorCode, errorMessage, now)
        } else {
            delivery.markFailed(errorCode, errorMessage, now)
        }
        pushDeliveryRepository.save(delivery)

        log.warn(
            "Push 전달 실패(deliveryId={}, notificationId={}, code={}, status={})",
            delivery.id,
            delivery.notificationId,
            errorCode,
            delivery.status,
        )

        if (invalidToken) removeInvalidDevice(delivery.deviceId)
    }

    // 이미 다른 요청(재등록, 해제)으로 지워졌을 수 있어 존재 여부를 먼저 확인한다 -- 없는 Row를
    // deleteById로 지우면 EmptyResultDataAccessException이 난다.
    private fun removeInvalidDevice(deviceId: Long) {
        if (!notificationDeviceRepository.existsById(deviceId)) return
        notificationDeviceRepository.deleteById(deviceId)
        log.info("무효한 Push Token이라 기기 등록을 제거했습니다(deviceId={})", deviceId)
    }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
        const val NOTIFICATION_UNAVAILABLE = "NOTIFICATION_UNAVAILABLE"
        const val DEVICE_NOT_FOUND = "DEVICE_NOT_FOUND"
    }
}
