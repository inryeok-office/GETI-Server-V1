package team.inreok.getiserver.domain.job.service

import team.inreok.getiserver.domain.job.entity.Job
import team.inreok.getiserver.domain.job.entity.type.ApplicationMethod
import team.inreok.getiserver.domain.job.exception.JobFormRequiredException
import team.inreok.getiserver.domain.job.exception.JobValidationFailedException
import java.net.URI
import java.time.LocalDateTime

// 공고 값 검증 규칙이다. Service 구현체에서 분리해 상태별 규칙을 한곳에서 읽을 수 있게 하고,
// 규칙 하나가 함수 하나에 대응하도록 잘게 나눴다.

/**
 * 상태와 무관하게 항상 지켜야 하는 값 형식을 검증한다. 임시저장(DRAFT)은 값이 비어 있는 것은
 * 허용하지만, 들어온 값이 잘못된 것은 허용하지 않는다.
 */
internal fun validateCommon(job: Job) {
    validateTitle(job.title)
    validateRecruitmentPeriod(job.recruitmentStartedAt, job.recruitmentEndedAt)
    validateTargetGrade(job.targetGrade)
    validateCapacity(job.capacity)
    job.externalUrl?.takeIf { it.isNotBlank() }?.let(::validateExternalUrl)
}

/**
 * 게시(PUBLISHED) 상태에서만 요구하는 값을 검증한다.
 *
 * 작업 지시서가 요구한 "내부 지원이면 Form 연결"과 "MOU 공고 정책"은 각각 Form Domain과
 * `jobs.form_id` Column이 없고, Company 공개 계약에 MOU 상태를 담지 않기로 해서 검증할 수 없다.
 * 전자는 INTERNAL 공고의 게시 자체를 막는 것으로 대체했고 후자는 후속 Issue로 남겼다.
 *
 * `targetGrade`와 모집 기간은 게시에도 필수로 두지 않는다. Column이 Nullable이고 전 학년
 * 대상이거나 상시 모집인 공고를 막을 근거가 명세에 없다.
 */
internal fun validateForPublish(job: Job) {
    if (job.applicationMethod == ApplicationMethod.INTERNAL) throw JobFormRequiredException()
    if (job.bodyMarkdown.isNullOrBlank()) {
        throw JobValidationFailedException("게시하려면 공고 본문을 입력해야 합니다.")
    }
    validatePublishExternalUrl(job.externalUrl)
}

private fun validateTitle(title: String) {
    if (title.isBlank()) throw JobValidationFailedException("공고 제목은 비어 있을 수 없습니다.")
}

private fun validateRecruitmentPeriod(
    start: LocalDateTime?,
    end: LocalDateTime?,
) {
    if (start != null && end != null && start.isAfter(end)) {
        throw JobValidationFailedException("모집 시작 시각은 종료 시각보다 늦을 수 없습니다.")
    }
}

private fun validateTargetGrade(targetGrade: Int?) {
    if (targetGrade != null && targetGrade !in MIN_TARGET_GRADE..MAX_TARGET_GRADE) {
        throw JobValidationFailedException("지원 대상 학년은 1, 2, 3 중 하나여야 합니다.")
    }
}

private fun validateCapacity(capacity: Int?) {
    if (capacity != null && capacity <= 0) {
        throw JobValidationFailedException("모집 인원은 1명 이상이어야 합니다.")
    }
}

private fun validatePublishExternalUrl(externalUrl: String?) {
    if (externalUrl.isNullOrBlank()) {
        throw JobValidationFailedException("외부 지원 공고를 게시하려면 외부 지원 URL이 필요합니다.")
    }
    validateExternalUrl(externalUrl)
}

/**
 * 외부 지원 URL의 형식만 확인한다. 이 서버가 해당 URL로 직접 요청하지 않으므로 SSRF 대상이
 * 아니고, Client가 안전하게 이동할 수 있는 Scheme인지만 본다.
 */
private fun validateExternalUrl(url: String) {
    val scheme =
        runCatching { URI(url).scheme }
            .getOrElse { throw JobValidationFailedException("외부 지원 URL 형식이 올바르지 않습니다.") }
    if (scheme?.lowercase() !in ALLOWED_URL_SCHEMES) {
        throw JobValidationFailedException("외부 지원 URL은 http 또는 https만 사용할 수 있습니다.")
    }
}

private const val MIN_TARGET_GRADE = 1
private const val MAX_TARGET_GRADE = 3
private val ALLOWED_URL_SCHEMES = setOf("http", "https")
