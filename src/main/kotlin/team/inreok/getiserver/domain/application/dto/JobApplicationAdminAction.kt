package team.inreok.getiserver.domain.application.dto

// 교사가 제출된 지원서를 검토할 때 수행할 수 있는 Action이다(Issue #125 "요구사항" 절). 학생 Action
// (SUBMIT/REQUEST_EDIT/RESUBMIT/WITHDRAW)은 [JobApplicationAction]으로 별도 관리한다.
enum class JobApplicationAdminAction {
    ALLOW_EDIT,
    REQUEST_REVISION,
    APPROVE,
    REJECT,
}
