package team.inreok.getiserver.domain.notification.service.impl

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
import org.springframework.web.client.body
import team.inreok.getiserver.domain.notification.config.FcmHttpConfig
import team.inreok.getiserver.domain.notification.config.FcmProperties
import team.inreok.getiserver.domain.notification.service.PushProvider
import team.inreok.getiserver.domain.notification.service.PushSendCommand
import team.inreok.getiserver.domain.notification.service.PushSendResult

/**
 * FCM HTTP v1 API(`POST /v1/projects/{projectId}/messages:send`)를 호출하는 Adapter다(Issue
 * #190). iOS/Android 모두 이 Provider 하나로 처리한다([PushProvider] KDoc의 근거 참고).
 *
 * ## 오류 분류
 *
 * FCM v1은 실패를 `{ error: { status, details: [{ errorCode }] } }` 형태로 내려준다(Google
 * Cloud 표준 Error Model + `FcmError.errorCode`). `UNREGISTERED`/`SENDER_ID_MISMATCH`는 Token
 * 자체가 무효하다는 뜻이라 재시도하지 않고 [PushSendResult.Failure.invalidToken]을 true로
 * 돌려준다(호출부가 `NotificationDevice`를 제거한다). `UNAVAILABLE`/`INTERNAL`/`QUOTA_EXCEEDED`는
 * 일시적 오류로 보고 재시도 가능으로 분류한다. 그 외(예: `INVALID_ARGUMENT`, `UNAUTHENTICATED`)는
 * 재시도해도 결과가 같을 오류라 재시도하지 않는다 -- 원인을 모른 채 반복 호출하지 않는다는 원칙은
 * `HttpDiscordBotClient`와 같다.
 *
 * ## 로그
 *
 * Access Token, Service Account Private Key, 전체 Request/Response Body 원문을 로그에 남기지
 * 않는다.
 */
@Component
class FcmPushProvider(
    @param:Qualifier(FcmHttpConfig.FCM_REST_CLIENT) private val restClient: RestClient,
    private val properties: FcmProperties,
    private val accessTokenProvider: FcmAccessTokenProvider,
) : PushProvider {
    private val log = LoggerFactory.getLogger(FcmPushProvider::class.java)

    @Suppress("ReturnCount")
    override fun send(command: PushSendCommand): PushSendResult {
        if (!properties.isConfigured()) {
            return notConfigured("FCM이 설정되지 않았습니다.")
        }
        val projectId = accessTokenProvider.projectId() ?: return notConfigured("FCM projectId를 확인할 수 없습니다.")
        val accessToken =
            accessTokenProvider.currentAccessToken()
                ?: return PushSendResult.Failure(
                    TOKEN_UNAVAILABLE,
                    retryable = true,
                    invalidToken = false,
                    message = "FCM Access Token을 발급받지 못했습니다.",
                )

        return try {
            val messageId =
                restClient
                    .post()
                    .uri("$FCM_BASE_URL/$projectId/messages:send")
                    .header("Authorization", "Bearer $accessToken")
                    .body(mapOf("message" to command.toFcmMessage()))
                    .retrieve()
                    .body<Map<*, *>>()
                    ?.get("name") as? String
            PushSendResult.Success(messageId)
        } catch (ex: RestClientResponseException) {
            toFailure(ex)
        } catch (ex: RestClientException) {
            log.warn("FCM 호출 실패(네트워크 또는 응답 처리 오류)", ex)
            PushSendResult.Failure(
                FCM_UNREACHABLE,
                retryable = true,
                invalidToken = false,
                message = "FCM에 연결하지 못했습니다.",
            )
        }
    }

    private fun PushSendCommand.toFcmMessage(): Map<String, Any?> =
        mapOf(
            "token" to token,
            "notification" to mapOf("title" to title, "body" to body),
            "data" to data,
        )

    private fun notConfigured(message: String): PushSendResult.Failure =
        PushSendResult.Failure(NOT_CONFIGURED, retryable = true, invalidToken = false, message = message)

    @Suppress("ReturnCount")
    private fun toFailure(ex: RestClientResponseException): PushSendResult.Failure {
        val body = runCatching { ex.getResponseBodyAs(Map::class.java) }.getOrNull()
        val error = body?.get("error") as? Map<*, *>
        val status = (error?.get("status") as? String)?.takeIf { it.isNotBlank() }
        val fcmErrorCode = extractFcmErrorCode(error)
        val message = (error?.get("message") as? String)?.take(MAX_ERROR_MESSAGE_LENGTH)

        if (status == null) {
            log.warn("FCM이 해석할 수 없는 오류 응답을 반환했습니다(status={})", ex.statusCode.value())
            return PushSendResult.Failure(
                MALFORMED_RESPONSE,
                retryable = true,
                invalidToken = false,
                message = "FCM 오류 응답을 해석하지 못했습니다.",
            )
        }

        val invalidToken = fcmErrorCode in INVALID_TOKEN_ERROR_CODES
        return PushSendResult.Failure(
            code = fcmErrorCode ?: status,
            retryable = !invalidToken && status in RETRYABLE_STATUSES,
            invalidToken = invalidToken,
            message = message,
        )
    }

    private fun extractFcmErrorCode(error: Map<*, *>?): String? {
        val details = error?.get("details") as? List<*> ?: return null
        return details
            .filterIsInstance<Map<*, *>>()
            .firstNotNullOfOrNull { it["errorCode"] as? String }
    }

    private companion object {
        const val FCM_BASE_URL = "https://fcm.googleapis.com/v1/projects"

        const val NOT_CONFIGURED = "NOT_CONFIGURED"
        const val TOKEN_UNAVAILABLE = "TOKEN_UNAVAILABLE"
        const val FCM_UNREACHABLE = "FCM_UNREACHABLE"
        const val MALFORMED_RESPONSE = "MALFORMED_RESPONSE"

        const val MAX_ERROR_MESSAGE_LENGTH = 1000

        // FCM v1 FcmError.errorCode 중 Token 자체가 무효하다는 뜻인 값들이다. INVALID_ARGUMENT는
        // Token 형식 오류로도 발생할 수 있지만 다른 요청 필드 오류로도 발생할 수 있어(우리 쪽
        // Payload 구성 오류 가능성) 여기 포함하지 않는다 -- 잘못 포함하면 우리 버그로 정상 기기
        // 등록이 삭제될 위험이 있다. 재시도하지 않을 뿐 기기는 남긴다.
        val INVALID_TOKEN_ERROR_CODES = setOf("UNREGISTERED", "SENDER_ID_MISMATCH")

        // Google Cloud 표준 Error Model의 status 값 중 일시적 오류로 보는 것들이다.
        val RETRYABLE_STATUSES = setOf("UNAVAILABLE", "INTERNAL", "RESOURCE_EXHAUSTED", "DEADLINE_EXCEEDED")
    }
}
