package team.inreok.getiserver.domain.notification

import com.redis.testcontainers.RedisContainer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import team.inreok.getiserver.domain.member.entity.Member
import team.inreok.getiserver.domain.member.entity.type.MemberStatus
import team.inreok.getiserver.domain.member.entity.type.OAuthProvider
import team.inreok.getiserver.domain.member.repository.MemberRepository
import team.inreok.getiserver.domain.notification.dto.NotificationDeviceRegisterRequest
import team.inreok.getiserver.domain.notification.entity.type.PushPlatform
import team.inreok.getiserver.domain.notification.exception.NotificationDeviceAccessDeniedException
import team.inreok.getiserver.domain.notification.exception.NotificationDeviceNotFoundException
import team.inreok.getiserver.domain.notification.repository.NotificationDeviceRepository
import team.inreok.getiserver.domain.notification.repository.NotificationSettingRepository
import team.inreok.getiserver.domain.notification.service.NotificationDeviceService
import team.inreok.getiserver.domain.notification.service.NotificationSettingService
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 푸시 기기 등록·해제 및 알림 설정(Issue #189)이 실제 PostgreSQL에서 의도대로 동작하는지 검증한다.
 *
 * Service Test(Mock Repository)로는 확인할 수 없는 것만 여기서 본다.
 * - `uk_notification_devices_device_key` Global Unique 제약과 `NotificationDeviceRepository.upsert`
 *   (`INSERT ... ON CONFLICT`)가 실제로 재등록·소유권 이전을 새 Row 없이 처리하는지
 * - 같은 deviceKey로 동시에 도착하는 등록 요청이 Unique 제약 위반 없이 하나의 Row로 수렴하는지
 * - `notification_settings`의 `member_id` Upsert가 동시 최초 설정 요청에서도 멱등한지
 * - `pushEnabled=false`로 꺼도 `notification_devices` Row가 삭제되지 않는지
 *
 * 이 Class는 `@SpringBootTest`라 `@DataJpaTest`(`RecommendationPersistenceIntegrationTest` 등)와
 * 달리 Test Method마다 Transaction을 자동 Rollback하지 않는다 -- 같은 Testcontainers DB를 모든
 * Test Method가 공유한다. 그래서 `repository.count()` 같은 전체 Table 기준 단언은 다른 Test
 * Method가 남긴 Row까지 세어 실패할 수 있다(`NotificationInboxPersistenceIntegrationTest`와 같은
 * 이유로 memberId/deviceKey를 Test마다 새로 만들어 범위를 좁히고, "같은 Row인지"는 Table 전체
 * 개수 대신 갱신 전후 대리 PK(`id`)가 그대로인지로 검증한다).
 */
@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = [
        "app.jwt.secret=notification-device-persistence-integration-test-only-jwt-secret-value",
        "app.jwt.access-token-expiration-seconds=1800",
        "app.jwt.refresh-token-expiration-seconds=1209600",
        "app.file.storage.bucket=geti-integration-test",
        "app.file.storage.region=us-east-1",
        "app.file.storage.access-key=integration-test-only-access-key",
        "app.file.storage.secret-key=integration-test-only-secret-key",
    ],
)
class NotificationDevicePersistenceIntegrationTest {
    @Autowired
    private lateinit var notificationDeviceService: NotificationDeviceService

    @Autowired
    private lateinit var notificationSettingService: NotificationSettingService

    @Autowired
    private lateinit var notificationDeviceRepository: NotificationDeviceRepository

    @Autowired
    private lateinit var notificationSettingRepository: NotificationSettingRepository

    @Autowired
    private lateinit var memberRepository: MemberRepository

    private var ownerId: Long = 0
    private var otherId: Long = 0

    @BeforeEach
    fun setUp() {
        ownerId = createMember()
        otherId = createMember()
    }

    // ---------- 기기 등록/갱신 ----------

    @Test
    fun `처음 등록하면 새 Row가 생긴다`() {
        val deviceKey = uniqueDeviceKey()

        val response = register(ownerId, deviceKey, "token-1", PushPlatform.ANDROID)

        assertThat(response.deviceKey).isEqualTo(deviceKey)
        assertThat(response.platform).isEqualTo(PushPlatform.ANDROID)
        val saved = notificationDeviceRepository.findByDeviceKey(deviceKey)
        assertThat(saved).isNotNull()
        assertThat(saved?.memberId).isEqualTo(ownerId)
        assertThat(saved?.pushToken).isEqualTo("token-1")
    }

    @Test
    fun `같은 deviceKey로 재등록하면 새 Row를 만들지 않고 기존 Row를 갱신한다`() {
        val deviceKey = uniqueDeviceKey()
        register(ownerId, deviceKey, "token-1", PushPlatform.ANDROID)
        val firstId = requireNotNull(notificationDeviceRepository.findByDeviceKey(deviceKey)).id

        register(ownerId, deviceKey, "token-2", PushPlatform.ANDROID)

        val saved = requireNotNull(notificationDeviceRepository.findByDeviceKey(deviceKey))
        assertThat(saved.id).isEqualTo(firstId)
        assertThat(saved.pushToken).isEqualTo("token-2")
    }

    @Test
    fun `재등록으로 pushToken과 platform이 함께 바뀐다`() {
        val deviceKey = uniqueDeviceKey()
        register(ownerId, deviceKey, "token-1", PushPlatform.ANDROID)

        val response = register(ownerId, deviceKey, "token-2", PushPlatform.IOS)

        assertThat(response.platform).isEqualTo(PushPlatform.IOS)
        val saved = requireNotNull(notificationDeviceRepository.findByDeviceKey(deviceKey))
        assertThat(saved.pushToken).isEqualTo("token-2")
        assertThat(saved.platform).isEqualTo(PushPlatform.IOS)
    }

    @Test
    fun `다른 회원이 같은 deviceKey로 등록하면 소유권이 이전되고 Row는 하나만 유지된다`() {
        val deviceKey = uniqueDeviceKey()
        register(ownerId, deviceKey, "token-1", PushPlatform.ANDROID)
        val firstId = requireNotNull(notificationDeviceRepository.findByDeviceKey(deviceKey)).id

        val response = register(otherId, deviceKey, "token-2", PushPlatform.IOS)

        assertThat(response.deviceKey).isEqualTo(deviceKey)
        val saved = requireNotNull(notificationDeviceRepository.findByDeviceKey(deviceKey))
        // 소유권 이전도 같은 물리 Row(같은 대리 PK)를 갱신한 것이지, 새 Row를 추가한 것이 아니다.
        assertThat(saved.id).isEqualTo(firstId)
        assertThat(saved.memberId).isEqualTo(otherId)
        assertThat(saved.pushToken).isEqualTo("token-2")
    }

    @Test
    fun `같은 deviceKey로 동시에 등록해도 uk_notification_devices_device_key 위반 없이 하나의 Row로 수렴한다`() {
        val deviceKey = uniqueDeviceKey()
        val executor = Executors.newFixedThreadPool(2)
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val results = ConcurrentLinkedQueue<Result<Unit>>()

        listOf(ownerId to "token-a", otherId to "token-b").forEach { (memberId, token) ->
            executor.submit {
                ready.countDown()
                start.await()
                val result =
                    runCatching {
                        register(memberId, deviceKey, token, PushPlatform.ANDROID)
                        Unit
                    }
                results.add(result)
            }
        }
        ready.await()
        start.countDown()
        executor.shutdown()
        check(executor.awaitTermination(30, TimeUnit.SECONDS)) { "동시 등록 Thread가 제한 시간 안에 끝나지 않았습니다." }

        // 두 요청 모두 uk_notification_devices_device_key 위반(DataIntegrityViolationException)
        // 없이 성공해야 한다 -- upsert(INSERT ... ON CONFLICT)가 아니라 순수 INSERT였다면 둘 중
        // 하나는 여기서 예외로 실패했을 것이다.
        assertThat(results).allSatisfy { assertThat(it.isSuccess).isTrue() }
        // deviceKey가 DB 차원에서 Global Unique이므로, 이 조회가 예외 없이 정확히 한 Row를
        // 반환한다는 사실 자체가 두 요청이 하나의 Row로 수렴했다는 증거다(중복 Row가 있었다면
        // Unique Index 위반으로 애초에 존재할 수 없다).
        val saved = requireNotNull(notificationDeviceRepository.findByDeviceKey(deviceKey))
        assertThat(saved.memberId).isIn(ownerId, otherId)
        assertThat(saved.pushToken).isIn("token-a", "token-b")
    }

    // ---------- 기기 해제 ----------

    @Test
    fun `본인이 등록한 기기를 해제하면 Row가 삭제된다`() {
        val deviceKey = uniqueDeviceKey()
        register(ownerId, deviceKey, "token-1", PushPlatform.ANDROID)

        notificationDeviceService.unregister(ownerId, deviceKey)

        assertThat(notificationDeviceRepository.findByDeviceKey(deviceKey)).isNull()
    }

    @Test
    fun `다른 회원이 등록한 기기를 해제하려 하면 403이고 Row는 그대로 남는다`() {
        val deviceKey = uniqueDeviceKey()
        register(ownerId, deviceKey, "token-1", PushPlatform.ANDROID)

        assertThatThrownBy { notificationDeviceService.unregister(otherId, deviceKey) }
            .isInstanceOf(NotificationDeviceAccessDeniedException::class.java)
        assertThat(notificationDeviceRepository.findByDeviceKey(deviceKey)).isNotNull()
    }

    @Test
    fun `등록되지 않은 deviceKey를 해제하려 하면 404다`() {
        assertThatThrownBy { notificationDeviceService.unregister(ownerId, uniqueDeviceKey()) }
            .isInstanceOf(NotificationDeviceNotFoundException::class.java)
    }

    // ---------- 푸시 알림 설정 ----------

    @Test
    fun `설정한 적 없는 회원은 기본값(true)을 반환한다`() {
        assertThat(notificationSettingService.getSettings(ownerId).pushEnabled).isTrue()
        assertThat(notificationSettingRepository.findById(ownerId)).isEmpty
    }

    @Test
    fun `설정을 변경하면 저장되고 다시 조회하면 그 값을 반환한다`() {
        notificationSettingService.updateSettings(ownerId, false)

        assertThat(notificationSettingService.getSettings(ownerId).pushEnabled).isFalse()
        assertThat(notificationSettingRepository.findById(ownerId)).isPresent
    }

    @Test
    fun `설정을 여러 번 바꿔도 회원당 Row는 하나만 유지된다`() {
        notificationSettingService.updateSettings(ownerId, false)
        notificationSettingService.updateSettings(ownerId, true)
        notificationSettingService.updateSettings(ownerId, false)

        // member_id 자체가 PK라 이 회원의 설정 Row는 DB 구조상 최대 1개다. findById가 예외 없이
        // 정확히 하나를 반환한다는 사실과 마지막 값이 그대로 반영됐는지만 확인한다.
        assertThat(notificationSettingRepository.findById(ownerId)).isPresent
        assertThat(notificationSettingService.getSettings(ownerId).pushEnabled).isFalse()
    }

    @Test
    fun `pushEnabled를 꺼도 등록된 기기 Row는 삭제되지 않는다`() {
        val deviceKey = uniqueDeviceKey()
        register(ownerId, deviceKey, "token-1", PushPlatform.ANDROID)

        notificationSettingService.updateSettings(ownerId, false)

        assertThat(notificationDeviceRepository.findByDeviceKey(deviceKey)).isNotNull()
        assertThat(notificationSettingService.getSettings(ownerId).pushEnabled).isFalse()
    }

    @Test
    fun `같은 회원의 설정을 동시에 변경해도 uk 위반 없이 하나의 Row로 수렴한다`() {
        val executor = Executors.newFixedThreadPool(2)
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val results = ConcurrentLinkedQueue<Result<Unit>>()

        listOf(true, false).forEach { pushEnabled ->
            executor.submit {
                ready.countDown()
                start.await()
                val result = runCatching { notificationSettingService.updateSettings(ownerId, pushEnabled) }.map { }
                results.add(result)
            }
        }
        ready.await()
        start.countDown()
        executor.shutdown()
        check(executor.awaitTermination(30, TimeUnit.SECONDS)) { "동시 설정 변경 Thread가 제한 시간 안에 끝나지 않았습니다." }

        // member_id가 PK이므로 두 요청 모두 예외 없이 성공했다는 사실 자체가 uk 위반 없이 하나의
        // Row로 수렴했다는 증거다(find-then-save 2단계였다면 하나는 uk 위반으로 실패했을 것이다).
        assertThat(results).allSatisfy { assertThat(it.isSuccess).isTrue() }
        assertThat(notificationSettingRepository.findById(ownerId)).isPresent
    }

    private fun uniqueDeviceKey(): String = "device-${UUID.randomUUID()}"

    private fun register(
        memberId: Long,
        deviceKey: String,
        pushToken: String,
        platform: PushPlatform,
    ) = notificationDeviceService.register(memberId, NotificationDeviceRegisterRequest(deviceKey, pushToken, platform))

    private fun createMember(): Long {
        val subject = UUID.randomUUID().toString()
        val member =
            memberRepository.saveAndFlush(
                Member(
                    oauthProvider = OAuthProvider.GOOGLE,
                    oauthSubject = subject,
                    email = "$subject@example.com",
                    status = MemberStatus.ACTIVE,
                ).apply { name = subject },
            )
        return requireNotNull(member.id)
    }

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:18.4-alpine"))

        @Container
        @ServiceConnection
        @JvmStatic
        val redis = RedisContainer(DockerImageName.parse("redis:8.8.1-alpine"))
    }
}
