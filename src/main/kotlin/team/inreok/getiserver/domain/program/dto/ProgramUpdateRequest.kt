package team.inreok.getiserver.domain.program.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Size
import java.time.LocalDateTime

/**
 * 프로그램 부분 수정 요청이다(원본 요구사항 문서 7절). 전달하지 않았거나 null인 Field는 기존
 * 값을 유지한다(부분 수정, PATCH). 게시 후 변경할 수 없는 `programType`/`targetGrades`는
 * 요구사항이 명시한 대로 이 요청에 아예 포함하지 않는다.
 *
 * `formId`만으로는 "전달 안 함(유지)"과 "명시적으로 null 보내 연결 해제"를 구분할 수 없어
 * (`formId?.let { ... }` 구조상 null은 항상 "유지"로 해석됨, PR #81 리뷰 지적) `clearFormId`를
 * 별도로 뒀다. `MemberProfileController.updateMyProfile`처럼 고정 DTO 대신 `JsonNode`로 Body 전체를
 * 받아 Key 존재 여부로 구분하는 방식도 검토했으나, 이 요청의 다른 Field(Bean Validation이 붙은
 * `title`/`location` 등)까지 전부 손대는 건 이번 지적 범위(Form 연결 해제 1건)를 넘어서는
 * Refactoring이라 채택하지 않았다. `CompanyUpdateRequest`도 같은 이유로 아직 고정 DTO를 유지한다.
 *
 * `fileIds`는 `formId`와 다른 방식으로 "유지"와 "전체 해제"를 구분한다(Issue #127) — List
 * Field라 별도 Flag 없이도 `null`(미전달, 유지)과 `emptyList()`(명시적 전체 해제)가 이미 서로
 * 다른 값이기 때문이다(`clearFormId`가 필요했던 이유는 `formId`가 단일 스칼라라 null이 "유지"
 * 하나로만 해석될 수밖에 없던 구조적 한계였고, List에는 그 한계가 없다). 값을 전달하면(빈 List
 * 포함) **그 목록을 최종 상태로 취급해 기존 연결을 전부 해제한 뒤 다시 연결한다** — 기존 연결 중
 * 유지하고 싶은 파일도 매번 다시 포함해 보내야 한다.
 *
 * 이 방식의 중요한 제약: `FileLinkPort.validateAndLink`의 소유권 검증(`FILE_NOT_OWNED`)은 **이
 * 요청을 보내는 사용자 본인이 그 파일의 최초 업로더인지**를 확인하며 Program 관리 권한과는
 * 무관하다. 등록자 본인이 아닌 다른 담당 교사나 개발자가 기존 첨부파일을 "유지"하려고 동일한
 * `fileId`를 그대로 재전송해도, 그 파일을 실제로 업로드한 사람이 아니면 403(`FILE_NOT_OWNED`)으로
 * 거부된다 — 첨부파일이 있는 프로그램은 사실상 그 파일들을 업로드한 사람만 `fileIds`를 다시
 * 보낼 수 있다(실측 확인, `FileLinkPortImpl.verifyLinkable` 참고).
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
    @param:Schema(
        description = "연결할 신청 양식 ID. 전달하지 않으면 기존 연결을 유지한다. `clearFormId=true`면 이 값은 무시된다.",
        example = "1",
        nullable = true,
    )
    val formId: Long? = null,
    @param:Schema(
        description =
            "true면 formId 값과 무관하게 기존 Form 연결을 해제한다(연결 해제 전용 플래그, " +
                "formId만으로는 \"미전달\"과 \"명시적 해제\"를 구분할 수 없어 별도로 둠). 기본값 false.",
        example = "false",
    )
    val clearFormId: Boolean = false,
    @param:Schema(
        description =
            "연결할 첨부파일 ID 목록. 전달하지 않으면(null) 기존 첨부파일을 그대로 유지한다. " +
                "전달하면(빈 배열 포함) 그 목록을 최종 상태로 취급해 기존 연결을 모두 해제한 뒤 " +
                "다시 연결한다 -- 유지하고 싶은 기존 파일도 다시 포함해야 한다. FilePurpose=" +
                "PROGRAM_ATTACHMENT로 업로드하고 이 요청을 보내는 본인이 소유한 파일만 연결할 수 " +
                "있다(다른 관리자가 업로드한 기존 첨부는 본인이 아니면 재전송해도 FILE_NOT_OWNED로 " +
                "거부된다).",
        example = "[1, 3]",
        nullable = true,
    )
    val fileIds: List<Long>? = null,
    @param:Schema(description = "변경 요약. 게시된 프로그램 신청자 알림 내용에 포함된다.", nullable = true)
    val changeSummary: String? = null,
)
