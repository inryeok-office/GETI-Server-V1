package team.inreok.getiserver.domain.job.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Size
import java.time.LocalDateTime

/**
 * 공고 내용 부분 수정 요청이다. 전달하지 않았거나 `null`인 Field는 기존 값을 그대로 유지한다
 * (`CompanyUpdateRequest`와 동일한 규칙). 값을 비우는 기능은 이번 범위에서 제외했다 —
 * 미전달과 명시적 `null`을 구분하려면 요청 Body를 원본 JSON으로 받아야 하는데, Bean Validation과
 * Swagger 문서화를 모두 수작업으로 대체해야 해 비용이 크다(Issue #60 제외 범위).
 *
 * `companyId`, `postingType`, `applicationMethod`는 이 Endpoint로 바꿀 수 없고 `status`는
 * 상태 변경 Endpoint(`PATCH /api/v1/admin/jobs/{jobId}/status`)로 분리되어 있다.
 */
@Schema(description = "공고 부분 수정 요청. 전달한 Field만 반영되고 null이거나 생략한 Field는 기존 값을 유지한다.")
data class JobUpdateRequest(
    @field:Size(max = 500, message = "공고 제목은 500자를 넘을 수 없습니다.")
    @param:Schema(description = "공고 제목", example = "2026 상반기 백엔드 채용(수정)", nullable = true, maxLength = 500)
    val title: String? = null,
    @param:Schema(description = "Markdown 본문", example = "## 모집 부문\n- 백엔드 개발자", nullable = true)
    val content: String? = null,
    @field:Size(max = 2000, message = "외부 지원 URL은 2000자를 넘을 수 없습니다.")
    @param:Schema(
        description = "외부 지원 URL. http 또는 https만 허용한다.",
        example = "https://example.com/apply",
        nullable = true,
        maxLength = 2000,
    )
    val externalUrl: String? = null,
    @param:Schema(description = "모집 시작 시각", example = "2026-08-01T00:00:00", nullable = true)
    val startDate: LocalDateTime? = null,
    @param:Schema(description = "모집 종료 시각", example = "2026-08-31T23:59:59", nullable = true)
    val endDate: LocalDateTime? = null,
    @param:Schema(description = "지원 대상 학년(1~3)", example = "3", nullable = true)
    val targetGrade: Int? = null,
    @param:Schema(description = "모집 인원(1 이상)", example = "2", nullable = true)
    val capacity: Int? = null,
    @param:Schema(description = "선착순 모집 여부", example = "true", nullable = true)
    val firstComeServed: Boolean? = null,
)
