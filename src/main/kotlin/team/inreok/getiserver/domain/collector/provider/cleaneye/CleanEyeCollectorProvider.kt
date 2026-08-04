package team.inreok.getiserver.domain.collector.provider.cleaneye

import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.http.HttpStatusCode
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.body
import org.springframework.web.util.UriComponentsBuilder
import team.inreok.getiserver.domain.collector.entity.type.JobDataQualityStatus
import team.inreok.getiserver.domain.collector.entity.type.JobSourceCode
import team.inreok.getiserver.domain.collector.provider.CollectorCollectionContext
import team.inreok.getiserver.domain.collector.provider.CollectorCollectionResult
import team.inreok.getiserver.domain.collector.provider.CollectorItemError
import team.inreok.getiserver.domain.collector.provider.CollectorProvider
import team.inreok.getiserver.domain.collector.provider.CollectorProviderException
import team.inreok.getiserver.domain.collector.provider.NormalizedCollectedJob
import team.inreok.getiserver.domain.collector.provider.ServiceKeyCodec
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/** 시도 하나를 Parsing한 결과. */
internal data class CleanEyePageResult(
    val jobs: List<NormalizedCollectedJob>,
    val errors: List<CollectorItemError>,
)

/**
 * 행정안전부 한국지역정보개발원_채용정보 조회 서비스(클린아이) Provider Adapter다. 공공데이터
 * 포털 활용신청 상세 페이지(https://www.data.go.kr/data/15159757/openapi.do)에 포함된 Swagger
 * 문서로 실제 Endpoint(`/B551982/openApiEmployInfo/openXmlEmployInfo`)·Query Parameter·
 * 응답 필드를 확인했고, 실제 인증키로 호출해 정상 응답(resultCode=0)을 받아 검증했다(Issue #62
 * 확장 범위, 최종 보고 참고).
 *
 * 이 API는 Page 단위가 아니라 시도(sidoCd) 단위로 그 시도 전체 결과를 한 번에 반환한다(Swagger
 * 문서에 pageNo/numOfRows Parameter가 없다). 시도코드 승인이 별도로 필요한
 * "지방공기업 시도코드 조회 서비스"는 이번 인증키로 호출할 수 없어(SERVICE_KEY_IS_NOT_REGISTERED_ERROR
 * 확인), 클린아이 잡플러스(job.cleaneye.go.kr)의 검색 화면이 실제로 사용하는 공개 코드 목록
 * (`POST /common/selectAdminCode.do`, gubun=2, dtlCd=T000008)에서 16개 시도코드를 그대로
 * 가져왔다 — 추측이 아니라 클린아이 자체 화면이 쓰는 값을 그대로 재사용한 것이다.
 */
@Component
@EnableConfigurationProperties(CleanEyeProviderProperties::class)
class CleanEyeCollectorProvider(
    private val properties: CleanEyeProviderProperties,
    restClientBuilder: RestClient.Builder,
) : CollectorProvider {
    private val restClient = restClientBuilder.build()

    override val sourceCode = JobSourceCode.CLEAN_EYE

    override fun isConfigured(): Boolean = properties.isConfigured()

    override fun collect(context: CollectorCollectionContext): CollectorCollectionResult =
        collectAllSido(context) { sidoCd -> fetchSido(sidoCd) }

    // 시도 하나의 실패가 다른 시도 수집을 막지 않도록 격리한다(Provider 하나의 실패가 다른
    // Provider를 막지 않는 것과 같은 원칙을 시도 단위로 적용).
    internal fun collectAllSido(
        context: CollectorCollectionContext,
        fetchSido: (sidoCd: String) -> String,
    ): CollectorCollectionResult {
        val jobs = mutableListOf<NormalizedCollectedJob>()
        val errors = mutableListOf<CollectorItemError>()
        val seenExternalIds = mutableSetOf<String>()
        var requestCount = 0

        KNOWN_SIDO_CODES.forEach { sidoCd ->
            requestCount++
            val page =
                try {
                    parsePage(fetchSido(sidoCd), context)
                } catch (ex: CollectorProviderException) {
                    errors.add(
                        CollectorItemError(
                            externalJobId = null,
                            code = "COLLECTOR_PAGE_FETCH_FAILED",
                            message = "시도($sidoCd) 조회에 실패해 이 시도는 건너뜁니다: ${ex.message}",
                        ),
                    )
                    return@forEach
                }
            page.jobs.forEach { job -> if (seenExternalIds.add(job.externalJobId)) jobs.add(job) }
            errors.addAll(page.errors)
        }

        return CollectorCollectionResult(jobs = jobs, errors = errors, requestCount = requestCount)
    }

    @Suppress("ThrowsCount")
    private fun fetchSido(sidoCd: String): String {
        val uri = buildUri(sidoCd)
        val body =
            try {
                restClient
                    .get()
                    .uri(uri)
                    .retrieve()
                    .onStatus({ status: HttpStatusCode -> status.is4xxClientError }) { _, response ->
                        throw if (response.statusCode.value() == 429) {
                            CollectorProviderException.RateLimited()
                        } else {
                            CollectorProviderException.ClientError(
                                "클린아이가 잘못된 요청 오류를 반환했습니다(${response.statusCode.value()}).",
                            )
                        }
                    }.onStatus({ status: HttpStatusCode -> status.is5xxServerError }) { _, response ->
                        throw CollectorProviderException.ServerError(
                            "클린아이가 서버 오류를 반환했습니다(${response.statusCode.value()}).",
                        )
                    }.body<ByteArray>()
            } catch (ex: CollectorProviderException) {
                throw ex
            } catch (ex: java.net.SocketTimeoutException) {
                throw CollectorProviderException.Timeout(cause = ex)
            } catch (ex: org.springframework.web.client.ResourceAccessException) {
                throw CollectorProviderException.NetworkError(ex.message ?: "클린아이 호출 중 네트워크 오류가 발생했습니다.", cause = ex)
            }

        // 클린아이도 MMA와 같은 이유(Content-Type에 charset 미포함, 실제 응답은 UTF-8)로 원본
        // Byte를 직접 받아 UTF-8로 명시적으로 Decoding한다(MmaCollectorProvider.fetchPage 참고).
        return body?.let { String(it, StandardCharsets.UTF_8) }
            ?: throw CollectorProviderException.ResponseInvalid("클린아이 응답 본문이 비어 있습니다.")
    }

    private fun buildUri(sidoCd: String): java.net.URI {
        val decodedKey = ServiceKeyCodec.decodeOnce(properties.serviceKey)
        return UriComponentsBuilder
            .fromUriString(properties.baseUrl)
            .queryParam("serviceKey", ServiceKeyCodec.encode(decodedKey))
            .queryParam("sidoCd", sidoCd)
            .queryParam("type", "xml")
            .build(true)
            .toUri()
    }

    // 단일 시도 응답 Parsing/정규화 규칙만 검증할 수 있도록 internal로 연다(WireMock/MockWebServer
    // 미추가 원칙, MmaCollectorProvider와 같은 패턴).
    @Suppress("TooGenericExceptionCaught")
    internal fun parsePage(
        xml: String,
        context: CollectorCollectionContext,
    ): CleanEyePageResult {
        val document =
            try {
                val factory =
                    DocumentBuilderFactory.newInstance().apply {
                        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                        setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
                        isExpandEntityReferences = false
                    }
                factory.newDocumentBuilder().parse(xml.byteInputStream(Charsets.UTF_8))
            } catch (ex: Exception) {
                throw CollectorProviderException.ResponseInvalid("클린아이 응답 XML을 해석할 수 없습니다.", cause = ex)
            }

        val resultCode =
            document
                .getElementsByTagName("resultCode")
                .item(0)
                ?.textContent
                ?.trim()
        validateResultCode(resultCode)

        val items = document.getElementsByTagName("item")
        val jobs = mutableListOf<NormalizedCollectedJob>()
        val errors = mutableListOf<CollectorItemError>()
        for (i in 0 until items.length) {
            val element = items.item(i) as? org.w3c.dom.Element ?: continue
            runCatching { toNormalizedJob(element, context) }
                .onSuccess { job -> if (job != null) jobs.add(job) }
                .onFailure { ex ->
                    errors.add(
                        CollectorItemError(
                            externalJobId = null,
                            code = "COLLECTED_JOB_NORMALIZATION_FAILED",
                            message = ex.message ?: "정규화 실패",
                        ),
                    )
                }
        }
        return CleanEyePageResult(jobs = jobs, errors = errors)
    }

    // Swagger 문서의 오류 코드 표(0/2/3/5/10/11/21/33)를 그대로 처리해야 하므로 분기가 많다.
    @Suppress("ThrowsCount")
    private fun validateResultCode(resultCode: String?) {
        when (resultCode) {
            null, "0", "3" -> Unit
            "21" -> throw CollectorProviderException.AuthenticationFailed("클린아이 인증 오류(resultCode=$resultCode)")
            "33" -> throw CollectorProviderException.ClientError("클린아이 서명되지 않은 호출(resultCode=$resultCode)")
            "5" -> throw CollectorProviderException.Timeout("클린아이 응답 지연(resultCode=$resultCode)")
            "2" -> throw CollectorProviderException.ServerError("클린아이 서버 오류(resultCode=$resultCode)")
            "10", "11" -> throw CollectorProviderException.ClientError("클린아이 요청 오류(resultCode=$resultCode)")
            else -> throw CollectorProviderException.ResponseInvalid("클린아이 알 수 없는 오류(resultCode=$resultCode)")
        }
    }

    private fun toNormalizedJob(
        element: org.w3c.dom.Element,
        context: CollectorCollectionContext,
    ): NormalizedCollectedJob? = buildCleanEyeNormalizedJob(element, context, sourceCode)

    private companion object {
        val log = LoggerFactory.getLogger(CleanEyeCollectorProvider::class.java)
    }
}

// 클린아이 잡플러스(job.cleaneye.go.kr) 검색 화면이 실제로 사용하는 시도코드다
// (POST https://job.cleaneye.go.kr/common/selectAdminCode.do, gubun=2, dtlCd=T000008로 확인,
// Issue #62 확장 범위 최종 보고 참고). 세종특별자치시가 포함되고 광주광역시가 "전남광주통합
// 특별시"로 합쳐진 최신 행정구역 개편을 그대로 반영한다 — 임의로 추정하거나 줄이지 않았다.
internal val KNOWN_SIDO_CODES =
    listOf(
        "007001",
        "007002",
        "007003",
        "007004",
        "007006",
        "007007",
        "007017",
        "007008",
        "007009",
        "007010",
        "007011",
        "007012",
        "007013",
        "007014",
        "007015",
        "007016",
    )

// CleanEyeCollectorProvider의 Instance 상태가 필요 없는 순수 함수라 클래스 밖으로 뺐다.
@Suppress("ReturnCount")
private fun buildCleanEyeNormalizedJob(
    element: org.w3c.dom.Element,
    context: CollectorCollectionContext,
    sourceCode: JobSourceCode,
): NormalizedCollectedJob? {
    val status = text(element, "STATUS")
    if (status != null && (status.contains("마감") || status.contains("종료"))) return null

    val externalJobId = text(element, "NO") ?: return null
    val title = text(element, "ENT_TITLE") ?: return null
    val companyName = text(element, "ENT_NAME") ?: return null

    val missingFields = mutableListOf<String>()
    val startDate = parseIsoDate(text(element, "PUB_DATE"))
    val endDate = parseIsoDate(text(element, "PUB_END_DATE"))
    if (endDate == null) missingFields.add("endDate")
    val content = text(element, "DUTY_DETAIL")
    if (content == null) missingFields.add("content")
    val externalUrl = text(element, "URL")
    if (externalUrl == null) missingFields.add("externalUrl")

    val licenses =
        listOfNotNull(
            text(element, "ENT_LICENSE1"),
            text(element, "ENT_LICENSE2"),
            text(element, "ENT_LICENSE3"),
            text(element, "ENT_LICENSE4"),
        ).filter { it != "-" }
    val qualificationDetail =
        (licenses + listOfNotNull(text(element, "SPECIAL_ITEM")).filter { it != "-" })
            .joinToString(separator = " ")
            .takeIf { it.isNotBlank() }

    return NormalizedCollectedJob(
        sourceCode = sourceCode,
        externalJobId = externalJobId,
        title = title,
        companyName = companyName,
        content = content,
        externalUrl = externalUrl,
        startDate = startDate,
        endDate = endDate,
        collectedAt = context.requestedAt,
        dataQualityStatus = JobDataQualityStatus.PARTIAL,
        missingFields = missingFields,
        educationCondition = null,
        careerCondition = text(element, "EMPLOY_GB"),
        employmentType = text(element, "JOB_TYPE"),
        jobFieldHint = text(element, "ENT_RECRUIT"),
        workRegion = text(element, "WORK_PLACE") ?: text(element, "ADDRESS"),
        qualificationDetail = qualificationDetail,
        recruitCount = text(element, "EMPLOY_NUM"),
        militaryServiceType = null,
    )
}

private fun text(
    element: org.w3c.dom.Element,
    tagName: String,
): String? =
    element
        .getElementsByTagName(tagName)
        .item(0)
        ?.textContent
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

private fun parseIsoDate(raw: String?): LocalDateTime? {
    if (raw.isNullOrBlank()) return null
    return runCatching { LocalDate.parse(raw, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay() }.getOrNull()
}
