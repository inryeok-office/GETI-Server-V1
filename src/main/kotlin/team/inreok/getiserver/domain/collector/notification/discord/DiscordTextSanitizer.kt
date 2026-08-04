package team.inreok.getiserver.domain.collector.notification.discord

/**
 * 외부 Provider가 준 원문(공고 제목·기업명 등)을 Discord Embed 필드에 넣기 전에 Sanitize한다.
 * HTML 태그·제어 문자를 제거하고 과도한 줄바꿈/공백을 한 줄로 접은 뒤, Discord Markdown
 * 특수문자를 Escape하고 필드별 최대 길이에 맞춰 자른다(Issue #62 확장 범위, "Discord Embed
 * 형식" 참고). 공고 본문 전체는 절대 포함하지 않으므로 이 대상은 항상 짧은 표시용 문자열이다.
 */
internal object DiscordTextSanitizer {
    private val HTML_TAG = Regex("<[^>]*>")
    private val CONTROL_CHARS = Regex("[\\p{Cntrl}&&[^\\n\\r\\t]]")
    private val WHITESPACE_RUN = Regex("\\s+")
    private val MARKDOWN_SPECIAL = Regex("([*_~`|\\\\>])")

    fun sanitize(
        raw: String,
        maxLength: Int,
    ): String {
        val noHtml = HTML_TAG.replace(raw, " ")
        val noControl = CONTROL_CHARS.replace(noHtml, "")
        val collapsed = WHITESPACE_RUN.replace(noControl, " ").trim()
        val escaped = MARKDOWN_SPECIAL.replace(collapsed) { match -> "\\" + match.value }
        return if (escaped.length > maxLength) escaped.take((maxLength - 1).coerceAtLeast(0)) + "…" else escaped
    }
}
