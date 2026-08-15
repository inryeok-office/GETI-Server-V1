package team.inreok.getiserver.domain.search.document

import org.springframework.data.annotation.Id
import org.springframework.data.elasticsearch.annotations.DateFormat
import org.springframework.data.elasticsearch.annotations.Document
import org.springframework.data.elasticsearch.annotations.Field
import org.springframework.data.elasticsearch.annotations.FieldType
import org.springframework.data.elasticsearch.annotations.Setting
import java.time.LocalDateTime

/**
 * 공고 검색 전용 Elasticsearch Document다(Issue #69). PostgreSQL의 `jobs`가 원본이고 이 Document는
 * 검색·필터·정렬을 위한 Read Model이므로, 여기 담긴 값이 오래됐다고 해서 원본 데이터가 잘못된
 * 것은 아니다(`viewCount`는 색인 이벤트 시점 값을 기준으로 하며 상세 조회 증가가 즉시 반영되지
 * 않는다 — 매 조회마다 색인하면 이벤트가 폭주하므로 의도적 절충이다, 완료 보고 참고).
 *
 * `indexName`은 실제로 요청할 때마다 [team.inreok.getiserver.domain.search.index.JobSearchIndexManager]가
 * 명시적 `IndexCoordinates`로 덮어써 사용한다(재색인이 Timestamp Index를 새로 만들고 Alias를
 * 전환해야 하므로 고정 이름 하나로는 부족하다). Annotation의 `indexName`은 Spring Data
 * Elasticsearch가 요구하는 필수값을 채우는 기본값일 뿐이며 실제로 이 이름으로 색인되지 않는다.
 */
@Document(indexName = "jobs-search")
@Setting(settingPath = "elasticsearch/job-search-settings.json")
data class JobSearchDocument(
    @Id
    val id: String,
    @Field(type = FieldType.Long)
    val jobId: Long,
    @Field(type = FieldType.Text, analyzer = "job_search_korean")
    val title: String,
    @Field(type = FieldType.Text, analyzer = "job_search_korean")
    val content: String?,
    @Field(type = FieldType.Keyword)
    val postingType: String,
    @Field(type = FieldType.Keyword)
    val applicationMethod: String,
    @Field(type = FieldType.Keyword)
    val status: String,
    @Field(type = FieldType.Long)
    val companyId: Long,
    // 공고 등록 후 기업이 삭제되면 null이다(JobSummaryResponse.company와 동일한 정책, PR #70
    // Review 반영 — 이전에는 빈 문자열로 채워 응답의 company Field가 항상 존재하는 것처럼
    // 보였는데, 원래 계약(기업이 없으면 company 자체가 null)과 달라 API 계약이 조용히
    // 바뀌는 문제였다).
    @Field(type = FieldType.Text, analyzer = "job_search_korean")
    val companyName: String?,
    @Field(type = FieldType.Keyword)
    val companyType: String?,
    // Presigned URL은 저장하지 않는다 -- 만료되는 값이라 색인에 두면 검색 결과가 곧 깨진 링크를
    // 반환하게 된다(Issue #92). 안정적인 File ID만 저장하고, 응답 조립 시점에 FileUrlPort로
    // 매번 새로 URL을 발급한다(JobSummaryResponse.from, JobSearchServiceImpl 참고). 기업이
    // 삭제되었거나 로고가 없으면 companyName과 같은 정책으로 null이다.
    @Field(type = FieldType.Long)
    val companyLogoFileId: Long?,
    @Field(type = FieldType.Keyword)
    val sourceName: String?,
    @Field(type = FieldType.Integer)
    val targetGrade: Int?,
    @Field(type = FieldType.Integer)
    val capacity: Int?,
    @Field(type = FieldType.Boolean)
    val firstComeServed: Boolean,
    @Field(type = FieldType.Long)
    val viewCount: Long,
    // format을 명시하지 않으면 Write 시점에 날짜만(yyyy-MM-dd) 저장되어 시각 정보가 사라지고,
    // 다시 읽을 때 LocalDateTime 변환이 실패한다(ConversionException, 실제 Elasticsearch로
    // 확인함). date_hour_minute_second_millis로 초 단위 이하까지 명시적으로 고정한다.
    @Field(type = FieldType.Date, format = [DateFormat.date_hour_minute_second_millis])
    val publishedAt: LocalDateTime?,
    @Field(type = FieldType.Date, format = [DateFormat.date_hour_minute_second_millis])
    val startDate: LocalDateTime?,
    @Field(type = FieldType.Date, format = [DateFormat.date_hour_minute_second_millis])
    val endDate: LocalDateTime?,
    // AI Analysis(Issue #132) 결과 중 검색에 안전하게 노출할 수 있는 값만 additive로 더한다
    // (Issue #144). `AiAnalysisSearchQueryPort`가 COMPLETED 상태일 때만 값을 채워 주므로,
    // 분석이 아직 없거나 진행 중이거나 마지막 시도가 실패했으면 아래 필드는 모두 빈 값/null이다.
    // provider/model/promptVersion/errorMessage 같은 내부 세부값은 색인하지 않는다.
    @Field(type = FieldType.Long)
    val requiredTechStackIds: List<Long> = emptyList(),
    @Field(type = FieldType.Long)
    val preferredTechStackIds: List<Long> = emptyList(),
    @Field(type = FieldType.Keyword)
    val highSchoolGraduateFit: String? = null,
    @Field(type = FieldType.Keyword)
    val entryLevelFit: String? = null,
    @Field(type = FieldType.Keyword)
    val difficulty: String? = null,
)
