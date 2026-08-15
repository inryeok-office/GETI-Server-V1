package team.inreok.getiserver.domain.ai.provider

/**
 * Provider 호출 전체를 실패시키는 오류의 공통 기반이다([team.inreok.getiserver.domain.collector.provider.CollectorProviderException]과
 * 같은 목적·형태). [retryable]은 오류 분류 정보로만 남긴다 — 실제 OpenAI Credit을 쓰는 호출이라
 * 자동 재시도는 구현하지 않는다(GETI Notion AI Analysis Phase 1 "Retry" 절, 비용 절감 우선).
 * [message]에는 API Key, Authorization Header, 요청/응답 원문을 담지 않는다.
 */
sealed class AiAnalysisProviderException(
    val code: String,
    val retryable: Boolean,
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause) {
    class NotConfigured(
        message: String = "OpenAI API Key가 설정되지 않았습니다.",
    ) : AiAnalysisProviderException("AI_PROVIDER_NOT_CONFIGURED", retryable = false, message = message)

    class AuthenticationFailed(
        message: String = "OpenAI 인증에 실패했습니다.",
        cause: Throwable? = null,
    ) : AiAnalysisProviderException("AI_PROVIDER_AUTH_FAILED", retryable = false, message = message, cause = cause)

    class RateLimited(
        message: String = "OpenAI 호출 제한을 초과했습니다.",
        cause: Throwable? = null,
    ) : AiAnalysisProviderException("AI_PROVIDER_RATE_LIMITED", retryable = true, message = message, cause = cause)

    class Timeout(
        message: String = "OpenAI 응답이 시간 내에 도착하지 않았습니다.",
        cause: Throwable? = null,
    ) : AiAnalysisProviderException("AI_PROVIDER_TIMEOUT", retryable = true, message = message, cause = cause)

    class NetworkError(
        message: String = "OpenAI 호출 중 네트워크 오류가 발생했습니다.",
        cause: Throwable? = null,
    ) : AiAnalysisProviderException("AI_PROVIDER_NETWORK_ERROR", retryable = true, message = message, cause = cause)

    class ServerError(
        message: String = "OpenAI가 서버 오류를 반환했습니다.",
        cause: Throwable? = null,
    ) : AiAnalysisProviderException("AI_PROVIDER_SERVER_ERROR", retryable = true, message = message, cause = cause)

    class ClientError(
        message: String = "OpenAI가 잘못된 요청 오류를 반환했습니다.",
        cause: Throwable? = null,
    ) : AiAnalysisProviderException("AI_PROVIDER_CLIENT_ERROR", retryable = false, message = message, cause = cause)

    class ResponseInvalid(
        message: String = "OpenAI 응답을 해석할 수 없습니다.",
        cause: Throwable? = null,
    ) : AiAnalysisProviderException("AI_PROVIDER_RESPONSE_INVALID", retryable = false, message = message, cause = cause)
}
