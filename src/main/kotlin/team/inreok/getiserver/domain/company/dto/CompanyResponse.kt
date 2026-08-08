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
        description =
            "로고 이미지 URL. 서버가 서명한 짧은 유효기간의 URL이며 브라우저가 바로 표시할 수 있다. " +
                "로고가 없으면 null이다.",
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
        /**
         * [logoUrl]은 호출 측이 `FileUrlPort`로 변환해 넘긴다. 이 DTO가 직접 발급하지 않는 것은
         * 발급에 요청자 ID와 권한 판정이 필요하고, 목록 응답에서는 여러 건을 한 번에 변환해야
         * 하기 때문이다(N+1 방지).
         */
        fun from(
            company: Company,
            logoUrl: String?,
        ): CompanyResponse =
            CompanyResponse(
                companyId = requireNotNull(company.id) { "저장된 Company는 id를 가져야 합니다." },
                name = company.name,
                companyType = company.type,
                mouStatus = company.mouStatus,
                sourceName = company.source,
                homepageUrl = company.websiteUrl,
                logoUrl = logoUrl,
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
