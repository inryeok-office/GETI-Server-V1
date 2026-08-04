package team.inreok.getiserver.domain.collector.provider

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

/**
 * 실제 JOB_ALIO/나라일터 응답에서 확인한 결함 있는 원문 데이터(Scheme 없는 URL, 뒤바뀐 모집
 * 기간)를 정규화하는 로직을 검증한다(Issue #62 확장 범위, 실 API 호출로 재현).
 */
class CollectedFieldNormalizerTest {
    @Test
    fun `http, https URL은 그대로 유지한다`() {
        assertThat(normalizeExternalUrl("https://example.com/job/1")).isEqualTo("https://example.com/job/1")
        assertThat(normalizeExternalUrl("http://example.com")).isEqualTo("http://example.com")
    }

    @Test
    fun `Scheme이 없는 순수 도메인은 https를 붙여 살린다`() {
        assertThat(normalizeExternalUrl("www.knudh.kr")).isEqualTo("https://www.knudh.kr")
    }

    @Test
    fun `http, https가 아닌 Scheme은 버린다`() {
        assertThat(normalizeExternalUrl("mailto:hr@example.com")).isNull()
        assertThat(normalizeExternalUrl("tel:02-1234-5678")).isNull()
    }

    @Test
    fun `공백이거나 없는 값은 null이다`() {
        assertThat(normalizeExternalUrl(null)).isNull()
        assertThat(normalizeExternalUrl("")).isNull()
        assertThat(normalizeExternalUrl("   ")).isNull()
    }

    @Test
    fun `모집 시작이 종료보다 늦으면 둘 다 미상으로 처리한다`() {
        val start = LocalDateTime.of(2026, 8, 10, 0, 0)
        val end = LocalDateTime.of(2026, 8, 1, 0, 0)

        val (normalizedStart, normalizedEnd) = normalizeRecruitmentPeriod(start, end)

        assertThat(normalizedStart).isNull()
        assertThat(normalizedEnd).isNull()
    }

    @Test
    fun `모집 시작이 종료보다 빠르면 그대로 유지한다`() {
        val start = LocalDateTime.of(2026, 8, 1, 0, 0)
        val end = LocalDateTime.of(2026, 8, 10, 0, 0)

        val (normalizedStart, normalizedEnd) = normalizeRecruitmentPeriod(start, end)

        assertThat(normalizedStart).isEqualTo(start)
        assertThat(normalizedEnd).isEqualTo(end)
    }

    @Test
    fun `둘 중 하나가 없으면 뒤바뀐 것으로 판단하지 않는다`() {
        val end = LocalDateTime.of(2026, 8, 10, 0, 0)

        val (normalizedStart, normalizedEnd) = normalizeRecruitmentPeriod(null, end)

        assertThat(normalizedStart).isNull()
        assertThat(normalizedEnd).isEqualTo(end)
    }
}
