package team.inreok.getiserver.domain.collector.provider.mma

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
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/**
 * 페이지 하나를 Parsing한 결과다. [itemCount]는 정규화 실패/무효(yuhyoYn=N)로 제외된 항목까지
 * 포함한 원본 `item` 개수로, 마지막 페이지 판정(요청한 pageSize보다 적게 왔는지)과 totalCount
 * 대비 누적 조회 수를 계산하는 기준으로 쓴다(정규화된 jobs.size와 다를 수 있다).
 */
internal data class MmaPageResult(
    val jobs: List<NormalizedCollectedJob>,
    val errors: List<CollectorItemError>,
    val itemCount: Int,
    val totalCount: Int?,
)

/** [MmaCollectorProvider.paginate] 순회 중 누적되는 상태다. */
private class MmaPaginationState {
    val jobs = mutableListOf<NormalizedCollectedJob>()
    val errors = mutableListOf<CollectorItemError>()
    val seenExternalIds = mutableSetOf<String>()
    var previousPageIds: Set<String>? = null
    var fetchedItemCount = 0
    var totalCount: Int? = null
}

/**
 * 병무청 병역일터(MMA) Provider Adapter다. 공식 활용신청 상세 페이지
 * (https://www.data.go.kr/data/3065599/openapi.do)에서 확인한 Endpoint(`CyJeongBo/list`)와
 * 목록 항목 필드명을 기준으로 구현했다.
 *
 * **알려진 한계(최종 보고 참고)**: 공식 페이지가 요청/응답 예시(Sample)를 제공하지 않아 다음은
 * 공공데이터포털의 공통 REST 규약(`response/header/resultCode,resultMsg`,
 * `response/body/items/item`, 공통 오류 코드 표)을 근거로 구현했고, 실제 인증키로 아직 검증하지
 * 못했다. 응답에 본문(content)이나 원문 URL(externalUrl)에 대응하는 필드가 공식 목록에 없어
 * 항상 missingFields에 포함되며(PARTIAL, 자동 게시 대상 아님), 실제 값이 확인되면 후속 작업에서
 * 반영한다.
 */
@Component
@EnableConfigurationProperties(MmaProviderProperties::class)
class MmaCollectorProvider(
    private val properties: MmaProviderProperties,
    restClientBuilder: RestClient.Builder,
) : CollectorProvider {
    private val restClient = restClientBuilder.build()

    override val sourceCode = JobSourceCode.MMA

    override fun isConfigured(): Boolean = properties.isConfigured()

    override fun collect(context: CollectorCollectionContext): CollectorCollectionResult {
        if (!isConfigured()) throw CollectorProviderException.AuthenticationFailed("MMA 인증키가 설정되지 않았습니다.")

        return paginate(context) { pageNo -> fetchPage(pageNo) }
    }

    // 페이지 순회(무한 루프 방지/중복 페이지 감지/최대 페이지 안전장치/부분 페이지 실패 처리)를
    // 실제 HTTP 호출과 분리해 internal로 열어 둔다. Test는 실제 RestClient 없이 Fixture XML을
    // 반환하는 fetchPage Lambda를 직접 주입해 순회 로직만 검증한다(WireMock/MockWebServer 미추가
    // 원칙 유지). 순회 계속 여부를 while 조건절로만 표현해 본문에 break/continue를 두지 않는다
    // (Cyclomatic Complexity/LoopWithTooManyJumpStatements 회피).
    internal fun paginate(
        context: CollectorCollectionContext,
        fetchPage: (pageNo: Int) -> String,
    ): CollectorCollectionResult {
        val state = MmaPaginationState()
        var pageNo = 1

        while (pageNo <= properties.maxPages && processNextPage(pageNo, fetchPage, context, state)) {
            pageNo++
        }

        if (pageNo > properties.maxPages) {
            log.warn("MMA 수집이 최대 페이지 수({})에 도달해 조기 종료했습니다.", properties.maxPages)
        }

        return CollectorCollectionResult(jobs = state.jobs, errors = state.errors)
    }

    // 페이지 하나를 조회·반영하고 순회를 계속할지(true) 중단할지(false)를 반환한다.
    @Suppress("ReturnCount")
    private fun processNextPage(
        pageNo: Int,
        fetchPage: (pageNo: Int) -> String,
        context: CollectorCollectionContext,
        state: MmaPaginationState,
    ): Boolean {
        val page =
            try {
                parsePage(fetchPage(pageNo), context)
            } catch (ex: CollectorProviderException) {
                // 첫 페이지 실패는 Provider 전체 실패로 그대로 전파한다(기존 단일 페이지 동작과
                // 호환). 이후 페이지의 실패는 지금까지 모은 결과를 보존하고 Run에 오류로 기록한
                // 뒤 순회를 중단한다(부분 페이지 실패 처리).
                if (pageNo == 1) throw ex
                state.errors.add(
                    CollectorItemError(
                        externalJobId = null,
                        code = "COLLECTOR_PAGE_FETCH_FAILED",
                        message = "페이지 $pageNo 조회에 실패해 이전 페이지까지의 결과만 반환합니다: ${ex.message}",
                    ),
                )
                return false
            }

        val currentPageIds = page.jobs.map { it.externalJobId }.toSet()
        if (currentPageIds.isNotEmpty() && currentPageIds == state.previousPageIds) {
            log.warn("MMA 페이지 {}가 이전 페이지와 동일한 항목을 반환해 순회를 중단합니다.", pageNo)
            return false
        }
        state.previousPageIds = currentPageIds

        page.jobs.forEach { job -> if (state.seenExternalIds.add(job.externalJobId)) state.jobs.add(job) }
        state.errors.addAll(page.errors)
        state.fetchedItemCount += page.itemCount
        state.totalCount = page.totalCount ?: state.totalCount

        return !isLastMmaPage(page, state.fetchedItemCount, state.totalCount, properties.pageSize)
    }

    // HTTP/Timeout/Network 오류를 각각 다른 CollectorProviderException 하위 타입으로 구분해
    // 재시도 정책(retryable)을 다르게 적용해야 하므로 throw 지점이 여러 곳이다.
    @Suppress("ThrowsCount")
    private fun fetchPage(pageNo: Int): String {
        val uri = buildUri(pageNo)
        val xml =
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
                                "MMA가 잘못된 요청 오류를 반환했습니다(${response.statusCode.value()}).",
                            )
                        }
                    }.onStatus({ status: HttpStatusCode -> status.is5xxServerError }) { _, response ->
                        throw CollectorProviderException.ServerError(
                            "MMA가 서버 오류를 반환했습니다(${response.statusCode.value()}).",
                        )
                    }.body<String>()
            } catch (ex: CollectorProviderException) {
                throw ex
            } catch (ex: java.net.SocketTimeoutException) {
                throw CollectorProviderException.Timeout(cause = ex)
            } catch (ex: org.springframework.web.client.ResourceAccessException) {
                throw CollectorProviderException.NetworkError(ex.message ?: "MMA 호출 중 네트워크 오류가 발생했습니다.", cause = ex)
            }

        return xml ?: throw CollectorProviderException.ResponseInvalid("MMA 응답 본문이 비어 있습니다.")
    }

    private fun buildUri(pageNo: Int): java.net.URI {
        val decodedKey = ServiceKeyCodec.decodeOnce(properties.serviceKey)
        // UriComponentsBuilder가 serviceKey를 다시 Encoding하지 않도록 직접 한 번만 Encoding해
        // 이미 완성된 Query 문자열로 전달한다(build(true) = "components already encoded").
        return UriComponentsBuilder
            .fromUriString(properties.baseUrl)
            .queryParam("serviceKey", ServiceKeyCodec.encode(decodedKey))
            .queryParam("pageNo", pageNo)
            .queryParam("numOfRows", properties.pageSize)
            .build(true)
            .toUri()
    }

    // 기존(단일 페이지) 호출부와 Test 호환을 위해 유지한다. 실제 순회는 parsePage를 사용한다.
    internal fun parse(
        xml: String,
        context: CollectorCollectionContext,
    ): CollectorCollectionResult {
        val page = parsePage(xml, context)
        return CollectorCollectionResult(jobs = page.jobs, errors = page.errors)
    }

    // DocumentBuilderFactory/DocumentBuilder.parse는 SAXException/IOException/
    // ParserConfigurationException 등 서로 다른 Checked Exception을 던진다. 모두 같은
    // ResponseInvalid로 변환하므로 공통 상위 타입(Exception)으로 한 번에 받는다(원인은 cause로 보존).
    //
    // internal로 열어 MmaCollectorProviderTest가 실제 HTTP 호출 없이(WireMock/MockWebServer
    // 추가 없이) 실제 응답과 동일한 형태의 XML Fixture로 Parsing/정규화 규칙만 직접 검증한다.
    // totalCount까지 함께 반환해 paginate()가 순회 종료 여부를 판단할 수 있게 한다.
    @Suppress("TooGenericExceptionCaught")
    internal fun parsePage(
        xml: String,
        context: CollectorCollectionContext,
    ): MmaPageResult {
        val document =
            try {
                val factory =
                    DocumentBuilderFactory.newInstance().apply {
                        // 외부 응답을 Parsing하므로 XXE(External Entity)를 명시적으로 차단한다.
                        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                        setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
                        isExpandEntityReferences = false
                    }
                factory.newDocumentBuilder().parse(xml.byteInputStream(Charsets.UTF_8))
            } catch (ex: Exception) {
                throw CollectorProviderException.ResponseInvalid("MMA 응답 XML을 해석할 수 없습니다.", cause = ex)
            }

        val resultCode =
            document
                .getElementsByTagName("resultCode")
                .item(0)
                ?.textContent
                ?.trim()
        validateResultCode(resultCode)

        val totalCount =
            document
                .getElementsByTagName("totalCount")
                .item(0)
                ?.textContent
                ?.trim()
                ?.toIntOrNull()

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
                            message =
                                ex.message ?: "정규화 실패",
                        ),
                    )
                }
        }
        return MmaPageResult(jobs = jobs, errors = errors, itemCount = items.length, totalCount = totalCount)
    }

    // 공공데이터포털 공통 오류 코드마다 재시도 정책이 다른 별도 CollectorProviderException으로
    // 변환해야 해서 분기마다 throw한다.
    @Suppress("ThrowsCount")
    private fun validateResultCode(resultCode: String?) {
        // 공공데이터포털 공통 오류 코드 표(신청 가이드 공통 규약) 기준이다. "00"은 정상,
        // "03"(NODATA)은 오류가 아니라 빈 결과로 취급한다.
        when (resultCode) {
            null, "00", "03" -> Unit

            "20", "21", "30", "32", "33" -> throw CollectorProviderException.AuthenticationFailed(
                "MMA 인증 오류(resultCode=$resultCode)",
            )

            "22" -> throw CollectorProviderException.RateLimited("MMA 호출 제한 초과(resultCode=$resultCode)")

            "05" -> throw CollectorProviderException.Timeout("MMA 응답 지연(resultCode=$resultCode)")

            "04" -> throw CollectorProviderException.NetworkError("MMA 통신 오류(resultCode=$resultCode)")

            "10", "11" -> throw CollectorProviderException.ClientError("MMA 요청 오류(resultCode=$resultCode)")

            else -> throw CollectorProviderException.ResponseInvalid("MMA 알 수 없는 오류(resultCode=$resultCode)")
        }
    }

    // 확인된 필드만 사용한다. 본문(content)·원문 URL(externalUrl)에 대응하는 필드가 공식 목록에
    // 없어 항상 missingFields에 포함시킨다(추측 금지, 최종 보고 참고). 필수 식별 필드가 없는
    // 항목을 일찍 걸러내는 가드 절이 중첩 if보다 읽기 쉽다고 판단해 ReturnCount를 그대로 둔다.
    @Suppress("ReturnCount")
    private fun toNormalizedJob(
        element: org.w3c.dom.Element,
        context: CollectorCollectionContext,
    ): NormalizedCollectedJob? {
        val valid = text(element, "yuhyoYn")
        if (valid != null && !valid.equals("Y", ignoreCase = true)) return null

        val externalJobId = text(element, "cygonggoNo") ?: return null
        val title = text(element, "cyjemokNm") ?: return null
        val companyName = text(element, "eopcheNm") ?: return null

        val missingFields = mutableListOf<String>()
        val endDate = parseDate(text(element, "magamDt"))
        if (endDate == null) missingFields.add("endDate")
        missingFields.add("content")
        missingFields.add("externalUrl")

        return NormalizedCollectedJob(
            sourceCode = sourceCode,
            externalJobId = externalJobId,
            title = title,
            companyName = companyName,
            content = null,
            externalUrl = null,
            startDate = null,
            endDate = endDate,
            collectedAt = context.requestedAt,
            dataQualityStatus = JobDataQualityStatus.PARTIAL,
            missingFields = missingFields,
        )
    }

    private companion object {
        private val log = LoggerFactory.getLogger(MmaCollectorProvider::class.java)
    }
}

// MmaCollectorProvider의 Instance 상태(properties 등)가 필요 없는 순수 함수라 클래스 밖으로
// 뺐다(detekt TooManyFunctions 회피 목적도 겸한다).

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

private fun parseDate(raw: String?): LocalDateTime? {
    if (raw.isNullOrBlank()) return null
    return runCatching { LocalDate.parse(raw, DateTimeFormatter.BASIC_ISO_DATE).atStartOfDay() }
        .recoverCatching { LocalDate.parse(raw, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay() }
        .getOrNull()
}

// 마지막 페이지 판정: 항목이 없거나, 요청한 페이지 크기보다 적게 왔거나, totalCount에 도달했다.
private fun isLastMmaPage(
    page: MmaPageResult,
    fetchedItemCount: Int,
    totalCount: Int?,
    pageSize: Int,
): Boolean =
    page.itemCount == 0 ||
        page.itemCount < pageSize ||
        (totalCount != null && fetchedItemCount >= totalCount)
