package team.inreok.getiserver.domain.collector.exception

import org.springframework.http.HttpStatus
import team.inreok.getiserver.global.error.ErrorCode

/**
 * Collector 운영 API가 실제로 처리하는 오류만 정의한다. Provider 실행 중 개별 오류
 * (COLLECTOR_PROVIDER_AUTH_FAILED 등)는 HTTP 응답이 아니라 CollectionRunError에 기록되는
 * 값이라 여기 포함하지 않는다(`domain.collector.provider.CollectorProviderException` 참고).
 */
enum class CollectorErrorCode(
    override val status: HttpStatus,
    override val defaultMessage: String,
) : ErrorCode {
    JOB_SOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 수집원을 찾을 수 없습니다."),
    SOURCE_NOT_CONFIGURED(HttpStatus.CONFLICT, "수집원이 아직 설정되지 않았습니다."),
    SOURCE_NOT_APPROVED(HttpStatus.CONFLICT, "수집원이 아직 승인되지 않았습니다."),
    COLLECTOR_ALREADY_RUNNING(HttpStatus.CONFLICT, "해당 수집원의 실행이 이미 진행 중입니다."),
    COLLECTION_RUN_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 수집 실행을 찾을 수 없습니다."),
    ;

    override val code: String get() = name
}
