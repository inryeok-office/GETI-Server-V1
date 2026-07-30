package team.inreok.getiserver.global.web

import org.slf4j.MDC

data class ApiResponse<T>(
    val data: T,
    val requestId: String? = MDC.get(RequestIdFilter.REQUEST_ID_MDC_KEY),
) {
    companion object {
        fun <T> of(data: T): ApiResponse<T> = ApiResponse(data)
    }
}
