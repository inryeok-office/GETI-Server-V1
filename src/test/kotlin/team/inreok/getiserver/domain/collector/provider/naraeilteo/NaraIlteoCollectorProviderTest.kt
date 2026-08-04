package team.inreok.getiserver.domain.collector.provider.naraeilteo

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import team.inreok.getiserver.domain.collector.provider.CollectorCollectionContext
import team.inreok.getiserver.domain.collector.provider.CollectorProviderException
import java.time.LocalDateTime

/**
 * 실제 HTTP 호출 없이 XML 응답 Parsing 규칙만 검증한다(WireMock/MockWebServer 미추가 원칙).
 * Fixture 필드명(idx/title/insttname/begindate/enddate/type01/contents/link01)은 Issue #62
 * 확장 범위에서 실제 인증키로 호출해 확인했다(최종 보고 참고).
 */
class NaraIlteoCollectorProviderTest {
    private val context = CollectorCollectionContext(requestedAt = LocalDateTime.of(2026, 8, 4, 3, 0), since = null)
    private val provider =
        NaraIlteoCollectorProvider(
            properties = NaraIlteoProviderProperties(enabled = true, serviceKey = "test-key"),
            restClientBuilder =
                org.springframework.web.client.RestClient
                    .builder(),
        )

    // isLastNaraIlteoListPage가 "요청한 pageSize보다 적은 항목이 오면 마지막 페이지"로 판정하므로,
    // 목록 페이지네이션 자체를 검증하는 Test는 Fixture 항목 수(1건)보다 작은 pageSize를 쓰는
    // 별도 Provider Instance가 필요하다(MmaCollectorProviderTest의 paginatingProvider와 같은 패턴).
    private val paginatingProvider =
        NaraIlteoCollectorProvider(
            properties =
                NaraIlteoProviderProperties(
                    enabled = true,
                    serviceKey = "test-key",
                    pageSize = 1,
                    maxPages = 3,
                ),
            restClientBuilder =
                org.springframework.web.client.RestClient
                    .builder(),
        )

    @Test
    fun `목록 응답을 NaraIlteoListItem으로 변환한다`() {
        val xml =
            listEnvelope(
                totalCount = 1,
                items =
                    """
                    <item>
                        <idx>274507</idx>
                        <title>한국산업인력공단 전산직 채용</title>
                        <insttname>한국산업인력공단</insttname>
                        <begindate>20260720</begindate>
                        <enddate>20260728</enddate>
                        <type01>g03</type01>
                    </item>
                    """,
            )

        val result = provider.parseListPage(xml)

        assertThat(result.itemCount).isEqualTo(1)
        val item = result.items.single()
        assertThat(item.idx).isEqualTo("274507")
        assertThat(item.title).isEqualTo("한국산업인력공단 전산직 채용")
        assertThat(item.insttname).isEqualTo("한국산업인력공단")
    }

    @Test
    fun `상세 응답의 본문과 URL을 정규화된 공고에 반영한다`() {
        val listItem =
            NaraIlteoListItem(
                idx = "274507",
                title = "한국산업인력공단 전산직 채용",
                insttname = "한국산업인력공단",
                begindate = "20260720",
                enddate = "20260728",
                type01 = "g03",
            )
        val detailXml =
            detailEnvelope(
                """
                <item>
                    <idx>274507</idx>
                    <title>한국산업인력공단 전산직 채용</title>
                    <contents>&lt;p&gt;전산 시스템 운영 업무&lt;/p&gt;</contents>
                    <link01>https://example.com/job/274507</link01>
                </item>
                """,
            )

        val job = provider.parseDetail(detailXml, listItem, context)

        assertThat(job).isNotNull
        assertThat(job!!.externalJobId).isEqualTo("274507")
        assertThat(job.content).isEqualTo("전산 시스템 운영 업무")
        assertThat(job.externalUrl).isEqualTo("https://example.com/job/274507")
        assertThat(job.startDate).isEqualTo(LocalDateTime.of(2026, 7, 20, 0, 0))
        assertThat(job.endDate).isEqualTo(LocalDateTime.of(2026, 7, 28, 0, 0))
        assertThat(job.employmentType).isEqualTo("공공기관 채용")
        assertThat(job.missingFields).isEmpty()
    }

    @Test
    fun `모집 시작이 종료보다 늦은 원문 데이터는 둘 다 미상으로 처리하고 예외를 던지지 않는다`() {
        val listItem =
            NaraIlteoListItem(
                idx = "1",
                title = "제목",
                insttname = "기관",
                begindate = "20260810",
                enddate = "20260801",
                type01 = null,
            )
        val detailXml = detailEnvelope("<item><idx>1</idx><title>제목</title></item>")

        val job = provider.parseDetail(detailXml, listItem, context)

        assertThat(job!!.startDate).isNull()
        assertThat(job.endDate).isNull()
        assertThat(job.missingFields).contains("endDate")
    }

    @Test
    fun `Scheme이 없는 원문 URL은 https를 붙여 살린다`() {
        val listItem =
            NaraIlteoListItem(
                idx = "1",
                title = "제목",
                insttname = "기관",
                begindate = null,
                enddate = null,
                type01 = null,
            )
        val detailXml =
            detailEnvelope("<item><idx>1</idx><title>제목</title><link01>www.example.go.kr</link01></item>")

        val job = provider.parseDetail(detailXml, listItem, context)

        assertThat(job!!.externalUrl).isEqualTo("https://www.example.go.kr")
    }

    @Test
    fun `본문 URL이 없으면 missingFields에 포함된다`() {
        val listItem =
            NaraIlteoListItem(
                idx = "1",
                title = "제목",
                insttname = "기관",
                begindate = null,
                enddate = null,
                type01 = null,
            )
        val detailXml = detailEnvelope("<item><idx>1</idx><title>제목</title></item>")

        val job = provider.parseDetail(detailXml, listItem, context)

        assertThat(job!!.missingFields).containsExactlyInAnyOrder("endDate", "content", "externalUrl")
    }

    @Test
    fun `resultCode 00은 성공으로 처리한다`() {
        val result = provider.parseListPage(listEnvelope(totalCount = 0, items = ""))

        assertThat(result.items).isEmpty()
    }

    @Test
    fun `resultCode 20은 인증 오류다`() {
        assertThatThrownBy { provider.parseListPage(listEnvelopeWithResultCode("20")) }
            .isInstanceOf(CollectorProviderException.AuthenticationFailed::class.java)
    }

    @Test
    fun `올바르지 않은 XML은 ResponseInvalid를 던진다`() {
        assertThatThrownBy { provider.parseListPage("이것은 XML이 아닙니다") }
            .isInstanceOf(CollectorProviderException.ResponseInvalid::class.java)
    }

    @Test
    fun `목록 여러 페이지를 모두 순회해 전체 항목을 수집한다`() {
        val pages =
            mapOf(
                1 to
                    listEnvelope(
                        totalCount = 2,
                        items = "<item><idx>1</idx><title>A</title><insttname>기관</insttname></item>",
                    ),
                2 to
                    listEnvelope(
                        totalCount = 2,
                        items = "<item><idx>2</idx><title>B</title><insttname>기관</insttname></item>",
                    ),
            )

        val state = paginatingProvider.paginateList { pageNo -> pages.getValue(pageNo) }

        assertThat(state.items.map { it.idx }).containsExactlyInAnyOrder("1", "2")
        assertThat(state.errors).isEmpty()
        assertThat(state.requestCount).isEqualTo(2)
    }

    @Test
    fun `첫 페이지 조회가 실패하면 예외를 그대로 전파한다`() {
        assertThatThrownBy {
            paginatingProvider.paginateList { throw CollectorProviderException.AuthenticationFailed() }
        }.isInstanceOf(CollectorProviderException.AuthenticationFailed::class.java)
    }

    @Test
    fun `이후 페이지 조회가 실패하면 이전까지의 결과를 보존하고 순회를 중단한다`() {
        val state =
            paginatingProvider.paginateList { pageNo ->
                if (pageNo == 1) {
                    listEnvelope(
                        totalCount = 10,
                        items = "<item><idx>1</idx><title>A</title><insttname>기관</insttname></item>",
                    )
                } else {
                    throw CollectorProviderException.ServerError()
                }
            }

        assertThat(state.items).hasSize(1)
        assertThat(state.errors).hasSize(1)
        assertThat(state.errors.single().code).isEqualTo("COLLECTOR_PAGE_FETCH_FAILED")
        // 실패한 페이지 조회 시도도 실제 발생한 외부 API 요청이므로 집계에 포함되어야 한다.
        assertThat(state.requestCount).isEqualTo(2)
    }

    private fun listEnvelope(
        totalCount: Int,
        items: String,
    ): String =
        """
        <response>
            <header><resultCode>00</resultCode><resultMsg>NORMAL SERVICE.</resultMsg></header>
            <body><items>$items</items><numOfRows>10</numOfRows><pageNo>1</pageNo><totalCount>$totalCount</totalCount></body>
        </response>
        """.trimIndent()

    private fun listEnvelopeWithResultCode(code: String): String =
        """
        <response>
            <header><resultCode>$code</resultCode><resultMsg>ERROR</resultMsg></header>
            <body><items></items><totalCount>0</totalCount></body>
        </response>
        """.trimIndent()

    private fun detailEnvelope(itemXml: String): String =
        """
        <response>
            <header><resultCode>00</resultCode><resultMsg>NORMAL SERVICE.</resultMsg></header>
            <body>$itemXml</body>
        </response>
        """.trimIndent()
}
