package team.inreok.getiserver.domain.file.access

import org.springframework.modulith.NamedInterface
import team.inreok.getiserver.domain.file.entity.type.FileOwnerType

/**
 * 파일이 연결된 리소스를 소유한 Domain이 "이 사용자가 이 파일을 받아도 되는가"를 판정한다
 * (요구사항 §16).
 *
 * **Interface를 File 도메인이 소유하고 각 Domain이 구현한다.** 저장소의 다른 공개 Port
 * (`job.query`, `member.query` 등)와 방향이 반대인데, File은 양방향 관계라서 그렇다.
 *
 * ```
 * §12: JobService ──> file.FileLinkPort              domain.job  ──> domain.file
 * §16: 파일 다운로드 권한 판정                        domain.file ──> domain.job (?)
 * ```
 *
 * 두 번째 화살표를 다른 Port들처럼 "소유 Domain이 공개하고 File이 참조"하는 방향으로 만들면
 * 순환 의존이 되어 `ModularityTest`(`modules.verify()`)가 실패한다. Notification은 소비만
 * 하는 단방향이라 그 방식이 가능했지만 File은 아니다. 그래서 의존을 뒤집어 모든 화살표가
 * `domain.X -> domain.file` 한쪽을 향하게 했다.
 *
 * File 도메인은 대상 리소스의 비즈니스 규칙을 알지 못한 채 [ownerType]으로 구현체를 찾아
 * 위임하기만 한다.
 */
@NamedInterface
interface FileAccessChecker {
    /** 이 구현체가 판정하는 리소스 종류. 같은 [FileOwnerType]에 구현체가 둘 이상이면 안 된다. */
    val ownerType: FileOwnerType

    /**
     * @param requesterId 다운로드를 요청한 회원 ID(인증은 이미 통과했다)
     * @param ownerId 파일이 연결된 리소스의 ID
     */
    fun canDownload(
        requesterId: Long,
        ownerId: Long,
    ): Boolean
}
