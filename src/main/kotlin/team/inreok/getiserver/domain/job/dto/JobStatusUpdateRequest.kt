package team.inreok.getiserver.domain.job.dto

import io.swagger.v3.oas.annotations.media.Schema
import team.inreok.getiserver.domain.job.entity.type.JobStatus

/**
 * 공고 상태 변경 요청이다. 허용 전이는 다음과 같고, 그 외(복구, 동일 상태로의 변경 포함)는
 * `JOB_STATUS_TRANSITION_INVALID`(409)로 거부한다.
 *
 * ```text
 * DRAFT     -> PUBLISHED | DELETED
 * PUBLISHED -> CLOSED    | DELETED
 * CLOSED    -> DELETED
 * ```
 */
@Schema(description = "공고 상태 변경 요청")
data class JobStatusUpdateRequest(
    @param:Schema(
        description =
            "변경할 상태. DRAFT->PUBLISHED는 게시 필수값을 모두 검증하고, DELETED는 Soft Delete로 " +
                "처리되어 실제 행과 북마크·지원 이력이 보존된다.",
        example = "PUBLISHED",
    )
    val status: JobStatus,
)
