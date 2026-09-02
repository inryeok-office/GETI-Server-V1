package team.inreok.getiserver.domain.notification.service.impl

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.notification.dto.NotificationDeviceRegisterRequest
import team.inreok.getiserver.domain.notification.dto.NotificationDeviceResponse
import team.inreok.getiserver.domain.notification.exception.NotificationDeviceAccessDeniedException
import team.inreok.getiserver.domain.notification.exception.NotificationDeviceNotFoundException
import team.inreok.getiserver.domain.notification.repository.NotificationDeviceRepository
import team.inreok.getiserver.domain.notification.service.NotificationDeviceService

@Service
class NotificationDeviceServiceImpl(
    private val notificationDeviceRepository: NotificationDeviceRepository,
) : NotificationDeviceService {
    @Transactional
    override fun register(
        memberId: Long,
        request: NotificationDeviceRegisterRequest,
    ): NotificationDeviceResponse {
        // find-then-save 2단계 대신 Upsert 하나로 처리해 같은 deviceKey로 거의 동시에 도착하는
        // 등록 요청(재시도, 다른 회원의 소유권 이전 포함)에서도 uk_notification_devices_device_key
        // 위반 없이 하나의 Row로 수렴한다(NotificationDeviceRepository.upsert KDoc 참고).
        notificationDeviceRepository.upsert(
            memberId = memberId,
            deviceKey = request.deviceKey,
            pushToken = request.pushToken,
            platform = request.platform.name,
        )
        val saved =
            requireNotNull(notificationDeviceRepository.findByDeviceKey(request.deviceKey)) {
                "Upsert 직후에는 NotificationDevice를 찾을 수 있어야 합니다."
            }
        return NotificationDeviceResponse(
            deviceKey = saved.deviceKey,
            platform = saved.platform,
            updatedAt = requireNotNull(saved.updatedAt) { "저장된 NotificationDevice는 updatedAt을 가져야 합니다." },
        )
    }

    @Transactional
    override fun unregister(
        memberId: Long,
        deviceKey: String,
    ) {
        val device =
            notificationDeviceRepository.findByDeviceKey(deviceKey)
                ?: throw NotificationDeviceNotFoundException(deviceKey)
        if (device.memberId != memberId) throw NotificationDeviceAccessDeniedException()
        notificationDeviceRepository.delete(device)
    }
}
