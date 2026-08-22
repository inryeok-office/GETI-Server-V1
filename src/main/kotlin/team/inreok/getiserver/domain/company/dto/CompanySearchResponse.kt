package team.inreok.getiserver.domain.company.dto

import io.swagger.v3.oas.annotations.media.Schema
import team.inreok.getiserver.domain.company.entity.Company
import team.inreok.getiserver.domain.company.entity.type.CompanyType
import team.inreok.getiserver.domain.company.entity.type.MouStatus

@Schema(description = "기업 검색·목록 결과. Spring Data Page 규약과 동일하게 page는 0부터 시작한다.")
data class CompanySearchResponse(
    @param:Schema(description = "검색 결과 목록")
    val content: List<CompanySummaryResponse>,
    @param:Schema(description = "현재 Page 번호(0부터 시작)", example = "0")
    val page: Int,
    @param:Schema(description = "Page당 개수", example = "20")
    val size: Int,
    @param:Schema(description = "전체 결과 개수", example = "42")
    val totalElements: Long,
    @param:Schema(description = "전체 Page 수", example = "3")
    val totalPages: Int,
    @param:Schema(description = "첫 Page 여부", example = "true")
    val first: Boolean,
    @param:Schema(description = "마지막 Page 여부", example = "false")
    val last: Boolean,
)

@Schema(description = "기업 목록 항목. 학생에게도 공개할 수 있는 정보만 포함한다.")
data class CompanySummaryResponse(
    @param:Schema(description = "기업 ID", example = "1")
    val companyId: Long,
    @param:Schema(description = "기업명", example = "인력개발원")
    val name: String,
    @param:Schema(description = "기업 유형", example = "GENERAL")
    val companyType: CompanyType,
    @param:Schema(description = "MOU 협약 상태", example = "ACTIVE")
    val mouStatus: MouStatus,
    @param:Schema(
        description = "로고 이미지 URL. 서버가 서명한 짧은 유효기간의 URL이다. 로고가 없으면 null이다.",
        nullable = true,
    )
    val logoUrl: String?,
    @param:Schema(description = "현재 모집 중인 공개 공고 수", example = "2")
    val openJobCount: Long,
    @param:Schema(description = "현재 모집 중인 MOU 공고 수", example = "1")
    val activeMouJobCount: Long,
    @param:Schema(description = "DRAFT를 제외한 누적 지원 건수", example = "24")
    val applicationCount: Long,
) {
    companion object {
        fun from(
            company: Company,
            logoUrl: String?,
            openJobCount: Long,
            activeMouJobCount: Long,
            applicationCount: Long,
        ): CompanySummaryResponse =
            CompanySummaryResponse(
                companyId = requireNotNull(company.id) { "저장된 Company는 id를 가져야 합니다." },
                name = company.name,
                companyType = company.type,
                mouStatus = company.mouStatus,
                logoUrl = logoUrl,
                openJobCount = openJobCount,
                activeMouJobCount = activeMouJobCount,
                applicationCount = applicationCount,
            )
    }
}
