package team.inreok.getiserver.domain.collector.dto

import io.swagger.v3.oas.annotations.media.Schema
import team.inreok.getiserver.domain.collector.entity.JobSource
import team.inreok.getiserver.domain.collector.entity.type.JobSourceApprovalStatus
import team.inreok.getiserver.domain.collector.entity.type.JobSourceCode
import team.inreok.getiserver.domain.collector.provider.ProviderRegistry
import java.time.LocalDateTime

@Schema(description = "수집원 활성 상태 변경 결과")
data class JobSourceUpdateResponse(
    @param:Schema(description = "수집원 ID")
    val sourceId: Long,
    @param:Schema(description = "수집원 코드")
    val sourceCode: JobSourceCode,
    @param:Schema(description = "승인 상태")
    val approvalStatus: JobSourceApprovalStatus,
    @param:Schema(description = "변경된 활성화 여부")
    val enabled: Boolean,
    @param:Schema(description = "실행 시점 설정 완료 여부")
    val configured: Boolean,
    @param:Schema(description = "변경 시각")
    val updatedAt: LocalDateTime?,
) {
    companion object {
        fun from(
            source: JobSource,
            providerRegistry: ProviderRegistry,
        ): JobSourceUpdateResponse =
            JobSourceUpdateResponse(
                sourceId = requireNotNull(source.id),
                sourceCode = source.sourceCode,
                approvalStatus = source.approvalStatus,
                enabled = source.enabled,
                configured = providerRegistry.isConfigured(source.sourceCode),
                updatedAt = source.updatedAt,
            )
    }
}
