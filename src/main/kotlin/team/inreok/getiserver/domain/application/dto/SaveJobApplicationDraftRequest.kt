package team.inreok.getiserver.domain.application.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid

@Schema(
    description =
        "지원서 임시저장 요청(요구사항 9절). 전달하지 않은 Field는 기존 값을 유지한다. " +
            "DRAFT 상태의 본인 지원서에만 사용할 수 있다.",
)
data class SaveJobApplicationDraftRequest(
    @param:Schema(description = "전화번호(선택)", nullable = true, example = "010-1234-5678")
    val contactPhone: String? = null,
    @param:Schema(description = "질문 답변 목록(선택). 전달하면 기존 답변 전체를 이 값으로 교체한다.", nullable = true)
    val answers: List<@Valid ApplicationAnswer>? = null,
    @param:Schema(description = "개인정보 수집·이용 동의 여부(선택)", nullable = true, example = "true")
    val privacyConsent: Boolean? = null,
)
