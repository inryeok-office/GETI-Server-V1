package team.inreok.getiserver.domain.program.dto

import io.swagger.v3.oas.annotations.media.Schema
import team.inreok.getiserver.domain.program.entity.type.ProgramApplicationAction

@Schema(description = "프로그램 신청·취소 요청. 신청·취소를 하나의 Action API로 처리한다(원본 요구사항 문서 11절).")
data class ProgramApplicationActionRequest(
    @param:Schema(description = "수행할 Action", example = "APPLY")
    val action: ProgramApplicationAction,
    @param:Schema(
        description = "신청 답변. action=APPLY이고 프로그램에 신청 양식이 연결된 경우에 사용한다.",
        nullable = true,
    )
    val answerData: List<ProgramApplicationAnswer>? = null,
)
