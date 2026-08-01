package team.inreok.getiserver.global.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Authorization Header의 Bearer Access Token을 검증해 [SecurityContextHolder]에 인증 정보를 채운다.
 * Token이 없거나 유효하지 않으면 그대로 다음 Filter로 넘긴다 — 인증이 필요한 Endpoint는
 * [SecurityConfig]의 `authorizeHttpRequests`가 미인증 상태를 401로 걸러낸다.
 *
 * `@Component`로 직접 등록하지 않고 [SecurityConfig]의 `@Bean` Method로만 생성한다. `@Component`로
 * 두면 `Filter` 구현체가 `@WebMvcTest` Slice의 기본 포함 대상이라, Security와 무관한 다른 Domain의
 * Slice Test까지 이 Filter(와 의존인 [JwtTokenProvider])를 억지로 구성하려다 실패한다. `SecurityConfig`는
 * 일반 `@Configuration`이라 `@WebMvcTest`가 기본으로 Scan하지 않으므로, 이 방식이면 Security를
 * 명시적으로 `@Import`하지 않는 Slice Test는 영향을 받지 않는다.
 */
class JwtAuthenticationFilter(
    private val jwtTokenProvider: JwtTokenProvider,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        resolveToken(request)
            ?.let(jwtTokenProvider::parseOrNull)
            ?.let { claims ->
                val memberId = JwtTokenProvider.memberIdOf(claims)
                val authorities = JwtTokenProvider.rolesOf(claims).map { SimpleGrantedAuthority("ROLE_$it") }
                SecurityContextHolder.getContext().authentication =
                    UsernamePasswordAuthenticationToken(memberId, null, authorities)
            }
        filterChain.doFilter(request, response)
    }

    private fun resolveToken(request: HttpServletRequest): String? {
        val header = request.getHeader(HttpHeaders.AUTHORIZATION)
        if (header == null || !header.startsWith(BEARER_PREFIX)) return null
        return header.removePrefix(BEARER_PREFIX)
    }

    companion object {
        private const val BEARER_PREFIX = "Bearer "
    }
}
