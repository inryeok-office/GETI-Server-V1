package team.inreok.getiserver.domain.collector.notification.discord

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import team.inreok.getiserver.domain.collector.entity.type.CollectionRunStatus
import team.inreok.getiserver.domain.collector.entity.type.CollectorAction
import java.time.LocalDateTime

class DiscordCollectionRunEmbedBuilderTest {
    private fun inputOf(
        status: CollectionRunStatus,
        totalCount: Int = 10,
        successCount: Int = 10,
        failureCount: Int = 0,
        partialQualityCount: Int = 0,
        failureReason: String? = null,
    ) = CollectionRunSummaryEmbedInput(
        runId = 7L,
        sourceDisplayName = "병역일터",
        action = CollectorAction.SYNC,
        status = status,
        totalCount = totalCount,
        successCount = successCount,
        failureCount = failureCount,
        partialQualityCount = partialQualityCount,
        startedAt = LocalDateTime.of(2026, 8, 4, 3, 0, 0),
        finishedAt = LocalDateTime.of(2026, 8, 4, 3, 2, 30),
        failureReason = failureReason,
    )

    @Suppress("UNCHECKED_CAST")
    private fun embedOf(payload: Map<String, Any?>) = (payload["embeds"] as List<Map<String, Any?>>).single()

    @Suppress("UNCHECKED_CAST")
    private fun fieldsOf(embed: Map<String, Any?>) = (embed["fields"] as List<Map<String, Any?>>)

    @Test
    fun `성공하면 초록색과 성공 Label을 사용한다`() {
        val embed = embedOf(DiscordCollectionRunEmbedBuilder.buildPayload(inputOf(CollectionRunStatus.SUCCESS)))

        assertThat(embed["color"]).isEqualTo(DISCORD_EMBED_GREEN)
        assertThat(embed["title"] as String).contains("✅", "[병역일터]", "성공")
    }

    @Test
    fun `부분 성공이면 노란색을 사용한다`() {
        val embed =
            embedOf(
                DiscordCollectionRunEmbedBuilder.buildPayload(
                    inputOf(CollectionRunStatus.PARTIAL_SUCCESS, successCount = 7, failureCount = 3),
                ),
            )

        assertThat(embed["color"]).isEqualTo(DISCORD_EMBED_YELLOW)
        assertThat(embed["title"] as String).contains("⚠️", "부분 성공")
    }

    @Test
    fun `실패하면 빨간색과 실패 사유 필드를 포함한다`() {
        val embed =
            embedOf(
                DiscordCollectionRunEmbedBuilder.buildPayload(
                    inputOf(
                        CollectionRunStatus.FAILED,
                        totalCount = 1,
                        successCount = 0,
                        failureCount = 1,
                        failureReason = "MMA 인증 오류",
                    ),
                ),
            )

        assertThat(embed["color"]).isEqualTo(DISCORD_EMBED_RED)
        val fieldValues = fieldsOf(embed).associate { it["name"] to it["value"] }
        assertThat(fieldValues["실패 사유"]).isEqualTo("MMA 인증 오류")
    }

    @Test
    fun `필수 필드(실행 유형, 결과, 시작·종료 일시, 소요 시간, 공고 수, Run ID)를 모두 포함한다`() {
        val embed = embedOf(DiscordCollectionRunEmbedBuilder.buildPayload(inputOf(CollectionRunStatus.SUCCESS)))
        val fieldNames = fieldsOf(embed).map { it["name"] }

        assertThat(fieldNames).contains("실행 유형", "결과", "시작 일시", "종료 일시", "소요 시간", "전체 공고 수", "성공", "실패", "Run ID")
    }

    @Test
    fun `소요 시간을 분 초 단위로 계산한다`() {
        val embed = embedOf(DiscordCollectionRunEmbedBuilder.buildPayload(inputOf(CollectionRunStatus.SUCCESS)))
        val duration = fieldsOf(embed).single { it["name"] == "소요 시간" }["value"]

        assertThat(duration).isEqualTo("2분 30초")
    }

    @Test
    fun `품질 경고가 0이면 해당 필드를 생성하지 않는다`() {
        val embed =
            embedOf(
                DiscordCollectionRunEmbedBuilder.buildPayload(
                    inputOf(CollectionRunStatus.SUCCESS, partialQualityCount = 0),
                ),
            )

        assertThat(fieldsOf(embed).map { it["name"] }).doesNotContain("품질 경고")
    }

    @Test
    fun `품질 경고가 있으면 필드에 포함한다`() {
        val embed =
            embedOf(
                DiscordCollectionRunEmbedBuilder.buildPayload(
                    inputOf(CollectionRunStatus.SUCCESS, partialQualityCount = 2),
                ),
            )

        val fieldValues = fieldsOf(embed).associate { it["name"] to it["value"] }
        assertThat(fieldValues["품질 경고"]).isEqualTo("2")
    }

    @Test
    fun `allowed_mentions는 항상 비어 있다`() {
        val payload = DiscordCollectionRunEmbedBuilder.buildPayload(inputOf(CollectionRunStatus.SUCCESS))

        assertThat(payload["allowed_mentions"]).isEqualTo(mapOf("parse" to emptyList<String>()))
    }
}
