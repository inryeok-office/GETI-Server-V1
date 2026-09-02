package team.inreok.getiserver.domain.application.service.impl

import team.inreok.getiserver.domain.application.dto.ApplicationAnswer
import team.inreok.getiserver.domain.application.entity.JobApplication
import team.inreok.getiserver.domain.file.entity.type.FileOwnerType
import team.inreok.getiserver.domain.file.entity.type.FilePurpose
import team.inreok.getiserver.domain.file.link.FileLinkPort
import tools.jackson.databind.ObjectMapper

/**
 * SUBMIT/RESUBMIT 시점에 답변(FILE 유형)이 참조하는 fileId를 실제로 지원서에 연결한다(Issue #134).
 *
 * 요구사항의 기본 Flow(학생이 File API로 업로드 -> fileId를 답변에 저장 -> SUBMIT -> Application과
 * File 연결)를 그대로 따라 "답변에 fileId 저장"과 "실제 연결"을 분리했다 -- `saveDraft`는 검증 없이
 * fileId를 그대로 왕복시키고(`ApplicationAnswer.fileIds` KDoc 참고), 소유권·목적·상태 검증
 * (`FileLinkPort.validateAndLink`)은 제출이 확정되는 이 시점에만 수행한다.
 *
 * 재제출로 답변의 fileIds가 바뀌면 유지·추가·제거를 모두 반영해야 하는데, `FileLinkPort`는 개별
 * 파일 단위 연결 해제를 제공하지 않고 대상 전체를 한 번에 해제하는 `unlinkAllOf`만 제공한다.
 * `CompanyServiceImpl.linkLogo`/`MemberProfileImageServiceImpl`과 같은 "전체 해제 후 재연결" 패턴을
 * 그대로 따른다 -- 이미 연결된 파일도 `unlink()` 이후 `UPLOADED`로 돌아가 재연결할 수 있다
 * (`StoredFile.unlink`/`linkTo` 참고). 원하는 최종 fileId 집합이 현재 연결된 집합과 같으면 아무 것도
 * 하지 않는다 -- 매 제출마다 불필요하게 해제·재연결하며 File 도메인에 Lock과 로그를 남기지 않기
 * 위해서다.
 *
 * 호출부(`JobApplicationServiceImpl.executeAction`)가 이미 상태 전이와 같은 Transaction 안에서
 * 호출해야 한다(요구사항 "Transaction 일관성" 절과 동일한 이유 -- 파일 연결만 성공하고 상태 전이가
 * Rollback되는 상황을 만들지 않는다).
 */
fun syncApplicationFiles(
    fileLinkPort: FileLinkPort,
    objectMapper: ObjectMapper,
    application: JobApplication,
    requesterId: Long,
) {
    val applicationId = requireNotNull(application.id)
    val desiredFileIds = fileIdsOf(objectMapper, application.answers)
    val currentFileIds =
        fileLinkPort.linkedFilesOf(FileOwnerType.JOB_APPLICATION, applicationId).map { it.fileId }.toSet()
    if (desiredFileIds.toSet() == currentFileIds) return

    if (currentFileIds.isNotEmpty()) {
        fileLinkPort.unlinkAllOf(FileOwnerType.JOB_APPLICATION, applicationId)
    }
    if (desiredFileIds.isNotEmpty()) {
        fileLinkPort.validateAndLink(
            requesterId = requesterId,
            fileIds = desiredFileIds,
            purpose = FilePurpose.JOB_APPLICATION,
            ownerId = applicationId,
        )
    }
}

private fun fileIdsOf(
    objectMapper: ObjectMapper,
    answersJson: String,
): List<Long> {
    if (answersJson.isBlank()) return emptyList()
    return objectMapper
        .readValue(answersJson, Array<ApplicationAnswer>::class.java)
        .flatMap { it.fileIds.orEmpty() }
        .distinct()
}
