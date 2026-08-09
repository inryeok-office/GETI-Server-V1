package team.inreok.getiserver.domain.notification.service

import org.springframework.stereotype.Component
import team.inreok.getiserver.domain.inquiry.query.InquiryDiscordPayloadSnapshot
import team.inreok.getiserver.domain.job.query.JobDiscordPayloadSnapshot
import team.inreok.getiserver.domain.notification.entity.type.DiscordMessageTemplate
import team.inreok.getiserver.domain.program.query.ProgramDiscordPayloadSnapshot
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 도메인 Snapshot을 GETI-Bot-V1이 받는 `data` Map으로 바꾼다.
 *
 * 서버는 Discord Embed를 만들지 않는다(후속 요구사항 문서 §3). 여기서 만드는 것은 색상·Field·
 * Markdown이 아니라 **의미 데이터**뿐이고, 실제 렌더링은 Bot이 한다.
 *
 * ## 왜 자르는가
 *
 * Bot의 Template별 `data` Schema는 전부 `.strict()`이고 필드마다 최대 길이가 있다. 예를 들어
 * `title`은 200자인데 `jobs.title`/`programs.title`은 DB에서 500자까지 허용된다. 자르지 않고
 * 보내면 `400 INVALID_REQUEST`가 오고 이 오류는 `retryable=false`라 **자동 재시도 없이 즉시
 * FAILED**가 되어 알림이 영구히 누락된다.
 *
 * ## 왜 빈 값을 빼는가
 *
 * Bot Schema의 문자열은 `trim` 후 `min(1)`이라 빈 문자열을 보내면 역시 `400`이다. 값이 없는
 * 선택 필드는 Map에 아예 넣지 않는다.
 */
@Component
class DiscordPayloadFactory {
    fun forJob(
        template: DiscordMessageTemplate,
        snapshot: JobDiscordPayloadSnapshot,
    ): Map<String, String> =
        buildMap {
            put("jobId", snapshot.jobId.toString())
            put("title", truncate(snapshot.title, TITLE_MAX_LENGTH))
            // companyName/deadline은 jobPublishedDataSchema에만 선언되어 있다. JOB_UPDATED와
            // JOB_CLOSED Schema는 jobId/title/changes|reason/url만 받고 .strict()라, 이 두 필드를
            // 넣으면 400 INVALID_REQUEST(재시도 불가)로 즉시 FAILED가 된다.
            if (template == DiscordMessageTemplate.JOB_PUBLISHED) {
                putIfPresent("companyName", snapshot.companyName, LABEL_MAX_LENGTH)
                putIfPresent("deadline", formatDateTime(snapshot.recruitmentEndedAt), SHORT_MAX_LENGTH)
            }
        }

    fun forProgram(
        template: DiscordMessageTemplate,
        snapshot: ProgramDiscordPayloadSnapshot,
    ): Map<String, String> =
        buildMap {
            put("programId", snapshot.programId.toString())
            put("title", truncate(snapshot.title, TITLE_MAX_LENGTH))
            // description/startAt/endAt은 PROGRAM_PUBLISHED Schema에만 있다.
            if (template == DiscordMessageTemplate.PROGRAM_PUBLISHED) {
                putIfPresent("description", snapshot.bodyMarkdown, DESCRIPTION_MAX_LENGTH)
                putIfPresent("startAt", formatDateTime(snapshot.eventStartedAt), SHORT_MAX_LENGTH)
                putIfPresent("endAt", formatDateTime(snapshot.eventEndedAt), SHORT_MAX_LENGTH)
            }
        }

    /**
     * 문의 접수 알림이다. Bot Schema에 없는 필드(이메일·전화번호·본문·제목)는 애초에 Snapshot에
     * 없으므로 여기서 실수로 실을 수 없다(§40).
     */
    fun forInquiry(snapshot: InquiryDiscordPayloadSnapshot): Map<String, String> =
        buildMap {
            put("inquiryId", snapshot.inquiryId.toString())
            putIfPresent("category", snapshot.category, SHORT_MAX_LENGTH)
            putIfPresent("requesterName", snapshot.requesterName, LABEL_MAX_LENGTH)
            putIfPresent("submittedAt", formatDateTime(snapshot.createdAt), SHORT_MAX_LENGTH)
        }

    private fun MutableMap<String, String>.putIfPresent(
        key: String,
        value: String?,
        maxLength: Int,
    ) {
        val normalized = value?.trim()?.takeIf { it.isNotEmpty() } ?: return
        put(key, truncate(normalized, maxLength))
    }

    /**
     * `LocalDateTime`을 Offset이 포함된 ISO-8601로 만든다(§47). Timezone 없이 보내면 Bot이
     * 어느 시각인지 확정할 수 없다. 저장소에 명시적인 Timezone 설정이 없어
     * `DiscordJobEmbedBuilder`·`DiscordCollectionRunEmbedBuilder`가 이미 쓰는
     * `ZoneId.systemDefault()`를 따른다.
     */
    private fun formatDateTime(value: LocalDateTime?): String? =
        value?.atZone(ZoneId.systemDefault())?.toOffsetDateTime()?.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

    /**
     * 상한을 넘으면 말줄임표를 붙여 자른다. 말줄임표를 포함한 최종 길이가 상한을 넘지 않는다 --
     * 그렇지 않으면 자르고도 Bot Schema를 위반한다.
     */
    private fun truncate(
        value: String,
        maxLength: Int,
    ): String {
        val trimmed = value.trim()
        if (trimmed.length <= maxLength) return trimmed
        return trimmed.take(maxLength - ELLIPSIS.length) + ELLIPSIS
    }

    private companion object {
        const val ELLIPSIS = "…"

        // GETI-Bot-V1 src/renderer/schemas.ts의 상한과 1:1로 맞춘 값이다.
        const val TITLE_MAX_LENGTH = 200
        const val LABEL_MAX_LENGTH = 100
        const val SHORT_MAX_LENGTH = 50
        const val DESCRIPTION_MAX_LENGTH = 500
    }
}
