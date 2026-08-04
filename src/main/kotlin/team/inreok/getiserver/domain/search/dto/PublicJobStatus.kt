package team.inreok.getiserver.domain.search.dto

import io.swagger.v3.oas.annotations.media.Schema
import team.inreok.getiserver.domain.job.entity.type.JobStatus

/**
 * 공개 검색에서 필터로 쓸 수 있는 상태만 담은 Enum이다. `JobStatus`를 그대로 Query Parameter로
 * 받으면 `DRAFT`/`DELETED`를 요청할 수 있게 되어 별도 거부 로직이 필요하지만, 허용 값만 가진
 * 이 Enum을 쓰면 Spring의 Type 변환이 실패하면서 `CommonErrorCode.TYPE_MISMATCH`(400)가
 * 자동으로 처리한다.
 *
 * `job` Module에도 같은 이름의 값 집합이 필요하지만(`getPublicDetail`의 공개 여부 판정), 그건
 * Query Parameter 계약이 아니라 내부 판정이라 별도 Enum 없이 `JobStatus` 집합으로 직접 비교한다
 * (Issue #69 — 검색 목록(`GET /api/v1/jobs`)의 소유권이 `job`에서 `search`로 옮겨지며 이 Enum도
 * 함께 옮겼다).
 */
@Schema(description = "공개 공고 목록에서 사용할 수 있는 상태 필터")
enum class PublicJobStatus(
    val jobStatus: JobStatus,
) {
    @Schema(description = "모집 중인 게시 공고")
    PUBLISHED(JobStatus.PUBLISHED),

    @Schema(description = "마감된 공고")
    CLOSED(JobStatus.CLOSED),
    ;

    companion object {
        /** 필터를 지정하지 않았을 때 공개 목록이 포함하는 상태 전체. */
        val ALL_VISIBLE: List<JobStatus> = entries.map { it.jobStatus }
    }
}
