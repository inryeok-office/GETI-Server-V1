package team.inreok.getiserver.global.web

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

/**
 * 요청마다 `requestId`를 MDC에 남겨 같은 요청의 Log를 하나로 묶고, [ApiResponse]/[ErrorResponse]가
 * 같은 값을 응답 Body와 [REQUEST_ID_HEADER] Header에 포함하도록 한다. Client가 이미
 * [REQUEST_ID_HEADER]로 값을 보냈다면 새로 만들지 않고 그대로 재사용해 여러 Service에 걸친
 * 요청을 하나의 requestId로 추적할 수 있게 한다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class RequestIdFilter : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val requestId = request.getHeader(REQUEST_ID_HEADER)?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        response.setHeader(REQUEST_ID_HEADER, requestId)
        MDC.put(REQUEST_ID_MDC_KEY, requestId)
        try {
            filterChain.doFilter(request, response)
        } finally {
            MDC.remove(REQUEST_ID_MDC_KEY)
        }
    }

    companion object {
        const val REQUEST_ID_HEADER = "X-Request-Id"
        const val REQUEST_ID_MDC_KEY = "requestId"
    }
}
