package team.inreok.getiserver.domain.company.dto

import io.swagger.v3.oas.annotations.media.Schema
import team.inreok.getiserver.domain.company.entity.Company
import team.inreok.getiserver.domain.company.entity.type.CompanyType
import team.inreok.getiserver.domain.company.entity.type.MouStatus
import java.time.LocalDate
import java.time.LocalDateTime

@Schema(description = "기업 상세 정보. 등록·수정·상세 조회가 같은 형태로 응답한다.")
data class CompanyResponse(
    @param:Schema(description = "기업 ID", example = "1")
    val companyId: Long,
    @param:Schema(description = "기업명", example = "인력개발원")
    val name: String,
    @param:Schema(description = "기업 유형", example = "GENERAL")
    val companyType: CompanyType,
    @param:Schema(description = "MOU 협약 상태", example = "ACTIVE")
    val mouStatus: MouStatus,
    @param:Schema(description = "정보 출처(외부 수집 기업과 직접 등록 기업 구분)", example = "manual", nullable = true)
    val sourceName: String?,
    @param:Schema(description = "홈페이지 URL", example = "https://example.com", nullable = true)
    val homepageUrl: String?,
    @param:Schema(
        description = "로고 이미지 URL. File 도메인 연동 전이라 항상 null이다.",
        nullable = true,
    )
    val logoUrl: String?,
    @param:Schema(description = "기업 소개", example = "IT 인재를 양성하는 교육 기관입니다.", nullable = true)
    val description: String?,
    @param:Schema(description = "업종", example = "소프트웨어 개발", nullable = true)
    val industry: String?,
    @param:Schema(description = "주소", example = "대구광역시 남구 대명동", nullable = true)
    val address: String?,
    @param:Schema(description = "MOU 협약 시작일", example = "2026-03-01", nullable = true)
    val mouStartDate: LocalDate?,
    @param:Schema(description = "MOU 협약 종료일", example = "2027-02-28", nullable = true)
    val mouEndDate: LocalDate?,
    @param:Schema(description = "등록 시각", example = "2026-03-01T10:15:30")
    val createdAt: LocalDateTime?,
    @param:Schema(description = "최근 수정 시각", example = "2026-03-02T09:00:00")
    val updatedAt: LocalDateTime?,
) {
    companion object {
        fun from(company: Company): CompanyResponse =
            CompanyResponse(
                companyId = requireNotNull(company.id) { "저장된 Company는 id를 가져야 합니다." },
                name = company.name,
                companyType = company.type,
                mouStatus = company.mouStatus,
                sourceName = company.source,
                homepageUrl = company.websiteUrl,
                // File 도메인 연동 전이라 logoFileId를 실제 URL로 변환할 수 없다(Member의
                // profileImageUrl과 동일한 처리).
                logoUrl = null,
                description = company.description,
                industry = company.industry,
                address = company.address,
                mouStartDate = company.mouStartedOn,
                mouEndDate = company.mouEndedOn,
                createdAt = company.createdAt,
                updatedAt = company.updatedAt,
            )
    }
}
