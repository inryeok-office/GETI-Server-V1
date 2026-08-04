package team.inreok.getiserver.domain.collector.provider.jobalio

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.web.client.RestClient
import team.inreok.getiserver.domain.collector.provider.CollectorCollectionContext
import team.inreok.getiserver.domain.collector.provider.CollectorProviderException
import java.time.LocalDateTime

/**
 * 실제 HTTP 호출 없이 JSON 응답 Parsing/정규화 규칙만 검증한다(WireMock/MockWebServer 미추가
 * 원칙, MmaCollectorProviderTest와 동일한 패턴). Fixture 필드명(recrutPblntSn/recrutPbancTtl/
 * instNm/pbancBgngYmd/pbancEndYmd/srcUrl/acbgCondNmLst/recrutSeNm/hireTypeNmLst/ncsCdNmLst/
 * workRgnNmLst/recrutNope/ongoingYn)은 Issue #62 확장 범위에서 실제 인증키로 호출해 확인했다
 * (최종 보고 참고).
 */
class JobAlioCollectorProviderTest {
    private val provider =
        JobAlioCollectorProvider(
            properties = JobAlioProviderProperties(enabled = true, serviceKey = "test-key"),
            restClientBuilder = RestClient.builder(),
        )

    private val context = CollectorCollectionContext(requestedAt = LocalDateTime.of(2026, 8, 4, 3, 0), since = null)

    @Test
    fun `정상 목록을 정규화된 공고로 변환한다`() {
        val json =
            envelope(
                totalCount = 1,
                items =
                    """
                    {
                      "recrutPblntSn": 303470,
                      "instNm": "한국보훈복지의료공단",
                      "recrutPbancTtl": "전산직 신입 채용",
                      "srcUrl": "https://example.com/job/303470",
                      "pbancBgngYmd": "20260804",
                      "pbancEndYmd": "20260812",
                      "acbgCondNmLst": "학력무관",
                      "recrutSeNm": "신입",
                      "hireTypeNmLst": "정규직",
                      "ncsCdNmLst": "정보통신",
                      "workRgnNmLst": "전남광주",
                      "recrutNope": 2,
                      "ongoingYn": "Y"
                    }
                    """,
            )

        val result = provider.parse(json, context)

        assertThat(result.errors).isEmpty()
        val job = result.jobs.single()
        assertThat(job.externalJobId).isEqualTo("303470")
        assertThat(job.title).isEqualTo("전산직 신입 채용")
        assertThat(job.companyName).isEqualTo("한국보훈복지의료공단")
        assertThat(job.externalUrl).isEqualTo("https://example.com/job/303470")
        assertThat(job.startDate).isEqualTo(LocalDateTime.of(2026, 8, 4, 0, 0))
        assertThat(job.endDate).isEqualTo(LocalDateTime.of(2026, 8, 12, 0, 0))
        assertThat(job.educationCondition).isEqualTo("학력무관")
        assertThat(job.careerCondition).isEqualTo("신입")
        assertThat(job.employmentType).isEqualTo("정규직")
        assertThat(job.jobFieldHint).isEqualTo("정보통신")
        assertThat(job.workRegion).isEqualTo("전남광주")
        assertThat(job.recruitCount).isEqualTo("2")
        assertThat(job.missingFields).containsExactly("content")
    }

    @Test
    fun `진행 중이 아닌 공고(ongoingYn=N)는 결과에서 제외된다`() {
        val json =
            envelope(
                totalCount = 1,
                items =
                    """
                    {
                      "recrutPblntSn": 1,
                      "instNm": "기관",
                      "recrutPbancTtl": "마감된 공고",
                      "ongoingYn": "N"
                    }
                    """,
            )

        val result = provider.parse(json, context)

        assertThat(result.jobs).isEmpty()
    }

    @Test
    fun `원문 URL이 없으면 missingFields에 포함된다`() {
        val json =
            envelope(
                totalCount = 1,
                items = """{ "recrutPblntSn": 1, "instNm": "기관", "recrutPbancTtl": "공고" }""",
            )

        val job = provider.parse(json, context).jobs.single()

        assertThat(job.missingFields).contains("externalUrl", "content")
    }

    @Test
    fun `resultCode 200은 성공으로 처리한다`() {
        val json = """{"resultCode": 200, "resultMsg": "성공했습니다.", "totalCount": 0, "result": []}"""

        val result = provider.parse(json, context)

        assertThat(result.jobs).isEmpty()
        assertThat(result.errors).isEmpty()
    }

    @Test
    fun `resultCode 7은 인증 오류다`() {
        val json = """{"resultCode": 7, "resultMsg": "인증 오류", "totalCount": 0, "result": []}"""

        assertThatThrownBy { provider.parse(json, context) }
            .isInstanceOf(CollectorProviderException.AuthenticationFailed::class.java)
    }

    @Test
    fun `필수 식별자가 없는 항목은 개별 오류로 기록되고 다른 항목 처리는 계속된다`() {
        val json =
            envelope(
                totalCount = 2,
                items =
                    """
                    { "instNm": "기관", "recrutPbancTtl": "공고번호 없음" },
                    { "recrutPblntSn": 2, "instNm": "기관", "recrutPbancTtl": "정상 공고" }
                    """,
            )

        val result = provider.parse(json, context)

        assertThat(result.jobs).hasSize(1)
        assertThat(result.jobs.single().externalJobId).isEqualTo("2")
    }

    @Test
    fun `올바르지 않은 JSON은 ResponseInvalid를 던진다`() {
        assertThatThrownBy { provider.parse("이것은 JSON이 아닙니다", context) }
            .isInstanceOf(CollectorProviderException.ResponseInvalid::class.java)
    }

    @Test
    fun `여러 페이지를 모두 순회해 전체 항목을 수집한다`() {
        val limitedProvider =
            JobAlioCollectorProvider(
                properties = JobAlioProviderProperties(enabled = true, serviceKey = "test-key", pageSize = 1),
                restClientBuilder = RestClient.builder(),
            )
        val calls = mutableListOf<Int>()
        val pages =
            mapOf(
                1 to envelope(totalCount = 2, items = itemOf(1)),
                2 to envelope(totalCount = 2, items = itemOf(2)),
            )

        val result =
            limitedProvider.paginate(context) { pageNo ->
                calls.add(pageNo)
                pages.getValue(pageNo)
            }

        assertThat(calls).containsExactly(1, 2)
        assertThat(result.jobs.map { it.externalJobId }).containsExactlyInAnyOrder("1", "2")
        assertThat(result.requestCount).isEqualTo(2)
    }

    private fun itemOf(id: Int): String =
        """{ "recrutPblntSn": $id, "instNm": "기관", "recrutPbancTtl": "공고 $id", "ongoingYn": "Y" }"""

    private fun envelope(
        totalCount: Int,
        items: String,
    ): String =
        """
        {
          "resultCode": 200,
          "resultMsg": "성공했습니다.",
          "totalCount": $totalCount,
          "result": [$items]
        }
        """.trimIndent()
}
