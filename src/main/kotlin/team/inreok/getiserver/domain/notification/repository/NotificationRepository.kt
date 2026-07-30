package team.inreok.getiserver.domain.notification.repository

import org.springframework.data.jpa.repository.JpaRepository
import team.inreok.getiserver.domain.notification.entity.Notification

interface NotificationRepository : JpaRepository<Notification, Long>
