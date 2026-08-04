package team.inreok.getiserver.domain.collector.scheduler

import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component

/**
 * `COLLECTOR_SEED_ENABLED=true`가 운영(prod/production) Profile에서 실수로 켜지면 개발용
 * Fixture 공고가 실제 서비스에 노출될 수 있다(Issue #62). Bean 생성 시점(Context Refresh 중)에
 * 검증해 위반하면 애플리케이션 기동 자체를 실패시킨다(Fail-Fast, application-prod.yaml의
 * 필수 환경변수 정책과 동일한 방식).
 */
@Component
class CollectorSeedProdGuard(
    private val environment: Environment,
    @param:Value("\${app.collector.seed.enabled:false}") private val seedEnabled: Boolean,
) {
    @PostConstruct
    fun verify() {
        val activeProfiles = environment.activeProfiles.toSet()
        val isProd =
            activeProfiles.any {
                it.equals("prod", ignoreCase = true) ||
                    it.equals("production", ignoreCase = true)
            }
        check(!(seedEnabled && isProd)) {
            "COLLECTOR_SEED_ENABLED=true는 prod/production Profile에서 사용할 수 없습니다(activeProfiles=$activeProfiles)."
        }
    }
}
