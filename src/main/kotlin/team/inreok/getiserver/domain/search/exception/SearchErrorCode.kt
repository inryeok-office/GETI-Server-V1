package team.inreok.getiserver.domain.search.exception

import org.springframework.http.HttpStatus
import team.inreok.getiserver.global.error.ErrorCode

/**
 * Search 운영 API가 실제로 처리하는 오류만 정의한다(Issue #69). 색인 동기화 실패는 HTTP
 * 응답이 아니라 `search_index_failures`에 기록되는 비동기 상태라 여기 포함하지 않는다
 * (`CollectorErrorCode`가 `CollectionRunError`를 별도로 다루는 것과 같은 이유).
 */
enum class SearchErrorCode(
    override val status: HttpStatus,
    override val defaultMessage: String,
) : ErrorCode {
    REINDEX_ALREADY_RUNNING(HttpStatus.CONFLICT, "이미 전체 재색인이 진행 중입니다."),
    ;

    override val code: String get() = name
}
