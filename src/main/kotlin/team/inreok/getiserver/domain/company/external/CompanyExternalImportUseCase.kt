package team.inreok.getiserver.domain.company.external

import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.modulith.NamedInterface

/**
 * Collector 도메인이 외부에서 수집한 기업명으로 기존 기업을 찾거나, 없으면 최소 정보로 새로
 * 등록하기 위한 유일한 공개 계약이다(Issue #62). Collector는 이 Interface를 통해서만 Company에
 * 접근하고, `CompanyRepository`나 `Company` Entity를 직접 참조하지 않는다.
 *
 * 정식 기업 등록(`POST /api/v1/admin/companies`, `CompanyService.create`)과 달리 화면에서 사람이
 * 직접 입력한 값이 아니므로, 중복 판정은 기존 기업 등록과 동일한 기준((name, type) 미삭제 Unique,
 * Issue #56 `uk_companies_name_type_active`)을 그대로 재사용해 별도 정책을 만들지 않는다.
 */
@NamedInterface
interface CompanyExternalImportUseCase {
    /**
     * `(companyName, GENERAL)` 조합으로 기존 기업을 찾아 있으면 그 ID를 반환하고, 없으면
     * MOU 없음(NONE) 상태의 최소 기업을 새로 만든다. 동시에 같은 기업명으로 수집이 들어와도
     * 중복 생성되지 않도록 DB Unique Constraint 위반을 재조회로 흡수한다.
     */
    fun findOrCreateExternal(command: CompanyExternalImportCommand): CompanyExternalImportResult
}

@NamedInterface
@Schema(description = "Collector가 수집한 기업 정보로 기존 기업을 찾거나 새로 만들기 위한 명령")
data class CompanyExternalImportCommand(
    @param:Schema(description = "Provider가 제공한 기업명 원문(앞뒤 공백만 정리됨)", example = "인력개발원")
    val companyName: String,
    @param:Schema(description = "수집 출처 코드. 신규 생성 시 companies.source에 기록된다.", example = "MMA")
    val sourceCode: String,
)

@NamedInterface
@Schema(description = "기업 해석 결과")
data class CompanyExternalImportResult(
    @param:Schema(description = "해석된 기업 ID")
    val companyId: Long,
    @param:Schema(description = "이번 호출로 새로 생성되었는지 여부(false면 기존 기업을 재사용)")
    val created: Boolean,
)
