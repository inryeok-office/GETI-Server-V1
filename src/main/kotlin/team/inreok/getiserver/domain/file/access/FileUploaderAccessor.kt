package team.inreok.getiserver.domain.file.access

import org.springframework.modulith.NamedInterface

/**
 * 관리자 공통 파일 목록(Issue #225)이 업로더 이름을 보여줄 때 쓰는 배치 조회 계약이다.
 *
 * **Interface를 File 도메인이 소유하고 Member가 구현한다.** 저장소의 다른 공개 Port(`job.query`,
 * `member.query` 등)와 방향이 반대인데, [FileAccessChecker]와 같은 이유다.
 *
 * ```
 * domain.member ──> domain.file   (MemberProfileImageServiceImpl이 FileLinkPort/FileUrlPort 사용)
 * ```
 *
 * 이미 위 방향의 의존이 있어, 업로더 이름 조회를 다른 공개 Port들처럼 "소유 Domain(member)이
 * 공개하고 소비 Domain(file)이 참조"하는 방향으로 만들면 `domain.file -> domain.member ->
 * domain.file` 순환 의존이 되어 `ModularityTest`(`modules.verify()`)가 실패한다(실제로 시도해
 * 확인함). 그래서 [FileAccessChecker]와 동일하게 의존을 뒤집어 File이 Interface를 소유하고
 * `domain.member`(이미 `domain.file`에 의존 중)가 구현해 Bean으로 등록한다.
 *
 * File 도메인은 `Member` Entity나 `MemberRepository`를 직접 참조하지 않는다. 목록 Row마다
 * 개별 조회하지 않도록 반드시 이 배치 Method 하나로 필요한 회원 id 전체를 한 번에 조회한다
 * (N+1 방지, `InquiryMemberSnapshotQueryPort.findAllByIds`와 동일한 원칙).
 */
@NamedInterface
interface FileUploaderAccessor {
    /** 존재하지 않는 id는 결과 Map에서 빠진다. */
    fun findAllByIds(memberIds: Set<Long>): Map<Long, FileUploaderSnapshot>
}

@NamedInterface
data class FileUploaderSnapshot(
    val memberId: Long,
    val name: String?,
)
