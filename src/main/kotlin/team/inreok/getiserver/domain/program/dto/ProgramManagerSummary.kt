package team.inreok.getiserver.domain.program.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "프로그램 담당 교사 요약")
data class ProgramManagerSummary(
    @param:Schema(description = "담당 교사 회원 ID", example = "10")
    val memberId: Long,
    @param:Schema(description = "담당 교사 이름", example = "홍길동", nullable = true)
    val name: String?,
)
