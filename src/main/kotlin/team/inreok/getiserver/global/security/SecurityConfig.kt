@file:Suppress("ForbiddenComment")

package team.inreok.getiserver.global.security

import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.config.annotation.web.AuthorizeHttpRequestsDsl
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
 * ⚠️ TEMPORARY: `securityFilterChain`의 HTTP Authorization Layer가 현재 전역 `permitAll`이다
 * (Frontend/API 연동 단계 지원, Issue #162 PR 본문 "임시 개발용 Security 전체 개방" 참고). 이
 * 상태에서는 CD로 배포된 환경의 모든 API가 Access Token 없이 호출 가능하다 -- 개발 연동 목적
 * 외(운영 서비스)로 이 상태를 유지하지 않는다. 기존 세부 Rule은 지우지 않고
 * [applyNormalSecurityRules]에 그대로 보존했다. 복구 방법: `authorize(anyRequest, permitAll)`
 * 한 줄을 지우고 `applyNormalSecurityRules()` 호출로 되돌린다.
 *
 * 아래는 [applyNormalSecurityRules]가 (현재는 호출되지 않지만) 정의하는 정상 정책이다.
 * `/api/v1/auth/session`, `/api/v1/auth/logout`,
 * `/api/v1/me/` 이하 모든 경로(Issue #50, 내 프로필/전공/기술스택), `/api/v1/members`(학생 이름
 * 검색·프로필 조회, Issue #50 후속), `/api/v1/companies`(기업 조회, Issue #56), `/api/v1/jobs`
 * (공고 조회, Issue #60)는 인증을 요구하고, `/api/v1/admin/companies`(기업 등록·수정·삭제,
 * Issue #56)는 DEVELOPER 역할까지, `/api/v1/admin/jobs`(공고 관리, Issue #60)는 TEACHER 또는
 * DEVELOPER 역할까지, `/api/v1/me/forms`(개인 신청 양식 관리, Application Epic #75 Issue #76)도
 * TEACHER 또는 DEVELOPER 역할까지 요구한다. `/api/v1/admin/programs`(프로그램 등록·수정·상태
 * 관리, Program 도메인 전체 개발 요구사항 Phase 1~3)는 TEACHER 또는 DEVELOPER 역할까지,
 * `POST /api/v1/programs/{id}/application-actions`(프로그램 신청·취소, 원본 요구사항 11절)는
 * STUDENT 역할까지, 나머지 `/api/v1/programs`(프로그램 목록·상세 조회)는 인증만 요구한다.
 * `/api/v1/notifications` 이하(인앱 알림 조회·읽음 처리, Notification Core)도 인증만 요구한다 —
 * 항상 요청자 본인의 알림만 다루기 때문이다. `/api/v1/files` 이하(공통 파일 업로드·다운로드,
 * File 도메인 Issue #85)도 인증만 요구하고, 파일별 소유권·접근 권한은 File 도메인이 판정한다.
 * `/api/v1/inquiries`(문의 등록·상세 조회)와 `/api/v1/me/inquiries`(내 문의 목록, 기존
 * `/api/v1/me/` 이하 규칙에 포함)도 인증만 요구한다 — 상세 조회의 본인 소유권 검증(개발자는 예외)은
 * Role로 알 수 없어 InquiryService가 별도로 수행한다. `/api/v1/me/job-recommendations`,
 * `/api/v1/me/recommendation-exclusions**`, `/api/v1/recommendations/settings`(맞춤 공고 추천
 * 조회·설정·관심 없음, Recommendation R3 Issue #152, Notion 계약 정합성 Issue #155)는 학생 전용
 * 기능이라 STUDENT Role까지 요구한다.
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

    // authorizeHttpRequests가 Domain이 늘어날 때마다 한 줄씩 길어지는 선언적 목록이라(Class
    // KDoc 참고) detekt 기본 LongMethod 임계값(60)을 넘는다. Domain별 규칙을 별도 Method로
    // 쪼개면 "구체적인 경로를 먼저 선언한다"는 순서 자체가 여러 Method에 흩어져 오히려 실수를
    // 유발한다(Program 사고 사례, 이 파일 Inquiry 규칙 주석 참고) -- 한 곳에 순서대로 두는 것이
    // 의도적인 설계다.
    @Suppress("LongMethod")
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
                // TEMPORARY(개발용 Security 전체 개방, Issue #162 PR 본문 "임시 개발용 Security
                // 전체 개방" 참고): Frontend/API 연동을 위해 HTTP Authorization Layer 전체를
                // permitAll로 연다. 기존 세부 Rule은 지우지 않고 applyNormalSecurityRules()에
                // 그대로 보존했다 -- 연동이 끝나면 아래 한 줄을 지우고 `applyNormalSecurityRules()`
                // 호출로 되돌리면 복구된다.
                //
                // TODO: Frontend 연동 완료 후 이 줄을 지우고 applyNormalSecurityRules()를 호출한다.
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

    // Test fixture only: existing controller tests can continue to verify the preserved policy.
    @Suppress("LongMethod")
    internal fun normalSecurityFilterChainForTest(
        http: HttpSecurity,
        jwtAuthenticationFilter: JwtAuthenticationFilter,
    ): SecurityFilterChain {
        http {
            csrf { disable() }
            sessionManagement { sessionCreationPolicy = SessionCreationPolicy.STATELESS }
            httpBasic { disable() }
            formLogin { disable() }
            authorizeHttpRequests { applyNormalSecurityRules() }
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

// ⚠️ TEMPORARY: 이번 개발 단계에서는 securityFilterChain이 이 Method를 호출하지 않는다(대신 전역
// permitAll, SecurityConfig Class KDoc 참고). Domain별 세부 Role/인증 Rule을 그대로 보존해 두는
// 목적뿐이다 -- Frontend 연동이 끝나면 securityFilterChain의 `authorize(anyRequest, permitAll)`을
// 지우고 `applyNormalSecurityRules()` 호출로 되돌리면 즉시 복구된다. Class 안에 두지 않고 File
// 최상위 Extension Function으로 둔 이유는 detekt `TooManyFunctions`를 피하면서도 이 Method 자체가
// SecurityConfig의 어떤 주입 필드도 쓰지 않는 순수 DSL 선언이기 때문이다.
@Suppress("LongMethod", "unused")
private fun AuthorizeHttpRequestsDsl.applyNormalSecurityRules() {
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
    // 맞춤 공고 추천 조회와 관심 없음(추천 제외)은 학생 전용 기능이라 STUDENT Role을
    // 요구한다(Notion 계약 정합성, Issue #155). 아래 "/api/v1/me/**" 규칙(인증만
    // 요구)에 가려지지 않도록 더 구체적인 경로를 먼저 선언한다 -- 위 Form 규칙과 같은
    // 이유. 요청자 본인 범위로 한정하는 것은 Role로 알 수 없어 RecommendationService가
    // memberId를 인증 Principal에서만 받아 처리한다.
    authorize("/api/v1/me/job-recommendations", hasRole("STUDENT"))
    authorize("/api/v1/me/recommendation-exclusions", hasRole("STUDENT"))
    authorize("/api/v1/me/recommendation-exclusions/**", hasRole("STUDENT"))
    // 내 문의 목록(요구사항 §13)도 학생·교사·개발자 모두 인증만 있으면 접근할 수
    // 있어 값 자체는 아래 "/api/v1/me/**" 규칙과 같다. 그래도 명시적으로 선언한다 --
    // 그 규칙에 암묵적으로 기대면 나중에 "/api/v1/me/**" 기본값이 바뀔 때(예: 다른
    // 하위 경로에 Role 제한이 생기며 그 파급 범위를 좁히려 규칙이 세분화되는 경우)
    // Inquiry가 조용히 영향받는다. 하위 경로가 없는 단일 Endpoint라 "/**" 형태는
    // 추가하지 않는다.
    authorize("/api/v1/me/inquiries", authenticated)
    authorize("/api/v1/me/**", authenticated)
    // 교직원 가입 승인·거절(승인 대기 교직원 승인/거절)은 개발자만 접근한다(Issue #99).
    // 더 구체적인 admin 경로를 먼저 선언해야 아래 회원 조회 규칙에 가려지지 않는다. (경로
    // Prefix가 /api/v1/admin/으로 /api/v1/members와 겹치지 않지만, admin 규칙을 먼저
    // 선언하는 순서 관례를 유지한다.)
    authorize("/api/v1/admin/members", hasRole("DEVELOPER"))
    authorize("/api/v1/admin/members/**", hasRole("DEVELOPER"))
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
    // 지원서 초안 조회·임시저장은 인증만 요구한다(소유자 본인 검증은 Role로 알 수
    // 없어 JobApplicationService가 별도로 수행한다, Application Epic #75 Issue #78).
    authorize("/api/v1/job-applications", authenticated)
    authorize("/api/v1/job-applications/**", authenticated)
    // 교사·개발자용 지원서 조회·검토(ALLOW_EDIT/REQUEST_REVISION/APPROVE/REJECT)는
    // 역할까지만 검증한다(Application Epic #75 Issue #125). 조회는 담당 공고 여부와
    // 무관히 모든 교사·개발자가 가능하고, 상태 변경(담당자만)은 JobApplicationAdminService가
    // 별도로 판단한다.
    authorize("/api/v1/admin/job-applications", hasAnyRole("TEACHER", "DEVELOPER"))
    authorize("/api/v1/admin/job-applications/**", hasAnyRole("TEACHER", "DEVELOPER"))
    // 프로그램 관리(등록·수정·상태 변경)는 교사와 개발자가 사용한다(Program 도메인
    // 전체 개발 요구사항 3절). 등록자·담당 교사 본인만 수정할 수 있는지는 Role만으로
    // 알 수 없어 ProgramService가 별도로 수행한다. 더 구체적인 admin 경로를 먼저
    // 선언해야 아래 조회 규칙에 가려지지 않는다.
    authorize("/api/v1/admin/programs", hasAnyRole("TEACHER", "DEVELOPER"))
    authorize("/api/v1/admin/programs/**", hasAnyRole("TEACHER", "DEVELOPER"))
    // 프로그램 신청·취소(원본 요구사항 문서 11절 권한: STUDENT)는 Role 자체가
    // 학생으로 고정되므로 여기서 STUDENT Role을 요구한다. 재학 여부(NOT_ENROLLED)는
    // Role이 아닌 학적 상태라 이것과 별개로 ProgramService가 추가로 판단한다. 더
    // 구체적인 경로를 먼저 선언해야 아래 조회 규칙에 가려지지 않는다.
    authorize(HttpMethod.POST, "/api/v1/programs/*/application-actions", hasRole("STUDENT"))
    // 프로그램 목록·상세 조회는 학생·교사·개발자 모두 접근할 수 있으므로 인증만
    // 요구한다.
    authorize("/api/v1/programs", authenticated)
    authorize("/api/v1/programs/**", authenticated)
    // 인앱 알림은 항상 요청자 본인의 알림만 다루므로 Role 구분 없이 인증만 요구한다
    // (Notification Core, docs/Notification/notification-core-plan.md). 본인 소유
    // 검증은 Role로 알 수 없어 NotificationService가 별도로 수행한다.
    authorize("/api/v1/notifications", authenticated)
    authorize("/api/v1/notifications/**", authenticated)
    // 파일 업로드·다운로드는 학생·교사·개발자가 모두 사용하므로 Role을 구분하지 않고
    // 인증만 요구한다(File 도메인 요구사항 §5/§15). 로그인만으로 모든 파일을 받을 수
    // 있다는 뜻은 아니다 -- 파일별 소유권과 대상 리소스 접근 권한은 Role로 알 수 없어
    // File 도메인의 Service 계층이 별도로 판정한다(§16).
    authorize("/api/v1/files", authenticated)
    authorize("/api/v1/files/**", authenticated)
    // 문의 관리(전체 목록·검색, 담당자 지정·해제, 상태 변경, 답변 등록)는 개발자만
    // 접근한다(요구사항 §51 권한 Matrix). Program에서 넓은 패턴이 먼저 선언되어
    // 학생 전용 API가 뚫렸던 사고(원본 실행 프롬프트 §3)를 반복하지 않도록, 구체적인
    // 하위 경로(.../answers, .../status, .../assignee)를 목록 조회·admin catch-all
    // 보다 먼저 선언한다. POST .../answers는 Phase 4에서 Controller를 추가하며, 그때
    // 가서 이 순서를 다시 손대지 않도록 지금 함께 선언해 둔다(현재는 대응하는
    // Controller가 없어 이 규칙에 실제로 도달하는 요청이 없다).
    authorize(HttpMethod.POST, "/api/v1/admin/inquiries/*/answers", hasRole("DEVELOPER"))
    authorize(HttpMethod.PATCH, "/api/v1/admin/inquiries/*/status", hasRole("DEVELOPER"))
    authorize(HttpMethod.PATCH, "/api/v1/admin/inquiries/*/assignee", hasRole("DEVELOPER"))
    authorize(HttpMethod.GET, "/api/v1/admin/inquiries", hasRole("DEVELOPER"))
    authorize("/api/v1/admin/inquiries/**", hasRole("DEVELOPER"))
    // 문의 등록·상세 조회는 학생·교사·개발자 모두 접근할 수 있으므로 인증만 요구한다.
    // 상세 조회의 본인 소유권 검증(다른 사용자 문의 차단, 개발자는 예외)은 Role로 알
    // 수 없어 InquiryService가 별도로 수행한다. "/api/v1/admin/inquiries"는 이 Prefix와
    // 겹치지 않지만, 위 admin 규칙을 이 규칙보다 먼저 선언하는 순서 관례를 유지한다.
    // "/api/v1/me/inquiries"는 기존 "/api/v1/me/**" 규칙(위)이 이미 포함하므로 별도로
    // 추가하지 않는다.
    authorize("/api/v1/inquiries", authenticated)
    authorize("/api/v1/inquiries/**", authenticated)
    // 추천 기능 ON/OFF 설정(Recommendation R3, Issue #152)도 학생 전용이라 STUDENT
    // Role을 요구한다. 조회·관심 없음은 위 "/api/v1/me/job-recommendations",
    // "/api/v1/me/recommendation-exclusions**"로 이미 다룬다(Notion 계약 정합성,
    // Issue #155) -- "settings"는 대응하는 Notion Endpoint를 찾지 못해 기존 경로를
    // 유지했다.
    authorize("/api/v1/recommendations/settings", hasRole("STUDENT"))
    authorize(anyRequest, permitAll)
}
