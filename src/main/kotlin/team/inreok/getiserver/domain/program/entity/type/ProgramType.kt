package team.inreok.getiserver.domain.program.entity.type

// 원본 요구사항 문서(GETI-Server Program 도메인 전체 개발 요구사항) 4절 값으로 확정했다(사용자
// 확인 완료). 기존 값(EMPLOYMENT/SPECIAL_LECTURE/EXPLANATION/ETC, V2 Migration)을 저장하는 실제
// 데이터가 없어 Breaking Change로 처리했다.
enum class ProgramType {
    SPECIAL_LECTURE,
    EDUCATION,
}
