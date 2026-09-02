package team.inreok.getiserver.domain.job.query

import org.springframework.modulith.NamedInterface

/**
 * `application` Module의 관리자 지원자 목록 필터(Issue #181)가 기업·담당 교사 기준으로 검색할 수
 * 있게 하는 공개 계약이다.
 *
 * `JobApplication`은 `jobId`만 가지고 있어 `companyId`/`managerMemberId`로 직접 필터링할 수 없다.
 * 물리 FK가 있어도 Native Query로 직접 Join하지 않는다(이 저장소의 일관된 원칙,
 * `InquiryAuthorSearchQueryPort` KDoc의 "물리 FK가 있어도 Native Query로 직접 Join하지 않는다"
 * 참고). 대신 이 Port로 조건에 맞는 Job id 집합만 받아 `jobId IN (...)` 조건으로 사용한다.
 */
@NamedInterface
interface JobApplicationAdminFilterQueryPort {
    /**
     * [companyId]/[managerMemberId]/[mineOnlyMemberId] 세 조건을 AND로 결합해 만족하는 삭제되지
     * 않은 Job id 집합을 반환한다. 값이 null인 조건은 적용하지 않는다. 세 값이 모두 null이면
     * 호출하지 않는 것을 권장한다(불필요한 Query 방지, 호출 측 책임).
     *
     * [mineOnlyMemberId]는 `managerMemberId` 또는 `createdByMemberId` 중 하나라도 일치하면
     * 포함한다(사용자 확인 완료 -- 공고에 `managerMemberId`를 지정하는 API가 아직 없어
     * `managerMemberId`만 기준으로 하면 "담당 공고" 필터가 항상 빈 목록이 되기 때문에 등록자
     * 기준도 포함한다).
     */
    fun findIdsByFilters(
        companyId: Long?,
        managerMemberId: Long?,
        mineOnlyMemberId: Long?,
    ): Set<Long>
}
