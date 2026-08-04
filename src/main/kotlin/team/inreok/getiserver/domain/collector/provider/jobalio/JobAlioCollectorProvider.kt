package team.inreok.getiserver.domain.collector.provider.jobalio

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
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** 페이지 하나를 Parsing한 결과. [MmaPageResult]와 같은 목적이다. */
internal data class JobAlioPageResult(
    val jobs: List<NormalizedCollectedJob>,
    val errors: List<CollectorItemError>,
    val itemCount: Int,
    val totalCount: Int?,
)

private class JobAlioPaginationState {
    val jobs = mutableListOf<NormalizedCollectedJob>()
    val errors = mutableListOf<CollectorItemError>()
    val seenExternalIds = mutableSetOf<String>()
    var previousPageIds: Set<String>? = null
    var fetchedItemCount = 0
    var totalCount: Int? = null
    var requestCount = 0
}

/**
 * 재정경제부_공공기관 채용정보 조회서비스(JOB-ALIO) Provider Adapter다. 공공데이터포털
 * 활용신청 상세 페이지(https://www.data.go.kr/data/15125273/openapi.do)에 Swagger 문서가
 * 포함되어 있어 실제 Endpoint(`/1051000/recruitment/list`)·Query Parameter·응답 필드를
 * 확인했고, 실제 인증키로 호출해 정상 응답(resultCode=200)을 받아 검증했다(Issue #62
 * 확장 범위, 최종 보고 참고).
 *
 * **알려진 한계**: 응답에 공고 본문(공고 전체 설명)에 대응하는 단일 필드가 없어 항상
 * missingFields에 포함된다. 대신 신청자격내용(aplyQlfcCn)·우대내용(prefCn)·결격사유(disqlfcRsn)를
 * 적합성 판정용 qualificationDetail로만 사용한다.
 */
@Component
@EnableConfigurationProperties(JobAlioProviderProperties::class)
class JobAlioCollectorProvider(
    private val properties: JobAlioProviderProperties,
    restClientBuilder: RestClient.Builder,
) : CollectorProvider {
    private val restClient = restClientBuilder.build()

    override val sourceCode = JobSourceCode.JOB_ALIO

    override fun isConfigured(): Boolean = properties.isConfigured()

    override fun collect(context: CollectorCollectionContext): CollectorCollectionResult {
        if (!isConfigured()) throw CollectorProviderException.AuthenticationFailed("JOB-ALIO 인증키가 설정되지 않았습니다.")
        return paginate(context) { pageNo -> fetchPage(pageNo) }
    }

    internal fun paginate(
        context: CollectorCollectionContext,
        fetchPage: (pageNo: Int) -> String,
    ): CollectorCollectionResult {
        val state = JobAlioPaginationState()
        var pageNo = 1

        while (pageNo <= properties.maxPages && processNextPage(pageNo, fetchPage, context, state)) {
            pageNo++
        }

        if (pageNo > properties.maxPages) {
            log.warn("JOB-ALIO 수집이 최대 페이지 수({})에 도달해 조기 종료했습니다.", properties.maxPages)
        }

        return CollectorCollectionResult(jobs = state.jobs, errors = state.errors, requestCount = state.requestCount)
    }

    // 페이지 하나를 조회·반영하고 순회를 계속할지(true) 중단할지(false)를 반환한다.
    @Suppress("ReturnCount")
    private fun processNextPage(
        pageNo: Int,
        fetchPage: (pageNo: Int) -> String,
        context: CollectorCollectionContext,
        state: JobAlioPaginationState,
    ): Boolean {
        state.requestCount++
        val page =
            try {
                parsePage(fetchPage(pageNo), context)
            } catch (ex: CollectorProviderException) {
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
            log.warn("JOB-ALIO 페이지 {}가 이전 페이지와 동일한 항목을 반환해 순회를 중단합니다.", pageNo)
            return false
        }
        state.previousPageIds = currentPageIds

        page.jobs.forEach { job -> if (state.seenExternalIds.add(job.externalJobId)) state.jobs.add(job) }
        state.errors.addAll(page.errors)
        state.fetchedItemCount += page.itemCount
        state.totalCount = page.totalCount ?: state.totalCount

        return !isLastJobAlioPage(page, state.fetchedItemCount, state.totalCount, properties.pageSize)
    }

    @Suppress("ThrowsCount")
    private fun fetchPage(pageNo: Int): String {
        val uri = buildUri(pageNo)
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
                                "JOB-ALIO가 잘못된 요청 오류를 반환했습니다(${response.statusCode.value()}).",
                            )
                        }
                    }.onStatus({ status: HttpStatusCode -> status.is5xxServerError }) { _, response ->
                        throw CollectorProviderException.ServerError(
                            "JOB-ALIO가 서버 오류를 반환했습니다(${response.statusCode.value()}).",
                        )
                    }.body<String>()
            } catch (ex: CollectorProviderException) {
                throw ex
            } catch (ex: java.net.SocketTimeoutException) {
                throw CollectorProviderException.Timeout(cause = ex)
            } catch (ex: org.springframework.web.client.ResourceAccessException) {
                throw CollectorProviderException.NetworkError(
                    ex.message ?: "JOB-ALIO 호출 중 네트워크 오류가 발생했습니다.",
                    cause = ex,
                )
            }

        return body ?: throw CollectorProviderException.ResponseInvalid("JOB-ALIO 응답 본문이 비어 있습니다.")
    }

    private fun buildUri(pageNo: Int): java.net.URI {
        val decodedKey = ServiceKeyCodec.decodeOnce(properties.serviceKey)
        return UriComponentsBuilder
            .fromUriString(properties.baseUrl)
            .queryParam("serviceKey", ServiceKeyCodec.encode(decodedKey))
            .queryParam("pageNo", pageNo)
            .queryParam("numOfRows", properties.pageSize)
            .queryParam("resultType", "json")
            .build(true)
            .toUri()
    }

    // 기존(단일 페이지) 호출부와 Test 호환을 위해 유지한다.
    internal fun parse(
        json: String,
        context: CollectorCollectionContext,
    ): CollectorCollectionResult {
        val page = parsePage(json, context)
        return CollectorCollectionResult(jobs = page.jobs, errors = page.errors)
    }

    @Suppress("TooGenericExceptionCaught")
    internal fun parsePage(
        json: String,
        context: CollectorCollectionContext,
    ): JobAlioPageResult {
        val root =
            try {
                JSON_MAPPER.readValue(json, Map::class.java)
            } catch (ex: Exception) {
                throw CollectorProviderException.ResponseInvalid("JOB-ALIO 응답 JSON을 해석할 수 없습니다.", cause = ex)
            }

        val resultCode = (root["resultCode"] as? Number)?.toInt()
        validateResultCode(resultCode)

        val totalCount = (root["totalCount"] as? Number)?.toInt()
        val items = (root["result"] as? List<*>)?.filterIsInstance<Map<*, *>>().orEmpty()

        val jobs = mutableListOf<NormalizedCollectedJob>()
        val errors = mutableListOf<CollectorItemError>()
        items.forEach { item ->
            runCatching { toNormalizedJob(item, context) }
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
        return JobAlioPageResult(jobs = jobs, errors = errors, itemCount = items.size, totalCount = totalCount)
    }

    // 공식 Swagger 문서의 오류 코드 표(0/1/2/3/5/6/7/10/11)와 실제 응답에서 확인한 성공 코드(200)를
    // 함께 처리해야 하므로 분기가 많다.
    @Suppress("ThrowsCount")
    private fun validateResultCode(resultCode: Int?) {
        when (resultCode) {
            null, SUCCESS_CODE, DOC_SUCCESS_CODE, NO_DATA_CODE -> {
                Unit
            }

            AUTH_ERROR_CODE -> {
                throw CollectorProviderException.AuthenticationFailed("JOB-ALIO 인증 오류(resultCode=$resultCode)")
            }

            SERVER_ERROR_CODE, DB_ERROR_CODE -> {
                throw CollectorProviderException.ServerError("JOB-ALIO 서버 오류(resultCode=$resultCode)")
            }

            SERVICE_CONNECTION_ERROR_CODE -> {
                throw CollectorProviderException.NetworkError("JOB-ALIO 통신 오류(resultCode=$resultCode)")
            }

            INVALID_PARAM_CODE, MISSING_PARAM_CODE, APP_ERROR_CODE -> {
                throw CollectorProviderException.ClientError("JOB-ALIO 요청 오류(resultCode=$resultCode)")
            }

            else -> {
                throw CollectorProviderException.ResponseInvalid("JOB-ALIO 알 수 없는 오류(resultCode=$resultCode)")
            }
        }
    }

    private fun toNormalizedJob(
        item: Map<*, *>,
        context: CollectorCollectionContext,
    ): NormalizedCollectedJob? = buildJobAlioNormalizedJob(item, context, sourceCode)

    private companion object {
        val log = LoggerFactory.getLogger(JobAlioCollectorProvider::class.java)
        val JSON_MAPPER =
            com.fasterxml.jackson.databind
                .ObjectMapper()
        const val SUCCESS_CODE = 200
        const val DOC_SUCCESS_CODE = 0
        const val NO_DATA_CODE = 3
        const val APP_ERROR_CODE = 1
        const val DB_ERROR_CODE = 2
        const val SERVICE_CONNECTION_ERROR_CODE = 5
        const val SERVER_ERROR_CODE = 6
        const val AUTH_ERROR_CODE = 7
        const val INVALID_PARAM_CODE = 10
        const val MISSING_PARAM_CODE = 11
    }
}

// JobAlioCollectorProvider의 Instance 상태가 필요 없는 순수 함수라 클래스 밖으로 뺐다(detekt
// TooManyFunctions 회피 목적도 겸한다, MmaCollectorProvider와 같은 패턴).

private fun isLastJobAlioPage(
    page: JobAlioPageResult,
    fetchedItemCount: Int,
    totalCount: Int?,
    pageSize: Int,
): Boolean =
    page.itemCount == 0 ||
        page.itemCount < pageSize ||
        (totalCount != null && fetchedItemCount >= totalCount)

// 필수 식별 필드가 없거나 진행 중이 아닌 공고를 일찍 걸러내는 가드 절이 중첩 if보다 읽기 쉽다고
// 판단해 ReturnCount를 그대로 둔다.
@Suppress("ReturnCount")
private fun buildJobAlioNormalizedJob(
    item: Map<*, *>,
    context: CollectorCollectionContext,
    sourceCode: JobSourceCode,
): NormalizedCollectedJob? {
    val ongoing = item.stringOrNull("ongoingYn")
    if (ongoing != null && !ongoing.equals("Y", ignoreCase = true)) return null

    val externalJobId = item.numberOrNull("recrutPblntSn")?.toLong()?.toString() ?: return null
    val title = item.stringOrNull("recrutPbancTtl") ?: return null
    val companyName = item.stringOrNull("instNm") ?: return null

    val missingFields = mutableListOf<String>()
    val (startDate, endDate) =
        normalizeRecruitmentPeriod(
            parseYmd(item.stringOrNull("pbancBgngYmd")),
            parseYmd(item.stringOrNull("pbancEndYmd")),
        )
    if (endDate == null) missingFields.add("endDate")
    val externalUrl = normalizeExternalUrl(item.stringOrNull("srcUrl"))
    if (externalUrl == null) missingFields.add("externalUrl")
    missingFields.add("content")

    val qualificationDetail =
        listOfNotNull(item.stringOrNull("aplyQlfcCn"), item.stringOrNull("prefCn"), item.stringOrNull("disqlfcRsn"))
            .joinToString(separator = " ")
            .takeIf { it.isNotBlank() }

    return NormalizedCollectedJob(
        sourceCode = sourceCode,
        externalJobId = externalJobId,
        title = title,
        companyName = companyName,
        content = null,
        externalUrl = externalUrl,
        startDate = startDate,
        endDate = endDate,
        collectedAt = context.requestedAt,
        dataQualityStatus = JobDataQualityStatus.PARTIAL,
        missingFields = missingFields,
        educationCondition = item.stringOrNull("acbgCondNmLst"),
        careerCondition = item.stringOrNull("recrutSeNm"),
        employmentType = item.stringOrNull("hireTypeNmLst"),
        jobFieldHint = item.stringOrNull("ncsCdNmLst"),
        workRegion = item.stringOrNull("workRgnNmLst"),
        qualificationDetail = qualificationDetail,
        recruitCount = item.numberOrNull("recrutNope")?.toString(),
        militaryServiceType = null,
    )
}

private fun Map<*, *>.stringOrNull(key: String): String? = (this[key] as? String)?.trim()?.takeIf { it.isNotEmpty() }

private fun Map<*, *>.numberOrNull(key: String): Number? = this[key] as? Number

private fun parseYmd(raw: String?): LocalDateTime? {
    if (raw.isNullOrBlank()) return null
    return runCatching { LocalDate.parse(raw, DateTimeFormatter.BASIC_ISO_DATE).atStartOfDay() }.getOrNull()
}
