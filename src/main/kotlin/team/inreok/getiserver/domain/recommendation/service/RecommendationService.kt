package team.inreok.getiserver.domain.recommendation.service

import org.springframework.data.domain.Pageable
import team.inreok.getiserver.domain.recommendation.dto.RecommendationExclusionListResponse
import team.inreok.getiserver.domain.recommendation.dto.RecommendationExclusionResponse
import team.inreok.getiserver.domain.recommendation.dto.RecommendationJobResponse
import team.inreok.getiserver.domain.recommendation.dto.RecommendationListResponse
import team.inreok.getiserver.domain.recommendation.dto.RecommendationSettingResponse
import team.inreok.getiserver.domain.recommendation.entity.type.ExclusionType
import team.inreok.getiserver.domain.recommendation.entity.type.SuitabilityLevel

/**
 * Recommendation 조회·사용자 설정 Use Case다(R3 Issue #152, Notion 계약 정합성 Issue #155).
 * [RecommendationGenerationService](R2 Core, 계산·저장)와 책임을 분리한다 -- 이 Service는 R2가
 * 만든 결과를 읽고 사용자 설정(ON/OFF, 관심 없음)을 바꿀 뿐 Score를 다시 계산하지 않는다.
 */
interface RecommendationService {
    /**
     * 요청자 본인의 오늘자 추천 결과를 조회한다. 추천 기능이 꺼져 있으면(설정한 적 없는 회원
     * 포함, default=false) 항상 `DISABLED` + 빈 목록을 반환한다. 오류로 취급하지 않는다(200).
     * [suitabilityLevel]이 null이 아니면 해당 등급만 필터링한다.
     */
    fun getMyRecommendations(
        memberId: Long,
        suitabilityLevel: SuitabilityLevel?,
        pageable: Pageable,
    ): RecommendationListResponse

    /** 추천 기능 ON/OFF를 저장한다. Row가 없으면 새로 만들고, 있으면 갱신한다(Lazy Create).
     * 재학 중(`AcademicStatus.ENROLLED`)이 아닌 회원이면
     * [team.inreok.getiserver.domain.recommendation.exception.RecommendationNotEnrolledException]을
     * 던진다(Notion 기능명세 "졸업생에게는 추천 설정을 제공하지 않는다"). */
    fun updateSetting(
        memberId: Long,
        enabled: Boolean,
    ): RecommendationSettingResponse

    /**
     * [jobId]를 [exclusionType]으로 관심 없음 등록한다(Notion
     * `POST /api/v1/me/recommendation-exclusions`). 같은 공고에 이미 관심 없음이 설정돼 있으면
     * [team.inreok.getiserver.domain.recommendation.exception.RecommendationExclusionAlreadyExistsException]을
     * 던진다(409). 요청자의 오늘자 해당 Job Recommendation을 같은 Transaction에서 함께 제거해
     * 다음 조회에 바로 반영한다. [jobId]가 존재하지 않거나 삭제됐으면
     * [team.inreok.getiserver.domain.recommendation.exception.RecommendationJobNotFoundException]을
     * 던진다.
     */
    fun registerExclusion(
        memberId: Long,
        jobId: Long,
        exclusionType: ExclusionType,
    ): RecommendationExclusionResponse

    /** 요청자 본인의 관심 없음 목록을 조회한다(Notion `GET /api/v1/me/recommendation-exclusions`).
     * [exclusionType]이 null이면 THIS_JOB/SIMILAR_JOBS 전체를 대상으로 한다. */
    fun listExclusions(
        memberId: Long,
        exclusionType: ExclusionType?,
        pageable: Pageable,
    ): RecommendationExclusionListResponse

    /**
     * [exclusionId]의 관심 없음 설정을 해제한다(Notion
     * `DELETE /api/v1/me/recommendation-exclusions/{exclusionId}`). 대상이 없거나 요청자 본인
     * 소유가 아니면
     * [team.inreok.getiserver.domain.recommendation.exception.RecommendationExclusionNotFoundException]을
     * 던진다(404). 해제만으로 과거 Recommendation을 복원하거나 새로 생성하지 않는다 -- 다음 R4
     * Daily Scheduler 실행에서 다시 후보가 될 수 있을 뿐이다.
     */
    fun removeExclusion(
        memberId: Long,
        exclusionId: Long,
    )

    /**
     * [jobId]를 북마크로 등록한다(Issue #170). 같은 공고를 이미 북마크했으면
     * [team.inreok.getiserver.domain.recommendation.exception.RecommendationBookmarkAlreadyExistsException]을
     * 던진다(409, 관심 없음 등록과 동일한 정책 -- 멱등이 아니다). 관심 없음(exclusion) 설정이
     * 이미 있는 Row라도 그 값은 그대로 두고 `bookmarked`만 갱신한다. 요청자의 오늘자 해당 Job
     * Recommendation을 같은 Transaction에서 함께 제거해 다음 조회에 바로 반영한다(북마크한 공고는
     * 추천 후보에서 제외한다는 기존 Hard Filter 정책, `RecommendationCandidateFilter`
     * `ALREADY_BOOKMARKED` 참고, 즉시 반영은 R6 SIMILAR_JOBS와 동일한 원칙). [jobId]가
     * 존재하지 않거나 삭제됐으면
     * [team.inreok.getiserver.domain.recommendation.exception.RecommendationJobNotFoundException]을
     * 던진다.
     */
    fun registerBookmark(
        memberId: Long,
        jobId: Long,
    ): RecommendationJobResponse

    /**
     * [jobId]의 북마크를 해제한다(Issue #170). 북마크하지 않았으면(Row 자체가 없거나
     * `bookmarked=false`)
     * [team.inreok.getiserver.domain.recommendation.exception.RecommendationBookmarkNotFoundException]을
     * 던진다(404). 그 Row에 관심 없음(exclusion) 설정이 남아 있으면 Row 자체는 지우지 않고
     * `bookmarked`만 갱신한다(`removeExclusion`과 대칭인 Row Lifecycle). 해제만으로 과거
     * Recommendation을 복원하거나 새로 생성하지 않는다 -- 다음 R4 Daily Scheduler 실행에서 다시
     * 후보로 평가될 수 있을 뿐이다.
     */
    fun removeBookmark(
        memberId: Long,
        jobId: Long,
    )
}
