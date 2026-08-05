package team.inreok.getiserver.domain.application.controller

import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageRequest
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import team.inreok.getiserver.domain.application.dto.CreateFormRequest
import team.inreok.getiserver.domain.application.dto.FormAction
import team.inreok.getiserver.domain.application.dto.FormActionRequest
import team.inreok.getiserver.domain.application.dto.FormActionResponse
import team.inreok.getiserver.domain.application.dto.FormCreateResponse
import team.inreok.getiserver.domain.application.dto.FormDetailResponse
import team.inreok.getiserver.domain.application.dto.FormListResponse
import team.inreok.getiserver.domain.application.dto.FormUpdateResponse
import team.inreok.getiserver.domain.application.dto.UpdateFormRequest
import team.inreok.getiserver.domain.application.entity.type.FormStatus
import team.inreok.getiserver.domain.application.entity.type.FormType
import team.inreok.getiserver.domain.application.exception.FormActionInvalidException
import team.inreok.getiserver.domain.application.exception.FormArchivedException
import team.inreok.getiserver.domain.application.exception.FormNotFoundException
import team.inreok.getiserver.domain.application.exception.FormNotOwnedException
import team.inreok.getiserver.domain.application.exception.InvalidFormFieldException
import team.inreok.getiserver.domain.application.exception.NotFormOwnerException
import team.inreok.getiserver.domain.application.service.FormService
import team.inreok.getiserver.global.security.JwtTokenProvider
import team.inreok.getiserver.global.security.SecurityConfig
import java.time.LocalDateTime

// SecurityConfig를 명시적으로 Import해 /api/v1/me/forms가 실제로 TEACHER/DEVELOPER 권한을
// 요구하는지(401/403)까지 검증한다(CompanyAdminControllerTest와 동일한 패턴).
@WebMvcTest(controllers = [FormController::class])
@Import(SecurityConfig::class)
@EnableWebSecurity
class FormControllerTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
    ) {
        @MockitoBean
        private lateinit var formService: FormService

        @MockitoBean
        private lateinit var jwtTokenProvider: JwtTokenProvider

        private fun authOf(
            memberId: Long,
            vararg roles: String,
        ) = authentication(
            UsernamePasswordAuthenticationToken(
                memberId,
                null,
                roles.map { SimpleGrantedAuthority("ROLE_$it") },
            ),
        )

        // Kotlin non-null 파라미터에 bare any()를 쓰면 null 반환으로 NPE가 나므로 Elvis로 기본값을
        // 채운다(CompanyAdminControllerTest와 동일한 방식).
        private fun anyCreateFormRequest(): CreateFormRequest =
            any(CreateFormRequest::class.java)
                ?: CreateFormRequest(name = "", formType = FormType.JOB, fields = emptyList())

        private fun anyUpdateFormRequest(): UpdateFormRequest =
            any(UpdateFormRequest::class.java) ?: UpdateFormRequest()

        private fun anyFormActionRequest(): FormActionRequest =
            any(FormActionRequest::class.java) ?: FormActionRequest(action = FormAction.ACTIVATE)

        private val fixedTime = LocalDateTime.of(2026, 3, 1, 10, 0, 0)

        @Test
        fun `교사가 양식을 생성하면 201과 함께 생성 결과를 반환한다`() {
            given(formService.create(anyLong(), anyCreateFormRequest()))
                .willReturn(
                    FormCreateResponse(formId = 1L, version = 1, status = FormStatus.DRAFT, createdAt = fixedTime),
                )

            mockMvc
                .perform(
                    post("/api/v1/me/forms")
                        .with(authOf(1L, "TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            """
                            {
                              "name": "2026 하계 인턴 지원서",
                              "formType": "JOB",
                              "fields": [
                                { "key": "motivation", "type": "TEXTAREA", "label": "지원 동기", "required": true }
                              ]
                            }
                            """.trimIndent(),
                        ),
                ).andExpect(status().isCreated)
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.formId").value(1))
                .andExpect(jsonPath("$.data.version").value(1))
        }

        @Test
        fun `필드 검증에 실패하면 400 INVALID_FORM_FIELD를 반환한다`() {
            // key 자체는 @NotBlank(Bean Validation)를 통과시키고, key 중복처럼 Service Layer
            // (FormFieldValidation)만 판단할 수 있는 위반을 재현해 Mock이 실제로 호출되게 한다.
            given(formService.create(anyLong(), anyCreateFormRequest()))
                .willThrow(InvalidFormFieldException("필드 key 'motivation'가 중복되었습니다."))

            mockMvc
                .perform(
                    post("/api/v1/me/forms")
                        .with(authOf(1L, "TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            """
                            { "name": "지원서", "formType": "JOB", "fields": [
                                { "key": "motivation", "type": "TEXT", "label": "A" },
                                { "key": "motivation", "type": "TEXT", "label": "B" }
                            ] }
                            """.trimIndent(),
                        ),
                ).andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error.code").value("INVALID_FORM_FIELD"))
        }

        @Test
        fun `학생은 양식을 생성할 수 없고 403을 반환한다`() {
            mockMvc
                .perform(
                    post("/api/v1/me/forms")
                        .with(authOf(3L, "STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            """
                            { "name": "지원서", "formType": "JOB", "fields": [
                                { "key": "a", "type": "TEXT", "label": "A" }
                            ] }
                            """.trimIndent(),
                        ),
                ).andExpect(status().isForbidden)
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))
        }

        @Test
        fun `인증 없이 양식을 생성하면 401 UNAUTHORIZED를 반환한다`() {
            mockMvc
                .perform(
                    post("/api/v1/me/forms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            """
                            { "name": "지원서", "formType": "JOB", "fields": [
                                { "key": "a", "type": "TEXT", "label": "A" }
                            ] }
                            """.trimIndent(),
                        ),
                ).andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
        }

        @Test
        fun `개발자가 내 양식 목록을 조회하면 200과 함께 목록을 반환한다`() {
            given(formService.list(2L, null, null, PageRequest.of(0, 20)))
                .willReturn(
                    FormListResponse(
                        content = emptyList(),
                        page = 0,
                        size = 20,
                        totalElements = 0,
                        totalPages = 0,
                        first = true,
                        last = true,
                    ),
                )

            mockMvc
                .perform(get("/api/v1/me/forms").with(authOf(2L, "DEVELOPER")))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.content").isArray())
        }

        @Test
        fun `양식 상세를 조회하면 200과 함께 상세를 반환한다`() {
            given(formService.get(1L, 1L))
                .willReturn(
                    FormDetailResponse(
                        formId = 1L,
                        name = "지원서",
                        formType = FormType.JOB,
                        status = FormStatus.DRAFT,
                        description = null,
                        schemaData = emptyList(),
                        createdAt = fixedTime,
                        updatedAt = fixedTime,
                    ),
                )

            mockMvc
                .perform(get("/api/v1/me/forms/1").with(authOf(1L, "TEACHER")))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.formId").value(1))
        }

        @Test
        fun `존재하지 않는 양식을 조회하면 404 FORM_NOT_FOUND를 반환한다`() {
            given(formService.get(999L, 1L)).willThrow(FormNotFoundException(999L))

            mockMvc
                .perform(get("/api/v1/me/forms/999").with(authOf(1L, "TEACHER")))
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.error.code").value("FORM_NOT_FOUND"))
        }

        @Test
        fun `다른 사용자의 양식을 조회하면 403 NOT_FORM_OWNER를 반환한다`() {
            given(formService.get(1L, 2L)).willThrow(NotFormOwnerException())

            mockMvc
                .perform(get("/api/v1/me/forms/1").with(authOf(2L, "TEACHER")))
                .andExpect(status().isForbidden)
                .andExpect(jsonPath("$.error.code").value("NOT_FORM_OWNER"))
        }

        @Test
        fun `양식을 수정하면 200과 함께 수정 결과를 반환한다`() {
            given(formService.update(anyLong(), anyLong(), anyUpdateFormRequest()))
                .willReturn(
                    FormUpdateResponse(
                        formId = 1L,
                        version = 2,
                        affectedJobIds = emptyList(),
                        notificationCreated = false,
                        updatedAt = fixedTime,
                    ),
                )

            mockMvc
                .perform(
                    patch("/api/v1/me/forms/1")
                        .with(authOf(1L, "TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "name": "새 이름" }"""),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.data.version").value(2))
                .andExpect(jsonPath("$.data.affectedJobIds").isArray())
                .andExpect(jsonPath("$.data.notificationCreated").value(false))
        }

        @Test
        fun `다른 사용자의 양식을 수정하면 403 FORM_NOT_OWNED를 반환한다`() {
            given(formService.update(anyLong(), anyLong(), anyUpdateFormRequest())).willThrow(FormNotOwnedException())

            mockMvc
                .perform(
                    patch("/api/v1/me/forms/1")
                        .with(authOf(2L, "TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "name": "새 이름" }"""),
                ).andExpect(status().isForbidden)
                .andExpect(jsonPath("$.error.code").value("FORM_NOT_OWNED"))
        }

        @Test
        fun `보관된 양식을 수정하면 409 FORM_ARCHIVED를 반환한다`() {
            given(formService.update(anyLong(), anyLong(), anyUpdateFormRequest())).willThrow(FormArchivedException())

            mockMvc
                .perform(
                    patch("/api/v1/me/forms/1")
                        .with(authOf(1L, "TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "name": "새 이름" }"""),
                ).andExpect(status().isConflict)
                .andExpect(jsonPath("$.error.code").value("FORM_ARCHIVED"))
        }

        @Test
        fun `양식 Action을 수행하면 200과 함께 결과를 반환한다`() {
            given(formService.executeAction(anyLong(), anyLong(), anyFormActionRequest()))
                .willReturn(
                    FormActionResponse(
                        formId = 1L,
                        name = "지원서",
                        formType = FormType.JOB,
                        status = FormStatus.ACTIVE,
                        version = 1,
                        updatedAt = fixedTime,
                    ),
                )

            mockMvc
                .perform(
                    post("/api/v1/me/forms/1/actions")
                        .with(authOf(1L, "TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "action": "ACTIVATE" }"""),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
        }

        @Test
        fun `허용되지 않는 상태 전이면 400 FORM_ACTION_INVALID를 반환한다`() {
            given(formService.executeAction(anyLong(), anyLong(), anyFormActionRequest()))
                .willThrow(FormActionInvalidException("현재 상태(ARCHIVED)에서는 ARCHIVE Action을 수행할 수 없습니다."))

            mockMvc
                .perform(
                    post("/api/v1/me/forms/1/actions")
                        .with(authOf(1L, "TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "action": "ARCHIVE" }"""),
                ).andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error.code").value("FORM_ACTION_INVALID"))
        }
    }
