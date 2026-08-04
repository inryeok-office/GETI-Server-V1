package team.inreok.getiserver.domain.collector.notification.discord

import team.inreok.getiserver.domain.collector.eligibility.JobRelevanceCategory
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** #FFFF00(순수 Yellow) Decimal 값. Job/Run Embed 색상은 항상 이 상수를 사용한다. */
const val DISCORD_EMBED_YELLOW = 16_776_960

/** Discord Embed 생성에 필요한 최소 표시 필드. 공고 본문(content)은 포함하지 않는다. */
data class JobNotificationEmbedInput(
    val jobId: Long,
    val sourceDisplayName: String,
    val title: String,
    val companyName: String,
    val externalUrl: String?,
    val recruitmentEndedAt: LocalDateTime?,
    val notifiedAt: LocalDateTime,
    val employmentType: String? = null,
    val educationCondition: String? = null,
    val careerCondition: String? = null,
    val relevanceCategory: JobRelevanceCategory? = null,
)

/**
 * Discord Webhook Payload(JSON으로 직렬화될 Map)를 만든다. 순수 함수라 실제 HTTP 호출 없이
 * 단위 Test로 검증할 수 있다(WireMock/MockWebServer 미추가 원칙, MmaCollectorProvider.parse와
 * 같은 패턴). 값이 없는 필드(근무지역·병역특례유형 등)는 현재 NormalizedCollectedJob이 해당
 * 정보를 담지 않거나 이번 알림 대상 Provider가 값을 주지 않아 생성하지 않는다("정보 없음" 등으로
 * 채우지 않는다, Issue #62 확장 범위).
 */
object DiscordJobEmbedBuilder {
    private const val TITLE_MAX = 256
    private const val FIELD_NAME_MAX = 256
    private const val FIELD_VALUE_MAX = 1024
    private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    fun buildPayload(input: JobNotificationEmbedInput): Map<String, Any?> {
        val prefix = "[${DiscordTextSanitizer.sanitize(input.sourceDisplayName, MAX_SOURCE_PREFIX)}] "
        val safeTitle = DiscordTextSanitizer.sanitize(input.title, (TITLE_MAX - prefix.length).coerceAtLeast(1))

        val fields =
            buildList {
                add(field("기업·기관명", input.companyName))
                add(field("출처", input.sourceDisplayName))
                input.relevanceCategory?.let { add(field("직무 분류", it.name)) }
                input.employmentType?.takeIf { it.isNotBlank() }?.let { add(field("고용형태", it)) }
                input.educationCondition?.takeIf { it.isNotBlank() }?.let { add(field("학력 조건", it)) }
                input.careerCondition?.takeIf { it.isNotBlank() }?.let { add(field("경력 조건", it)) }
                input.recruitmentEndedAt?.let { add(field("모집마감", it.format(DATE_FORMAT))) }
                add(field("GETI 공고ID", input.jobId.toString()))
            }

        val embed =
            buildMap<String, Any?> {
                put("title", (prefix + safeTitle).take(TITLE_MAX))
                if (!input.externalUrl.isNullOrBlank()) put("url", input.externalUrl)
                put("description", "새로운 채용공고가 GETI에 등록되었습니다.")
                put("color", DISCORD_EMBED_YELLOW)
                put("fields", fields)
                put("footer", mapOf("text" to "GETI Collector"))
                put(
                    "timestamp",
                    input.notifiedAt
                        .atZone(ZoneId.systemDefault())
                        .toOffsetDateTime()
                        .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                )
            }

        return mapOf(
            "embeds" to listOf(embed),
            // 알림 대상 공고 텍스트는 신뢰할 수 없는 외부 응답이라 @everyone/@here/Role/User
            // 언급이 실수로라도 발동하지 않도록 명시적으로 비운다.
            "allowed_mentions" to mapOf("parse" to emptyList<String>()),
        )
    }

    private fun field(
        name: String,
        value: String,
    ): Map<String, Any> =
        mapOf(
            "name" to DiscordTextSanitizer.sanitize(name, FIELD_NAME_MAX),
            "value" to DiscordTextSanitizer.sanitize(value, FIELD_VALUE_MAX),
            "inline" to true,
        )

    private const val MAX_SOURCE_PREFIX = 64
}
