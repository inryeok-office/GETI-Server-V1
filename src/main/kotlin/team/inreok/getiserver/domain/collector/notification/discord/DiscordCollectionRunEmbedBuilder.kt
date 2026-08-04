package team.inreok.getiserver.domain.collector.notification.discord

import team.inreok.getiserver.domain.collector.entity.type.CollectionRunStatus
import team.inreok.getiserver.domain.collector.entity.type.CollectorAction
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 기존 CD 배포 알림(cd.yml)이 쓰는 discord.py 고전 팔레트와 맞춘 상수다. */
const val DISCORD_EMBED_GREEN = 3_066_993
const val DISCORD_EMBED_RED = 15_158_332

/** Discord Collection Run 완료 Embed 생성에 필요한 최소 정보. */
data class CollectionRunSummaryEmbedInput(
    val runId: Long,
    val sourceDisplayName: String,
    val action: CollectorAction,
    val status: CollectionRunStatus,
    val totalCount: Int,
    val successCount: Int,
    val failureCount: Int,
    val partialQualityCount: Int,
    val startedAt: LocalDateTime,
    val finishedAt: LocalDateTime,
    /** Provider 실행 자체가 실패한 경우의 사유(finishAsProviderFailure). 정상 종료면 null. */
    val failureReason: String?,
)

/**
 * Collector 실행(CollectionRun) 하나가 끝날 때마다 보내는 요약 Embed를 만든다. 개별 신규 공고
 * 알림(DiscordJobEmbedBuilder)과 달리 "이번 실행이 어떻게 끝났는지"를 요약하는 운영용 알림이라
 * DB 기반 재시도 대상이 아니다(기존 CD 배포 알림과 동일하게 Best Effort로 전송한다, 최종 보고
 * 참고). 순수 함수라 실제 HTTP 호출 없이 검증할 수 있다.
 */
object DiscordCollectionRunEmbedBuilder {
    private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private const val MAX_SOURCE_NAME = 64
    private const val MAX_FAILURE_REASON = 500

    fun buildPayload(input: CollectionRunSummaryEmbedInput): Map<String, Any?> {
        val presentation = presentationOf(input.status)
        val safeSourceName = DiscordTextSanitizer.sanitize(input.sourceDisplayName, MAX_SOURCE_NAME)

        val fields =
            buildList {
                add(field("실행 유형", input.action.name))
                add(field("결과", presentation.label))
                add(field("시작 일시", input.startedAt.format(DATE_FORMAT)))
                add(field("종료 일시", input.finishedAt.format(DATE_FORMAT)))
                add(field("소요 시간", formatDuration(Duration.between(input.startedAt, input.finishedAt))))
                add(field("전체 공고 수", input.totalCount.toString()))
                add(field("성공", input.successCount.toString()))
                add(field("실패", input.failureCount.toString()))
                if (input.partialQualityCount > 0) add(field("품질 경고", input.partialQualityCount.toString()))
                input.failureReason?.let {
                    add(field("실패 사유", DiscordTextSanitizer.sanitize(it, MAX_FAILURE_REASON)))
                }
                add(field("Run ID", input.runId.toString()))
            }

        val embed =
            buildMap<String, Any?> {
                put("title", "${presentation.emoji} [$safeSourceName] 수집 실행 ${presentation.label}")
                put("description", "Collector 수집 실행이 종료되었습니다.")
                put("color", presentation.color)
                put("fields", fields)
                put("footer", mapOf("text" to "GETI Collector"))
                put(
                    "timestamp",
                    input.finishedAt
                        .atZone(ZoneId.systemDefault())
                        .toOffsetDateTime()
                        .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                )
            }

        return mapOf(
            "embeds" to listOf(embed),
            "allowed_mentions" to mapOf("parse" to emptyList<String>()),
        )
    }

    private data class StatusPresentation(
        val emoji: String,
        val label: String,
        val color: Int,
    )

    // PENDING/RUNNING/CANCELED는 실제로 이 Embed가 만들어지는 시점(Run 종료 시)에는 나타나지
    // 않지만, CollectionRunStatus의 모든 값을 방어적으로 처리한다.
    private fun presentationOf(status: CollectionRunStatus): StatusPresentation =
        when (status) {
            CollectionRunStatus.SUCCESS -> {
                StatusPresentation("✅", "성공", DISCORD_EMBED_GREEN)
            }

            CollectionRunStatus.PARTIAL_SUCCESS -> {
                StatusPresentation("⚠️", "부분 성공", DISCORD_EMBED_YELLOW)
            }

            CollectionRunStatus.FAILED -> {
                StatusPresentation("❌", "실패", DISCORD_EMBED_RED)
            }

            CollectionRunStatus.CANCELED -> {
                StatusPresentation("⏹️", "취소", DISCORD_EMBED_RED)
            }

            CollectionRunStatus.PENDING, CollectionRunStatus.RUNNING -> {
                StatusPresentation(
                    "⏳",
                    "진행 중",
                    DISCORD_EMBED_YELLOW,
                )
            }
        }

    private fun formatDuration(duration: Duration): String {
        val totalSeconds = duration.seconds.coerceAtLeast(0)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return if (minutes > 0) "${minutes}분 ${seconds}초" else "${seconds}초"
    }

    private fun field(
        name: String,
        value: String,
    ): Map<String, Any> = mapOf("name" to name, "value" to value, "inline" to true)
}
