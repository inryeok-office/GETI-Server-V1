package team.inreok.getiserver.domain.file.link

import org.springframework.modulith.NamedInterface
import team.inreok.getiserver.domain.file.entity.type.FileOwnerType
import team.inreok.getiserver.domain.file.entity.type.FilePurpose

/**
 * 다른 Domain이 업로드된 파일을 자기 리소스에 연결할 때 쓰는 공개 계약이다(요구사항 §12).
 *
 * 다른 Domain은 `StoredFileRepository`나 `StoredFile` Entity를 직접 만지지 않고 이 Port만
 * 사용한다. 반대로 File 도메인은 대상 리소스의 **비즈니스 권한**을 판정하지 않는다.
 *
 * ```
 * Application 도메인: "이 학생이 이 지원서를 수정할 수 있는가"
 * File 도메인:        "이 파일이 이 학생 소유이며 JOB_APPLICATION 용도인가"
 * ```
 *
 * 의존 방향은 항상 `domain.X -> domain.file` 한쪽이다. 파일 다운로드 권한을 판정할 때 필요한
 * 반대 방향 정보는 각 Domain이
 * [FileAccessChecker][team.inreok.getiserver.domain.file.access.FileAccessChecker]를 구현해
 * 제공한다 -- 그래야 Spring Modulith가 순환 의존으로 실패하지 않는다.
 */
@NamedInterface
interface FileLinkPort {
    /**
     * 소유권·목적·상태·개수를 검증하고 파일들을 대상 리소스에 연결한다.
     *
     * 연결 대상 종류는 [purpose]가 결정한다([FilePurpose.ownerType]). 호출자가 목적과 대상을
     * 따로 넘겨 서로 어긋나는 상황 자체를 만들지 않기 위해서다.
     *
     * 검증 항목(요구사항 §12/§14/§24):
     * 1. `fileIds`에 중복이 없다
     * 2. 모든 파일이 존재한다 -> 없으면 `FILE_NOT_FOUND`
     * 3. 업로드가 끝났고 아직 연결되지 않았다 -> 아니면 `FILE_NOT_FOUND` / `FILE_ALREADY_LINKED`
     * 4. **요청자가 업로드한 파일이다** -> 아니면 `FILE_NOT_OWNED`
     * 5. 파일의 목적이 [purpose]와 같다 -> 아니면 `FILE_PURPOSE_MISMATCH`
     * 6. 연결 후 개수가 목적별 상한 이내다 -> 넘으면 `FILE_COUNT_EXCEEDED`
     *
     * 4번이 §14의 핵심 보안 요구사항이다. File ID는 단순 식별자일 뿐 권한 증명이 아니므로,
     * 이 검사가 없으면 학생 A가 올린 파일을 학생 B가 자기 지원서에 붙일 수 있다.
     *
     * @return 연결된 파일들의 공개 Metadata. 순서는 [fileIds]와 같다.
     */
    fun validateAndLink(
        requesterId: Long,
        fileIds: Collection<Long>,
        purpose: FilePurpose,
        ownerId: Long,
    ): List<FileSnapshot>

    /**
     * 대상 리소스에 연결된 파일을 모두 연결 해제한다. 리소스 수정으로 첨부가 교체되거나 빠질 때
     * 사용한다.
     *
     * Storage Binary를 즉시 지우지 않는다(요구사항 §18/§39) -- 리소스 삭제와 파일 물리 삭제는
     * 다른 사건이고 보존해야 할 이력이 남아 있을 수 있다. 실제 삭제는 Cleanup(Phase 5)이
     * 보존 기간을 보고 판단한다.
     */
    fun unlinkAllOf(
        ownerType: FileOwnerType,
        ownerId: Long,
    )

    /**
     * 파일들의 공개 Metadata를 읽는다. 존재하지 않거나 사용자에게 보이지 않는 상태
     * (PENDING/FAILED/DELETED)의 fileId는 결과에서 빠진다.
     *
     * 목록 응답이 여러 파일을 함께 다루므로 단건 조회 반복(N+1)을 만들지 않도록 배치로 둔다.
     */
    fun snapshotsOf(fileIds: Collection<Long>): Map<Long, FileSnapshot>

    /**
     * 리소스에 현재 연결된(`LINKED`) 파일 전체를 조회한다.
     *
     * Member/Company처럼 리소스 하나가 파일을 최대 1개만 갖는 경우는 이 Method가 필요 없다 --
     * 자기 Entity의 `profileImageFileId`/`logoFileId` 같은 단일 FK 컬럼을 그대로 읽으면 되기
     * 때문이다. 이 Method는 **하나의 리소스가 여러 파일을 가질 수 있고, 그 목록 자체를 응답에
     * 실어야 하는** 경우를 위해 추가했다(Inquiry 도메인이 문의/답변 첨부파일 목록을 §5/§17
     * 응답에 실어야 하는 첫 사례, 요구사항 §7/§14/§45).
     *
     * 대안으로 소비 Domain이 자기 fileId 목록을 별도 연결 테이블에 중복 저장하는 방법도
     * 검토했으나 채택하지 않았다 -- `files.owner_type`/`owner_id`가 이미 그 연결 관계의
     * Source of Truth이고, 별도 테이블을 또 두면 두 저장소가 어긋날 수 있는 상태(연결은
     * 됐는데 Join 테이블에 없거나, 그 반대인 상태)를 새로 만들 뿐이다. 다음에 같은 요구(리소스
     * 하나가 여러 파일을 갖고 그 목록을 응답에 실어야 하는 경우)가 생기면 새 연결 테이블을
     * 만들지 말고 이 Method를 그대로 재사용한다.
     *
     * [ownerType]/[ownerId] 조합은 [unlinkAllOf]와 같은 순서를 쓴다.
     */
    fun linkedFilesOf(
        ownerType: FileOwnerType,
        ownerId: Long,
    ): List<FileSnapshot>

    /**
     * [linkedFilesOf]의 배치 버전이다. 여러 리소스에 연결된 파일을 한 번에 조회해
     * `ownerId -> 파일 목록` Map으로 돌려준다.
     *
     * 문의 상세처럼 답변 여러 건의 첨부파일 목록을 한 응답에 함께 실어야 하는 경우, 답변 수만큼
     * [linkedFilesOf]를 반복 호출하면 N+1이 된다.
     * [team.inreok.getiserver.domain.member.query.InquiryMemberSnapshotQueryPort.findAllByIds]와
     * 같은 배치 원칙을 따른다.
     *
     * 첨부파일이 없는 [ownerId]는 결과 Map에서 빠진다(빈 List Entry를 만들지 않는다) -- 호출
     * 측이 `map[ownerId].orEmpty()`로 다루면 된다.
     */
    fun linkedFilesOf(
        ownerType: FileOwnerType,
        ownerIds: Collection<Long>,
    ): Map<Long, List<FileSnapshot>>
}
