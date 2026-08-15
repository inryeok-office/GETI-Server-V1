package team.inreok.getiserver.domain.application.dto

// 학생이 본인 지원서에 수행할 수 있는 Action이다(Issue #124 "요구사항" 절). 교사 Action
// (ALLOW_EDIT/REQUEST_REVISION/APPROVE/REJECT)은 별도 Issue(Phase 4) 범위다.
enum class JobApplicationAction {
    SUBMIT,
    REQUEST_EDIT,
    RESUBMIT,
    WITHDRAW,
}
