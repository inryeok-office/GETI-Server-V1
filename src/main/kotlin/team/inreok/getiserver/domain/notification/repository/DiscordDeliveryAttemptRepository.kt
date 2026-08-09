package team.inreok.getiserver.domain.notification.repository

import org.springframework.data.jpa.repository.JpaRepository
import team.inreok.getiserver.domain.notification.entity.DiscordDeliveryAttempt

interface DiscordDeliveryAttemptRepository : JpaRepository<DiscordDeliveryAttempt, Long> {
    fun countByDiscordDeliveryId(discordDeliveryId: Long): Long

    fun findAllByDiscordDeliveryIdOrderByIdAsc(discordDeliveryId: Long): List<DiscordDeliveryAttempt>
}
