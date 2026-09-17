package team.inreok.getiserver.domain.notification.service.impl

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anySet
import org.mockito.ArgumentMatchers.eq
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import team.inreok.getiserver.domain.job.query.JobDiscordSendTargetQueryPort
import team.inreok.getiserver.domain.job.query.JobDiscordSendTargetSnapshot
import team.inreok.getiserver.domain.notification.config.DiscordBotProperties
import team.inreok.getiserver.domain.notification.entity.DiscordDelivery
import team.inreok.getiserver.domain.notification.entity.type.DiscordDeliveryAction
import team.inreok.getiserver.domain.notification.entity.type.DiscordDeliveryStatus
import team.inreok.getiserver.domain.notification.entity.type.DiscordDeliveryTargetType
import team.inreok.getiserver.domain.notification.entity.type.DiscordMessageTemplate
import team.inreok.getiserver.domain.notification.entity.type.DiscordSendTargetType
import team.inreok.getiserver.domain.notification.exception.DiscordSendTargetInvalidTargetGradeException
import team.inreok.getiserver.domain.notification.repository.DiscordDeliveryRepository
import team.inreok.getiserver.domain.notification.service.DiscordDeliveryRetryPolicy
import team.inreok.getiserver.domain.program.query.ProgramDiscordSendTargetQueryPort
import team.inreok.getiserver.domain.program.query.ProgramDiscordSendTargetSnapshot
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
class DiscordSendTargetQueryServiceImplTest {
    @Mock
    private lateinit var jobQueryPort: JobDiscordSendTargetQueryPort

    @Mock
    private lateinit var programQueryPort: ProgramDiscordSendTargetQueryPort

    @Mock
    private lateinit var deliveryRepository: DiscordDeliveryRepository

    private val properties = DiscordBotProperties(enabled = true, baseUrl = "http://bot:3000", internalApiKey = "key")

    private fun service() =
        DiscordSendTargetQueryServiceImpl(
            jobQueryPort = jobQueryPort,
            programQueryPort = programQueryPort,
            deliveryRepository = deliveryRepository,
            retryPolicy = DiscordDeliveryRetryPolicy(properties),
        )

    @Test
    fun `Job과 Program을 정확한 혼합 순서와 Page 정보로 반환한다`() {
        val job = job(1L, "공고", LocalDateTime.of(2026, 9, 17, 12, 0), 2)
        val program = program(2L, "프로그램", LocalDateTime.of(2026, 9, 17, 11, 0), listOf(1, 2))
        given(jobQueryPort.countPublished(null, null)).willReturn(1L)
        given(programQueryPort.countPublished(null, null)).willReturn(1L)
        given(jobQueryPort.findPublished(null, null, null, null, 100)).willReturn(listOf(job))
        given(programQueryPort.findPublished(null, null, null, null, 100)).willReturn(listOf(program))
        given(deliveryRepository.findLatestCreateDeliveries(anyTargetTypeSet(), anyIdSet(), anyAction()))
            .willReturn(emptyList())
        given(deliveryRepository.findLatestDeliveries(anyTargetTypeSet(), anyIdSet())).willReturn(emptyList())

        val response = service().list(null, null, null, PageRequest.of(0, 20))

        assertThat(response.content.map { it.targetType to it.targetId })
            .containsExactly(DiscordSendTargetType.JOB to 1L, DiscordSendTargetType.PROGRAM to 2L)
        assertThat(response.content[0].targetGrades).containsExactly(2)
        assertThat(response.content[1].targetGrades).containsExactly(1, 2)
        assertThat(response.totalElements).isEqualTo(2L)
        assertThat(response.totalPages).isEqualTo(1)
        assertThat(response.first).isTrue()
        assertThat(response.last).isTrue()
        verify(deliveryRepository).findLatestCreateDeliveries(anyTargetTypeSet(), anyIdSet(), anyAction())
        verify(deliveryRepository).findLatestDeliveries(anyTargetTypeSet(), anyIdSet())
    }

    @Test
    fun `두 번째 Page도 전체 순서 기준으로 누락 없이 반환한다`() {
        val first = job(1L, "첫 번째", LocalDateTime.of(2026, 9, 17, 12, 0), null)
        val second = program(2L, "두 번째", LocalDateTime.of(2026, 9, 17, 11, 0), listOf(2))
        given(jobQueryPort.countPublished(null, null)).willReturn(1L)
        given(programQueryPort.countPublished(null, null)).willReturn(1L)
        given(jobQueryPort.findPublished(null, null, null, null, 100)).willReturn(listOf(first))
        given(programQueryPort.findPublished(null, null, null, null, 100)).willReturn(listOf(second))
        given(deliveryRepository.findLatestCreateDeliveries(anyTargetTypeSet(), anyIdSet(), anyAction()))
            .willReturn(emptyList())
        given(deliveryRepository.findLatestDeliveries(anyTargetTypeSet(), anyIdSet())).willReturn(emptyList())

        val response = service().list(null, null, null, PageRequest.of(1, 1))

        assertThat(response.content.single().targetId).isEqualTo(2L)
        assertThat(response.page).isEqualTo(1)
        assertThat(response.totalElements).isEqualTo(2L)
        assertThat(response.totalPages).isEqualTo(2)
        assertThat(response.last).isTrue()
    }

    @Test
    fun `targetType JOB는 Program Query를 호출하지 않는다`() {
        given(jobQueryPort.countPublished("백엔드", 2)).willReturn(1L)
        given(jobQueryPort.findPublished("백엔드", 2, null, null, 100))
            .willReturn(listOf(job(1L, "백엔드", LocalDateTime.of(2026, 9, 17, 12, 0), 2)))
        given(deliveryRepository.findLatestCreateDeliveries(anyTargetTypeSet(), anyIdSet(), anyAction()))
            .willReturn(emptyList())
        given(deliveryRepository.findLatestDeliveries(anyTargetTypeSet(), anyIdSet())).willReturn(emptyList())

        service().list(DiscordSendTargetType.JOB, " 백엔드 ", 2, PageRequest.of(0, 20))

        verify(jobQueryPort).countPublished("백엔드", 2)
        verify(jobQueryPort).findPublished("백엔드", 2, null, null, 100)
        verify(programQueryPort, never()).countPublished(any(), any())
        verify(programQueryPort, never()).findPublished(any(), any(), any(), any(), anyInt())
    }

    @Test
    fun `targetGrade가 1에서 3을 벗어나면 400용 예외를 던진다`() {
        assertThatThrownBy { service().list(null, null, 4, PageRequest.of(0, 20)) }
            .isInstanceOf(DiscordSendTargetInvalidTargetGradeException::class.java)
    }

    @Test
    fun `최신 CREATE Delivery가 없으면 delivery는 null이다`() {
        given(jobQueryPort.countPublished(null, null)).willReturn(1L)
        given(jobQueryPort.findPublished(null, null, null, null, 100))
            .willReturn(listOf(job(1L, "공고", LocalDateTime.of(2026, 9, 17, 12, 0), null)))
        val update = delivery(9L, DiscordDeliveryAction.UPDATE, DiscordDeliveryStatus.DELIVERED)
        given(deliveryRepository.findLatestCreateDeliveries(anyTargetTypeSet(), anyIdSet(), anyAction()))
            .willReturn(emptyList())
        given(deliveryRepository.findLatestDeliveries(anyTargetTypeSet(), anyIdSet())).willReturn(listOf(update))

        val response = service().list(null, null, null, PageRequest.of(0, 20))

        assertThat(response.content.single().delivery).isNull()
        assertThat(update.id).isEqualTo(9L)
    }

    @Test
    fun `FAILED 최신 CREATE Delivery는 canRetry true다`() {
        val failed =
            delivery(3L, DiscordDeliveryAction.CREATE, DiscordDeliveryStatus.FAILED).apply {
                manualRetryCount =
                    1
            }
        given(jobQueryPort.countPublished(null, null)).willReturn(1L)
        given(jobQueryPort.findPublished(null, null, null, null, 100))
            .willReturn(listOf(job(1L, "공고", LocalDateTime.of(2026, 9, 17, 12, 0), null)))
        given(deliveryRepository.findLatestCreateDeliveries(anyTargetTypeSet(), anyIdSet(), anyAction()))
            .willReturn(listOf(failed))
        given(deliveryRepository.findLatestDeliveries(anyTargetTypeSet(), anyIdSet())).willReturn(listOf(failed))

        val delivery =
            service()
                .list(null, null, null, PageRequest.of(0, 20))
                .content
                .single()
                .delivery

        assertThat(delivery?.status).isEqualTo(DiscordDeliveryStatus.FAILED)
        assertThat(delivery?.manualRetryCount).isEqualTo(1)
        assertThat(delivery?.canRetry).isTrue()
    }

    @ParameterizedTest
    @EnumSource(
        value = DiscordDeliveryStatus::class,
        names = ["PENDING", "PROCESSING", "DELIVERED"],
    )
    fun `진행 중이거나 성공한 CREATE Delivery는 재시도할 수 없다`(status: DiscordDeliveryStatus) {
        val current = delivery(3L, DiscordDeliveryAction.CREATE, status)
        given(jobQueryPort.countPublished(null, null)).willReturn(1L)
        given(jobQueryPort.findPublished(null, null, null, null, 100))
            .willReturn(listOf(job(1L, "공고", LocalDateTime.of(2026, 9, 17, 12, 0), null)))
        given(deliveryRepository.findLatestCreateDeliveries(anyTargetTypeSet(), anyIdSet(), anyAction()))
            .willReturn(listOf(current))
        given(deliveryRepository.findLatestDeliveries(anyTargetTypeSet(), anyIdSet())).willReturn(listOf(current))

        val delivery =
            service()
                .list(null, null, null, PageRequest.of(0, 20))
                .content
                .single()
                .delivery

        assertThat(delivery?.canRetry).isFalse()
    }

    private fun job(
        id: Long,
        title: String,
        createdAt: LocalDateTime,
        targetGrade: Int?,
    ) = JobDiscordSendTargetSnapshot(id, title, targetGrade?.let(::listOf), createdAt)

    private fun program(
        id: Long,
        title: String,
        createdAt: LocalDateTime,
        targetGrades: List<Int>,
    ) = ProgramDiscordSendTargetSnapshot(id, title, targetGrades, createdAt)

    private fun delivery(
        id: Long,
        action: DiscordDeliveryAction,
        status: DiscordDeliveryStatus,
    ) = DiscordDelivery(
        targetType = DiscordDeliveryTargetType.JOB,
        targetId = 1L,
        action = action,
        template =
            if (action ==
                DiscordDeliveryAction.CREATE
            ) {
                DiscordMessageTemplate.JOB_PUBLISHED
            } else {
                DiscordMessageTemplate.JOB_UPDATED
            },
        channelId = "channel-1",
        idempotencyKey = "JOB:1:$action",
    ).apply {
        this.id = id
        this.status = status
        this.createdAt = LocalDateTime.of(2026, 9, 17, 9, 0)
    }

    private fun anyTargetTypeSet(): Set<DiscordDeliveryTargetType> = anySet<DiscordDeliveryTargetType>() ?: emptySet()

    private fun anyIdSet(): Set<Long> = anySet<Long>() ?: emptySet()

    private fun anyAction(): DiscordDeliveryAction =
        any(DiscordDeliveryAction::class.java) ?: DiscordDeliveryAction.CREATE
}
