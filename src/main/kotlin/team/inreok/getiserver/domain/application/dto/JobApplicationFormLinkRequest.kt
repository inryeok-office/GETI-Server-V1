package team.inreok.getiserver.domain.application.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "공고-양식 연결 요청")
data class JobApplicationFormLinkRequest(
    @param:Schema(description = "연결할 양식 ID(호출자 본인 소유의 ACTIVE 상태 JOB 양식이어야 함)", example = "1")
    val formId: Long,
)
