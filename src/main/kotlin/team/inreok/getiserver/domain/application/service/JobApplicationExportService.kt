package team.inreok.getiserver.domain.application.service

import team.inreok.getiserver.domain.file.archive.FileArchiveEntry
import java.io.OutputStream

/**
 * Application Phase 10(Issue #138). 한 공고의 지원자 자료를 ZIP으로 묶어 스트리밍한다. ZIP
 * 생성 자체는 File 도메인의 `FileArchivePort`(Issue #154)를 그대로 쓰고, 이 Service는
 * "이 공고의 어떤 지원자가 대상인가"와 "이 요청자가 이 공고의 자료를 받을 권한이 있는가"만
 * 판단한다(§23 원칙 -- File은 업무 권한을 판정하지 않는다).
 *
 * Method를 [buildExportEntries]/[writeZip] 둘로 나눈 이유: 전자는 DB만 읽는 짧은 읽기 전용
 * Transaction이고, 후자(Storage Streaming)는 느린 외부 I/O라 Transaction 밖에서 실행해야
 * 한다(`.claude/rules/spring-boot.md`). 하나의 Method 안에서 같은 Bean의 다른 `@Transactional`
 * Method를 호출하면(Self-invocation) Spring AOP Proxy를 거치지 않아 Transaction이 걸리지
 * 않으므로, Controller가 이 둘을 순서대로 호출하는 두 단계로 공개 계약 자체를 나눴다.
 */
interface JobApplicationExportService {
    /**
     * @param applicationIds 지정하면 이 지원서들의 자료만 대상으로 한다(Issue #203). `null`이면
     *   기존과 동일하게 이 공고의 전체 지원자가 대상이다(하위 호환). 이 공고에 속하지 않거나
     *   존재하지 않는 `applicationId`는 오류로 처리하지 않고 조용히 무시한다.
     * @throws team.inreok.getiserver.domain.application.exception.JobNotFoundException 공고가 없음
     * @throws team.inreok.getiserver.domain.application.exception.ApplicationReviewForbiddenException
     *   요청자가 이 공고의 등록자·담당 교사·개발자가 아님
     */
    fun buildExportEntries(
        jobId: Long,
        requesterMemberId: Long,
        isDeveloper: Boolean,
        applicationIds: List<Long>? = null,
    ): List<FileArchiveEntry>

    /** `FileArchivePort.writeZip`의 얇은 위임이다 -- Controller가 File Port를 직접 참조하지 않게 한다. */
    fun writeZip(
        entries: List<FileArchiveEntry>,
        outputStream: OutputStream,
    )
}
