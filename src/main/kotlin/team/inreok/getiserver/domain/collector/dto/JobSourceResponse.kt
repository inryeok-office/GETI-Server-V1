package team.inreok.getiserver.domain.collector.dto

import io.swagger.v3.oas.annotations.media.Schema
import team.inreok.getiserver.domain.collector.entity.JobSource
import team.inreok.getiserver.domain.collector.entity.type.JobSourceApprovalStatus
import team.inreok.getiserver.domain.collector.entity.type.JobSourceCode
import team.inreok.getiserver.domain.collector.entity.type.JobSourceType
import team.inreok.getiserver.domain.collector.provider.ProviderRegistry
import java.time.LocalDateTime

@Schema(description = "수집원 설정과 상태. 인증키 원문은 포함하지 않는다.")
data class JobSourceResponse(
    @param:Schema(description = "수집원 ID", example = "1")
    val sourceId: Long,
    @param:Schema(description = "수집원 코드", example = "MMA")
    val sourceCode: JobSourceCode,
    @param:Schema(description = "수집원 이름")
    val name: String,
    @param:Schema(description = "수집원 유형")
    val sourceType: JobSourceType,
    @param:Schema(description = "승인 상태")
    val approvalStatus: JobSourceApprovalStatus,
    @param:Schema(description = "활성화 여부")
    val enabled: Boolean,
    @param:Schema(description = "실행 시점 설정 완료 여부(Provider 구현·인증 설정 존재 여부로 계산, DB에 저장된 값이 아님)")
    val configured: Boolean,
    @param:Schema(description = "일일 호출 제한(nullable)", nullable = true)
    val dailyRequestLimit: Int?,
    @param:Schema(description = "마지막 수집 시도 시각(nullable)", nullable = true)
    val lastCollectedAt: LocalDateTime?,
    @param:Schema(description = "마지막 성공 시각(nullable)", nullable = true)
    val lastSuccessAt: LocalDateTime?,
    @param:Schema(description = "마지막 실패 시각(nullable)", nullable = true)
    val lastFailureAt: LocalDateTime?,
    @param:Schema(description = "마지막 오류 요약(nullable, Secret 미포함)", nullable = true)
    val lastError: String?,
) {
    companion object {
        fun from(
            source: JobSource,
            providerRegistry: ProviderRegistry,
        ): JobSourceResponse =
            JobSourceResponse(
                sourceId = requireNotNull(source.id),
                sourceCode = source.sourceCode,
                name = source.name,
                sourceType = source.sourceType,
                approvalStatus = source.approvalStatus,
                enabled = source.enabled,
                configured = providerRegistry.isConfigured(source.sourceCode),
                dailyRequestLimit = source.dailyRequestLimit,
                lastCollectedAt = source.lastCollectedAt,
                lastSuccessAt = source.lastSuccessAt,
                lastFailureAt = source.lastFailureAt,
                lastError = source.lastError,
            )
    }
}

@Schema(description = "수집원 설정·상태 목록")
data class JobSourceListResponse(
    @param:Schema(description = "수집원 목록")
    val sources: List<JobSourceResponse>,
)
