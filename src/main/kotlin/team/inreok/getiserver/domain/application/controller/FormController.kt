package team.inreok.getiserver.domain.application.controller

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
import team.inreok.getiserver.domain.application.dto.CreateFormRequest
import team.inreok.getiserver.domain.application.dto.FormActionRequest
import team.inreok.getiserver.domain.application.dto.FormActionResponse
import team.inreok.getiserver.domain.application.dto.FormCreateResponse
import team.inreok.getiserver.domain.application.dto.FormDetailResponse
import team.inreok.getiserver.domain.application.dto.FormListResponse
import team.inreok.getiserver.domain.application.dto.FormUpdateResponse
import team.inreok.getiserver.domain.application.dto.UpdateFormRequest
import team.inreok.getiserver.domain.application.entity.type.FormStatus
import team.inreok.getiserver.domain.application.entity.type.FormType
import team.inreok.getiserver.domain.application.service.FormService
import team.inreok.getiserver.global.openapi.BEARER_AUTH_SCHEME
import team.inreok.getiserver.global.web.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

@Tag(
    name = "Application - 개인 신청 양식",
    description =
        "교사·개발자 개인이 소유하는 재사용 가능한 신청 양식(Form)을 생성·조회·수정·복제·활성화· " +
            "보관한다. 필요 권한: 교사, 개발자. 다른 사용자가 소유한 양식은 검색·조회·수정할 수 없다.",
)
@SecurityRequirement(name = BEARER_AUTH_SCHEME)
// SecurityConfig가 이 경로를 TEACHER 또는 DEVELOPER 역할 필수로 지정하므로, 여기 도달했다는 것은
// 이미 유효한 Access Token으로 인증되고 두 역할 중 하나를 가졌다는 뜻이다. 소유권(ownerMemberId)
// 검증은 Role만으로는 알 수 없어 FormService가 별도로 수행한다.
@RestController
@RequestMapping("/api/v1/me/forms")
class FormController(
    private val formService: FormService,
) {
    @Operation(
        summary = "개인 신청 양식 생성",
        description = """
            로그인한 사용자 소유로 새 신청 양식을 생성한다. 최초 Form Version은 항상 1이다.
            `fields` 배열의 순서가 곧 표시 순서(order)이며, 필드 key는 서로 달라야 한다.
            `status`를 ARCHIVED로 지정해 생성할 수 없다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "201", description = "생성 성공"),
        SwaggerApiResponse(
            responseCode = "400",
            description = "필드 검증 실패(INVALID_FORM_FIELD), 요청 값 형식 오류(VALIDATION_FAILED)",
        ),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(responseCode = "403", description = "교사·개발자가 아님 (FORBIDDEN)"),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createForm(
        authentication: Authentication,
        @Valid @RequestBody request: CreateFormRequest,
    ): ApiResponse<FormCreateResponse> {
        val ownerMemberId = authentication.principal as Long
        return ApiResponse.of(formService.create(ownerMemberId, request))
    }

    @Operation(
        summary = "내 양식 목록 조회",
        description = """
            로그인한 사용자가 소유한 양식만 조회한다. `formType`, `status`로 필터링할 수 있다.
            목록 기본값 page=0, size=20이며 최대 size=100이다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "조회 성공(결과가 없으면 빈 목록)"),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(responseCode = "403", description = "교사·개발자가 아님 (FORBIDDEN)"),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @GetMapping
    fun listForms(
        authentication: Authentication,
        @Parameter(description = "양식 대상 필터(선택)") @RequestParam(required = false) formType: FormType?,
        @Parameter(description = "양식 상태 필터(선택)") @RequestParam(required = false) status: FormStatus?,
        @Parameter(description = "Pagination(page: 0부터 시작, size: 기본 20, 최대 100)") pageable: Pageable,
    ): ApiResponse<FormListResponse> {
        val ownerMemberId = authentication.principal as Long
        return ApiResponse.of(formService.list(ownerMemberId, formType, status, pageable))
    }

    @Operation(
        summary = "양식 상세 조회",
        description = """
            formId로 지정한 양식의 상세 정보를 조회한다. 항상 현재(최신) Form Version의 필드
            구조를 보여준다. 다른 사용자가 소유한 양식은 조회할 수 없다(존재 자체는 확인되지만
            소유자가 아니면 403).
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "조회 성공"),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(responseCode = "403", description = "교사·개발자가 아니거나 다른 사용자의 양식 (FORBIDDEN, NOT_FORM_OWNER)"),
        SwaggerApiResponse(responseCode = "404", description = "양식이 없음 (FORM_NOT_FOUND)"),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @GetMapping("/{formId}")
    fun getForm(
        authentication: Authentication,
        @Parameter(description = "조회할 양식 ID", example = "1") @PathVariable formId: Long,
    ): ApiResponse<FormDetailResponse> {
        val ownerMemberId = authentication.principal as Long
        return ApiResponse.of(formService.get(formId, ownerMemberId))
    }

    @Operation(
        summary = "양식 부분 수정 (버전 관리)",
        description = """
            요청 Body에 전달한 Field만 수정한다(부분 수정, PATCH). `fields`를 전달하면 새
            Form Version이 생성되고 현재 버전이 증가한다 — 기존 제출 지원서는 제출 당시 버전을
            그대로 유지하므로 기존 Form Version을 덮어쓰지 않는다. `name`/`description`/`status`만
            바뀌는 경우는 새 버전을 만들지 않는다. 보관(ARCHIVED)된 양식은 수정할 수 없다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "수정 성공"),
        SwaggerApiResponse(
            responseCode = "400",
            description = "필드 검증 실패(INVALID_FORM_FIELD), 요청 값 형식 오류(VALIDATION_FAILED)",
        ),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(
            responseCode = "403",
            description = "교사·개발자가 아니거나 다른 사용자의 양식 (FORBIDDEN, FORM_NOT_OWNED)",
        ),
        SwaggerApiResponse(responseCode = "404", description = "양식이 없음 (FORM_NOT_FOUND)"),
        SwaggerApiResponse(responseCode = "409", description = "보관된 양식 (FORM_ARCHIVED)"),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @PatchMapping("/{formId}")
    fun updateForm(
        authentication: Authentication,
        @Parameter(description = "수정할 양식 ID", example = "1") @PathVariable formId: Long,
        @Valid @RequestBody request: UpdateFormRequest,
    ): ApiResponse<FormUpdateResponse> {
        val ownerMemberId = authentication.principal as Long
        return ApiResponse.of(formService.update(formId, ownerMemberId, request))
    }

    @Operation(
        summary = "양식 Action (복제·활성화·보관)",
        description = """
            DUPLICATE는 현재 최신 버전의 필드 구조만 복사한 새 양식을 생성한다(원본 지원서·답변은
            복제하지 않음). ACTIVATE는 DRAFT 또는 ARCHIVED 상태에서만, ARCHIVE는 DRAFT 또는
            ACTIVE 상태에서만 허용한다. 그 외 상태 전이는 400으로 거부한다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "Action 수행 성공"),
        SwaggerApiResponse(responseCode = "400", description = "허용되지 않는 상태 전이 (FORM_ACTION_INVALID)"),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(
            responseCode = "403",
            description = "교사·개발자가 아니거나 다른 사용자의 양식 (FORBIDDEN, FORM_NOT_OWNED)",
        ),
        SwaggerApiResponse(responseCode = "404", description = "양식이 없음 (FORM_NOT_FOUND)"),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @PostMapping("/{formId}/actions")
    fun executeFormAction(
        authentication: Authentication,
        @Parameter(description = "대상 양식 ID", example = "1") @PathVariable formId: Long,
        @Valid @RequestBody request: FormActionRequest,
    ): ApiResponse<FormActionResponse> {
        val ownerMemberId = authentication.principal as Long
        return ApiResponse.of(formService.executeAction(formId, ownerMemberId, request))
    }
}
