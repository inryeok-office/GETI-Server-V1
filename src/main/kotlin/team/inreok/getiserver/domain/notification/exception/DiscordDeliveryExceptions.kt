package team.inreok.getiserver.domain.notification.exception

import team.inreok.getiserver.domain.notification.entity.type.DiscordDeliveryStatus
import team.inreok.getiserver.global.error.BusinessException

class DiscordDeliveryNotFoundException(
    deliveryId: Long,
) : BusinessException(
        DiscordDeliveryErrorCode.DISCORD_DELIVERY_NOT_FOUND,
        "요청한 Discord 전달 이력을 찾을 수 없습니다. (deliveryId=$deliveryId)",
    )

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
