package team.inreok.getiserver.domain.collector.provider.naraeilteo

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
import team.inreok.getiserver.domain.collector.provider.normalizeExternalUrl
import team.inreok.getiserver.domain.collector.provider.normalizeRecruitmentPeriod
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/** `getList` 응답 한 건. 본문(content)·원문 URL은 없어 `getItem`으로 별도 조회해야 한다. */
internal data class NaraIlteoListItem(
    val idx: String,
    val title: String,
    val insttname: String,
    val begindate: String?,
    val enddate: String?,
    val type01: String?,
)

internal data class NaraIlteoListPageResult(
    val items: List<NaraIlteoListItem>,
    val itemCount: Int,
    val totalCount: Int?,
)

/** [NaraIlteoCollectorProvider.paginateList] 순회 중 누적되는 상태다. Test에서 직접 검증한다. */
internal class NaraIlteoListState {
    val items = mutableListOf<NaraIlteoListItem>()
    val errors = mutableListOf<CollectorItemError>()
    val seenIdx = mutableSetOf<String>()
    var previousPageIds: Set<String>? = null
    var fetchedItemCount = 0
    var totalCount: Int? = null
    var requestCount = 0
}

/**
 * 인사혁신처_공공취업정보 조회 서비스(나라일터) Provider Adapter다. 공공데이터포털 활용신청
 * 상세 페이지(https://www.data.go.kr/data/15156780/openapi.do)에 포함된 Swagger 문서로
 * `getList`(목록)/`getItem`(상세) Endpoint·Query Parameter·응답 필드를 확인했고, 실제
 * 인증키로 호출해 정상 응답(resultCode=00)을 받아 검증했다(Issue #62 확장 범위, 최종 보고
 * 참고).
 *
 * `getList`는 본문(contents)·원문 URL(link01)을 주지 않아 목록의 공고마다 `getItem`을 한 번 더
 * 호출한다(2단계 호출). 전체 데이터가 28만 건을 넘어(2008년 이후 누적) 무제한 순회는 하지
 * 않는다 — `Begin_de`/`End_de`(공고등록일 검색 범위, Swagger에서 확인)로 조회 범위를 최근으로
 * 좁히고, `maxDetailFetchesPerRun`으로 실행당 상세 조회(`getItem`) 최대 건수를 제한한다. 최초
 * 수집은 `initialLookbackDays`(기본 90일)만큼만 과거로 거슬러 올라가 부분 백필로 시작하고,
 * 이후 일일 실행은 `context.since`(직전 성공 시각)부터 증분 수집한다.
 */
@Component
@EnableConfigurationProperties(NaraIlteoProviderProperties::class)
class NaraIlteoCollectorProvider(
    private val properties: NaraIlteoProviderProperties,
    restClientBuilder: RestClient.Builder,
) : CollectorProvider {
    private val restClient = restClientBuilder.build()

    override val sourceCode = JobSourceCode.NARA_ILTEO

    override fun isConfigured(): Boolean = properties.isConfigured()

    override fun collect(context: CollectorCollectionContext): CollectorCollectionResult {
        if (!isConfigured()) throw CollectorProviderException.AuthenticationFailed("나라일터 인증키가 설정되지 않았습니다.")

        val beginDate = beginDateOf(context)
        val endDate = context.requestedAt.toLocalDate()
        val listState = paginateList { pageNo -> fetchXml(buildListUri(pageNo, beginDate, endDate), "나라일터 목록") }

        return fetchDetails(listState, context)
    }

    private fun beginDateOf(context: CollectorCollectionContext): LocalDate =
        context.since?.toLocalDate() ?: context.requestedAt.toLocalDate().minusDays(properties.initialLookbackDays)

    // 목록 페이지 순회(무한 루프 방지/중복 페이지 감지/최대 페이지 안전장치/부분 페이지 실패 처리)를
    // 실제 HTTP 호출과 분리해 internal로 열어 둔다. Test는 실제 RestClient 없이 Fixture XML을
    // 반환하는 fetchPage Lambda를 직접 주입해 순회 로직만 검증한다(Mma/JobAlio Provider와 같은 패턴).
    internal fun paginateList(fetchPage: (pageNo: Int) -> String): NaraIlteoListState {
        val state = NaraIlteoListState()
        var pageNo = 1
        while (pageNo <= properties.maxPages && processNextListPage(pageNo, fetchPage, state)) {
            pageNo++
        }
        if (pageNo > properties.maxPages) {
            log.warn("나라일터 목록 순회가 최대 페이지 수({})에 도달해 조기 종료했습니다.", properties.maxPages)
        }
        return state
    }

    // 페이지 하나를 조회·반영하고 순회를 계속할지(true) 중단할지(false)를 반환한다. 첫 페이지
    // 실패는 Provider 전체 실패로 전파하고(기존 단일 페이지 동작과 호환), 이후 페이지의 실패는
    // 지금까지 모은 결과를 보존하고 오류로 기록한 뒤 순회를 중단한다(Mma/JobAlio Provider와 같은
    // 부분 페이지 실패 처리 패턴).
    @Suppress("ReturnCount")
    private fun processNextListPage(
        pageNo: Int,
        fetchPage: (pageNo: Int) -> String,
        state: NaraIlteoListState,
    ): Boolean {
        state.requestCount++
        val page =
            try {
                parseListPage(fetchPage(pageNo))
            } catch (ex: CollectorProviderException) {
                if (pageNo == 1) throw ex
                state.errors.add(
                    CollectorItemError(
                        externalJobId = null,
                        code = "COLLECTOR_PAGE_FETCH_FAILED",
                        message = "나라일터 목록 페이지 $pageNo 조회에 실패해 이전 페이지까지의 결과만 반환합니다: ${ex.message}",
                    ),
                )
                return false
            }

        val currentPageIds = page.items.map { it.idx }.toSet()
        if (currentPageIds.isNotEmpty() && currentPageIds == state.previousPageIds) {
            log.warn("나라일터 목록 페이지 {}가 이전 페이지와 동일한 항목을 반환해 순회를 중단합니다.", pageNo)
            return false
        }
        state.previousPageIds = currentPageIds

        page.items.forEach { item -> if (state.seenIdx.add(item.idx)) state.items.add(item) }
        state.fetchedItemCount += page.itemCount
        state.totalCount = page.totalCount ?: state.totalCount

        return !isLastNaraIlteoListPage(page, state.fetchedItemCount, state.totalCount, properties.pageSize)
    }

    // 상세 조회(getItem) 호출 상한을 넘는 나머지 항목은 버리지 않고 오류로 기록해 다음 실행에서
    // 다시 조회 대상이 되게 한다(Since 기반 증분 수집이라 같은 기간을 다시 훑으면 남은 항목도
    // 결국 처리된다).
    private fun fetchDetails(
        listState: NaraIlteoListState,
        context: CollectorCollectionContext,
    ): CollectorCollectionResult {
        val jobs = mutableListOf<NormalizedCollectedJob>()
        val errors = listState.errors.toMutableList()
        var requestCount = listState.requestCount

        val toFetch = listState.items.take(properties.maxDetailFetchesPerRun)
        val skipped = listState.items.size - toFetch.size
        if (skipped > 0) {
            log.warn("나라일터 상세 조회 상한({})을 넘어 {}건을 다음 실행으로 미룹니다.", properties.maxDetailFetchesPerRun, skipped)
        }

        toFetch.forEach { item ->
            requestCount++
            runCatching { fetchXml(buildDetailUri(item.idx), "나라일터 상세") }
                .mapCatching { xml -> parseDetail(xml, item, context) }
                .onSuccess { job -> if (job != null) jobs.add(job) }
                .onFailure { ex ->
                    errors.add(
                        CollectorItemError(
                            externalJobId = item.idx,
                            code = "COLLECTED_JOB_NORMALIZATION_FAILED",
                            message = ex.message ?: "상세 조회/정규화 실패",
                        ),
                    )
                }
        }

        return CollectorCollectionResult(jobs = jobs, errors = errors, requestCount = requestCount)
    }

    @Suppress("ThrowsCount")
    private fun fetchXml(
        uri: java.net.URI,
        label: String,
    ): String {
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
                            CollectorProviderException.ClientError("$label 요청이 잘못됐습니다(${response.statusCode.value()}).")
                        }
                    }.onStatus({ status: HttpStatusCode -> status.is5xxServerError }) { _, response ->
                        throw CollectorProviderException.ServerError("$label 서버 오류(${response.statusCode.value()}).")
                    }.body<ByteArray>()
            } catch (ex: CollectorProviderException) {
                throw ex
            } catch (ex: java.net.SocketTimeoutException) {
                throw CollectorProviderException.Timeout(cause = ex)
            } catch (ex: org.springframework.web.client.ResourceAccessException) {
                throw CollectorProviderException.NetworkError(ex.message ?: "$label 호출 중 네트워크 오류가 발생했습니다.", cause = ex)
            }
        // 나라일터도 MMA와 같은 이유(Content-Type에 charset 미포함, 실제 응답은 UTF-8)로 원본
        // Byte를 직접 받아 UTF-8로 명시적으로 Decoding한다(MmaCollectorProvider.fetchPage 참고).
        return body?.let { String(it, StandardCharsets.UTF_8) }
            ?: throw CollectorProviderException.ResponseInvalid("$label 응답 본문이 비어 있습니다.")
    }

    private fun buildListUri(
        pageNo: Int,
        beginDate: LocalDate,
        endDate: LocalDate,
    ): java.net.URI {
        val decodedKey = ServiceKeyCodec.decodeOnce(properties.serviceKey)
        return UriComponentsBuilder
            .fromUriString("${properties.baseUrl}/getList")
            .queryParam("serviceKey", ServiceKeyCodec.encode(decodedKey))
            .queryParam("pageNo", pageNo)
            .queryParam("numOfRows", properties.pageSize)
            .queryParam("Begin_de", beginDate.format(DateTimeFormatter.ISO_LOCAL_DATE))
            .queryParam("End_de", endDate.format(DateTimeFormatter.ISO_LOCAL_DATE))
            .build(true)
            .toUri()
    }

    private fun buildDetailUri(idx: String): java.net.URI {
        val decodedKey = ServiceKeyCodec.decodeOnce(properties.serviceKey)
        return UriComponentsBuilder
            .fromUriString("${properties.baseUrl}/getItem")
            .queryParam("serviceKey", ServiceKeyCodec.encode(decodedKey))
            .queryParam("idx", idx)
            .build(true)
            .toUri()
    }

    internal fun parseListPage(xml: String): NaraIlteoListPageResult {
        val document = parseNaraIlteoXml(xml, "나라일터 목록")
        validateNaraIlteoResultCode(text(document.documentElement, "resultCode"))

        val totalCount = text(document.documentElement, "totalCount")?.toIntOrNull()
        val items = document.getElementsByTagName("item")
        val listItems =
            (0 until items.length).mapNotNull { i -> (items.item(i) as? org.w3c.dom.Element)?.let(::toListItemOrNull) }
        return NaraIlteoListPageResult(items = listItems, itemCount = items.length, totalCount = totalCount)
    }

    internal fun parseDetail(
        xml: String,
        listItem: NaraIlteoListItem,
        context: CollectorCollectionContext,
    ): NormalizedCollectedJob? {
        val document = parseNaraIlteoXml(xml, "나라일터 상세")
        validateNaraIlteoResultCode(text(document.documentElement, "resultCode"))
        return buildNaraIlteoNormalizedJob(document, listItem, context, sourceCode)
    }

    private companion object {
        val log = LoggerFactory.getLogger(NaraIlteoCollectorProvider::class.java)
    }
}

@Suppress("TooGenericExceptionCaught")
private fun parseNaraIlteoXml(
    xml: String,
    label: String,
): org.w3c.dom.Document =
    try {
        val factory =
            DocumentBuilderFactory.newInstance().apply {
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
                isExpandEntityReferences = false
            }
        factory.newDocumentBuilder().parse(xml.byteInputStream(Charsets.UTF_8))
    } catch (ex: Exception) {
        throw CollectorProviderException.ResponseInvalid("$label 응답 XML을 해석할 수 없습니다.", cause = ex)
    }

// 공공데이터포털 공통 오류 코드 표를 그대로 처리해야 하므로 분기가 많다(MMA와 같은 표).
@Suppress("ThrowsCount")
private fun validateNaraIlteoResultCode(resultCode: String?) {
    when (resultCode) {
        null, "00", "03" -> Unit

        "20", "21", "30", "32", "33" -> throw CollectorProviderException.AuthenticationFailed(
            "나라일터 인증 오류(resultCode=$resultCode)",
        )

        "22" -> throw CollectorProviderException.RateLimited("나라일터 호출 제한 초과(resultCode=$resultCode)")

        "05" -> throw CollectorProviderException.Timeout("나라일터 응답 지연(resultCode=$resultCode)")

        "04" -> throw CollectorProviderException.NetworkError("나라일터 통신 오류(resultCode=$resultCode)")

        "10", "11" -> throw CollectorProviderException.ClientError("나라일터 요청 오류(resultCode=$resultCode)")

        else -> throw CollectorProviderException.ResponseInvalid("나라일터 알 수 없는 오류(resultCode=$resultCode)")
    }
}

// 필수 식별 필드(idx/title/insttname)가 없는 항목을 일찍 걸러내는 가드 절이 중첩 if보다 읽기
// 쉽다고 판단해 ReturnCount를 그대로 둔다(MmaCollectorProvider와 같은 패턴).
@Suppress("ReturnCount")
private fun toListItemOrNull(element: org.w3c.dom.Element): NaraIlteoListItem? {
    val idx = text(element, "idx") ?: return null
    val title = text(element, "title") ?: return null
    val insttname = text(element, "insttname") ?: return null
    return NaraIlteoListItem(
        idx = idx,
        title = title,
        insttname = insttname,
        begindate = text(element, "begindate"),
        enddate = text(element, "enddate"),
        type01 = text(element, "type01"),
    )
}

private fun isLastNaraIlteoListPage(
    page: NaraIlteoListPageResult,
    fetchedItemCount: Int,
    totalCount: Int?,
    pageSize: Int,
): Boolean =
    page.itemCount == 0 ||
        page.itemCount < pageSize ||
        (totalCount != null && fetchedItemCount >= totalCount)

@Suppress("ReturnCount")
private fun buildNaraIlteoNormalizedJob(
    document: org.w3c.dom.Document,
    listItem: NaraIlteoListItem,
    context: CollectorCollectionContext,
    sourceCode: JobSourceCode,
): NormalizedCollectedJob? {
    val item = document.getElementsByTagName("item").item(0) as? org.w3c.dom.Element ?: return null

    val missingFields = mutableListOf<String>()
    val (startDate, endDate) = normalizeRecruitmentPeriod(parseYmd(listItem.begindate), parseYmd(listItem.enddate))
    if (endDate == null) missingFields.add("endDate")
    val content = text(item, "contents")?.let(::stripHtml)
    if (content == null) missingFields.add("content")
    val externalUrl = normalizeExternalUrl(text(item, "link01"))
    if (externalUrl == null) missingFields.add("externalUrl")

    return NormalizedCollectedJob(
        sourceCode = sourceCode,
        externalJobId = listItem.idx,
        title = listItem.title,
        companyName = listItem.insttname,
        content = content,
        externalUrl = externalUrl,
        startDate = startDate,
        endDate = endDate,
        collectedAt = context.requestedAt,
        dataQualityStatus = JobDataQualityStatus.PARTIAL,
        missingFields = missingFields,
        educationCondition = null,
        careerCondition = null,
        employmentType = jobTypeLabelOf(listItem.type01),
        jobFieldHint = jobTypeLabelOf(listItem.type01),
        workRegion = null,
        qualificationDetail = content,
        recruitCount = null,
        militaryServiceType = null,
    )
}

// Swagger 문서가 확인해 준 공고유형1 코드(:전체, g01:국가공무원, g02:지방공무원, g03:공공기관,
// g04:교육청)만 그대로 옮긴다 — 추측 코드를 추가하지 않는다.
private fun jobTypeLabelOf(type01: String?): String? =
    when (type01) {
        "g01" -> "국가공무원 채용"
        "g02" -> "지방공무원 채용"
        "g03" -> "공공기관 채용"
        "g04" -> "교육청 채용"
        else -> null
    }

// Tag만 있고 실제 텍스트가 없으면(예: 이미지 하나만 있는 본문) null을 반환해 "본문 없음"으로
// 취급한다 — Tag가 섞인 원문을 그대로 돌려주면 missingFields가 "content"를 놓치고 Job/Discord에
// HTML Tag가 그대로 노출된다.
private fun stripHtml(raw: String): String? =
    raw
        .replace(Regex("<[^>]*>"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .takeIf { it.isNotEmpty() }

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

private fun parseYmd(raw: String?): LocalDateTime? {
    if (raw.isNullOrBlank()) return null
    return runCatching { LocalDate.parse(raw, DateTimeFormatter.BASIC_ISO_DATE).atStartOfDay() }.getOrNull()
}
