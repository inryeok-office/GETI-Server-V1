package team.inreok.getiserver.domain.collector.notification.discord

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

/**
 * Payload Builder는 순수 함수라 실제 HTTP 호출 없이 검증한다. Discord Embed 형식 요구사항
 * (색상 상수, Title Prefix, 필드 생략, allowed_mentions 비움, Markdown Escape)을 각각 확인한다.
 */
class DiscordJobEmbedBuilderTest {
    private val notifiedAt = LocalDateTime.of(2026, 8, 4, 3, 0)

    @Test
    fun `제목에 출처를 접두어로 붙이고 색상은 Discord Yellow 상수를 사용한다`() {
        val payload =
            DiscordJobEmbedBuilder.buildPayload(
                JobNotificationEmbedInput(
                    jobId = 42L,
                    sourceDisplayName = "병역일터",
                    title = "백엔드 개발자 모집",
                    companyName = "인력개발원",
                    externalUrl = "https://example.com/job/1",
                    recruitmentEndedAt = LocalDateTime.of(2026, 12, 31, 0, 0),
                    notifiedAt = notifiedAt,
                ),
            )

        @Suppress("UNCHECKED_CAST")
        val embed = (payload["embeds"] as List<Map<String, Any?>>).single()

        assertThat(embed["title"]).isEqualTo("[병역일터] 백엔드 개발자 모집")
        assertThat(embed["url"]).isEqualTo("https://example.com/job/1")
        assertThat(embed["description"]).isEqualTo("새로운 채용공고가 GETI에 등록되었습니다.")
        assertThat(embed["color"]).isEqualTo(DISCORD_EMBED_YELLOW)
        assertThat(DISCORD_EMBED_YELLOW).isEqualTo(16_705_372)
    }

    @Test
    fun `원문 URL이 없으면 url 필드를 생략한다`() {
        val payload =
            DiscordJobEmbedBuilder.buildPayload(
                JobNotificationEmbedInput(
                    jobId = 1L,
                    sourceDisplayName = "병역일터",
                    title = "제목",
                    companyName = "기업",
                    externalUrl = null,
                    recruitmentEndedAt = null,
                    notifiedAt = notifiedAt,
                ),
            )

        @Suppress("UNCHECKED_CAST")
        val embed = (payload["embeds"] as List<Map<String, Any?>>).single()

        assertThat(embed).doesNotContainKey("url")
    }

    @Test
    fun `모집마감이 없으면 해당 필드를 생성하지 않는다`() {
        val payload =
            DiscordJobEmbedBuilder.buildPayload(
                JobNotificationEmbedInput(
                    jobId = 1L,
                    sourceDisplayName = "병역일터",
                    title = "제목",
                    companyName = "기업",
                    externalUrl = null,
                    recruitmentEndedAt = null,
                    notifiedAt = notifiedAt,
                ),
            )

        @Suppress("UNCHECKED_CAST")
        val embed = (payload["embeds"] as List<Map<String, Any?>>).single()

        @Suppress("UNCHECKED_CAST")
        val fieldNames = (embed["fields"] as List<Map<String, Any?>>).map { it["name"] }

        assertThat(fieldNames).doesNotContain("모집마감")
        assertThat(fieldNames).containsExactly("기업·기관명", "출처", "GETI 공고ID")
    }

    @Test
    fun `allowed_mentions는 항상 비어 있다`() {
        val payload =
            DiscordJobEmbedBuilder.buildPayload(
                JobNotificationEmbedInput(
                    jobId = 1L,
                    sourceDisplayName = "병역일터",
                    title = "제목",
                    companyName = "기업",
                    externalUrl = null,
                    recruitmentEndedAt = null,
                    notifiedAt = notifiedAt,
                ),
            )

        assertThat(payload["allowed_mentions"]).isEqualTo(mapOf("parse" to emptyList<String>()))
    }

    @Test
    fun `제목의 Discord Markdown 특수문자를 Escape한다`() {
        val payload =
            DiscordJobEmbedBuilder.buildPayload(
                JobNotificationEmbedInput(
                    jobId = 1L,
                    sourceDisplayName = "병역일터",
                    title = "**중요** _채용_ ~공고~",
                    companyName = "기업",
                    externalUrl = null,
                    recruitmentEndedAt = null,
                    notifiedAt = notifiedAt,
                ),
            )

        @Suppress("UNCHECKED_CAST")
        val embed = (payload["embeds"] as List<Map<String, Any?>>).single()

        assertThat(embed["title"] as String).doesNotContain("**중요**")
        assertThat(embed["title"] as String).contains("\\*\\*중요\\*\\*")
    }

    @Test
    fun `과도하게 긴 제목은 잘라낸다`() {
        val longTitle = "가".repeat(500)
        val payload =
            DiscordJobEmbedBuilder.buildPayload(
                JobNotificationEmbedInput(
                    jobId = 1L,
                    sourceDisplayName = "병역일터",
                    title = longTitle,
                    companyName = "기업",
                    externalUrl = null,
                    recruitmentEndedAt = null,
                    notifiedAt = notifiedAt,
                ),
            )

        @Suppress("UNCHECKED_CAST")
        val embed = (payload["embeds"] as List<Map<String, Any?>>).single()

        assertThat((embed["title"] as String).length).isLessThanOrEqualTo(256)
    }
}
