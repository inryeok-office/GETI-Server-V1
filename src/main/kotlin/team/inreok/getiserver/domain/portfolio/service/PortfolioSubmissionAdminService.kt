package team.inreok.getiserver.domain.portfolio.service

import org.springframework.data.domain.Pageable
import team.inreok.getiserver.domain.file.archive.FileArchiveEntry
import team.inreok.getiserver.domain.portfolio.dto.PortfolioSubmissionStatusListResponse
import java.io.OutputStream

/**
 * 관리자(교사·개발자)가 수합 요청의 제출 현황을 조회하고 제출 자료를 일괄 다운로드하는 Use Case다
 * (요구사항 §19/§24, Issue #204 Phase 2b).
 *
 * 소유권(등록자 본인만)까지 검증하지 않고 역할(TEACHER/DEVELOPER)만 본다 -- `SecurityConfig`가
 * `/api/v1/admin/portfolio-requests` 하위 경로를 두 역할로 이미 제한하며, 이는 등록자 소유권을
 * 강제하지 않는 기존 Portfolio 관리 API(PortfolioRequestAdminController)와 같은 관례다.
 *
 * 제출 자료 조회([buildExportEntries])와 ZIP Streaming([writeZip])을 분리한 이유는 Storage Streaming이
 * 느린 외부 I/O라 DB Transaction 밖에서 실행해야 하기 때문이다(§17, JobApplicationExportService와 동일).
 */
interface PortfolioSubmissionAdminService {
    /**
     * 대상 학생 전체를 기준으로 제출 현황을 조회한다. 아직 제출하지 않은 학생도 "미제출"로 포함한다.
     *
     * [submitted]가 있으면 제출 완료(SUBMITTED) 여부로 좁히고, [name]이 있으면 학생 이름으로 좁힌다.
     * 이름 검색·정렬은 `member` Module 소유라 `portfolio`에서 직접 Join할 수 없어, 대상·제출물·프로필을
     * 각각 배치로 조회한 뒤 Service 계층에서 합쳐 필터·정렬·Pagination한다(대상은 요청당 유한하다).
     */
    fun getSubmissionStatuses(
        requestId: Long,
        submitted: Boolean?,
        name: String?,
        pageable: Pageable,
    ): PortfolioSubmissionStatusListResponse

    /**
     * 일괄 다운로드에 담을 File 목록을 만든다. [submittedOnly]가 true면 제출 완료(SUBMITTED) 제출물의
     * 파일만 담는다. 내려받을 파일이 하나도 없으면 `NoSubmissionsToExportException`을 던진다 --
     * Storage에 접근하거나 응답 Byte를 쓰기 전에 판정해, 정상적인 JSON 오류로 나가게 한다.
     */
    fun buildExportEntries(
        requestId: Long,
        submittedOnly: Boolean,
    ): List<FileArchiveEntry>

    /** [buildExportEntries]가 만든 목록을 ZIP으로 묶어 [outputStream]에 쓴다. 별도 Transaction을 열지 않는다. */
    fun writeZip(
        entries: List<FileArchiveEntry>,
        outputStream: OutputStream,
    )
}
