package team.inreok.getiserver.domain.notification.exception

import team.inreok.getiserver.domain.notification.entity.type.DiscordDeliveryStatus
import team.inreok.getiserver.domain.notification.entity.type.DiscordDeliveryTargetType
import team.inreok.getiserver.global.error.BusinessException

class DiscordDeliveryNotFoundException(
    deliveryId: Long,
) : BusinessException(
        DiscordDeliveryErrorCode.DISCORD_DELIVERY_NOT_FOUND,
        "요청한 Discord 전달 이력을 찾을 수 없습니다. (deliveryId=$deliveryId)",
    )

/**
 * 대상(targetType+targetId)으로 조회했는데 Delivery가 아직 없다. 대상 자체가 없거나, 대상은
 * 있지만 Discord Delivery를 만든 적이 없는 경우(발행 조건 미충족 등) 둘 다 여기 해당한다 --
 * 클라이언트 입장에서는 "조회할 상태가 없다"는 결과가 같다.
 */
class DiscordDeliveryNotFoundForTargetException(
    targetType: DiscordDeliveryTargetType,
    targetId: Long,
) : BusinessException(
        DiscordDeliveryErrorCode.DISCORD_DELIVERY_NOT_FOUND,
        "요청한 Discord 전달 이력을 찾을 수 없습니다. (targetType=$targetType, targetId=$targetId)",
    )

/** Program 등록자·담당 교사·개발자가 아닌 사용자가 Discord 상태를 조회·재시도하려 했다(요구사항 §37). */
class DiscordDeliveryManageForbiddenException :
    BusinessException(DiscordDeliveryErrorCode.DISCORD_DELIVERY_MANAGE_FORBIDDEN)

/**
 * FAILED가 아닌 상태에서 수동 재시도를 요청했다(후속 요구사항 문서 §18). 현재 상태를 함께
 * 알려준다 -- 이 값은 운영자가 보는 우리 코드의 문구이지 내부 구현 노출이 아니다.
 */
class DiscordDeliveryNotRetryableException(
    deliveryId: Long,
    status: DiscordDeliveryStatus,
) : BusinessException(
        DiscordDeliveryErrorCode.DISCORD_DELIVERY_NOT_RETRYABLE,
        "실패한 Discord 전달만 다시 시도할 수 있습니다. (deliveryId=$deliveryId, status=$status)",
    )

class DiscordDeliveryRetryLimitExceededException(
    deliveryId: Long,
    manualRetryCount: Int,
) : BusinessException(
        DiscordDeliveryErrorCode.DISCORD_DELIVERY_RETRY_LIMIT_EXCEEDED,
        "수동 재시도 가능 횟수를 모두 사용했습니다. (deliveryId=$deliveryId, manualRetryCount=$manualRetryCount)",
    )
