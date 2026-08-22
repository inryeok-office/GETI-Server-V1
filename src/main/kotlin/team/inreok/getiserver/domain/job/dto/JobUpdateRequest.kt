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
 *
 * `fileIds`는 다른 Field와 다른 방식으로 "유지"와 "전체 해제"를 구분한다(Issue #126,
 * `ProgramUpdateRequest.fileIds`와 동일한 이유) — List Field라 별도 Flag 없이도 `null`(미전달,
 * 유지)과 `emptyList()`(명시적 전체 해제)가 이미 서로 다른 값이기 때문이다. 값을 전달하면(빈
 * List 포함) **그 목록을 최종 상태로 취급해 기존 연결을 전부 해제한 뒤 다시 연결한다** — 기존
 * 연결 중 유지하고 싶은 파일도 매번 다시 포함해 보내야 한다.
 *
 * 이 방식의 중요한 제약: `FileLinkPort.validateAndLink`의 소유권 검증(`FILE_NOT_OWNED`)은 **이
 * 요청을 보내는 사용자 본인이 그 파일의 최초 업로더인지**를 확인하며 공고 관리 권한과는 무관하다.
 * 등록자 본인이 아닌 다른 교사나 개발자가 기존 첨부파일을 "유지"하려고 동일한 `fileId`를 그대로
 * 재전송해도, 그 파일을 실제로 업로드한 사람이 아니면 403(`FILE_NOT_OWNED`)으로 거부된다.
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
    // 근무지역과 고용형태는 정해진 값 집합이 없는 표시 전용 자유 문자열이다(Issue #169).
    @field:Size(max = 255, message = "근무지역은 255자를 넘을 수 없습니다.")
    @param:Schema(
        description = "근무지역. 정해진 값 집합이 없는 표시 전용 문자열이다.",
        example = "서울특별시 중구",
        nullable = true,
        maxLength = 255,
    )
    val location: String? = null,
    @field:Size(max = 255, message = "고용형태는 255자를 넘을 수 없습니다.")
    @param:Schema(
        description = "고용형태. 정해진 값 집합이 없는 표시 전용 문자열이다.",
        example = "인턴",
        nullable = true,
        maxLength = 255,
    )
    val employmentType: String? = null,
    @param:Schema(description = "선착순 모집 여부", example = "true", nullable = true)
    val firstComeServed: Boolean? = null,
    @field:Size(max = 255, message = "Discord 채널 Key는 255자를 넘을 수 없습니다.")
    @param:Schema(
        description = "게시할 Discord 채널의 논리 Key. 허용 목록에 없으면 거부된다.",
        example = "job-notice",
        nullable = true,
        maxLength = 255,
    )
    val discordChannelKey: String? = null,
    @param:Schema(
        description =
            "연결할 첨부파일 ID 목록. 전달하지 않으면(null) 기존 첨부파일을 그대로 유지한다. " +
                "전달하면(빈 배열 포함) 그 목록을 최종 상태로 취급해 기존 연결을 모두 해제한 뒤 " +
                "다시 연결한다 -- 유지하고 싶은 기존 파일도 다시 포함해야 한다. FilePurpose=" +
                "JOB_ATTACHMENT로 업로드하고 이 요청을 보내는 본인이 소유한 파일만 연결할 수 " +
                "있다(다른 관리자가 업로드한 기존 첨부는 본인이 아니면 재전송해도 FILE_NOT_OWNED로 " +
                "거부된다).",
        example = "[1, 3]",
        nullable = true,
    )
    val fileIds: List<Long>? = null,
)
