package team.inreok.getiserver.domain.notification.service.impl

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anySet
import org.mockito.ArgumentMatchers.eq
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import team.inreok.getiserver.domain.inquiry.query.InquiryDiscordPayloadQueryPort
import team.inreok.getiserver.domain.job.query.JobDiscordPayloadQueryPort
import team.inreok.getiserver.domain.notification.config.DiscordBotProperties
import team.inreok.getiserver.domain.notification.entity.DiscordDelivery
import team.inreok.getiserver.domain.notification.entity.type.DiscordDeliveryStatus
import team.inreok.getiserver.domain.notification.entity.type.DiscordDeliveryTargetType
import team.inreok.getiserver.domain.notification.entity.type.DiscordMessageTemplate
import team.inreok.getiserver.domain.notification.repository.DiscordDeliveryRepository
import team.inreok.getiserver.domain.notification.service.DiscordDeliveryRetryPolicy
import team.inreok.getiserver.domain.program.query.ProgramDiscordPayloadQueryPort
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DiscordDeliveryAdminQueryServiceImplTest {
    @Mock
    private lateinit var deliveryRepository: DiscordDeliveryRepository

    @Mock
    private lateinit var jobPayloadQueryPort: JobDiscordPayloadQueryPort

    @Mock
    private lateinit var programPayloadQueryPort: ProgramDiscordPayloadQueryPort

    @Mock
    private lateinit var inquiryPayloadQueryPort: InquiryDiscordPayloadQueryPort

    private val properties =
        DiscordBotProperties(enabled = true, baseUrl = "http://bot:3000", internalApiKey = "key")

    private fun service() =
        DiscordDeliveryAdminQueryServiceImpl(
            deliveryRepository = deliveryRepository,
            jobPayloadQueryPort = jobPayloadQueryPort,
            programPayloadQueryPort = programPayloadQueryPort,
            inquiryPayloadQueryPort = inquiryPayloadQueryPort,
            retryPolicy = DiscordDeliveryRetryPolicy(properties),
            properties = properties,
        )

    // ---------- 목록과 대상 이름 ----------

    @Test
    fun `대상 종류가 섞여 있어도 Domain당 한 번씩만 이름을 조회한다`() {
        val deliveries =
            listOf(
                delivery(id = 1L, template = DiscordMessageTemplate.JOB_PUBLISHED, targetId = 10L),
                delivery(id = 2L, template = DiscordMessageTemplate.JOB_UPDATED, targetId = 11L),
                delivery(id = 3L, template = DiscordMessageTemplate.PROGRAM_PUBLISHED, targetId = 20L),
                delivery(id = 4L, template = DiscordMessageTemplate.INQUIRY_CREATED, targetId = 30L),
            )
        givenPage(deliveries)
        given(jobPayloadQueryPort.findDisplayNamesByIds(setOf(10L, 11L)))
            .willReturn(mapOf(10L to "백엔드 신입 공고", 11L to "프론트엔드 신입 공고"))
        given(programPayloadQueryPort.findDisplayNamesByIds(setOf(20L))).willReturn(mapOf(20L to "해커톤"))
        given(inquiryPayloadQueryPort.findDisplayNamesByIds(setOf(30L))).willReturn(mapOf(30L to "ERROR"))

        val response = service().listRecent(null, PageRequest.of(0, 20))

        // Port마다 정확히 1회 -- 행마다 부르면 N+1이 된다.
        verify(jobPayloadQueryPort).findDisplayNamesByIds(setOf(10L, 11L))
        verify(programPayloadQueryPort).findDisplayNamesByIds(setOf(20L))
        verify(inquiryPayloadQueryPort).findDisplayNamesByIds(setOf(30L))
        assertThat(response.content.map { it.targetName })
            .containsExactly("백엔드 신입 공고", "프론트엔드 신입 공고", "해커톤", "ERROR")
    }

    @Test
    fun `해당 종류의 대상이 없으면 그 Port를 호출하지 않는다`() {
        givenPage(listOf(delivery(id = 1L, template = DiscordMessageTemplate.JOB_PUBLISHED, targetId = 10L)))
        given(jobPayloadQueryPort.findDisplayNamesByIds(setOf(10L))).willReturn(mapOf(10L to "백엔드 신입 공고"))

        service().listRecent(null, PageRequest.of(0, 20))

        verify(programPayloadQueryPort, never()).findDisplayNamesByIds(anyIdSet())
        verify(inquiryPayloadQueryPort, never()).findDisplayNamesByIds(anyIdSet())
    }

    @Test
    fun `원본이 사라진 대상의 이름은 null이다`() {
        givenPage(listOf(delivery(id = 1L, template = DiscordMessageTemplate.JOB_PUBLISHED, targetId = 10L)))
        given(jobPayloadQueryPort.findDisplayNamesByIds(setOf(10L))).willReturn(emptyMap())

        val response = service().listRecent(null, PageRequest.of(0, 20))

        assertThat(response.content.single().targetName).isNull()
    }

    @Test
    fun `공고와 프로그램의 대상 ID가 같아도 이름이 섞이지 않는다`() {
        givenPage(
            listOf(
                delivery(id = 1L, template = DiscordMessageTemplate.JOB_PUBLISHED, targetId = 1L),
                delivery(id = 2L, template = DiscordMessageTemplate.PROGRAM_PUBLISHED, targetId = 1L),
            ),
        )
        given(jobPayloadQueryPort.findDisplayNamesByIds(setOf(1L))).willReturn(mapOf(1L to "공고 제목"))
        given(programPayloadQueryPort.findDisplayNamesByIds(setOf(1L))).willReturn(mapOf(1L to "프로그램 제목"))

        val response = service().listRecent(null, PageRequest.of(0, 20))

        assertThat(response.content.map { it.targetName }).containsExactly("공고 제목", "프로그램 제목")
    }

    @Test
    fun `결과가 비면 다른 Module을 조회하지 않고 빈 목록을 반환한다`() {
        givenPage(emptyList())

        val response = service().listRecent(null, PageRequest.of(0, 20))

        assertThat(response.content).isEmpty()
        assertThat(response.totalElements).isZero()
        verify(jobPayloadQueryPort, never()).findDisplayNamesByIds(anyIdSet())
        verify(programPayloadQueryPort, never()).findDisplayNamesByIds(anyIdSet())
        verify(inquiryPayloadQueryPort, never()).findDisplayNamesByIds(anyIdSet())
        verify(deliveryRepository, never()).findLatestDeliveryIds(anyTargetTypeSet(), anyIdSet())
    }

    // ---------- canRetry ----------

    @Test
    fun `대상의 최신 실패 전달은 재시도할 수 있다`() {
        val failed = delivery(id = 5L, status = DiscordDeliveryStatus.FAILED, targetId = 10L)
        givenPage(listOf(failed), latestIds = listOf(5L))

        val response = service().listRecent(null, PageRequest.of(0, 20))

        assertThat(response.content.single().canRetry).isTrue()
    }

    @Test
    fun `최신이 아닌 실패 전달은 재시도할 수 없다`() {
        // 공고 게시(CREATE)가 실패한 뒤 수정(UPDATE)이 성공한 상황이다. 재시도 API는 대상의
        // 최신 Delivery만 다시 시도하므로, 목록이 이 행에 재시도 버튼을 띄우면 409가 난다.
        val failedCreate =
            delivery(
                id = 5L,
                template = DiscordMessageTemplate.JOB_PUBLISHED,
                status = DiscordDeliveryStatus.FAILED,
                targetId = 10L,
            )
        val deliveredUpdate =
            delivery(
                id = 9L,
                template = DiscordMessageTemplate.JOB_UPDATED,
                status = DiscordDeliveryStatus.DELIVERED,
                targetId = 10L,
            )
        givenPage(listOf(deliveredUpdate, failedCreate), latestIds = listOf(9L))

        val response = service().listRecent(null, PageRequest.of(0, 20))

        assertThat(response.content.map { it.deliveryId to it.canRetry })
            .containsExactly(9L to false, 5L to false)
    }

    @Test
    fun `수동 재시도 상한을 모두 쓴 실패 전달은 재시도할 수 없다`() {
        val exhausted =
            delivery(
                id = 5L,
                status = DiscordDeliveryStatus.FAILED,
                manualRetryCount = properties.maxManualRetryCount,
                targetId = 10L,
            )
        givenPage(listOf(exhausted), latestIds = listOf(5L))

        val response = service().listRecent(null, PageRequest.of(0, 20))

        assertThat(response.content.single().canRetry).isFalse()
    }

    @Test
    fun `성공한 전달은 최신이어도 재시도할 수 없다`() {
        val delivered = delivery(id = 5L, status = DiscordDeliveryStatus.DELIVERED, targetId = 10L)
        givenPage(listOf(delivered), latestIds = listOf(5L))

        val response = service().listRecent(null, PageRequest.of(0, 20))

        assertThat(response.content.single().canRetry).isFalse()
    }

    // ---------- Filter와 Pagination ----------

    @Test
    fun `status Filter는 그대로 Repository에 전달된다`() {
        givenPage(emptyList())

        service().listRecent(DiscordDeliveryStatus.FAILED, PageRequest.of(0, 20))

        val statusCaptor = ArgumentCaptor.forClass(DiscordDeliveryStatus::class.java)
        verify(deliveryRepository).findRecent(statusCaptor.capture(), any(), any(), anyPageable())
        assertThat(statusCaptor.value).isEqualTo(DiscordDeliveryStatus.FAILED)
    }

    @Test
    fun `클라이언트가 보낸 Sort는 Repository로 전달되지 않는다`() {
        // 정렬은 Repository Query의 ORDER BY가 고정한다. Sort를 그대로 넘기면 충돌한다.
        givenPage(emptyList())

        service().listRecent(null, PageRequest.of(1, 50, Sort.by("createdAt").ascending()))

        val pageableCaptor = ArgumentCaptor.forClass(Pageable::class.java)
        verify(deliveryRepository).findRecent(any(), any(), any(), pageableCaptor.capture() ?: Pageable.unpaged())
        assertThat(pageableCaptor.value.sort.isSorted).isFalse()
        assertThat(pageableCaptor.value.pageNumber).isEqualTo(1)
        assertThat(pageableCaptor.value.pageSize).isEqualTo(50)
    }

    @Test
    fun `재시도 상한은 설정값을 그대로 노출한다`() {
        givenPage(listOf(delivery(id = 1L, targetId = 10L)), latestIds = listOf(1L))

        val item = service().listRecent(null, PageRequest.of(0, 20)).content.single()

        assertThat(item.maxAutomaticRetryCount).isEqualTo(properties.maxAutomaticRetryCount)
        assertThat(item.maxManualRetryCount).isEqualTo(properties.maxManualRetryCount)
    }

    @Test
    fun `기간 Filter는 lastAttemptAt 기준으로 Repository에 전달된다`() {
        givenPage(emptyList())
        val startAt = LocalDateTime.of(2026, 8, 25, 0, 0)
        val endAt = LocalDateTime.of(2026, 8, 26, 0, 0)

        service().listRecent(DiscordDeliveryStatus.FAILED, PageRequest.of(0, 20), startAt, endAt)

        verify(deliveryRepository).findRecent(eq(DiscordDeliveryStatus.FAILED), eq(startAt), eq(endAt), anyPageable())
    }

    // ---------- Fixture ----------

    // Mockito의 any()/anySet()은 null을 돌려주는데 이 Repository/Port 인자는 Kotlin non-null이라
    // 그대로 쓰면 NPE가 난다. 기존 Test(NotificationControllerTest.anyPageable)와 같은 방식으로
    // 감싼다.
    private fun anyPageable(): Pageable = any(Pageable::class.java) ?: Pageable.unpaged()

    private fun anyIdSet(): Set<Long> = anySet<Long>() ?: emptySet()

    private fun anyTargetTypeSet(): Set<DiscordDeliveryTargetType> = anySet<DiscordDeliveryTargetType>() ?: emptySet()

    private fun givenPage(
        deliveries: List<DiscordDelivery>,
        latestIds: List<Long> = deliveries.mapNotNull { it.id },
    ) {
        given(deliveryRepository.findRecent(any(), any(), any(), anyPageable()))
            .willReturn(PageImpl(deliveries, PageRequest.of(0, 20), deliveries.size.toLong()))
        given(deliveryRepository.findLatestDeliveryIds(anyTargetTypeSet(), anyIdSet())).willReturn(latestIds)
    }

    private fun delivery(
        id: Long,
        template: DiscordMessageTemplate = DiscordMessageTemplate.JOB_PUBLISHED,
        status: DiscordDeliveryStatus = DiscordDeliveryStatus.PENDING,
        manualRetryCount: Int = 0,
        targetId: Long = 100L,
    ) = DiscordDelivery(
        targetType = template.targetType,
        targetId = targetId,
        action = template.action,
        template = template,
        channelId = "channel-1",
        idempotencyKey = "${template.targetType}:$targetId:${template.action}",
    ).apply {
        this.id = id
        this.status = status
        this.manualRetryCount = manualRetryCount
        // @CreationTimestamp는 실제 저장 시점에만 채워진다. requestedAt이 이 값을 요구하므로
        // Test Fixture에서도 채워 둔다.
        this.createdAt = LocalDateTime.of(2026, 8, 20, 9, 0)
    }
}
