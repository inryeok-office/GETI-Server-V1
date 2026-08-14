package team.inreok.getiserver.domain.application.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "학생 지원서 Action 요청")
data class JobApplicationActionRequest(
    @param:Schema(description = "수행할 Action", example = "SUBMIT")
    val action: JobApplicationAction,
)
