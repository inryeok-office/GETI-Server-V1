package team.inreok.getiserver.domain.collector.notification.discord

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DiscordTextSanitizerTest {
    @Test
    fun `HTML 태그를 제거한다`() {
        val result = DiscordTextSanitizer.sanitize("<b>굵게</b> 표시", 100)

        assertThat(result).doesNotContain("<b>", "</b>")
        assertThat(result).contains("굵게", "표시")
    }

    @Test
    fun `과도한 줄바꿈과 공백을 한 칸으로 접는다`() {
        val result = DiscordTextSanitizer.sanitize("첫 줄\n\n\n\n둘째 줄   셋째", 100)

        assertThat(result).isEqualTo("첫 줄 둘째 줄 셋째")
    }

    @Test
    fun `Discord Markdown 특수문자를 Escape한다`() {
        val result = DiscordTextSanitizer.sanitize("*강조* _기울임_ ~취소선~ `코드` \\backslash", 200)

        assertThat(result).contains("\\*강조\\*", "\\_기울임\\_", "\\~취소선\\~", "\\`코드\\`")
    }

    @Test
    fun `최대 길이를 넘으면 말줄임표와 함께 잘라낸다`() {
        val result = DiscordTextSanitizer.sanitize("가나다라마바사", 5)

        assertThat(result).hasSize(5)
        assertThat(result).endsWith("…")
    }

    @Test
    fun `제어 문자를 제거한다`() {
        val bell = 7.toChar()
        val result = DiscordTextSanitizer.sanitize("정상$bell 텍스트", 100)

        assertThat(result).isEqualTo("정상 텍스트")
    }
}
