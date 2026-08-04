package team.inreok.getiserver.domain.company.query

import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.modulith.NamedInterface
import team.inreok.getiserver.domain.company.entity.type.CompanyType

/**
 * 다른 Domain Module이 기업의 존재 여부와 공개 요약 정보만 조회할 수 있게 하는 계약이다.
 * `domain.company`의 나머지 Package(`service`, `dto`, `entity`, `repository`)는 Module 내부
 * 구현이라 밖에서 참조할 수 없으므로(Spring Modulith), 이 Package만 Named Interface로 공개한다.
 *
 * 등록·수정·삭제는 여기 포함하지 않는다. 기업 정보를 바꾸는 것은 Company Module의 책임이고,
 * 다른 Domain이 우회로로 쓰지 못하게 하기 위함이다.
 */
@NamedInterface
interface CompanyQuery {
    /** 삭제되지 않은 기업의 공개 요약을 반환한다. 없거나 이미 삭제됐으면 null. */
    fun findActiveSummary(companyId: Long): CompanySummary?

    /**
     * 목록 응답에서 기업명을 채울 때 사용한다. 항목마다 [findActiveSummary]를 부르면 N+1이
     * 되므로 한 번에 조회한다. 존재하지 않거나 삭제된 ID는 결과 Map에 담기지 않는다.
     */
    fun findActiveSummaries(companyIds: Collection<Long>): Map<Long, CompanySummary>

    /**
     * Search Domain이 Elasticsearch Document의 `companyType` 필드를 채울 때만 쓰는 최소 조회다
     * (Issue #69). `CompanySummary`에 `CompanyType`을 넣지 않는 이유는 [CompanySummary] 문서
     * 참고 — 응답 Contract를 넓히지 않기 위해 별도 메서드로 분리했다. `company.entity.type`은
     * `operation.entity.type`과 같은 방식으로 Named Interface로 이미 공개되어 있다.
     */
    fun findActiveType(companyId: Long): CompanyType?
}

/**
 * 다른 Domain의 API 응답에 그대로 실을 수 있는 기업 최소 정보다. `CompanyType`/`MouStatus`는
 * `domain.company.entity.type`(Module 내부 Package)에 있어 여기 담으면 참조하는 Domain이
 * 다시 내부 구현에 의존하게 되므로 포함하지 않는다.
 */
@NamedInterface
@Schema(description = "기업 요약 정보. 공고 등 다른 도메인 응답에 포함된다.")
data class CompanySummary(
    @param:Schema(description = "기업 ID", example = "1")
    val companyId: Long,
    @param:Schema(description = "기업명", example = "인력개발원")
    val name: String,
)
