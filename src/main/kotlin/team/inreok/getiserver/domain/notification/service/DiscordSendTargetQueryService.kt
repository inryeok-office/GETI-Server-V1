package team.inreok.getiserver.domain.notification.service

import org.springframework.data.domain.Pageable
import team.inreok.getiserver.domain.notification.dto.DiscordSendTargetListResponse
import team.inreok.getiserver.domain.notification.entity.type.DiscordSendTargetType

interface DiscordSendTargetQueryService {
    fun list(
        targetType: DiscordSendTargetType?,
        targetName: String?,
        targetGrade: Int?,
        pageable: Pageable,
    ): DiscordSendTargetListResponse
}
