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
    /**
     * 삭제되지 않은 기업의 공개 요약을 반환한다. 없거나 이미 삭제됐으면 null.
     *
     * [requesterId]를 전달하면 로고가 있을 때 [FileUrlPort][team.inreok.getiserver.domain.file.link.FileUrlPort]로
     * 서명된 로고 URL을 발급해 [CompanySummary.logoUrl]에 채운다(Issue #92). 요청자를 알 수 없는
     * 호출(색인·외부 수집·Discord 알림처럼 인증된 사용자가 없는 시스템 문맥)은 이 인자를 생략하면
     * 되고, 이때는 URL을 발급하지 않아 `logoUrl`이 항상 null이다 — 불필요한 File 조회를 피하고,
     * 특히 Elasticsearch 색인 경로에서 만료되는 Presigned URL이 실수로 저장되는 것을 막는다.
     */
    fun findActiveSummary(
        companyId: Long,
        requesterId: Long? = null,
    ): CompanySummary?

    /**
     * 목록 응답에서 기업 요약을 채울 때 사용한다. 항목마다 [findActiveSummary]를 부르면 N+1이
     * 되므로 한 번에 조회한다. 존재하지 않거나 삭제된 ID는 결과 Map에 담기지 않는다.
     *
     * [requesterId]의 의미는 [findActiveSummary]와 같다 — 전달하면 대상 기업들의 로고 URL을
     * 한 번의 배치 호출로 발급한다(N+1 방지, `CompanyServiceImpl.search`와 같은 방식).
     */
    fun findActiveSummaries(
        companyIds: Collection<Long>,
        requesterId: Long? = null,
    ): Map<Long, CompanySummary>

    /**
     * Search Domain이 Elasticsearch Document의 `companyType` 필드를 채울 때만 쓰는 최소 조회다
     * (Issue #69). `CompanySummary`에 `CompanyType`을 넣지 않는 이유는 [CompanySummary] 문서
     * 참고 — 응답 Contract를 넓히지 않기 위해 별도 메서드로 분리했다. `company.entity.type`은
     * `operation.entity.type`과 같은 방식으로 Named Interface로 이미 공개되어 있다.
     */
    fun findActiveType(companyId: Long): CompanyType?

    /**
     * Search Domain이 Elasticsearch Document의 `companyLogoFileId` 필드를 채울 때만 쓰는 최소
     * 조회다(Issue #92). 색인은 만료되는 Presigned URL이 아니라 안정적인 File ID를 저장해야
     * 하므로 [CompanySummary.logoUrl](URL로 이미 변환된 값)이 아니라 원본 File ID가 필요하고,
     * [findActiveType]과 같은 이유로 별도 메서드로 분리했다.
     */
    fun findActiveLogoFileId(companyId: Long): Long?
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
    @param:Schema(
        description =
            "로고 이미지 URL. 서버가 서명한 짧은 유효기간의 URL이며 브라우저가 바로 표시할 수 있다. " +
                "로고가 없거나 발급하지 않은 조회(요청자를 알 수 없는 시스템 문맥)면 null이다.",
        nullable = true,
    )
    val logoUrl: String? = null,
)
