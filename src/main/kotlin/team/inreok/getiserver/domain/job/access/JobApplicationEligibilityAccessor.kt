package team.inreok.getiserver.domain.job.access

import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.modulith.NamedInterface

/**
 * `job`이 소유하고 `application`이 구현하는 SPI다(`JobAiAnalysisAccessor`와 같은 방식 --
 * `docs/architecture/modularity.md` "Domain 간 허용 의존" 참고). 요청자 기준 학생 지원
 * 가능여부·지원 현황을 Job 상세·검색 응답(`application`)에 포함하기 위해 필요하다(Issue #136).
 *
 * 방향을 이렇게 둔 이유: `application`은 이미 `job.query.JobApplicationSnapshotQueryPort`(공고
 * 정보 조회)로 `job`에 의존한다. 만약 `job`이 거꾸로 `application.query`에 의존하면
 * `application -> job -> application` 순환 의존이 생겨 `ModularityTest`(`modules.verify()`)가
 * 실패한다. 대신 `job`이 이 Interface(자신이 소유한 타입)를 정의하고, `application`이 구현체를
 * Bean으로 등록한다 -- `job`은 `application`의 어떤 Package도 참조하지 않는다.
 *
 * `search`(`JobSearchServiceImpl`)도 같은 Interface를 그대로 소비한다 -- `search`는 이미 공고
 * 색인·동기화 때문에 `job`에 의존하므로(Issue #69) 새 순환을 만들지 않는다. 같은 계산을 두 곳에
 * 다른 계약으로 중복 정의하지 않기 위해 하나의 SPI를 공유한다.
 */
@NamedInterface
interface JobApplicationEligibilityAccessor {
    /**
     * [jobIds]에 대해 [requesterMemberId] 기준 지원 가능 여부·지원 현황을 한 번에 계산한다(N+1
     * 방지, `JobNotificationTargetQueryPort.findAllByIds`와 같은 이유 -- 검색 결과 한 Page가 최대
     * 100건). 결과 Map에는 [jobIds]의 모든 항목이 그대로 담긴다.
     *
     * [requesterMemberId]가 학생이 아니거나(교사·개발자, 재학 상태 없음) 존재하지 않으면 별도
     * 분기 없이 `computeEligibilityReason`의 `member == null` 판정(NOT_ENROLLED)을 그대로
     * 따른다 -- 미인증 요청은 이 Interface에 도달하지 않는다. `SecurityConfig`가 `/api/v1/jobs`
     * 하위 전체 경로(상세·검색 둘 다 포함)를 `authenticated`로 지정해, 요구사항 원문에 없던 이
     * 가정을 PR #151 코드리뷰 시점에 실제 설정으로 확인했다(Issue #136 DECISION_REQUIRED, 확인
     * 완료).
     */
    fun findAllByJobIds(
        jobIds: Set<Long>,
        requesterMemberId: Long,
    ): Map<Long, JobApplicationEligibilityAccessSnapshot>
}

/**
 * `JobAiAnalysisAccessSnapshot`과 같은 방식으로 Swagger Annotation을 직접 붙여
 * `JobDetailResponse.application`/`JobSummaryResponse.application`에 그대로 재사용한다.
 * [eligibilityReason]은 `application.entity.type.JobApplicationEligibilityReason`의, [applicationStatus]는
 * `application.entity.type.JobApplicationStatus`의, [availableActions]는
 * `application.dto.JobApplicationAction`의 각 Enum 이름 문자열이다 -- `job`/`search`가
 * `application`의 Enum 타입을 직접 참조하면 다시 순환 의존이 생기므로 String으로 노출한다
 * (`JobApplicationJobSnapshot.status`와 같은 이유).
 */
@NamedInterface
@Schema(description = "요청자 기준 학생 지원 가능 여부·지원 현황. 서버가 계산한 값이며 클라이언트는 직접 계산하지 않는다.")
data class JobApplicationEligibilityAccessSnapshot(
    @param:Schema(description = "지원 가능 여부", example = "true")
    val canApply: Boolean,
    @param:Schema(description = "지원 불가 사유(가능하면 AVAILABLE)", example = "AVAILABLE")
    val eligibilityReason: String,
    @param:Schema(description = "사유에 대응하는 안내 문구", example = "지원 가능한 공고입니다.")
    val eligibilityMessage: String,
    @param:Schema(description = "요청자의 이 공고에 대한 활성 지원서 ID. 활성 지원서가 없으면 null.", nullable = true)
    val applicationId: Long?,
    @param:Schema(description = "활성 지원서 상태. 활성 지원서가 없으면 null.", example = "SUBMITTED", nullable = true)
    val applicationStatus: String?,
    @param:Schema(
        description =
            "지금 시도할 수 있는 Action 목록. 활성 지원서가 없고 지원 가능하면 [\"CREATE_DRAFT\"], " +
                "활성 지원서가 있으면 그 상태에서 가능한 학생 Action(SUBMIT/REQUEST_EDIT/RESUBMIT/WITHDRAW), " +
                "둘 다 아니면 빈 배열.",
    )
    val availableActions: List<String>,
)
