package team.inreok.getiserver.domain.collector.provider.mma

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.web.client.RestClient
import team.inreok.getiserver.domain.collector.entity.type.JobDataQualityStatus
import team.inreok.getiserver.domain.collector.provider.CollectorCollectionContext
import team.inreok.getiserver.domain.collector.provider.CollectorProviderException
import java.time.LocalDateTime

/**
 * 실제 HTTP 호출 없이 XML 응답 Parsing/정규화 규칙만 검증한다(WireMock/MockWebServer를 새로
 * 추가하지 않기 위해 `MmaCollectorProvider.parse`를 internal로 열어 직접 호출, Issue #62 참고).
 * Fixture 필드명(cygonggoNo/cyjemokNm/eopcheNm/magamDt/yuhyoYn/ddeopmuNm/cjhakryeok/
 * gyeongryeokGbcdNm/gmhyeongtaeNm/geunmujy/mjinwonNm/yowonGbcdNm)은 Issue #62 확장 범위에서
 * 실제 인증키로 호출해 확인했다(최종 보고 참고).
 */
class MmaCollectorProviderTest {
    private val provider =
        MmaCollectorProvider(
            properties = MmaProviderProperties(enabled = true, serviceKey = "test-key"),
            restClientBuilder = RestClient.builder(),
        )

    private val context = CollectorCollectionContext(requestedAt = LocalDateTime.of(2026, 8, 4, 3, 0), since = null)

    @Test
    fun `정상 목록을 정규화된 공고로 변환한다`() {
        val xml =
            envelope(
                """
                <item>
                    <cygonggoNo>2026-000123</cygonggoNo>
                    <cyjemokNm>백엔드 개발자 모집</cyjemokNm>
                    <eopcheNm>인력개발원</eopcheNm>
                    <magamDt>20261231</magamDt>
                    <yuhyoYn>Y</yuhyoYn>
                </item>
                """,
            )

        val result = provider.parse(xml, context)

        assertThat(result.errors).isEmpty()
        assertThat(result.jobs).hasSize(1)
        val job = result.jobs.single()
        assertThat(job.externalJobId).isEqualTo("2026-000123")
        assertThat(job.title).isEqualTo("백엔드 개발자 모집")
        assertThat(job.companyName).isEqualTo("인력개발원")
        assertThat(job.endDate).isEqualTo(LocalDateTime.of(2026, 12, 31, 0, 0))
        // 본문·원문 URL 필드가 공식 목록에 없어 항상 PARTIAL·missingFields에 포함된다.
        assertThat(job.dataQualityStatus).isEqualTo(JobDataQualityStatus.PARTIAL)
        assertThat(job.missingFields).containsExactlyInAnyOrder("content", "externalUrl")
    }

    @Test
    fun `실제 응답에 있는 학력·경력·고용형태·근무지역·모집인원·요원구분을 그대로 담는다`() {
        val xml =
            envelope(
                """
                <item>
                    <cygonggoNo>2026-000200</cygonggoNo>
                    <cyjemokNm>정보처리 산업기능요원 모집</cyjemokNm>
                    <eopcheNm>인력개발원</eopcheNm>
                    <magamDt>20261231</magamDt>
                    <yuhyoYn>Y</yuhyoYn>
                    <ddeopmuNm>사내 업무 시스템 유지보수</ddeopmuNm>
                    <cjhakryeok>고등학교졸업</cjhakryeok>
                    <gyeongryeokGbcdNm>신입/경력</gyeongryeokGbcdNm>
                    <gmhyeongtaeNm>주5일</gmhyeongtaeNm>
                    <jggyeyeolCdNm>공학계</jggyeyeolCdNm>
                    <geunmujy>경상남도 창원시 의창구</geunmujy>
                    <mjinwonNm>1명</mjinwonNm>
                    <yowonGbcdNm>산업기능요원</yowonGbcdNm>
                </item>
                """,
            )

        val job = provider.parse(xml, context).jobs.single()

        assertThat(job.content).isEqualTo("사내 업무 시스템 유지보수")
        assertThat(job.educationCondition).isEqualTo("고등학교졸업")
        assertThat(job.careerCondition).isEqualTo("신입/경력")
        assertThat(job.employmentType).isEqualTo("주5일")
        assertThat(job.jobFieldHint).isEqualTo("공학계")
        assertThat(job.workRegion).isEqualTo("경상남도 창원시 의창구")
        assertThat(job.recruitCount).isEqualTo("1명")
        assertThat(job.militaryServiceType).isEqualTo("산업기능요원")
        // 원문 URL 대응 필드가 없어 content가 채워져도 externalUrl은 여전히 missingFields다.
        assertThat(job.missingFields).containsExactly("externalUrl")
    }

    @Test
    fun `빈 목록이면 오류 없이 빈 결과를 반환한다`() {
        val result = provider.parse(envelope(""), context)

        assertThat(result.jobs).isEmpty()
        assertThat(result.errors).isEmpty()
    }

    @Test
    fun `마감일이 없으면 endDate도 missingFields에 포함된다`() {
        val xml =
            envelope(
                """
                <item>
                    <cygonggoNo>2026-000124</cygonggoNo>
                    <cyjemokNm>프론트엔드 개발자 모집</cyjemokNm>
                    <eopcheNm>인력개발원</eopcheNm>
                    <yuhyoYn>Y</yuhyoYn>
                </item>
                """,
            )

        val job = provider.parse(xml, context).jobs.single()

        assertThat(job.endDate).isNull()
        assertThat(job.missingFields).contains("endDate", "content", "externalUrl")
    }

    @Test
    fun `유효하지 않은 공고(yuhyoYn=N)는 결과에서 제외된다`() {
        val xml =
            envelope(
                """
                <item>
                    <cygonggoNo>2026-000125</cygonggoNo>
                    <cyjemokNm>마감된 공고</cyjemokNm>
                    <eopcheNm>인력개발원</eopcheNm>
                    <yuhyoYn>N</yuhyoYn>
                </item>
                """,
            )

        val result = provider.parse(xml, context)

        assertThat(result.jobs).isEmpty()
        assertThat(result.errors).isEmpty()
    }

    @Test
    fun `필수 식별 필드가 없는 항목은 개별 오류로 기록되고 다른 항목 처리는 계속된다`() {
        val xml =
            envelope(
                """
                <item>
                    <cyjemokNm>공고번호 없음</cyjemokNm>
                    <eopcheNm>인력개발원</eopcheNm>
                    <yuhyoYn>Y</yuhyoYn>
                </item>
                <item>
                    <cygonggoNo>2026-000126</cygonggoNo>
                    <cyjemokNm>정상 공고</cyjemokNm>
                    <eopcheNm>인력개발원</eopcheNm>
                    <yuhyoYn>Y</yuhyoYn>
                </item>
                """,
            )

        val result = provider.parse(xml, context)

        assertThat(result.jobs).hasSize(1)
        assertThat(result.jobs.single().externalJobId).isEqualTo("2026-000126")
    }

    @Test
    fun `resultCode가 인증 오류면 AuthenticationFailed를 던진다`() {
        assertThatThrownBy { provider.parse(envelopeWithResultCode("20"), context) }
            .isInstanceOf(CollectorProviderException.AuthenticationFailed::class.java)
    }

    @Test
    fun `resultCode가 호출 제한 초과면 RateLimited를 던진다`() {
        assertThatThrownBy { provider.parse(envelopeWithResultCode("22"), context) }
            .isInstanceOf(CollectorProviderException.RateLimited::class.java)
    }

    @Test
    fun `resultCode가 NODATA(03)면 오류가 아니라 빈 결과로 처리한다`() {
        val result = provider.parse(envelopeWithResultCode("03"), context)

        assertThat(result.jobs).isEmpty()
        assertThat(result.errors).isEmpty()
    }

    @Test
    fun `올바르지 않은 XML은 ResponseInvalid를 던진다`() {
        assertThatThrownBy { provider.parse("이것은 XML이 아닙니다", context) }
            .isInstanceOf(CollectorProviderException.ResponseInvalid::class.java)
    }

    @Test
    fun `여러 페이지를 모두 순회해 전체 항목을 수집한다`() {
        val pages =
            mapOf(
                1 to envelopeWithItems(items("2026-000201", "2026-000202"), totalCount = 3),
                2 to envelopeWithItems(items("2026-000203"), totalCount = 3),
            )

        val result = paginatingProvider.paginate(context) { pageNo -> pages.getValue(pageNo) }

        assertThat(result.jobs.map { it.externalJobId })
            .containsExactlyInAnyOrder("2026-000201", "2026-000202", "2026-000203")
        assertThat(result.errors).isEmpty()
    }

    @Test
    fun `요청한 페이지 크기보다 적은 항목이 오면 마지막 페이지로 판단해 순회를 멈춘다`() {
        val calls = mutableListOf<Int>()
        val pages =
            mapOf(
                1 to envelopeWithItems(items("A", "B"), totalCount = null),
                2 to envelopeWithItems(items("C"), totalCount = null),
            )

        paginatingProvider.paginate(context) { pageNo ->
            calls.add(pageNo)
            pages.getValue(pageNo)
        }

        assertThat(calls).containsExactly(1, 2)
    }

    @Test
    fun `페이지 간 동일한 외부 공고 ID는 한 번만 결과에 반영한다`() {
        val pages =
            mapOf(
                1 to envelopeWithItems(items("DUP-1", "DUP-2"), totalCount = 4),
                2 to envelopeWithItems(items("DUP-2", "DUP-3"), totalCount = 4),
            )

        val result = paginatingProvider.paginate(context) { pageNo -> pages.getValue(pageNo) }

        assertThat(result.jobs.map { it.externalJobId })
            .containsExactlyInAnyOrder("DUP-1", "DUP-2", "DUP-3")
    }

    @Test
    fun `totalCount에 도달하면 페이지가 가득 차 있어도 순회를 멈춘다`() {
        val calls = mutableListOf<Int>()
        val pages =
            mapOf(
                1 to envelopeWithItems(items("A", "B"), totalCount = 2),
                2 to envelopeWithItems(items("C", "D"), totalCount = 2),
            )

        val result =
            paginatingProvider.paginate(context) { pageNo ->
                calls.add(pageNo)
                pages.getValue(pageNo)
            }

        assertThat(calls).containsExactly(1)
        assertThat(result.jobs).hasSize(2)
    }

    @Test
    fun `중간 페이지가 비어 있으면 순회를 멈춘다`() {
        val calls = mutableListOf<Int>()
        val pages =
            mapOf(
                1 to envelopeWithItems(items("A", "B"), totalCount = 10),
                2 to envelopeWithItems("", totalCount = 10),
            )

        val result =
            paginatingProvider.paginate(context) { pageNo ->
                calls.add(pageNo)
                pages.getValue(pageNo)
            }

        assertThat(calls).containsExactly(1, 2)
        assertThat(result.jobs).hasSize(2)
    }

    @Test
    fun `페이지 조회 중 Provider 오류가 발생하면 이전까지의 결과를 보존하고 순회를 중단한다`() {
        val result =
            paginatingProvider.paginate(context) { pageNo ->
                if (pageNo == 1) {
                    envelopeWithItems(items("A", "B"), totalCount = 10)
                } else {
                    throw CollectorProviderException.ServerError()
                }
            }

        assertThat(result.jobs).hasSize(2)
        assertThat(result.errors).hasSize(1)
        assertThat(result.errors.single().code).isEqualTo("COLLECTOR_PAGE_FETCH_FAILED")
    }

    @Test
    fun `첫 페이지 조회가 실패하면 예외를 그대로 전파한다`() {
        assertThatThrownBy {
            paginatingProvider.paginate(context) { throw CollectorProviderException.AuthenticationFailed() }
        }.isInstanceOf(CollectorProviderException.AuthenticationFailed::class.java)
    }

    @Test
    fun `동일한 항목을 반환하는 페이지가 반복되면 순회를 중단한다(중복 페이지 감지)`() {
        val calls = mutableListOf<Int>()

        val result =
            paginatingProvider.paginate(context) { pageNo ->
                calls.add(pageNo)
                envelopeWithItems(items("A", "B"), totalCount = 9999)
            }

        assertThat(calls).containsExactly(1, 2)
        assertThat(result.jobs).hasSize(2)
    }

    @Test
    fun `최대 페이지 수에 도달하면 더 이상 조회하지 않고 안전하게 종료한다`() {
        val limitedProvider =
            MmaCollectorProvider(
                properties = MmaProviderProperties(enabled = true, serviceKey = "test-key", pageSize = 2, maxPages = 3),
                restClientBuilder = RestClient.builder(),
            )
        val calls = mutableListOf<Int>()

        limitedProvider.paginate(context) { pageNo ->
            calls.add(pageNo)
            envelopeWithItems(items("A-$pageNo", "B-$pageNo"), totalCount = 9_999)
        }

        assertThat(calls).containsExactly(1, 2, 3)
    }

    private val paginatingProvider =
        MmaCollectorProvider(
            properties = MmaProviderProperties(enabled = true, serviceKey = "test-key", pageSize = 2),
            restClientBuilder = RestClient.builder(),
        )

    private fun items(vararg externalJobIds: String): String =
        externalJobIds.joinToString(separator = "") { id ->
            """
            <item>
                <cygonggoNo>$id</cygonggoNo>
                <cyjemokNm>공고 $id</cyjemokNm>
                <eopcheNm>인력개발원</eopcheNm>
                <yuhyoYn>Y</yuhyoYn>
            </item>
            """
        }

    private fun envelopeWithItems(
        itemsXml: String,
        totalCount: Int?,
    ): String =
        """
        <response>
            <header><resultCode>00</resultCode><resultMsg>NORMAL_SERVICE</resultMsg></header>
            <body><items>$itemsXml</items><totalCount>${totalCount ?: ""}</totalCount></body>
        </response>
        """.trimIndent()

    private fun envelope(itemsXml: String): String =
        """
        <response>
            <header><resultCode>00</resultCode><resultMsg>NORMAL_SERVICE</resultMsg></header>
            <body><items>$itemsXml</items><numOfRows>10</numOfRows><pageNo>1</pageNo><totalCount>0</totalCount></body>
        </response>
        """.trimIndent()

    private fun envelopeWithResultCode(code: String): String =
        """
        <response>
            <header><resultCode>$code</resultCode><resultMsg>ERROR</resultMsg></header>
            <body><items></items><numOfRows>10</numOfRows><pageNo>1</pageNo><totalCount>0</totalCount></body>
        </response>
        """.trimIndent()
}
