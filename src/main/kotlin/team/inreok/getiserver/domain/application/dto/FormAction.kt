package team.inreok.getiserver.domain.application.dto

// 요구사항 5.6절 "복제/활성화/보관"에 대응하는 API 값이다. 원문이 한글 명사만 제시해 영문 값은
// 이번 PR에서 새로 정했다(docs/application/application-domain-plan.md §4).
enum class FormAction {
    DUPLICATE,
    ACTIVATE,
    ARCHIVE,
}
