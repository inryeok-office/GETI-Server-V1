package team.inreok.getiserver.domain.search.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Search 도메인(Issue #69) 전용 설정값이다. Elasticsearch 접속 정보(`spring.elasticsearch.*`)는
 * Spring Boot가 기본 제공하는 Property를 그대로 쓰고, 여기서는 GETI가 추가로 필요한 값만 다룬다.
 */
@ConfigurationProperties(prefix = "app.search")
data class SearchProperties(
    /** 검색 Query와 애플리케이션이 항상 참조하는 고정 이름(Alias). 실제 물리 Index는 재색인마다 새로 만들고 이 Alias를 전환한다. */
    val indexAlias: String = "jobs-search",
    /** 재색인이 새로 만드는 물리 Index 이름의 접두사(`{prefix}-{timestamp}`). */
    val indexPrefix: String = "jobs-search",
    /** 전체 재색인 1회 조회당 Postgres에서 읽어오는 공고 수(Keyset Pagination Page Size). */
    val reindexBatchSize: Int = 500,
    /** 색인 실패 재시도 최대 횟수. 초과하면 영구 실패로 표시하고 더 이상 재시도하지 않는다. */
    val indexRetryMaxAttempts: Int = 5,
    /** 색인 실패 재시도 기본 대기(초). 시도마다 `attemptCount`제곱으로 지수 증가한다. */
    val indexRetryBackoffSeconds: Long = 30,
)
