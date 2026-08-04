package team.inreok.getiserver.domain.collector.provider

/**
 * Provider 실행 전체를 실패시키는 오류의 공통 기반이다. [retryable]로 재시도 정책을 구분한다
 * (네트워크 오류·5xx는 제한된 횟수로 재시도 가능, 인증 실패·4xx는 즉시 실패). 실제 HTTP 호출이
 * 없는 이번 범위에서는 향후 Adapter가 이 계층에 맞춰 예외를 던질 수 있도록 하는 확장 지점이다.
 * message에는 Secret이나 요청 URL 전체를 담지 않는다.
 */
sealed class CollectorProviderException(
    val code: String,
    val retryable: Boolean,
    message: String,
) : RuntimeException(message) {
    class AuthenticationFailed(
        message: String = "Provider 인증에 실패했습니다.",
    ) : CollectorProviderException("COLLECTOR_PROVIDER_AUTH_FAILED", retryable = false, message = message)

    class RateLimited(
        message: String = "Provider 호출 제한을 초과했습니다.",
    ) : CollectorProviderException("COLLECTOR_PROVIDER_RATE_LIMITED", retryable = false, message = message)

    class Timeout(
        message: String = "Provider 응답이 시간 내에 도착하지 않았습니다.",
    ) : CollectorProviderException("COLLECTOR_PROVIDER_TIMEOUT", retryable = true, message = message)

    class NetworkError(
        message: String = "Provider 호출 중 네트워크 오류가 발생했습니다.",
    ) : CollectorProviderException("COLLECTOR_PROVIDER_NETWORK_ERROR", retryable = true, message = message)

    class ServerError(
        message: String = "Provider가 서버 오류를 반환했습니다.",
    ) : CollectorProviderException("COLLECTOR_PROVIDER_SERVER_ERROR", retryable = true, message = message)

    class ClientError(
        message: String = "Provider가 잘못된 요청 오류를 반환했습니다.",
    ) : CollectorProviderException("COLLECTOR_PROVIDER_CLIENT_ERROR", retryable = false, message = message)

    class ResponseInvalid(
        message: String = "Provider 응답을 해석할 수 없습니다.",
    ) : CollectorProviderException("COLLECTOR_PROVIDER_RESPONSE_INVALID", retryable = false, message = message)
}
