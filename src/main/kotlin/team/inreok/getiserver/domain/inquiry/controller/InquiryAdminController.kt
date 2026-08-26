package team.inreok.getiserver.domain.inquiry.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import team.inreok.getiserver.domain.inquiry.dto.InquiryAdminListResponse
import team.inreok.getiserver.domain.inquiry.dto.InquiryAnswerCreateRequest
import team.inreok.getiserver.domain.inquiry.dto.InquiryAnswerCreateResponse
import team.inreok.getiserver.domain.inquiry.dto.InquiryAssigneeUpdateRequest
import team.inreok.getiserver.domain.inquiry.dto.InquiryAssigneeUpdateResponse
import team.inreok.getiserver.domain.inquiry.dto.InquiryStatusUpdateRequest
import team.inreok.getiserver.domain.inquiry.dto.InquiryStatusUpdateResponse
import team.inreok.getiserver.domain.inquiry.entity.type.InquiryStatus
import team.inreok.getiserver.domain.inquiry.entity.type.InquiryType
import team.inreok.getiserver.domain.inquiry.service.InquiryService
import team.inreok.getiserver.global.openapi.BEARER_AUTH_SCHEME
import team.inreok.getiserver.global.web.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

@Tag(
    name = "Inquiry - 문의 관리",
    description = "전체 문의를 조회·검색하고 담당자·상태를 관리한다. 필요 권한: 개발자.",
)
@SecurityRequirement(name = BEARER_AUTH_SCHEME)
// SecurityConfig가 이 경로 전체를 DEVELOPER 역할 필수로 지정하므로, 여기 도달했다는 것은 이미
// 유효한 Access Token으로 인증되고 DEVELOPER 역할을 가졌다는 뜻이다(요구사항 §51 권한 Matrix).
// 담당자 지정 대상 자체의 유효성(role=DEVELOPER AND status=ACTIVE)은 Role만으로 알 수 없어
// InquiryService가 별도로 수행한다.
@RestController
@RequestMapping("/api/v1/admin/inquiries")
class InquiryAdminController(
    private val inquiryService: InquiryService,
) {
    @Operation(
        summary = "전체 문의 목록 조회",
        description = """
            개발자가 모든 문의를 조회·검색한다. 모든 Filter는 함께 조합할 수 있고(AND), 지정하지
            않은 Filter는 적용하지 않는다. `query`는 문의 제목·내용·작성자 이름을 대소문자 구분
            없이 검색한다(학번은 검색 대상이 아니다). `mineOnly=true`면 현재 로그인한 개발자가
            담당자인 문의만 반환하며, `assigneeId`를 함께 지정하면 두 조건이 단순 AND로 결합된다
            (서로 다른 개발자를 가리키면 결과가 항상 빈 목록이 된다). 기본 page=0, size=20이며
            최대 size=100이다. 정렬은 최신 등록순으로 고정된다.

            Discord 접수 알림 상태(`discordStatus`)로 필터링하는 기능은 이번 범위에 포함되지
            않는다. Discord 전달 상태는 이 목록에 포함되지 않고
            `GET /api/v1/admin/inquiries/{inquiryId}/discord`로 개별 조회한다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "조회 성공(결과가 없으면 빈 목록)"),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(responseCode = "403", description = "개발자 권한이 없음 (FORBIDDEN)"),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @GetMapping
    fun listInquiries(
        authentication: Authentication,
        @Parameter(description = "답변 여부 필터(선택). answered=true는 answeredAt 존재, false는 null인 문의만 조회")
        @RequestParam(required = false)
        answered: Boolean?,
        @Parameter(description = "문의 유형 필터(선택)") @RequestParam(required = false) inquiryType: InquiryType?,
        @Parameter(description = "문의 상태 필터(선택)") @RequestParam(required = false) status: InquiryStatus?,
        @Parameter(description = "검색어(선택). 제목·내용·작성자 이름 대상") @RequestParam(required = false) query: String?,
        @Parameter(description = "담당 개발자 회원 ID 필터(선택)") @RequestParam(required = false) assigneeId: Long?,
        @Parameter(description = "현재 로그인한 개발자가 담당자인 문의만 조회(선택, 기본 false)")
        @RequestParam(required = false, defaultValue = "false")
        mineOnly: Boolean,
        @Parameter(description = "Pagination(page: 0부터 시작, size: 기본 20, 최대 100)") pageable: Pageable,
    ): ApiResponse<InquiryAdminListResponse> {
        val memberId = authentication.principal as Long
        return ApiResponse.of(
            inquiryService.listAdmin(inquiryType, status, query, assigneeId, mineOnly, memberId, pageable, answered),
        )
    }

    @Operation(
        summary = "담당 개발자 지정·해제",
        description = """
            문의의 담당 개발자를 지정하거나(`assigneeId` 지정) 해제한다(`assigneeId=null`).
            지정 대상은 role=DEVELOPER이면서 status=ACTIVE인 회원만 가능하다. 담당자 지정은 문의
            상태(status)를 자동으로 바꾸지 않는다 -- 배정과 진행 상태는 독립적이다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "지정·해제 성공"),
        SwaggerApiResponse(
            responseCode = "400",
            description = "role=DEVELOPER 또는 status=ACTIVE가 아닌 회원을 지정 (INVALID_INQUIRY_ASSIGNEE)",
        ),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(responseCode = "403", description = "개발자 권한이 없음 (FORBIDDEN)"),
        SwaggerApiResponse(
            responseCode = "404",
            description = "문의가 없음(INQUIRY_NOT_FOUND), 지정하려는 회원이 없음(MEMBER_NOT_FOUND)",
        ),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @PatchMapping("/{inquiryId}/assignee")
    fun updateAssignee(
        @Parameter(description = "대상 문의 ID", example = "1") @PathVariable inquiryId: Long,
        @Valid @RequestBody request: InquiryAssigneeUpdateRequest,
    ): ApiResponse<InquiryAssigneeUpdateResponse> = ApiResponse.of(inquiryService.updateAssignee(inquiryId, request))

    @Operation(
        summary = "문의 상태 변경",
        description = """
            문의 상태를 변경한다. 허용되는 상태 전이는 다음과 같고 그 외(CLOSED에서의 모든 전이
            포함)는 400으로 거부한다.

            ```
            RECEIVED    -> IN_PROGRESS | ANSWERED
            IN_PROGRESS -> ANSWERED | CLOSED
            ANSWERED    -> CLOSED
            ```

            `status=ANSWERED`를 직접 지정할 때 답변이 하나도 없으면 같은 오류로 거부한다("답변
            없이 ANSWERED"라는 정합성 모순을 막기 위함). CLOSED는 최종 상태이며 이후 답변을 등록할
            수 없다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "상태 변경 성공"),
        SwaggerApiResponse(
            responseCode = "400",
            description = "허용되지 않은 상태 전이 또는 답변 없이 ANSWERED 지정 (INQUIRY_STATUS_INVALID)",
        ),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(responseCode = "403", description = "개발자 권한이 없음 (FORBIDDEN)"),
        SwaggerApiResponse(responseCode = "404", description = "문의가 없음 (INQUIRY_NOT_FOUND)"),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @PatchMapping("/{inquiryId}/status")
    fun changeStatus(
        @Parameter(description = "상태를 변경할 문의 ID", example = "1") @PathVariable inquiryId: Long,
        @Valid @RequestBody request: InquiryStatusUpdateRequest,
    ): ApiResponse<InquiryStatusUpdateResponse> = ApiResponse.of(inquiryService.changeStatus(inquiryId, request))

    @Operation(
        summary = "답변 등록",
        description = """
            문의에 답변을 등록한다. 답변 등록은 문의 상태를 항상 ANSWERED로 자동 전이한다 --
            담당자 지정과 달리 답변은 문의의 진행 상태 자체를 나타내는 사건이기 때문이다. CLOSED
            상태의 문의에는 답변을 등록할 수 없다(이미 종료됨). `fileIds`는
            FilePurpose=INQUIRY_ANSWER_ATTACHMENT로 업로드하고 본인이 소유한 파일만 연결할 수
            있다.

            응답의 `notificationCreated`는 문의 작성자에게 보낼 인앱 알림 생성을 Notification
            도메인에 요청하는 데 성공했는지를 뜻하며, 알림 Row가 실제로 만들어졌는지는 보장하지
            않는다 -- 실제 생성은 이 요청과 분리된 Transaction에서 비동기로 처리된다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "201", description = "등록 성공"),
        SwaggerApiResponse(
            responseCode = "400",
            description =
                "답변 내용 공백(VALIDATION_FAILED), 첨부파일 목적 불일치(FILE_PURPOSE_MISMATCH), " +
                    "첨부 개수 초과(FILE_COUNT_EXCEEDED), 중복 File ID(INVALID_REQUEST)",
        ),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(
            responseCode = "403",
            description = "개발자 권한이 없음(FORBIDDEN), 본인이 업로드하지 않은 첨부파일(FILE_NOT_OWNED)",
        ),
        SwaggerApiResponse(
            responseCode = "404",
            description = "문의가 없음(INQUIRY_NOT_FOUND), 첨부파일이 없음(FILE_NOT_FOUND)",
        ),
        SwaggerApiResponse(
            responseCode = "409",
            description = "이미 CLOSED된 문의에 답변을 등록함(INQUIRY_ALREADY_CLOSED), 이미 연결된 첨부파일(FILE_ALREADY_LINKED)",
        ),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @PostMapping("/{inquiryId}/answers")
    @ResponseStatus(HttpStatus.CREATED)
    fun createAnswer(
        authentication: Authentication,
        @Parameter(description = "답변을 등록할 문의 ID", example = "1") @PathVariable inquiryId: Long,
        @Valid @RequestBody request: InquiryAnswerCreateRequest,
    ): ApiResponse<InquiryAnswerCreateResponse> {
        val memberId = authentication.principal as Long
        return ApiResponse.of(inquiryService.createAnswer(inquiryId, request, memberId))
    }
}
