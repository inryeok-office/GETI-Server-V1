package team.inreok.getiserver.global.discord

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.core.env.StandardEnvironment
import org.springframework.core.io.FileSystemResource

/**
 * 허용 채널 검증과 학년 → Mention Role 변환의 단일 판정 지점인 [DiscordChannelResolver]를 검증한다
 * (`docs/notification/discord-event-wiring-plan.md` §5, §8). Job·Program 등록 시점의 입력 검증과
 * Notification의 Delivery 생성이 같은 로직을 쓰므로, 여기가 어긋나면 두 곳이 함께 어긋난다.
 */
class DiscordChannelResolverTest {
    private fun resolver(
        channels: Map<String, DiscordChannelConfig> = emptyMap(),
        gradeRoles: Map<String, String> = emptyMap(),
        defaultJobChannelKey: String = "",
        inquiryChannelKey: String = "",
    ) = DiscordChannelResolver(
        DiscordChannelProperties(
            channels = channels,
            gradeRoles = gradeRoles,
            defaultJobChannelKey = defaultJobChannelKey,
            inquiryChannelKey = inquiryChannelKey,
        ),
    )

    private val configuredChannels =
        mapOf(
            "job-notice" to DiscordChannelConfig(channelId = "111", displayName = "공고 공지"),
            "program-notice" to DiscordChannelConfig(channelId = "222", displayName = "프로그램 공지"),
            "inquiry-alert" to DiscordChannelConfig(channelId = "333", displayName = "문의 접수 알림"),
        )

    @Nested
    @DisplayName("Job 채널 Key 허용 검증")
    inner class JobChannelKey {
        @Test
        fun `허용 목록에 있고 실제 채널이 설정된 Key만 허용한다`() {
            val resolver = resolver(channels = configuredChannels)

            assertThat(resolver.isAllowedJobChannelKey("job-notice")).isTrue()
        }

        @Test
        fun `허용 목록에 없는 Key는 거부한다`() {
            val resolver = resolver(channels = configuredChannels)

            assertThat(resolver.isAllowedJobChannelKey("random-channel")).isFalse()
        }

        @Test
        fun `Key는 등록됐지만 Snowflake가 비어 있으면 거부한다`() {
            // 설정 구조만 있고 실제 값이 아직 주입되지 않은 상태다(§5.1). 이 Key를 허용하면
            // 등록은 통과했는데 Delivery는 생기지 않는 어긋난 상태가 된다.
            val resolver = resolver(channels = mapOf("job-notice" to DiscordChannelConfig(channelId = "")))

            assertThat(resolver.isAllowedJobChannelKey("job-notice")).isFalse()
        }
    }

    @Nested
    @DisplayName("Job 채널 해석")
    inner class JobChannelResolution {
        @Test
        fun `Key를 지정하면 그 채널 Snowflake로 해석한다`() {
            val resolver = resolver(channels = configuredChannels, defaultJobChannelKey = "job-notice")

            assertThat(resolver.resolveJobChannelId("program-notice")).isEqualTo("222")
        }

        @Test
        fun `Key가 없으면 기본 채널로 해석한다`() {
            val resolver = resolver(channels = configuredChannels, defaultJobChannelKey = "job-notice")

            assertThat(resolver.resolveJobChannelId(null)).isEqualTo("111")
            assertThat(resolver.resolveJobChannelId(" ")).isEqualTo("111")
        }

        @Test
        fun `기본 채널 Key가 비어 있으면 null이다`() {
            val resolver = resolver(channels = configuredChannels, defaultJobChannelKey = "")

            assertThat(resolver.resolveJobChannelId(null)).isNull()
        }
    }

    @Nested
    @DisplayName("Program 채널")
    inner class ProgramChannel {
        @Test
        fun `허용 채널의 Snowflake와 일치하면 허용한다`() {
            val resolver = resolver(channels = configuredChannels)

            assertThat(resolver.isAllowedProgramChannelId("222")).isTrue()
        }

        @Test
        fun `허용 목록에 없는 Snowflake는 거부한다`() {
            val resolver = resolver(channels = configuredChannels)

            assertThat(resolver.isAllowedProgramChannelId("999")).isFalse()
        }

        @Test
        fun `채널이 하나도 설정되지 않았으면 어떤 Snowflake도 거부한다`() {
            val resolver = resolver(channels = mapOf("job-notice" to DiscordChannelConfig(channelId = "")))

            // 빈 문자열끼리 우연히 일치해 통과하지 않아야 한다.
            assertThat(resolver.isAllowedProgramChannelId("")).isFalse()
            assertThat(resolver.isAllowedProgramChannelId("111")).isFalse()
        }

        @Test
        fun `저장된 원시 Snowflake는 정규화만 한다`() {
            val resolver = resolver(channels = configuredChannels)

            assertThat(resolver.resolveProgramChannelId("222")).isEqualTo("222")
            assertThat(resolver.resolveProgramChannelId(null)).isNull()
            assertThat(resolver.resolveProgramChannelId(" ")).isNull()
        }
    }

    @Nested
    @DisplayName("Inquiry 채널")
    inner class InquiryChannel {
        @Test
        fun `고정 Key로 해석한다`() {
            val resolver = resolver(channels = configuredChannels, inquiryChannelKey = "inquiry-alert")

            assertThat(resolver.resolveInquiryChannelId()).isEqualTo("333")
        }

        @Test
        fun `고정 Key가 비어 있으면 null이다`() {
            val resolver = resolver(channels = configuredChannels, inquiryChannelKey = "")

            assertThat(resolver.resolveInquiryChannelId()).isNull()
        }
    }

    @Nested
    @DisplayName("학년 Mention Role 변환")
    inner class GradeRoles {
        private val gradeRoles = mapOf("1" to "role-1", "2" to "role-2", "3" to "")

        @Test
        fun `학년을 Role Snowflake로 바꾼다`() {
            val resolver = resolver(gradeRoles = gradeRoles)

            assertThat(resolver.roleIdsForGrades(listOf(1, 2))).containsExactly("role-1", "role-2")
        }

        @Test
        fun `매핑이 없거나 비어 있는 학년은 조용히 빠진다`() {
            // Role이 없다고 Delivery 생성 자체를 막지 않는다 -- Mention은 부가 기능이다.
            val resolver = resolver(gradeRoles = gradeRoles)

            assertThat(resolver.roleIdsForGrades(listOf(1, 3, 4))).containsExactly("role-1")
        }

        @Test
        fun `학년이 없으면 빈 목록이다`() {
            val resolver = resolver(gradeRoles = gradeRoles)

            assertThat(resolver.roleIdsForGrades(emptyList())).isEmpty()
        }

        @Test
        fun `같은 Role로 매핑된 학년이 중복되지 않는다`() {
            val resolver = resolver(gradeRoles = mapOf("1" to "role-all", "2" to "role-all"))

            assertThat(resolver.roleIdsForGrades(listOf(1, 2))).containsExactly("role-all")
        }
    }

    @Nested
    @DisplayName("application.yaml 기본 설정")
    inner class ShippedDefaults {
        /**
         * `app.discord.channel-policy` 블록이 `application.yaml`에 실제로 선언돼 있는지 확인한다.
         * Snowflake는 비어 있는 것이 정상이지만(§5.1) **채널 Key와 기본 Key까지 비어 있으면**
         * 환경변수로 Snowflake를 주입해도 `channelIdOf("")`가 null이라 기본 채널 Job과 Inquiry는
         * Delivery가 영원히 생기지 않는다. 그 회귀를 여기서 막는다.
         */
        private fun shippedProperties(): DiscordChannelProperties {
            // ClassPath가 아니라 파일을 직접 읽는다 -- src/test/resources/application.yaml이 같은
            // 이름으로 운영 설정을 완전히 대체하므로(그 파일 주석 참고) ClassPathResource로는
            // 검증 대상인 운영 설정에 도달할 수 없다.
            val resource = FileSystemResource("src/main/resources/application.yaml")
            assertThat(resource.exists())
                .describedAs("운영 application.yaml을 찾지 못했다(작업 디렉터리=${System.getProperty("user.dir")})")
                .isTrue()

            val environment = StandardEnvironment()
            YamlPropertySourceLoader()
                .load("application.yaml", resource)
                .forEach { environment.propertySources.addLast(it) }
            return Binder
                .get(
                    environment,
                ).bindOrCreate("app.discord.channel-policy", DiscordChannelProperties::class.java)
        }

        @Test
        fun `채널 Key 3개와 기본 Key가 선언돼 있다`() {
            val properties = shippedProperties()

            assertThat(properties.channels.keys).contains("job-notice", "program-notice", "inquiry-alert")
            assertThat(properties.defaultJobChannelKey).isEqualTo("job-notice")
            assertThat(properties.inquiryChannelKey).isEqualTo("inquiry-alert")
            assertThat(properties.gradeRoles.keys).contains("1", "2", "3")
        }

        @Test
        fun `Snowflake는 비어 있어 Delivery를 만들지 않는다`() {
            // 실제 값이 확정되지 않아 비워 둔 상태다(결정 4). Fail-Fast가 아니라 조용히 건너뛴다.
            val resolver = DiscordChannelResolver(shippedProperties())

            assertThat(resolver.resolveJobChannelId(null)).isNull()
            assertThat(resolver.resolveInquiryChannelId()).isNull()
            assertThat(resolver.isAllowedJobChannelKey("job-notice")).isFalse()
        }
    }
}
