package team.inreok.getiserver.domain.collector.entity.type

// 확정 순서(Notion API 명세서): MMA -> JOB_ALIO -> CLEAN_EYE -> NARA_ILTEO -> IBK_ONE_JOB -> SARAMIN -> WORK24.
// MANUAL은 Collector가 실행하는 외부 Provider가 아니라 Job 도메인의 수동 등록 출처를 표현하기 위한 값이다.
enum class JobSourceCode {
    MMA,
    JOB_ALIO,
    CLEAN_EYE,
    NARA_ILTEO,
    IBK_ONE_JOB,
    SARAMIN,
    WORK24,
    MANUAL,
}
