package team.inreok.getiserver.domain.recommendation.service

import team.inreok.getiserver.domain.recommendation.dto.RecommendationListResponse
import team.inreok.getiserver.domain.recommendation.dto.RecommendationSettingResponse

/**
 * Recommendation R3(Issue #152)의 조회·사용자 설정 Use Case다. [RecommendationGenerationService]
 * (R2 Core, 계산·저장)와 책임을 분리한다 -- 이 Service는 R2가 만든 결과를 읽고 사용자 설정(ON/OFF,
 * 관심 없음)을 바꿀 뿐 Score를 다시 계산하지 않는다.
 */
interface RecommendationService {
    /**
     * 요청자 본인의 오늘자 추천 결과를 조회한다. 추천 기능이 꺼져 있으면(설정한 적 없는 회원
     * 포함, default=false) 항상 `DISABLED` + 빈 목록을 반환한다. 오류로 취급하지 않는다(200).
     */
    fun getMyRecommendations(memberId: Long): RecommendationListResponse

    /** 추천 기능 ON/OFF를 저장한다. Row가 없으면 새로 만들고, 있으면 갱신한다(Lazy Create). */
    fun updateSetting(
        memberId: Long,
        enabled: Boolean,
    ): RecommendationSettingResponse

    /**
     * [jobId]를 관심 없음(`THIS_JOB`)으로 설정한다. 멱등이다 -- 이미 관심 없음 상태여도 오류 없이
     * 성공한다. 요청자의 오늘자 해당 Job Recommendation을 같은 Transaction에서 함께 제거해
     * 다음 조회에 바로 반영한다. [jobId]가 존재하지 않거나 삭제됐으면
     * [team.inreok.getiserver.domain.recommendation.exception.RecommendationJobNotFoundException]을
     * 던진다.
     */
    fun markNotInterested(
        memberId: Long,
        jobId: Long,
    )

    /**
     * [jobId]의 관심 없음 설정을 해제한다. 멱등이다 -- 이미 해제된 상태(또는 애초에 설정한 적
     * 없는 상태)여도 오류 없이 성공한다. 해제만으로 과거 Recommendation을 복원하거나 새로
     * 생성하지 않는다 -- 다음 R4 Daily Scheduler 실행에서 다시 후보가 될 수 있을 뿐이다.
     */
    fun removeNotInterested(
        memberId: Long,
        jobId: Long,
    )
}
