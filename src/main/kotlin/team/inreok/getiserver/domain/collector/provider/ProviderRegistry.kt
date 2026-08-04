package team.inreok.getiserver.domain.collector.provider

import org.springframework.stereotype.Component
import team.inreok.getiserver.domain.collector.entity.type.JobSourceCode

/**
 * Spring Context에 등록된 [CollectorProvider] Bean을 sourceCode로 조회한다. 이번 Issue에는
 * 실제 Provider 구현체가 하나도 없으므로 `providers`는 항상 비어 있고, `find`는 항상 null을
 * 반환한다 — 애플리케이션은 이 상태에서도 정상 기동해야 한다(Test는 `src/test` 전용 Fake
 * Provider로 등록 동작을 검증한다).
 */
@Component
class ProviderRegistry(
    providers: List<CollectorProvider>,
) {
    private val providersBySourceCode: Map<JobSourceCode, CollectorProvider> = providers.associateBy { it.sourceCode }

    fun find(sourceCode: JobSourceCode): CollectorProvider? = providersBySourceCode[sourceCode]

    /** 실제 구현체가 등록되어 있고 그 구현체가 스스로 설정 완료를 보고할 때만 true다. */
    fun isConfigured(sourceCode: JobSourceCode): Boolean = find(sourceCode)?.isConfigured() ?: false
}
