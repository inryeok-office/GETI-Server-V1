package team.inreok.getiserver.domain.search.dto

// 어떤 문서·코드에도 값이 정의되어 있지 않아(Notion 접근 불가, 완료 보고 참고) 이번 범위에
// 실제로 필요한 유일한 동작인 전체 재색인만 정의한다. OperationType.SEARCH_REINDEX(operation
// 도메인)와 이름을 맞췄다. 추가 값(부분 재색인 등)은 실제 요구가 생기면 후속 Issue에서 정한다.
enum class SearchAction {
    REINDEX,
}
