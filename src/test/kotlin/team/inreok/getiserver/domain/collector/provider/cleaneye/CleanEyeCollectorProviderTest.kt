package team.inreok.getiserver.domain.collector.provider.cleaneye

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.web.client.RestClient
import team.inreok.getiserver.domain.collector.provider.CollectorCollectionContext
import team.inreok.getiserver.domain.collector.provider.CollectorProviderException
import java.time.LocalDateTime

/**
 * 실제 HTTP 호출 없이 XML 응답 Parsing/정규화 규칙과 시도(sidoCd) 순회만 검증한다(WireMock/
 * MockWebServer 미추가 원칙). Fixture 필드명(ENT_NAME/ENT_TITLE/STATUS/DUTY_DETAIL/URL/
 * PUB_DATE/PUB_END_DATE/EMPLOY_GB/JOB_TYPE/ENT_RECRUIT/WORK_PLACE/EMPLOY_NUM/NO)은
 * Issue #62 확장 범위에서 실제 인증키로 호출해 확인했다(최종 보고 참고).
 */
class CleanEyeCollectorProviderTest {
    private val provider =
        CleanEyeCollectorProvider(
            properties = CleanEyeProviderProperties(enabled = true, serviceKey = "test-key"),
            restClientBuilder = RestClient.builder(),
        )

    private val context = CollectorCollectionContext(requestedAt = LocalDateTime.of(2026, 8, 4, 3, 0), since = null)

    @Test
    fun `정상 목록을 정규화된 공고로 변환한다`() {
        val xml =
            envelope(
                """
                <item>
                    <NO>78293</NO>
                    <ENT_NAME>중구시설관리공단</ENT_NAME>
                    <ENT_TITLE>전산직 채용 공고</ENT_TITLE>
                    <STATUS>모집중</STATUS>
                    <ENT_RECRUIT>정보통신</ENT_RECRUIT>
                    <EMPLOY_NUM>1</EMPLOY_NUM>
                    <EMPLOY_GB>신입+경력</EMPLOY_GB>
                    <JOB_TYPE>일반정규직</JOB_TYPE>
                    <WORK_PLACE>서울특별시 중구</WORK_PLACE>
                    <DUTY_DETAIL>전산 시스템 운영</DUTY_DETAIL>
                    <PUB_DATE>2026-07-31</PUB_DATE>
                    <PUB_END_DATE>2026-08-10</PUB_END_DATE>
                    <URL>https://job.cleaneye.go.kr/user/ypCareersData.do?idx=78293</URL>
                </item>
                """,
            )

        val result = provider.parsePage(xml, context)

        assertThat(result.errors).isEmpty()
        val job = result.jobs.single()
        assertThat(job.externalJobId).isEqualTo("78293")
        assertThat(job.title).isEqualTo("전산직 채용 공고")
        assertThat(job.companyName).isEqualTo("중구시설관리공단")
        assertThat(job.content).isEqualTo("전산 시스템 운영")
        assertThat(job.externalUrl).isEqualTo("https://job.cleaneye.go.kr/user/ypCareersData.do?idx=78293")
        assertThat(job.startDate).isEqualTo(LocalDateTime.of(2026, 7, 31, 0, 0))
        assertThat(job.endDate).isEqualTo(LocalDateTime.of(2026, 8, 10, 0, 0))
        assertThat(job.careerCondition).isEqualTo("신입+경력")
        assertThat(job.employmentType).isEqualTo("일반정규직")
        assertThat(job.jobFieldHint).isEqualTo("정보통신")
        assertThat(job.workRegion).isEqualTo("서울특별시 중구")
        assertThat(job.recruitCount).isEqualTo("1")
        assertThat(job.missingFields).isEmpty()
    }

    @Test
    fun `마감된 공고는 결과에서 제외된다`() {
        val xml =
            envelope(
                """
                <item>
                    <NO>1</NO>
                    <ENT_NAME>기관</ENT_NAME>
                    <ENT_TITLE>마감 공고</ENT_TITLE>
                    <STATUS>모집마감</STATUS>
                </item>
                """,
            )

        val result = provider.parsePage(xml, context)

        assertThat(result.jobs).isEmpty()
    }

    @Test
    fun `resultCode 0은 성공으로 처리한다`() {
        val result = provider.parsePage(envelopeWithResultCode("0", ""), context)

        assertThat(result.jobs).isEmpty()
        assertThat(result.errors).isEmpty()
    }

    @Test
    fun `resultCode 3(데이터 없음)은 오류가 아니라 빈 결과로 처리한다`() {
        val result = provider.parsePage(envelopeWithResultCode("3", ""), context)

        assertThat(result.jobs).isEmpty()
        assertThat(result.errors).isEmpty()
    }

    @Test
    fun `resultCode 21은 인증 오류다`() {
        assertThatThrownBy { provider.parsePage(envelopeWithResultCode("21", ""), context) }
            .isInstanceOf(CollectorProviderException.AuthenticationFailed::class.java)
    }

    @Test
    fun `올바르지 않은 XML은 ResponseInvalid를 던진다`() {
        assertThatThrownBy { provider.parsePage("이것은 XML이 아닙니다", context) }
            .isInstanceOf(CollectorProviderException.ResponseInvalid::class.java)
    }

    @Test
    fun `16개 시도를 모두 순회하고 시도별 요청 횟수를 기록한다`() {
        val calls = mutableListOf<String>()

        val result =
            provider.collectAllSido(context) { sidoCd ->
                calls.add(sidoCd)
                envelope("")
            }

        assertThat(calls).hasSize(16)
        assertThat(calls).isEqualTo(KNOWN_SIDO_CODES)
        assertThat(result.requestCount).isEqualTo(16)
    }

    @Test
    fun `한 시도 조회가 실패해도 다른 시도 수집은 계속된다`() {
        val result =
            provider.collectAllSido(context) { sidoCd ->
                if (sidoCd == KNOWN_SIDO_CODES.first()) {
                    throw CollectorProviderException.ServerError()
                }
                envelope(
                    """
                    <item>
                        <NO>1</NO>
                        <ENT_NAME>기관</ENT_NAME>
                        <ENT_TITLE>공고</ENT_TITLE>
                    </item>
                    """,
                )
            }

        assertThat(result.errors).hasSize(1)
        assertThat(result.jobs).isNotEmpty()
    }

    private fun envelope(itemsXml: String): String =
        """
        <response>
            <openXmlEmployInfo-header><resultCode>0</resultCode><resultMsg>SUCCESS_INFO</resultMsg></openXmlEmployInfo-header>
            <openXmlEmployInfo-body><items>$itemsXml</items><totalCount>0</totalCount></openXmlEmployInfo-body>
        </response>
        """.trimIndent()

    private fun envelopeWithResultCode(
        code: String,
        itemsXml: String,
    ): String =
        """
        <response>
            <openXmlEmployInfo-header><resultCode>$code</resultCode><resultMsg>MSG</resultMsg></openXmlEmployInfo-header>
            <openXmlEmployInfo-body><items>$itemsXml</items><totalCount>0</totalCount></openXmlEmployInfo-body>
        </response>
        """.trimIndent()
}
