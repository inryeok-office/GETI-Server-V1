package team.inreok.getiserver.domain.file.service

// LIKE Wildcard(%, _)와 Escape 문자(\) 자체를 이스케이프해 검색어에 포함된 %/_가 의도치 않게
// Wildcard로 해석되지 않고 문자 그대로 매칭되게 한다. Repository의 LIKE 절은 ESCAPE '\'를 사용한다
// (domain.application.service.escapeLikePattern/domain.inquiry.service.escapeLikePattern/
// domain.member.service.escapeLikePattern/domain.company.service.escapeLikePattern과 동일한 관례 --
// 이 저장소는 이 작은 Helper를 도메인마다 각자 둔다).
internal fun escapeLikePattern(value: String): String =
    value
        .replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_")
