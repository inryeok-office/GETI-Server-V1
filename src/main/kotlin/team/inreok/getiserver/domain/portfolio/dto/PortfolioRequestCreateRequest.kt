package team.inreok.getiserver.domain.portfolio.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.LocalDateTime

/**
 * 포트폴리오 수합 요청 등록 요청이다(요구사항 §4). 항상 DRAFT 상태로 생성되고, 공개(PUBLISHED)는
 * 상태 변경 API로 수행한다.
 *
 * `fileIds`(안내/양식 파일)는 File Integration(Phase 3, §16)에서 추가한다 -- 지금 넣으면 없는
 * 기능을 있는 것으로 오해하므로 Field 자체를 넣지 않았다.
 */
@Schema(description = "포트폴리오 수합 요청 등록 요청")
data class PortfolioRequestCreateRequest(
    @field:NotBlank(message = "수합 요청 제목은 비어 있을 수 없습니다.")
    @field:Size(max = 500, message = "수합 요청 제목은 500자를 넘을 수 없습니다.")
    @param:Schema(description = "수합 요청 제목(필수, 최대 500자)", example = "2026 상반기 포트폴리오 수합", maxLength = 500)
    val title: String,
    @param:Schema(description = "수합 요청 설명", example = "졸업 포트폴리오를 제출해 주세요.", nullable = true)
    val description: String? = null,
    @param:Schema(description = "제출 마감 시각(필수)", example = "2026-09-30T23:59:59")
    val dueAt: LocalDateTime,
    @param:Schema(
        description = "제출 대상 학생 memberId 목록(필수, 최소 1명). 실제 학생(STUDENT)만 지정할 수 있다.",
        example = "[1, 2, 3]",
    )
    val targetStudentIds: List<Long>,
)
