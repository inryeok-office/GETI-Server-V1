package team.inreok.getiserver.domain.recommendation.scheduler

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import team.inreok.getiserver.domain.member.query.RecommendationAudienceQueryPort
import team.inreok.getiserver.domain.recommendation.repository.RecommendationPreferenceRepository
import team.inreok.getiserver.domain.recommendation.service.RecommendationGenerationRunner

/**
 * 추천을 활성화한 재학생을 대상으로 일일 추천을 자동 생성한다(Recommendation R4, Issue #160).
 * `ProgramCloseScheduler`/`SearchIndexRetryScheduler`와 같은 구조다 -- 이 Class는 대상 memberId를
 * 조회해 순회만 하고, 실제 계산·상태 기록은 `RecommendationGenerationRunner`에 위임한다("언제
 * 도는가"만 알고 "무엇을 하는가"는 모른다).
 *
 * 실행 시각(`cron`)이 GETI Notion에 아직 확정되지 않았다(DECISION_REQUIRED, Issue #160 PR 본문
 * 참고). 근거 없는 기본값을 넣는 대신 Spring `@Scheduled`가 지원하는 비활성 Sentinel(`"-"`)을
 * 기본값으로 둔다 -- 운영자가 `RECOMMENDATION_GENERATION_SCHEDULER_CRON`을 명시적으로 설정하기
 * 전까지는 이 Method 자체가 등록되지 않아 자동 실행되지 않는다.
 *
 * 서버가 한 대만 배포된 현재 전제에서는 여러 Instance 동시 실행을 막는 분산 Lock(ShedLock 등)이
 * 없다(`CollectorScheduler`와 동일한 근거). `RecommendationGenerationServiceImpl.generateForMember`가
 * 회원의 오늘자 추천을 delete-then-insert로 통째로 교체하므로, 같은 회원이 중복 처리돼도 결과가
 * 깨지지 않고 마지막 실행 결과로 수렴한다(멱등).
 *
 * 한 회원 처리 실패가 나머지 대상 처리를 막지 않도록 `runCatching`으로 건별 격리한다
 * (`SearchIndexRetryScheduler.retryDueFailures`와 동일한 방식).
 */
@Component
class RecommendationGenerationScheduler(
    private val recommendationPreferenceRepository: RecommendationPreferenceRepository,
    private val recommendationAudienceQueryPort: RecommendationAudienceQueryPort,
    private val recommendationGenerationRunner: RecommendationGenerationRunner,
) {
    private val log = LoggerFactory.getLogger(RecommendationGenerationScheduler::class.java)

    @Scheduled(cron = "\${app.recommendation.generation-scheduler.cron:-}")
    fun generateDailyRecommendations() {
        val enabledMemberIds = recommendationPreferenceRepository.findMemberIdsByEnabledTrue()
        val targetMemberIds = recommendationAudienceQueryPort.filterEligibleStudentIds(enabledMemberIds)
        targetMemberIds.forEach { memberId ->
            runCatching { recommendationGenerationRunner.generateForMember(memberId) }
                .onFailure { ex -> log.error("일일 추천 생성 중 처리되지 않은 오류(memberId={})", memberId, ex) }
        }
    }
}
