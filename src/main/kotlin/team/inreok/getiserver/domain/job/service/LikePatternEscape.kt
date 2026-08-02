package team.inreok.getiserver.domain.job.service

// LIKE Wildcard(%, _)와 Escape 문자(\) 자체를 이스케이프해 검색어에 포함된 %/_가 의도치 않게
// Wildcard로 해석되지 않고 문자 그대로 매칭되게 한다. Repository의 LIKE 절은 ESCAPE '\'를 사용한다.
//
// domain.company.service에 동일한 함수가 있지만 그 Package는 Company Module 내부 구현이라
// 참조하면 ModularityTest가 실패한다. global로 승격하는 것은 이번 Issue 범위 밖의 Refactoring이라
// 후속 작업으로 남기고(Issue #60 후속 후보) 여기 복제한다.
internal fun escapeLikePattern(value: String): String =
    value
        .replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_")
