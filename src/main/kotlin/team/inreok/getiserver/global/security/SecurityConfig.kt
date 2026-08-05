package team.inreok.getiserver.global.security

import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import team.inreok.getiserver.global.error.CommonErrorCode
import team.inreok.getiserver.global.error.ErrorResponse
import tools.jackson.databind.ObjectMapper

/**
 * Stateless JWT 인증 기반 Filter Chain이다. `/api/v1/auth/session`, `/api/v1/auth/logout`,
 * `/api/v1/me/` 이하 모든 경로(Issue #50, 내 프로필/전공/기술스택), `/api/v1/members`(학생 이름
 * 검색·프로필 조회, Issue #50 후속), `/api/v1/companies`(기업 조회, Issue #56), `/api/v1/jobs`
 * (공고 조회, Issue #60)는 인증을 요구하고, `/api/v1/admin/companies`(기업 등록·수정·삭제,
 * Issue #56)는 DEVELOPER 역할까지, `/api/v1/admin/jobs`(공고 관리, Issue #60)는 TEACHER 또는
 * DEVELOPER 역할까지, `/api/v1/me/forms`(개인 신청 양식 관리, Application Epic #75 Issue #76)도
 * TEACHER 또는 DEVELOPER 역할까지 요구한다.
 * 전공/기술스택 메타데이터 조회 등 다른
 * Domain은 아직 Spring Security와 연동되지 않아
 * ([AuthorizationHeaderSupport][team.inreok.getiserver.global.web.AuthorizationHeaderSupport] 참고)
 * 여기서 인증을 강제하면 기존 동작이 깨지므로 permitAll로 둔다. 나머지 Domain의 실제 인증 적용은
 * 각 Domain PR에서 별도로 다룬다.
 */
@Configuration
class SecurityConfig(
    private val objectMapper: ObjectMapper,
) {
    // JwtAuthenticationFilter는 @Component가 아니라 여기서만 생성한다(JwtAuthenticationFilter.kt의
    // Class 주석 참고) — Security와 무관한 Domain의 @WebMvcTest Slice가 이 Filter Chain을
    // 억지로 구성하지 않도록 하기 위함이다.
    @Bean
    fun jwtAuthenticationFilter(jwtTokenProvider: JwtTokenProvider): JwtAuthenticationFilter =
        JwtAuthenticationFilter(jwtTokenProvider)

    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        jwtAuthenticationFilter: JwtAuthenticationFilter,
    ): SecurityFilterChain {
        http {
            csrf { disable() }
            sessionManagement { sessionCreationPolicy = SessionCreationPolicy.STATELESS }
            httpBasic { disable() }
            formLogin { disable() }
            authorizeHttpRequests {
                // CORS Preflight(OPTIONS)에는 Authorization Header가 없어 authenticated에 막힌다. CORS가
                // 활성화되는 시점에 Cross-Origin의 /session·/logout이 막히지 않도록 먼저 허용한다(코드 리뷰 P2 반영).
                authorize(HttpMethod.OPTIONS, "/**", permitAll)
                // Swagger UI/OpenAPI JSON은 anyRequest(permitAll)에 의해서도 이미 허용되지만, 향후
                // anyRequest 기본값이 바뀌어도 Swagger 접근이 조용히 막히지 않도록 명시적으로 선언한다
                // (docs/ai/openapi-documentation.md 참고). Production에서는 springdoc 자체가
                // application-prod.yaml에서 비활성화되어 이 경로가 애초에 존재하지 않는다.
                authorize("/v3/api-docs/**", permitAll)
                authorize("/swagger-ui/**", permitAll)
                authorize("/swagger-ui.html", permitAll)
                // CD Readiness Polling과 Docker Health Check가 인증 없이 호출해야 하므로 노출 중인
                // Actuator Endpoint(health,info, docs/development/cd.md 참고)만 명시적으로 permitAll
                // 처리한다. env/beans/mappings/heapdump 등 다른 Actuator Endpoint는 management.endpoints
                // .web.exposure.include에도 없어 애초에 존재하지 않는다.
                authorize("/actuator/health", permitAll)
                authorize("/actuator/health/**", permitAll)
                authorize("/actuator/info", permitAll)
                authorize("/api/v1/auth/session", authenticated)
                authorize("/api/v1/auth/logout", authenticated)
                // 개인 신청 양식(Form) 관리는 교사·개발자만 접근한다(Application Epic #75, Issue #76).
                // 소유자 본인 검증(다른 교사의 양식 차단)은 Role만으로 알 수 없어 FormService가
                // 별도로 수행한다. 더 구체적인 경로를 먼저 선언해야 아래 /api/v1/me/** 규칙에
                // 가려지지 않는다.
                authorize("/api/v1/me/forms", hasAnyRole("TEACHER", "DEVELOPER"))
                authorize("/api/v1/me/forms/**", hasAnyRole("TEACHER", "DEVELOPER"))
                authorize("/api/v1/me/**", authenticated)
                authorize("/api/v1/members", authenticated)
                authorize("/api/v1/members/**", authenticated)
                // 기업 관리(등록·수정·삭제)는 개발자만 접근한다. 더 구체적인 admin 경로를 먼저
                // 선언해야 아래 조회 규칙에 가려지지 않는다(Issue #56, Notion API 명세서 권한).
                authorize("/api/v1/admin/companies", hasRole("DEVELOPER"))
                authorize("/api/v1/admin/companies/**", hasRole("DEVELOPER"))
                // 기업 조회(목록·상세)는 학생·교사·개발자 모두 접근할 수 있으므로 인증만 요구한다.
                authorize("/api/v1/companies", authenticated)
                authorize("/api/v1/companies/**", authenticated)
                // 공고 관리(등록·수정·상태 변경·관리자 상세)는 교사와 개발자가 사용한다(Issue #60).
                // 담당자 본인만 수정할 수 있는지는 기준이 확정되지 않아 역할까지만 검증한다.
                // 더 구체적인 admin 경로를 먼저 선언해야 아래 조회 규칙에 가려지지 않는다.
                authorize("/api/v1/admin/jobs", hasAnyRole("TEACHER", "DEVELOPER"))
                authorize("/api/v1/admin/jobs/**", hasAnyRole("TEACHER", "DEVELOPER"))
                // Collector 운영(수집원 관리·수동 실행·수집 실행 이력)은 개발자만 접근한다(Issue #62).
                authorize("/api/v1/admin/job-sources", hasRole("DEVELOPER"))
                authorize("/api/v1/admin/job-sources/**", hasRole("DEVELOPER"))
                authorize("/api/v1/admin/collector-actions", hasRole("DEVELOPER"))
                authorize("/api/v1/admin/collection-runs", hasRole("DEVELOPER"))
                authorize("/api/v1/admin/collection-runs/**", hasRole("DEVELOPER"))
                // 검색 색인 운영(전체 재구축)은 개발자만 접근한다(Issue #69).
                authorize("/api/v1/admin/search-actions", hasRole("DEVELOPER"))
                // 공고 조회(목록·상세)는 학생·교사·개발자 모두 접근할 수 있으므로 인증만 요구한다.
                authorize("/api/v1/jobs", authenticated)
                authorize("/api/v1/jobs/**", authenticated)
                // 공개 공고 출처 목록(JobSourceController)도 인증된 사용자 모두 접근할 수 있다(Issue #62).
                authorize("/api/v1/job-sources", authenticated)
                authorize(anyRequest, permitAll)
            }
            exceptionHandling {
                authenticationEntryPoint = jsonAuthenticationEntryPoint()
                accessDeniedHandler = jsonAccessDeniedHandler()
            }
            addFilterBefore<UsernamePasswordAuthenticationFilter>(jwtAuthenticationFilter)
        }
        return http.build()
    }

    private fun jsonAuthenticationEntryPoint() =
        AuthenticationEntryPoint { request, response, _ ->
            writeError(response, request.requestURI, HttpStatus.UNAUTHORIZED, CommonErrorCode.UNAUTHORIZED)
        }

    private fun jsonAccessDeniedHandler() =
        AccessDeniedHandler { request, response, _ ->
            writeError(response, request.requestURI, HttpStatus.FORBIDDEN, CommonErrorCode.FORBIDDEN)
        }

    private fun writeError(
        response: HttpServletResponse,
        path: String,
        status: HttpStatus,
        errorCode: CommonErrorCode,
    ) {
        response.status = status.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = "UTF-8"
        val body = ErrorResponse.of(errorCode = errorCode, path = path)
        response.writer.write(objectMapper.writeValueAsString(body))
    }
}
