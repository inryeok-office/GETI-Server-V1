package team.inreok.getiserver.domain.application.entity.type

// Application 도메인의 우선 대상은 JOB이다. PROGRAM은 이후 Program 도메인이 같은 양식 구조를
// 재사용할 수 있도록 값만 미리 정의해 둔다(요구사항 4절).
enum class FormType {
    JOB,
    PROGRAM,
}
