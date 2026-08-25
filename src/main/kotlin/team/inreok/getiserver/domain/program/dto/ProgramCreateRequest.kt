package team.inreok.getiserver.domain.program.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import team.inreok.getiserver.domain.program.entity.type.ProgramStatus
import team.inreok.getiserver.domain.program.entity.type.ProgramType
import java.time.LocalDateTime

/**
 * 프로그램 등록·임시저장 요청이다(원본 요구사항 문서 6절).
 *
 * `fileIds`(본문 첨부파일 연결)는 File 도메인의 공개 Port(`FileLinkPort`, Issue #85)로 소유권·
 * 목적·상태·개수를 검증한 뒤 연결한다(Issue #127). Discord 실제 게시(Phase 7)도 아직 연동하지
 * 않아 `discordChannelId`는 값 자체의 필수 여부만 검증하고 저장만 한다.
 */
@Schema(description = "프로그램 등록·임시저장 요청")
data class ProgramCreateRequest(
    @field:NotBlank(message = "프로그램 제목은 비어 있을 수 없습니다.")
    @field:Size(max = 500, message = "프로그램 제목은 500자를 넘을 수 없습니다.")
    @param:Schema(description = "프로그램 제목(필수, 최대 500자)", example = "2026 여름방학 백엔드 특강", maxLength = 500)
    val title: String,
    @param:Schema(description = "프로그램 유형(필수)", example = "SPECIAL_LECTURE")
    val programType: ProgramType,
    @param:Schema(
        description = "저장할 상태(필수). DRAFT 또는 PUBLISHED만 지정할 수 있다. CLOSED·DELETED는 상태 변경 API를 사용한다.",
        example = "DRAFT",
        allowableValues = ["DRAFT", "PUBLISHED"],
    )
    val status: ProgramStatus,
    @param:Schema(description = "Markdown 본문", example = "## 모집 안내\n- 대상: 2, 3학년", nullable = true)
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
    @param:Schema(description = "장소", example = "본관 2층 대강당", nullable = true, maxLength = 500)
    val location: String? = null,
    @param:Schema(description = "대상 학년 목록(1~3, 1개 이상, 중복 불가)", example = "[2, 3]", nullable = true)
    val targetGrades: List<Int>? = null,
    @param:Schema(description = "모집 정원(1 이상)", example = "20", nullable = true)
    val capacity: Int? = null,
    @param:Schema(
        description = "연결할 신청 양식 ID. FormType=PROGRAM, FormStatus=ACTIVE이고 본인이 소유한 Form만 연결할 수 있다.",
        example = "1",
        nullable = true,
    )
    val formId: Long? = null,
    @field:Size(max = 255, message = "Discord 채널 ID는 255자를 넘을 수 없습니다.")
    @param:Schema(
        description = "게시할 프로그램 전용 Discord 채널 ID. PUBLISHED로 저장하려면 필수다.",
        example = "1234567890",
        nullable = true,
        maxLength = 255,
    )
    val discordChannelId: String? = null,
    @param:Schema(
        description =
            "연결할 첨부파일 ID 목록(선택). FilePurpose=PROGRAM_ATTACHMENT로 업로드하고 본인이 " +
                "소유한 파일만 연결할 수 있다. 개수 상한은 app.file.policies 설정을 따른다.",
        example = "[1, 2]",
    )
    val fileIds: List<Long> = emptyList(),
)
