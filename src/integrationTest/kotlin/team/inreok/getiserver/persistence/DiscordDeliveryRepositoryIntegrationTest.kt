package team.inreok.getiserver.persistence

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.PageRequest
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import team.inreok.getiserver.domain.notification.entity.DiscordDelivery
import team.inreok.getiserver.domain.notification.entity.DiscordDeliveryAttempt
import team.inreok.getiserver.domain.notification.entity.type.DiscordDeliveryAttemptResult
import team.inreok.getiserver.domain.notification.entity.type.DiscordDeliveryAttemptType
import team.inreok.getiserver.domain.notification.entity.type.DiscordDeliveryStatus
import team.inreok.getiserver.domain.notification.entity.type.DiscordMessageTemplate
import team.inreok.getiserver.domain.notification.repository.DiscordDeliveryAttemptRepository
import team.inreok.getiserver.domain.notification.repository.DiscordDeliveryRepository
import java.time.LocalDateTime

/**
 * Discord 전달 Table(V19)과 Worker Query가 실제 PostgreSQL에서 의도대로 동작하는지 검증한다.
 * `ddl-auto=validate`가 함께 돌아 [DiscordDelivery]·[DiscordDeliveryAttempt] Mapping이 V19
 * Schema와 일치하는지도 확인된다.
 *
 * 실제 Discord나 실제 Bot을 호출하지 않는다(후속 요구사항 문서 §56).
 */
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration::class)
class DiscordDeliveryRepositoryIntegrationTest
    @Autowired
    constructor(
        private val deliveryRepository: DiscordDeliveryRepository,
        private val attemptRepository: DiscordDeliveryAttemptRepository,
    ) {
        private val now = LocalDateTime.of(2026, 8, 9, 12, 0)

        @BeforeEach
        fun setUp() {
            attemptRepository.deleteAll()
            attemptRepository.flush()
            deliveryRepository.deleteAll()
            deliveryRepository.flush()
        }

        @Test
        fun `Delivery를 저장하고 Idempotency Key로 다시 찾는다`() {
            val saved = deliveryRepository.saveAndFlush(delivery(key = "PROGRAM:1:CREATE"))

            val found = deliveryRepository.findByIdempotencyKey("PROGRAM:1:CREATE")

            assertThat(found?.id).isEqualTo(saved.id)
            assertThat(found?.status).isEqualTo(DiscordDeliveryStatus.PENDING)
        }

        @Test
        fun `같은 Idempotency Key로는 두 번 저장할 수 없다`() {
            // 같은 Domain Event가 중복 수신돼도 Row가 하나만 생기게 하는 제약이다(요구사항 §49).
            deliveryRepository.saveAndFlush(delivery(key = "PROGRAM:1:CREATE"))

            assertThatThrownBy { deliveryRepository.saveAndFlush(delivery(key = "PROGRAM:1:CREATE")) }
                .isInstanceOf(DataIntegrityViolationException::class.java)
        }

        @Test
        fun `아직 시도하지 않은 Delivery는 재시도 시각과 무관하게 대상이 된다`() {
            val target = deliveryRepository.saveAndFlush(delivery(key = "PROGRAM:1:CREATE"))

            val dueIds = findDueIds()

            assertThat(dueIds).containsExactly(target.id)
        }

        @Test
        fun `재시도 시각이 아직 오지 않은 Delivery는 대상에서 빠진다`() {
            deliveryRepository.saveAndFlush(
                delivery(key = "PROGRAM:1:CREATE").apply { nextRetryAt = now.plusMinutes(5) },
            )

            assertThat(findDueIds()).isEmpty()
        }

        @Test
        fun `재시도 시각이 지난 Delivery는 대상이 된다`() {
            val target =
                deliveryRepository.saveAndFlush(
                    delivery(key = "PROGRAM:1:CREATE").apply { nextRetryAt = now.minusMinutes(1) },
                )

            assertThat(findDueIds()).containsExactly(target.id)
        }

        @Test
        fun `FAILED와 DELIVERED는 Worker 대상에서 빠진다`() {
            deliveryRepository.saveAndFlush(
                delivery(key = "PROGRAM:1:CREATE").apply { status = DiscordDeliveryStatus.FAILED },
            )
            deliveryRepository.saveAndFlush(
                delivery(key = "PROGRAM:2:CREATE").apply { status = DiscordDeliveryStatus.DELIVERED },
            )

            assertThat(findDueIds()).isEmpty()
        }

        @Test
        fun `선점은 한 번만 성공한다`() {
            // 두 인스턴스가 같은 Row를 동시에 집어도 하나만 처리 권한을 얻는다(요구사항 §30).
            val target = deliveryRepository.saveAndFlush(delivery(key = "PROGRAM:1:CREATE"))
            val id = requireNotNull(target.id)

            val first = claim(id)
            val second = claim(id)

            assertThat(first).isEqualTo(1)
            assertThat(second).isZero()
        }

        @Test
        fun `선점하면 PROCESSING과 선점 시각이 저장된다`() {
            val target = deliveryRepository.saveAndFlush(delivery(key = "PROGRAM:1:CREATE"))
            val id = requireNotNull(target.id)

            claim(id)

            val claimed = requireNotNull(deliveryRepository.findById(id).orElse(null))
            assertThat(claimed.status).isEqualTo(DiscordDeliveryStatus.PROCESSING)
            assertThat(claimed.processingStartedAt).isNotNull()
        }

        @Test
        fun `임계값을 넘긴 PROCESSING만 회수한다`() {
            val stale =
                deliveryRepository.saveAndFlush(
                    delivery(key = "PROGRAM:1:CREATE").apply {
                        status = DiscordDeliveryStatus.PROCESSING
                        processingStartedAt = now.minusMinutes(30)
                    },
                )
            val running =
                deliveryRepository.saveAndFlush(
                    delivery(key = "PROGRAM:2:CREATE").apply {
                        status = DiscordDeliveryStatus.PROCESSING
                        processingStartedAt = now.minusSeconds(10)
                    },
                )

            val recovered = recoverStale(threshold = now.minusMinutes(5))

            assertThat(recovered).isEqualTo(1)
            assertThat(reload(stale).status).isEqualTo(DiscordDeliveryStatus.PENDING)
            assertThat(reload(stale).processingStartedAt).isNull()
            // 다른 인스턴스가 정상적으로 전송 중인 Row는 건드리지 않는다.
            assertThat(reload(running).status).isEqualTo(DiscordDeliveryStatus.PROCESSING)
        }

        @Test
        fun `회수된 Delivery는 다음 Sweep의 대상이 된다`() {
            val stale =
                deliveryRepository.saveAndFlush(
                    delivery(key = "PROGRAM:1:CREATE").apply {
                        status = DiscordDeliveryStatus.PROCESSING
                        processingStartedAt = now.minusMinutes(30)
                    },
                )

            recoverStale(threshold = now.minusMinutes(5))

            assertThat(findDueIds()).containsExactly(stale.id)
        }

        @Test
        fun `대상별 최신 Delivery를 찾는다`() {
            val template = DiscordMessageTemplate.PROGRAM_UPDATED
            deliveryRepository.saveAndFlush(delivery(key = "PROGRAM:1:UPDATE:1", template = template))
            val latest =
                deliveryRepository.saveAndFlush(delivery(key = "PROGRAM:1:UPDATE:2", template = template))

            val found =
                deliveryRepository.findFirstByTargetTypeAndTargetIdOrderByIdDesc(template.targetType, 1L)

            assertThat(found?.id).isEqualTo(latest.id)
        }

        @Test
        fun `시도 이력을 Delivery에 연결해 저장한다`() {
            val target = deliveryRepository.saveAndFlush(delivery(key = "PROGRAM:1:CREATE"))
            val deliveryId = requireNotNull(target.id)

            attemptRepository.saveAndFlush(attempt(deliveryId, attemptNumber = 1))
            attemptRepository.saveAndFlush(attempt(deliveryId, attemptNumber = 2))

            assertThat(attemptRepository.countByDiscordDeliveryId(deliveryId)).isEqualTo(2)
            assertThat(attemptRepository.findAllByDiscordDeliveryIdOrderByIdAsc(deliveryId))
                .extracting<Int> { it.attemptNumber }
                .containsExactly(1, 2)
        }

        @Test
        fun `존재하지 않는 Delivery에는 시도 이력을 남길 수 없다`() {
            assertThatThrownBy { attemptRepository.saveAndFlush(attempt(discordDeliveryId = 999_999L)) }
                .isInstanceOf(DataIntegrityViolationException::class.java)
        }

        @Test
        fun `Mention Role 목록을 저장하고 다시 읽는다`() {
            val roleIds = (1..10).map { "role-$it" }
            deliveryRepository.saveAndFlush(
                DiscordDelivery(
                    targetType = DiscordMessageTemplate.PROGRAM_PUBLISHED.targetType,
                    targetId = 1L,
                    action = DiscordMessageTemplate.PROGRAM_PUBLISHED.action,
                    template = DiscordMessageTemplate.PROGRAM_PUBLISHED,
                    channelId = "channel-1",
                    roleIds = DiscordDelivery.joinRoleIds(roleIds),
                    idempotencyKey = "PROGRAM:1:CREATE",
                ),
            )

            val found = requireNotNull(deliveryRepository.findByIdempotencyKey("PROGRAM:1:CREATE"))

            // Snowflake 64자 × 10개도 Column 길이 안에 들어가야 한다.
            assertThat(found.roleIdList()).isEqualTo(roleIds)
        }

        // --- Helper -------------------------------------------------------

        private fun findDueIds() =
            deliveryRepository.findDueIds(DiscordDeliveryStatus.PENDING, now, PageRequest.of(0, 50))

        private fun claim(id: Long) =
            deliveryRepository.claim(
                id = id,
                pending = DiscordDeliveryStatus.PENDING,
                processing = DiscordDeliveryStatus.PROCESSING,
                now = now,
            )

        private fun recoverStale(threshold: LocalDateTime) =
            deliveryRepository.recoverStaleProcessing(
                pending = DiscordDeliveryStatus.PENDING,
                processing = DiscordDeliveryStatus.PROCESSING,
                threshold = threshold,
                now = now,
            )

        private fun reload(delivery: DiscordDelivery) =
            requireNotNull(deliveryRepository.findById(requireNotNull(delivery.id)).orElse(null))

        private fun delivery(
            key: String,
            template: DiscordMessageTemplate = DiscordMessageTemplate.PROGRAM_PUBLISHED,
            targetId: Long = key.split(":")[1].toLong(),
        ) = DiscordDelivery(
            targetType = template.targetType,
            targetId = targetId,
            action = template.action,
            template = template,
            channelId = "channel-1",
            idempotencyKey = key,
        )

        private fun attempt(
            discordDeliveryId: Long,
            attemptNumber: Int = 1,
        ) = DiscordDeliveryAttempt(
            discordDeliveryId = discordDeliveryId,
            attemptType = DiscordDeliveryAttemptType.AUTOMATIC,
            attemptNumber = attemptNumber,
            requestId = "req-$attemptNumber",
            startedAt = now,
            finishedAt = now.plusSeconds(1),
            result = DiscordDeliveryAttemptResult.SUCCESS,
        )

        companion object {
            @Container
            @ServiceConnection
            @JvmStatic
            val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:18.4-alpine"))
        }
    }
