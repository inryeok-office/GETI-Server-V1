package team.inreok.getiserver.domain.inquiry.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import team.inreok.getiserver.domain.inquiry.dto.InquiryCreateRequest
import team.inreok.getiserver.domain.inquiry.dto.InquiryCreateResponse
import team.inreok.getiserver.domain.inquiry.dto.InquiryDetailResponse
import team.inreok.getiserver.domain.inquiry.service.InquiryService
import team.inreok.getiserver.global.openapi.BEARER_AUTH_SCHEME
import team.inreok.getiserver.global.web.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

@Tag(
    name = "Inquiry - 문의 등록·조회",
    description = "문의를 등록하고 문의·답변 상세를 조회한다. 필요 권한: 학생, 교사, 개발자(상세 조회는 본인 문의만, 개발자는 전체).",
)
@SecurityRequirement(name = BEARER_AUTH_SCHEME)
// SecurityConfig가 /api/v1/inquiries 이하를 인증 필수로 두므로, 여기 도달했다는 것은 인증을 이미
// 통과했다는 뜻이다. 상세 조회의 소유권 검증(본인 문의만, 개발자는 예외, 요구사항 §16/§52)은
// Role만으로 알 수 없어 InquiryService가 별도로 수행한다. Controller는 memberId를 요청
// Parameter로 받지 않고 인증 주체(authentication.principal)에서만 꺼낸다.
@RestController
@RequestMapping("/api/v1/inquiries")
class InquiryController(
    private val inquiryService: InquiryService,
) {
    @Operation(
        summary = "문의 등록",
        description = """
            새 문의를 등록한다. status·담당자는 클라이언트가 지정할 수 없고 서버가 RECEIVED로
            고정한다. `fileIds`는 FilePurpose=INQUIRY_ATTACHMENT로 업로드하고 본인이 소유한
            파일만 연결할 수 있다.

            문의 접수 시 Discord 관리자 채널로 알림을 예약한다. 이 알림은 원본 Transaction Commit
            이후 별도로 처리되어, Discord 장애가 문의 등록 자체를 실패시키지 않는다. 이 응답에는
            Discord 전달 상태를 포함하지 않는다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "201", description = "등록 성공"),
        SwaggerApiResponse(
            responseCode = "400",
            description =
                "제목·내용 공백(VALIDATION_FAILED), 첨부파일 문제(FILE_NOT_FOUND, FILE_NOT_OWNED, " +
                    "FILE_PURPOSE_MISMATCH, FILE_COUNT_EXCEEDED, 중복 File ID)",
        ),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createInquiry(
        authentication: Authentication,
        @Valid @RequestBody request: InquiryCreateRequest,
    ): ApiResponse<InquiryCreateResponse> {
        val memberId = authentication.principal as Long
        return ApiResponse.of(inquiryService.create(request, memberId))
    }

    @Operation(
        summary = "문의·답변 상세 조회",
        description = """
            문의 하나와 그에 달린 답변 목록(등록 순)을 조회한다. 학생·교사는 본인이 작성한
            문의만 조회할 수 있고, 다른 사용자의 문의를 조회하면 403이다. 개발자는 관리 목적으로
            모든 문의를 조회할 수 있다.

            `assignee`(담당 개발자)는 개발자 응답에만 포함된다 -- 학생·교사 응답에서는 항상
            null이다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "조회 성공"),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(responseCode = "403", description = "본인이 작성한 문의가 아님 (INQUIRY_ACCESS_DENIED)"),
        SwaggerApiResponse(responseCode = "404", description = "문의가 없음 (INQUIRY_NOT_FOUND)"),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @GetMapping("/{inquiryId}")
    fun getInquiry(
        authentication: Authentication,
        @Parameter(description = "조회할 문의 ID", example = "1") @PathVariable inquiryId: Long,
    ): ApiResponse<InquiryDetailResponse> {
        val memberId = authentication.principal as Long
        val isDeveloper = authentication.authorities.any { it.authority == "ROLE_DEVELOPER" }
        return ApiResponse.of(inquiryService.getDetail(inquiryId, memberId, isDeveloper))
    }
}
