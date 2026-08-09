package team.inreok.getiserver.domain.notification.service.impl

import org.hibernate.exception.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.inquiry.query.InquiryDiscordPayloadQueryPort
import team.inreok.getiserver.domain.job.query.JobDiscordPayloadQueryPort
import team.inreok.getiserver.domain.notification.config.DiscordBotProperties
import team.inreok.getiserver.domain.notification.dto.DiscordDeliveryEnqueueCommand
import team.inreok.getiserver.domain.notification.entity.DiscordDelivery
import team.inreok.getiserver.domain.notification.entity.DiscordDeliveryAttempt
import team.inreok.getiserver.domain.notification.entity.type.DiscordDeliveryAction
import team.inreok.getiserver.domain.notification.entity.type.DiscordDeliveryAttemptResult
import team.inreok.getiserver.domain.notification.entity.type.DiscordDeliveryAttemptType
import team.inreok.getiserver.domain.notification.entity.type.DiscordDeliveryStatus
import team.inreok.getiserver.domain.notification.entity.type.DiscordDeliveryTargetType
import team.inreok.getiserver.domain.notification.exception.DiscordDeliveryNotFoundException
import team.inreok.getiserver.domain.notification.exception.DiscordDeliveryNotRetryableException
import team.inreok.getiserver.domain.notification.exception.DiscordDeliveryRetryLimitExceededException
import team.inreok.getiserver.domain.notification.repository.DiscordDeliveryAttemptRepository
import team.inreok.getiserver.domain.notification.repository.DiscordDeliveryRepository
import team.inreok.getiserver.domain.notification.service.DiscordBotClient
import team.inreok.getiserver.domain.notification.service.DiscordBotResult
import team.inreok.getiserver.domain.notification.service.DiscordCreateCommand
import team.inreok.getiserver.domain.notification.service.DiscordDeliveryRetryPolicy
import team.inreok.getiserver.domain.notification.service.DiscordDeliveryService
import team.inreok.getiserver.domain.notification.service.DiscordIdempotencyKeys
import team.inreok.getiserver.domain.notification.service.DiscordPatchCommand
import team.inreok.getiserver.domain.notification.service.DiscordPayloadFactory
import team.inreok.getiserver.domain.program.query.ProgramDiscordPayloadQueryPort
import java.time.LocalDateTime
import java.util.UUID

/**
 * Discord 전달의 생성·전송·재시도를 담당한다.
 *
 * ## Transaction 경계
 *
 * [processDueDeliveries]와 그 하위 처리에는 `@Transactional`이 없다. Bot HTTP 호출은 수 초가
 * 걸릴 수 있고, 그것을 Transaction 안에 넣으면 DB Connection과 Lock을 그만큼 오래 쥐게 된다
 * (.claude/rules/spring-boot.md: 느리거나 실패할 수 있는 I/O를 Transaction 내부에서 수행하지
 * 않는다). 선점(`claim`), Attempt 저장, 상태 반영은 각각 짧은 Transaction으로 나뉜다.
 *
 * ## 동시성
 *
 * 여러 인스턴스나 수동 재시도가 같은 Row를 동시에 집어도
 * [DiscordDeliveryRepository.claim]의 조건부 UPDATE가 하나만 통과시킨다. 분산 Lock Library를
 * 새로 도입하지 않는다(후속 요구사항 문서 §30).
 */
@Service
class DiscordDeliveryServiceImpl(
    private val deliveryRepository: DiscordDeliveryRepository,
    private val attemptRepository: DiscordDeliveryAttemptRepository,
    private val botClient: DiscordBotClient,
    private val payloadFactory: DiscordPayloadFactory,
    private val retryPolicy: DiscordDeliveryRetryPolicy,
    private val properties: DiscordBotProperties,
    private val jobPayloadQueryPort: JobDiscordPayloadQueryPort,
    private val programPayloadQueryPort: ProgramDiscordPayloadQueryPort,
    private val inquiryPayloadQueryPort: InquiryDiscordPayloadQueryPort,
) : DiscordDeliveryService {
    private val log = LoggerFactory.getLogger(DiscordDeliveryServiceImpl::class.java)

    @Transactional
    override fun enqueue(command: DiscordDeliveryEnqueueCommand): Long {
        val template = command.template
        val idempotencyKey =
            DiscordIdempotencyKeys.of(
                targetType = template.targetType,
                targetId = command.targetId,
                action = template.action,
                sourceUpdatedAt = command.sourceUpdatedAt,
            )

        deliveryRepository.findByIdempotencyKey(idempotencyKey)?.let { existing ->
            log.debug("이미 예약된 Discord 전달이라 재생성하지 않습니다: idempotencyKey={}", idempotencyKey)
            return requireNotNull(existing.id)
        }

        val delivery =
            DiscordDelivery(
                targetType = template.targetType,
                targetId = command.targetId,
                action = template.action,
                template = template,
                channelId = command.channelId,
                // Mention은 최초 성공 CREATE 한 번만 허용된다(§24). CREATE가 아닌 Action은
                // 저장 단계에서 아예 비워, 이후 어떤 경로로도 Mention이 새어 나가지 않게 한다.
                roleIds =
                    if (template.action == DiscordDeliveryAction.CREATE) {
                        DiscordDelivery.joinRoleIds(command.roleIds.take(DiscordDelivery.MAX_ROLE_IDS))
                    } else {
                        null
                    },
                idempotencyKey = idempotencyKey,
            )

        return try {
            requireNotNull(deliveryRepository.saveAndFlush(delivery).id)
        } catch (ex: DataIntegrityViolationException) {
            // 같은 Event가 동시에 두 번 처리되면 위 조회를 둘 다 통과한 뒤 하나가 UNIQUE 제약에
            // 걸린다. 이는 정상 흐름이므로 기존 Row의 id를 돌려준다. 다른 원인의 제약 위반까지
            // "이미 존재함"으로 오판하면 실제 저장 실패가 조용히 묻히므로 제약 이름을 확인한다
            // (JobNotificationServiceImpl.createDeliveryOrNull과 같은 방식).
            if (!isIdempotencyKeyViolation(ex)) throw ex
            val existing =
                deliveryRepository.findByIdempotencyKey(idempotencyKey)
                    ?: throw ex
            log.debug("동시 생성으로 UNIQUE 제약에 걸려 기존 Delivery를 재사용합니다: idempotencyKey={}", idempotencyKey)
            requireNotNull(existing.id)
        }
    }

    override fun processDueDeliveries(): Int {
        if (!properties.isConfigured()) return 0

        val now = LocalDateTime.now()
        recoverStaleProcessing(now)

        val dueIds =
            deliveryRepository.findDueIds(
                status = DiscordDeliveryStatus.PENDING,
                now = now,
                pageable = PageRequest.of(0, properties.batchSize),
            )

        return dueIds.count { deliveryId -> processOne(deliveryId) }
    }

    @Transactional
    override fun retryManually(deliveryId: Long) {
        val delivery =
            deliveryRepository
                .findById(deliveryId)
                .orElseThrow { DiscordDeliveryNotFoundException(deliveryId) }

        // 자동 재시도가 끝난 FAILED에서만 허용한다(§18). PROCESSING을 되돌리면 지금 전송 중인
        // 요청과 겹치고, DELIVERED를 다시 보내면 Discord에 중복 메시지가 생긴다.
        if (delivery.status != DiscordDeliveryStatus.FAILED) {
            throw DiscordDeliveryNotRetryableException(deliveryId, delivery.status)
        }
        if (!retryPolicy.canRetryManually(delivery.manualRetryCount)) {
            throw DiscordDeliveryRetryLimitExceededException(deliveryId, delivery.manualRetryCount)
        }

        delivery.markManualRetryRequested()
        deliveryRepository.save(delivery)
        log.info(
            "Discord 전달 수동 재시도를 예약했습니다(deliveryId={}, manualRetryCount={})",
            deliveryId,
            delivery.manualRetryCount,
        )
    }

    private fun recoverStaleProcessing(now: LocalDateTime) {
        val recovered =
            deliveryRepository.recoverStaleProcessing(
                pending = DiscordDeliveryStatus.PENDING,
                processing = DiscordDeliveryStatus.PROCESSING,
                threshold = now.minusNanos(properties.staleProcessingThresholdMs * NANOS_PER_MILLI),
                now = now,
            )
        if (recovered > 0) {
            log.warn("중단된 Discord 전달 {}건을 재시도 대기로 되돌렸습니다.", recovered)
        }
    }

    /**
     * Delivery 한 건을 선점해 전송한다. 선점에 실패했으면(다른 인스턴스가 이미 가져갔거나 상태가
     * 바뀌었으면) 아무것도 하지 않고 false를 반환한다.
     *
     * 선점 실패와 Row 소실 각각을 조기 반환하는 편이 중첩 if보다 읽기 쉽다(`JobIndexQueryPortImpl`과
     * 같은 이유). 예기치 못한 오류로 Sweep 전체가 멈추면 나머지 Delivery까지 함께 지연되므로, 한
     * 건의 `RuntimeException`은 여기서 넓게 잡아 로그로 남긴다(`JobNotificationServiceImpl`과 동일).
     */
    @Suppress("ReturnCount", "TooGenericExceptionCaught")
    private fun processOne(deliveryId: Long): Boolean {
        val claimedAt = LocalDateTime.now()
        val claimed =
            deliveryRepository.claim(
                id = deliveryId,
                pending = DiscordDeliveryStatus.PENDING,
                processing = DiscordDeliveryStatus.PROCESSING,
                now = claimedAt,
            )
        if (claimed != 1) return false

        val delivery = deliveryRepository.findById(deliveryId).orElse(null) ?: return false
        // 수동 재시도로 예약된 첫 시도만 MANUAL로 남긴다. 이후 백오프 재시도는 다시 AUTOMATIC이다.
        val attemptType =
            if (delivery.manualRetryPending) {
                DiscordDeliveryAttemptType.MANUAL
            } else {
                DiscordDeliveryAttemptType.AUTOMATIC
            }
        return try {
            send(delivery, attemptType)
            true
        } catch (ex: RuntimeException) {
            // 한 건의 예기치 못한 오류가 Sweep 전체를 멈추지 않게 한다. 이 Row는 PROCESSING으로
            // 남지만 Stale 회수가 임계값 이후에 되살린다.
            log.error("Discord 전달 처리 중 예기치 못한 오류(deliveryId={})", deliveryId, ex)
            false
        }
    }

    private fun send(
        delivery: DiscordDelivery,
        attemptType: DiscordDeliveryAttemptType,
    ) {
        val deliveryId = requireNotNull(delivery.id)

        val data = resolvePayload(delivery)
        if (data == null) {
            // 존재하지 않는 대상은 재시도해도 생기지 않는다. Bot을 호출하지 않았으므로 Attempt도
            // 남기지 않는다 -- attempts는 "실제 Bot 호출 이력"이다(§12).
            finish(delivery, TARGET_NOT_FOUND, "대상을 찾을 수 없어 Discord 전달을 중단했습니다.", retryable = false)
            return
        }

        val messageId = delivery.discordMessageId
        if (delivery.action != DiscordDeliveryAction.CREATE && messageId == null) {
            // 기존 메시지를 수정해야 하는데 대상 messageId가 없다. 자동으로 새 메시지를 만들지
            // 않는다(§22) -- 중복 공지가 생기기 때문이다.
            finish(
                delivery,
                MISSING_DISCORD_MESSAGE_ID,
                "수정할 Discord 메시지 ID가 없어 전달을 중단했습니다.",
                retryable = false,
            )
            return
        }

        val requestId = UUID.randomUUID().toString()
        val startedAt = LocalDateTime.now()
        val result =
            if (delivery.action == DiscordDeliveryAction.CREATE) {
                botClient.create(
                    DiscordCreateCommand(
                        targetType = delivery.targetType,
                        template = delivery.template,
                        channelId = delivery.channelId,
                        roleIds = delivery.roleIdList(),
                        data = data,
                        requestId = requestId,
                        idempotencyKey = delivery.idempotencyKey,
                    ),
                )
            } else {
                botClient.patch(
                    DiscordPatchCommand(
                        targetType = delivery.targetType,
                        action = delivery.action,
                        template = delivery.template,
                        channelId = delivery.channelId,
                        messageId = requireNotNull(messageId),
                        data = data,
                        requestId = requestId,
                    ),
                )
            }
        val finishedAt = LocalDateTime.now()

        recordAttempt(deliveryId, attemptType, requestId, startedAt, finishedAt, result)
        applyResult(delivery, result, finishedAt)
    }

    private fun resolvePayload(delivery: DiscordDelivery): Map<String, String>? =
        when (delivery.targetType) {
            DiscordDeliveryTargetType.JOB -> {
                jobPayloadQueryPort
                    .findById(delivery.targetId)
                    ?.let { payloadFactory.forJob(delivery.template, it) }
            }

            DiscordDeliveryTargetType.PROGRAM -> {
                programPayloadQueryPort
                    .findById(delivery.targetId)
                    ?.let { payloadFactory.forProgram(delivery.template, it) }
            }

            DiscordDeliveryTargetType.INQUIRY -> {
                inquiryPayloadQueryPort
                    .findById(delivery.targetId)
                    ?.let { payloadFactory.forInquiry(it) }
            }
        }

    private fun applyResult(
        delivery: DiscordDelivery,
        result: DiscordBotResult,
        now: LocalDateTime,
    ) {
        when (result) {
            is DiscordBotResult.Success -> {
                if (result.messageId.isBlank()) {
                    // CREATE가 성공했다는데 messageId가 없으면 이후 UPDATE/CLOSE/DELETE가
                    // 불가능하다. Contract 오류로 취급하고 DELIVERED로 처리하지 않는다(§21).
                    finish(delivery, MISSING_MESSAGE_ID, "Bot이 Discord 메시지 ID를 반환하지 않았습니다.", retryable = false)
                    return
                }
                delivery.markDelivered(result.messageId, now)
                deliveryRepository.save(delivery)
                log.info(
                    "Discord 전달 성공(deliveryId={}, targetType={}, targetId={}, action={})",
                    delivery.id,
                    delivery.targetType,
                    delivery.targetId,
                    delivery.action,
                )
            }

            is DiscordBotResult.Failure -> {
                finish(delivery, result.code, result.message, retryable = result.retryable)
            }
        }
    }

    /**
     * 실패를 상태에 반영한다. 재시도 가능하고 자동 횟수가 남았으면 다음 시각을 예약하고,
     * 아니면 FAILED로 확정한다.
     */
    private fun finish(
        delivery: DiscordDelivery,
        errorCode: String,
        errorMessage: String?,
        retryable: Boolean,
    ) {
        val now = LocalDateTime.now()
        val retryAt =
            if (retryable) retryPolicy.nextRetryAt(delivery.automaticRetryCount, now) else null

        if (retryAt != null) {
            delivery.markRetryScheduled(retryAt, errorCode, errorMessage, now)
        } else {
            delivery.markFailed(errorCode, errorMessage, now)
        }
        deliveryRepository.save(delivery)

        log.warn(
            "Discord 전달 실패(deliveryId={}, targetType={}, targetId={}, action={}, code={}, status={})",
            delivery.id,
            delivery.targetType,
            delivery.targetId,
            delivery.action,
            errorCode,
            delivery.status,
        )
    }

    private fun recordAttempt(
        deliveryId: Long,
        attemptType: DiscordDeliveryAttemptType,
        requestId: String,
        startedAt: LocalDateTime,
        finishedAt: LocalDateTime,
        result: DiscordBotResult,
    ) {
        val failure = result as? DiscordBotResult.Failure
        attemptRepository.save(
            DiscordDeliveryAttempt(
                discordDeliveryId = deliveryId,
                attemptType = attemptType,
                attemptNumber = (attemptRepository.countByDiscordDeliveryId(deliveryId) + 1).toInt(),
                requestId = requestId,
                startedAt = startedAt,
                finishedAt = finishedAt,
                result =
                    if (failure == null) {
                        DiscordDeliveryAttemptResult.SUCCESS
                    } else {
                        DiscordDeliveryAttemptResult.FAILURE
                    },
                errorCode = failure?.code,
                retryable = failure?.retryable,
            ),
        )
    }

    private fun isIdempotencyKeyViolation(ex: DataIntegrityViolationException): Boolean {
        val constraintName = (ex.cause as? ConstraintViolationException)?.constraintName
        return constraintName?.equals(IDEMPOTENCY_KEY_CONSTRAINT, ignoreCase = true) == true ||
            ex.message?.contains(IDEMPOTENCY_KEY_CONSTRAINT, ignoreCase = true) == true
    }

    private companion object {
        const val IDEMPOTENCY_KEY_CONSTRAINT = "uk_discord_deliveries_idempotency_key"
        const val NANOS_PER_MILLI = 1_000_000L

        // 서버가 자체적으로 분류하는 실패 코드다. Bot의 ErrorCode 9종과 겹치지 않는 이름을 쓴다.
        const val TARGET_NOT_FOUND = "TARGET_NOT_FOUND"
        const val MISSING_DISCORD_MESSAGE_ID = "MISSING_DISCORD_MESSAGE_ID"
        const val MISSING_MESSAGE_ID = "MISSING_MESSAGE_ID"
    }
}
