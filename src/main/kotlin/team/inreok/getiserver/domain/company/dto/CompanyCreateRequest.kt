package team.inreok.getiserver.domain.company.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import team.inreok.getiserver.domain.company.entity.type.CompanyType
import team.inreok.getiserver.domain.company.entity.type.MouStatus
import java.time.LocalDate

@Schema(description = "기업 등록 요청")
data class CompanyCreateRequest(
    @field:NotBlank(message = "기업명은 필수입니다.")
    @field:Size(max = 255, message = "기업명은 255자를 넘을 수 없습니다.")
    @param:Schema(description = "기업명(필수, 최대 255자)", example = "인력개발원", maxLength = 255)
    val name: String,
    @param:Schema(description = "기업 유형(필수)", example = "GENERAL")
    val companyType: CompanyType,
    @param:Schema(
        description = "MOU 협약 상태. 생략하면 NONE으로 저장된다.",
        example = "NONE",
        defaultValue = "NONE",
    )
    val mouStatus: MouStatus = MouStatus.NONE,
    @field:Size(max = 255, message = "정보 출처는 255자를 넘을 수 없습니다.")
    @param:Schema(
        description = "정보 출처. 교사·개발자가 직접 등록한 기업과 외부 수집 기업을 구분하는 값이다.",
        example = "manual",
        nullable = true,
        maxLength = 255,
    )
    val sourceName: String? = null,
    @field:Size(max = 1000, message = "홈페이지 URL은 1000자를 넘을 수 없습니다.")
    @param:Schema(description = "홈페이지 URL", example = "https://example.com", nullable = true, maxLength = 1000)
    val homepageUrl: String? = null,
    @param:Schema(
        description = "기업 소개",
        example = "IT 인재를 양성하는 교육 기관입니다.",
        nullable = true,
    )
    val description: String? = null,
    @field:Size(max = 255, message = "업종은 255자를 넘을 수 없습니다.")
    @param:Schema(description = "업종", example = "소프트웨어 개발", nullable = true, maxLength = 255)
    val industry: String? = null,
    @field:Size(max = 1000, message = "주소는 1000자를 넘을 수 없습니다.")
    @param:Schema(description = "주소", example = "대구광역시 남구 대명동", nullable = true, maxLength = 1000)
    val address: String? = null,
    @param:Schema(description = "MOU 협약 시작일", example = "2026-03-01", nullable = true)
    val mouStartDate: LocalDate? = null,
    @param:Schema(description = "MOU 협약 종료일", example = "2027-02-28", nullable = true)
    val mouEndDate: LocalDate? = null,
)
