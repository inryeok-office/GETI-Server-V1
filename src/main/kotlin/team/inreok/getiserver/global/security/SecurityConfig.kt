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
 * `/api/v1/me/` 이하 모든 경로(Issue #50, 내 프로필/전공/기술스택)만 인증을 요구한다. 다른
 * Domain(회원 검색, 전공/기술스택 메타데이터 조회 등)은 아직 Spring Security와 연동되지 않아
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
                authorize("/api/v1/auth/session", authenticated)
                authorize("/api/v1/auth/logout", authenticated)
                authorize("/api/v1/me/**", authenticated)
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
