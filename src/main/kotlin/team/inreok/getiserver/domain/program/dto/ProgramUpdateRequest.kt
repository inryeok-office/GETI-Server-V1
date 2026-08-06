package team.inreok.getiserver.domain.program.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Size
import java.time.LocalDateTime

/**
 * 프로그램 부분 수정 요청이다(원본 요구사항 문서 7절). 전달하지 않았거나 null인 Field는 기존
 * 값을 유지한다(부분 수정, PATCH). 게시 후 변경할 수 없는 `programType`/`targetGrades`는
 * 요구사항이 명시한 대로 이 요청에 아예 포함하지 않는다. `fileIds`는 [ProgramCreateRequest]와
 * 같은 이유로 이번 범위에서 제외했다.
 */
@Schema(description = "프로그램 부분 수정 요청")
data class ProgramUpdateRequest(
    @field:Size(max = 500, message = "프로그램 제목은 500자를 넘을 수 없습니다.")
    @param:Schema(description = "프로그램 제목", example = "2026 여름방학 백엔드 특강(변경)", nullable = true, maxLength = 500)
    val title: String? = null,
    @param:Schema(description = "Markdown 본문", nullable = true)
    val content: String? = null,
    @param:Schema(description = "행사 시작 일시", example = "2026-08-10T10:00:00", nullable = true)
    val startAt: LocalDateTime? = null,
    @param:Schema(description = "행사 종료 일시", example = "2026-08-10T17:00:00", nullable = true)
    val endAt: LocalDateTime? = null,
    @param:Schema(description = "신청 시작 일시", example = "2026-08-01T00:00:00", nullable = true)
    val applicationStartAt: LocalDateTime? = null,
    @param:Schema(description = "신청 종료 일시", example = "2026-08-07T23:59:59", nullable = true)
    val applicationEndAt: LocalDateTime? = null,
    @field:Size(max = 500, message = "장소는 500자를 넘을 수 없습니다.")
    @param:Schema(description = "장소", nullable = true, maxLength = 500)
    val location: String? = null,
    @param:Schema(
        description =
            "모집 정원. 증가는 항상 허용되고, 감소는 현재 활성 신청 인원 이상까지만 허용된다" +
                "(CAPACITY_BELOW_CURRENT_APPLICANTS).",
        example = "25",
        nullable = true,
    )
    val capacity: Int? = null,
    @param:Schema(description = "연결할 신청 양식 ID", example = "1", nullable = true)
    val formId: Long? = null,
    @param:Schema(description = "변경 요약. 게시된 프로그램 신청자 알림 내용에 포함된다.", nullable = true)
    val changeSummary: String? = null,
)
