package team.inreok.getiserver.domain.application.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(description = "공고-양식 연결 결과")
data class JobApplicationFormLinkResponse(
    @param:Schema(description = "공고 ID", example = "1")
    val jobId: Long,
    @param:Schema(description = "연결된 양식 ID", example = "1")
    val formId: Long,
    @param:Schema(description = "연결 시점의 양식 버전", example = "2")
    val formVersion: Int,
    @param:Schema(description = "연결(또는 재연결) 일시")
    val updatedAt: LocalDateTime,
)
