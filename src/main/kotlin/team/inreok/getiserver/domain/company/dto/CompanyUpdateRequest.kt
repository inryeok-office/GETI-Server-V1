package team.inreok.getiserver.domain.company.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Size
import team.inreok.getiserver.domain.company.entity.type.CompanyType
import team.inreok.getiserver.domain.company.entity.type.MouStatus
import java.time.LocalDate

/**
 * 기업 부분 수정(PATCH) 요청이다. 전달하지 않은 Field(또는 `null`)는 기존 값을 그대로 유지한다.
 * 값을 명시적으로 비우는(지우는) 동작은 API 명세서에 정의되어 있지 않아 지원하지 않는다 —
 * Member의 부분 수정처럼 "미전달"과 "명시적 null"을 구분해야 하는 요구사항이 확인되면
 * 그때 `JsonNode` 방식으로 확장한다.
 */
@Schema(description = "기업 부분 수정 요청. 전달하지 않았거나 null인 Field는 기존 값을 유지한다.")
data class CompanyUpdateRequest(
    @field:Size(max = 255, message = "기업명은 255자를 넘을 수 없습니다.")
    @param:Schema(description = "기업명", example = "인력개발원", nullable = true, maxLength = 255)
    val name: String? = null,
    @param:Schema(description = "기업 유형", example = "PUBLIC_INSTITUTION", nullable = true)
    val companyType: CompanyType? = null,
    @param:Schema(description = "MOU 협약 상태", example = "ACTIVE", nullable = true)
    val mouStatus: MouStatus? = null,
    @field:Size(max = 255, message = "정보 출처는 255자를 넘을 수 없습니다.")
    @param:Schema(description = "정보 출처", example = "manual", nullable = true, maxLength = 255)
    val sourceName: String? = null,
    @field:Size(max = 1000, message = "홈페이지 URL은 1000자를 넘을 수 없습니다.")
    @param:Schema(description = "홈페이지 URL", example = "https://example.com", nullable = true, maxLength = 1000)
    val homepageUrl: String? = null,
    @param:Schema(description = "기업 소개", example = "IT 인재를 양성하는 교육 기관입니다.", nullable = true)
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
