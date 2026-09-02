package team.inreok.getiserver.domain.job.query

import org.springframework.modulith.NamedInterface
import java.time.LocalDateTime

/**
 * `search` Module이 Elasticsearch Document를 채우기 위해 Job의 현재 상태를 다시 읽는 유일한
 * 공개 계약이다(Issue #69). `search`는 이 Interface를 통해서만 Job을 읽고, `Job` Entity나
 * `JobRepository`를 직접 참조하지 않는다.
 *
 * "공개 검색 대상"의 기준(삭제되지 않고 PUBLISHED 또는 CLOSED)은 Job Module이 판단한다 —
 * [findById]는 대상이 아니면 null을 반환하고, `search`는 null이면 색인에서 제거하면 된다.
 */
@NamedInterface
interface JobIndexQueryPort {
    /** 공개 검색 대상이 아니면(존재하지 않거나, 삭제됐거나, DRAFT면) null을 반환한다. */
    fun findById(jobId: Long): JobIndexSnapshot?

    /**
     * 전체 재색인용 Keyset Pagination 조회다. `jobId > afterId`인 공개 검색 대상을 id 오름차순
     * 최대 `limit`건 반환한다. 첫 호출은 `afterId = 0`으로 시작한다.
     */
    fun findForReindex(
        afterId: Long,
        limit: Int,
    ): List<JobIndexSnapshot>
}

@NamedInterface
data class JobIndexSnapshot(
    val jobId: Long,
    val title: String,
    /** 공고 본문(nullable). Elasticsearch 전문 검색 대상이다. */
    val content: String?,
    /** `PostingType.name` */
    val postingType: String,
    /** `ApplicationMethod.name` */
    val applicationMethod: String,
    /** `JobStatus.name`. [JobIndexQueryPort]가 이미 공개 대상만 반환하므로 항상 PUBLISHED/CLOSED다. */
    val status: String,
    val companyId: Long,
    val targetGrade: Int?,
    val capacity: Int?,
    val firstComeServed: Boolean,
    val viewCount: Long,
    val publishedAt: LocalDateTime?,
    val startDate: LocalDateTime?,
    val endDate: LocalDateTime?,
    /** `jobs.source_name`(nullable). Search의 `sourceName` 필터가 그대로 비교하는 값이다. */
    val sourceName: String?,
    /**
     * 근무지역·고용형태(nullable). 정해진 값 집합이 없는 표시 전용 문자열이라 검색어 매칭
     * 대상이 아니고 목록 응답에 그대로 실린다(Issue #169). 기존 호출부를 깨지 않도록 기본값을
     * 둔 additive 확장이다(AI 분석 필드와 같은 방식, Issue #144).
     */
    val location: String? = null,
    val employmentType: String? = null,
)
