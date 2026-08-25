package team.inreok.getiserver.domain.member.dto

/**
 * 교직원 가입 승인 API가 수행할 처리 종류다(Issue #99). 승인(PENDING->ACTIVE, TEACHER 부여) 또는
 * 거절(PENDING->REJECTED)만 존재한다.
 */
enum class ApprovalAction {
    APPROVE,
    REJECT,
}
