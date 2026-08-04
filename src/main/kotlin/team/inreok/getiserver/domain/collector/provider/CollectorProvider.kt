package team.inreok.getiserver.domain.collector.provider

import team.inreok.getiserver.domain.collector.entity.type.JobSourceCode
import java.time.LocalDateTime

/**
 * 외부 채용 공고 수집원을 확장하기 위한 SPI다. 실제 MMA/JOB_ALIO 등 HTTP Adapter는 이번 Issue의
 * 범위가 아니며(API Key 미준비), 이 Interface만 구현하면 향후 Provider Registry가 자동으로
 * 인식한다. Test 전용 Fake Provider는 `src/test`에만 두고 Production Source Set에는 실제
 * 동작하는 구현체가 없는 상태를 유지한다(Provider가 하나도 없어도 애플리케이션은 정상 기동한다).
 */
interface CollectorProvider {
    val sourceCode: JobSourceCode

    /** 이 Provider가 실제로 외부 API를 호출할 준비(인증 등)가 되어 있는지. Secret 원문은 노출하지 않는다. */
    fun isConfigured(): Boolean

    /**
     * 외부 공고를 수집해 정규화된 결과를 반환한다. Provider가 스스로 복구할 수 없는 오류는
     * [CollectorProviderException]의 하위 타입으로 던지고, 개별 공고 단위 파싱 실패는 예외를
     * 던지지 않고 [CollectorCollectionResult.errors]에 담아 다른 공고 처리를 막지 않는다.
     */
    fun collect(context: CollectorCollectionContext): CollectorCollectionResult
}

/** Provider 호출에 필요한 최소 실행 Context. Provider 전용 인증 Parameter는 여기 담지 않는다. */
data class CollectorCollectionContext(
    val requestedAt: LocalDateTime,
    val since: LocalDateTime?,
)

/** 개별 공고 단위 처리 실패(파싱·정규화 실패 등). Provider 전체 실행 실패는 [CollectorProviderException]을 사용한다. */
data class CollectorItemError(
    val externalJobId: String?,
    val code: String,
    val message: String,
)

/** [requestCount]는 이번 collect() 호출 한 번이 실제로 발생시킨 외부 HTTP 요청 수다(Discord 실행 요약 알림 표시용). */
data class CollectorCollectionResult(
    val jobs: List<NormalizedCollectedJob>,
    val errors: List<CollectorItemError> = emptyList(),
    val requestCount: Int = 0,
)
