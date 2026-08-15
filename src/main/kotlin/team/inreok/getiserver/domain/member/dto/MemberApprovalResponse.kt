package team.inreok.getiserver.domain.member.dto

import io.swagger.v3.oas.annotations.media.Schema
import team.inreok.getiserver.domain.member.entity.type.MemberStatus
import java.time.LocalDateTime

/**
 * 교직원 가입 승인·거절 처리 결과다(Issue #99). 처리 후 회원의 최종 상태와, 거절이면 저장한 사유,
 * 처리 시각을 반환한다.
 */
@Schema(description = "교직원 가입 승인·거절 처리 결과")
data class MemberApprovalResponse(
    @param:Schema(description = "처리한 회원 ID", example = "42")
    val memberId: Long,
    @param:Schema(description = "처리 후 회원 상태. 승인은 ACTIVE, 거절은 REJECTED.", example = "ACTIVE")
    val status: MemberStatus,
    @param:Schema(description = "거절 사유. 승인이면 null.", example = "제출한 재직 증명 자료가 확인되지 않았습니다.", nullable = true)
    val reason: String?,
    @param:Schema(description = "처리 시각", example = "2026-08-13T09:30:00")
    val processedAt: LocalDateTime,
)
