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
    @Field(type = FieldType.Text, analyzer = "job_search_korean")
    val companyName: String,
    @Field(type = FieldType.Keyword)
    val companyType: String?,
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
)
