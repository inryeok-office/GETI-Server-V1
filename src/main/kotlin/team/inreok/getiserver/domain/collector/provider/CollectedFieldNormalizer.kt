package team.inreok.getiserver.domain.collector.provider

import java.net.URI
import java.time.LocalDateTime

private val ALLOWED_EXTERNAL_URL_SCHEMES = setOf("http", "https")

/**
 * Provider가 원문 그대로 준 URL 후보를 Job 저장 가능한 형태로 정규화한다(JobValidation이 http/https
 * 이외 Scheme을 거부해 원문을 그대로 넘기면 Upsert 자체가 실패할 수 있다 — 실제 JOB_ALIO/나라일터
 * 응답에서 "www.example.kr"처럼 Scheme이 빠진 순수 도메인을 실제로 확인했다, Issue #62 확장 범위).
 * 값이 없거나 http/https로 되살릴 수 없으면 null을 반환해 "없는 필드"로 취급한다.
 */
@Suppress("ReturnCount")
fun normalizeExternalUrl(raw: String?): String? {
    val trimmed = raw?.trim().orEmpty()
    if (trimmed.isEmpty()) return null

    val scheme = runCatching { URI(trimmed).scheme }.getOrNull()
    if (scheme != null) {
        return trimmed.takeIf { scheme.lowercase() in ALLOWED_EXTERNAL_URL_SCHEMES }
    }

    // Scheme만 빠진 순수 도메인/경로로 보고 https를 붙여 살린다(mailto:/tel: 등 다른 Scheme이
    // 이미 있는 값은 위에서 걸러진다).
    val withScheme = "https://$trimmed"
    val normalizedScheme = runCatching { URI(withScheme).scheme }.getOrNull()
    return withScheme.takeIf { normalizedScheme?.lowercase() in ALLOWED_EXTERNAL_URL_SCHEMES }
}

/**
 * 모집 시작·종료 일시가 뒤바뀐 원문 데이터(실제 나라일터 응답에서 확인)를 방어한다. 어느 쪽이
 * 잘못됐는지 알 수 없으므로 둘 다 버려 "모집 기간 미상"으로 취급한다 — Job.validateRecruitmentPeriod가
 * start > end를 거부해 Upsert 전체가 실패하는 대신, 이 공고는 계속 저장된다.
 */
fun normalizeRecruitmentPeriod(
    startDate: LocalDateTime?,
    endDate: LocalDateTime?,
): Pair<LocalDateTime?, LocalDateTime?> =
    if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
        null to null
    } else {
        startDate to endDate
    }
