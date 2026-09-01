package team.inreok.getiserver.domain.notification.service.impl

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import team.inreok.getiserver.domain.notification.config.NotificationRetentionProperties
import team.inreok.getiserver.domain.notification.repository.NotificationRepository
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
class NotificationRetentionCleanupServiceImplTest {
    @Mock
    private lateinit var notificationRepository: NotificationRepository

    private val properties =
        NotificationRetentionProperties(
            softDeletedRetentionDays = 30,
            readRetentionDays = 180,
            unreadRetentionDays = 365,
            batchSize = 100,
        )

    private val service: NotificationRetentionCleanupServiceImpl by lazy {
        NotificationRetentionCleanupServiceImpl(notificationRepository, properties)
    }

    private fun anyDateTime(): LocalDateTime = any(LocalDateTime::class.java) ?: LocalDateTime.now()

    private fun anyPageable(): Pageable = any(Pageable::class.java) ?: PageRequest.of(0, 1)

    // Mockito의 eq()도 any()와 마찬가지로 참조 Type에서는 null을 반환할 수 있어(Kotlin 비Null
    // 매개변수로 바로 넘기면 Intrinsics NullPointerException) 같은 `?:` 관례로 감싼다
    // (SearchReindexServiceImplTest.eqString/eqSet과 동일한 방식).
    private fun eqDateTime(value: LocalDateTime): LocalDateTime = eq(value) ?: value

    private fun eqPageable(value: Pageable): Pageable = eq(value) ?: value

    @Test
    fun `대상이 없으면 아무것도 지우지 않고 0을 반환한다`() {
        given(notificationRepository.findExpiredSoftDeletedIds(anyDateTime(), anyPageable()))
            .willReturn(emptyList())
        given(notificationRepository.findExpiredReadIds(anyDateTime(), anyPageable()))
            .willReturn(emptyList())
        given(notificationRepository.findExpiredUnreadIds(anyDateTime(), anyPageable()))
            .willReturn(emptyList())

        val deletedCount = service.cleanupExpiredNotifications(LocalDateTime.now())

        assertThat(deletedCount).isZero
        verify(notificationRepository, never()).deleteAllByIdInBatch(any())
    }

    @Test
    fun `세 정책의 대상 id를 모두 Batch 삭제하고 합산 건수를 반환한다`() {
        given(notificationRepository.findExpiredSoftDeletedIds(anyDateTime(), anyPageable()))
            .willReturn(listOf(1L, 2L))
        given(notificationRepository.findExpiredReadIds(anyDateTime(), anyPageable()))
            .willReturn(listOf(3L))
        given(notificationRepository.findExpiredUnreadIds(anyDateTime(), anyPageable()))
            .willReturn(listOf(4L, 5L, 6L))

        val deletedCount = service.cleanupExpiredNotifications(LocalDateTime.now())

        assertThat(deletedCount).isEqualTo(6)
        verify(notificationRepository).deleteAllByIdInBatch(listOf(1L, 2L))
        verify(notificationRepository).deleteAllByIdInBatch(listOf(3L))
        verify(notificationRepository).deleteAllByIdInBatch(listOf(4L, 5L, 6L))
    }

    @Test
    fun `각 정책은 Batch 상한 건수만큼만 조회한다`() {
        given(notificationRepository.findExpiredSoftDeletedIds(anyDateTime(), anyPageable()))
            .willReturn(emptyList())
        given(notificationRepository.findExpiredReadIds(anyDateTime(), anyPageable()))
            .willReturn(emptyList())
        given(notificationRepository.findExpiredUnreadIds(anyDateTime(), anyPageable()))
            .willReturn(emptyList())

        service.cleanupExpiredNotifications(LocalDateTime.now())

        verify(notificationRepository).findExpiredSoftDeletedIds(anyDateTime(), eqPageable(PageRequest.of(0, 100)))
        verify(notificationRepository).findExpiredReadIds(anyDateTime(), eqPageable(PageRequest.of(0, 100)))
        verify(notificationRepository).findExpiredUnreadIds(anyDateTime(), eqPageable(PageRequest.of(0, 100)))
    }

    @Test
    fun `각 정책의 cutoff는 now에서 각자의 보관 일수를 뺀 시각이다`() {
        given(notificationRepository.findExpiredSoftDeletedIds(anyDateTime(), anyPageable()))
            .willReturn(emptyList())
        given(notificationRepository.findExpiredReadIds(anyDateTime(), anyPageable()))
            .willReturn(emptyList())
        given(notificationRepository.findExpiredUnreadIds(anyDateTime(), anyPageable()))
            .willReturn(emptyList())
        val now = LocalDateTime.of(2026, 8, 23, 0, 0)

        service.cleanupExpiredNotifications(now)

        verify(notificationRepository).findExpiredSoftDeletedIds(eqDateTime(now.minusDays(30)), anyPageable())
        verify(notificationRepository).findExpiredReadIds(eqDateTime(now.minusDays(180)), anyPageable())
        verify(notificationRepository).findExpiredUnreadIds(eqDateTime(now.minusDays(365)), anyPageable())
    }
}
