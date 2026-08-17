package team.inreok.getiserver.domain.file.archive

import org.springframework.modulith.NamedInterface
import java.io.OutputStream

/**
 * 여러 File을 하나의 ZIP으로 묶어 스트리밍하는 공개 계약이다(Issue #154, File 도메인 Epic #84 Phase 6,
 * `docs/file/file-domain-plan.md` §18 / `docs/file/file_domain_development.md` §23).
 *
 * File 도메인이 판단하지 않는 것: **어떤 신청자를 선택할 수 있는가**, **이 교사가 그 신청자의
 * 파일을 볼 수 있는가**(§23 원문). 이 Port는 [fileIds]를 "호출자가 이미 정당하다고 판단해 넘긴
 * 목록"으로 다룬다 -- `FileLinkPort.validateAndLink`처럼 소유권을 다시 검증하지 않는다는 뜻이다.
 * File 도메인이 여기서 실제로 책임지는 것은 다음 네 가지뿐이다(§23).
 *
 * 1. 권한 검증된 File 목록 조회 -- 존재하지 않거나 보이지 않는 상태([FileErrorCode][team.inreok.getiserver.domain.file.exception.FileErrorCode]
 *    관점에서 `FILE_NOT_FOUND`와 동일한 조건, `FileLinkPort.snapshotsOf`와 같은 판정)의 fileId는
 *    조용히 건너뛴다
 * 2. Storage Stream 획득
 * 3. ZIP 생성(Streaming, 메모리 미적재)
 * 4. Archive 파일명 안전화(경로 구분자 제거, 중복 이름 처리)
 *
 * ZIP Export API Endpoint 자체의 소유권은 이 Port로 가져오지 않는다(§23) -- 예를 들어
 * `GET /api/v1/admin/jobs/{jobId}/applications/export`는 Application 도메인이 소유하고, 그
 * 구현이 신청자 목록·권한을 판정해 fileId 목록을 만든 뒤 이 Port를 호출한다.
 */
@NamedInterface
interface FileArchivePort {
    /**
     * [entries]에 해당하는 File을 하나의 ZIP으로 묶어 [outputStream]에 순서대로 쓴다.
     *
     * 개수·총 용량이 [FileArchiveProperties]의 상한을 넘으면 `FileArchiveTooLargeException`을
     * 던진다(호출자가 무거운 Storage 조회를 시작하기 전에 막기 위해 Storage에 접근하기 전에
     * 먼저 검사한다). [entries]를 모두 조회한 뒤 실제로 포함할 파일이 하나도 없으면(전부 존재하지
     * 않거나 보이지 않는 상태) `FileArchiveEmptyException`을 던진다 -- 빈 ZIP을 내려주지 않는다.
     *
     * 개별 파일을 Storage에서 읽는 도중 오류가 나면 그 파일만 건너뛰고 나머지는 계속 담는다(한
     * 사용자의 File 오류가 전체 다운로드를 막지 않는다, Issue #138 요구사항) -- 다만 그 오류가
     * 해당 파일의 ZIP Entry를 열기(`putNextEntry`) **전에** 발생했을 때만 안전하게 건너뛸 수
     * 있다. Entry를 연 뒤(Body를 일부라도 쓴 뒤) Storage 읽기가 끊기면 그 Entry까지 포함된 ZIP이
     * 잘린 채로 남는다 -- 완전한 원자성을 보장하려면 파일마다 임시 버퍼링이 필요한데, 이번
     * 범위에서는 과한 설계로 판단해 도입하지 않았다(문서화된 한계, 남은 위험으로 보고).
     *
     * 두 개 이상의 [entries]가 같은 [FileArchiveEntry.displayName]으로 정규화되면 뒤에 오는
     * 항목에 `(2)`, `(3)`처럼 번호를 붙여 ZIP 안에서 이름이 겹치지 않게 한다.
     *
     * **[outputStream]을 닫는지 여부는 실행 경로에 따라 다르다**(PR #156 리뷰 반영). `ZipEntry`를
     * 하나라도 쓰기 시작한 뒤(정상 반환이든 이 Method가 던지는 예외든)에는 내부에서 연
     * `ZipOutputStream`을 `close()`하며, 이는 감싸고 있는 [outputStream]까지 함께 닫는다.
     * 반대로 `FileArchiveTooLargeException`/[entries]가 비어 있거나 전부 보이지 않아 던지는
     * `FileArchiveEmptyException`처럼 `ZipOutputStream`을 아예 만들기 전에(Storage에 접근하기
     * 전에) 검증만으로 실패하는 경로에서는 [outputStream]에 전혀 손대지 않는다 -- 호출자가 직접
     * 닫아야 한다. HTTP Response의 `OutputStream`을 넘길 소비 측(Application Phase 10, #138)은
     * 이 비대칭을 감안해 실패 검증 경로에서도 자신이 직접 정리하도록 만들어야 한다.
     *
     * @return 실제로 ZIP에 담긴 fileId와 건너뛴 fileId(사유와 무관하게 모두 포함, **순서를
     *   보장하지 않는다**) 목록.
     */
    fun writeZip(
        entries: List<FileArchiveEntry>,
        outputStream: OutputStream,
    ): FileArchiveResult
}

/**
 * ZIP에 담을 File 하나를 가리킨다.
 *
 * [displayName]을 [FileArchivePort] 호출자가 정하게 한 이유: 지원자별로 폴더처럼 보이는 이름
 * (예: `"홍길동_이력서.pdf"`)을 붙이는 것은 Application 같은 소비 Domain의 표시 정책이지 File
 * 도메인의 관심사가 아니다. File 도메인은 그 이름을 ZIP Entry로 쓰기에 안전하게 다듬기만 한다
 * (경로 구분자 제거, 중복 처리).
 */
@NamedInterface
data class FileArchiveEntry(
    val fileId: Long,
    val displayName: String,
)

@NamedInterface
data class FileArchiveResult(
    val includedFileIds: List<Long>,
    val skippedFileIds: List<Long>,
)
