package team.inreok.getiserver.domain.job.exception

import team.inreok.getiserver.global.error.BusinessException

/**
 * 내부 지원(`ApplicationMethod.INTERNAL`) 공고를 게시하려 할 때 발생한다. Form Domain과
 * `jobs.form_id` Column이 아직 없어 지원서 양식을 연결할 방법이 없으므로, 지원할 수 없는 공고가
 * 공개 목록에 노출되지 않도록 게시 자체를 막는다(Issue #60 제외 범위).
 */
class JobFormRequiredException :
    BusinessException(
        JobErrorCode.JOB_FORM_REQUIRED,
        "내부 지원 공고는 지원서 양식 연동 후에 게시할 수 있습니다.",
    )
