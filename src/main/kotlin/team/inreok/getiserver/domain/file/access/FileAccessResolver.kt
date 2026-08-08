package team.inreok.getiserver.domain.file.access

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import team.inreok.getiserver.domain.file.entity.StoredFile
import team.inreok.getiserver.domain.file.entity.type.FileOwnerType
import team.inreok.getiserver.domain.file.entity.type.FileStatus

/**
 * 등록된 [FileAccessChecker]들을 [FileOwnerType]별로 모아 두고 판정을 위임한다.
 *
 * **구현체가 없는 리소스 종류는 거부가 기본값이다.** 아직 파일을 붙이는 기능이 없는 Domain의
 * 파일이 실수로 노출되는 것을 막기 위해서다. 나중에 각 Domain이 파일 첨부를 구현하면서
 * Checker를 함께 등록하면 그때부터 허용된다.
 *
 * 파일 하나에 대한 최종 판정은 [canAccess]에 모아 둔다. 같은 규칙을 여러 Service가 각자
 * 구현하면 한쪽만 고쳐져 우회 경로가 생긴다 -- 실제로 다운로드 API와 이미지 URL 발급이 서로
 * 다른 규칙을 쓰고 있었다.
 */
@Component
class FileAccessResolver(
    checkers: List<FileAccessChecker>,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val checkersByOwnerType: Map<FileOwnerType, FileAccessChecker> =
        checkers.associateBy { it.ownerType }.also {
            // 같은 ownerType에 구현체가 둘이면 associateBy가 조용히 하나를 버려, 어느 쪽이
            // 적용되는지 알 수 없는 상태로 배포된다. 기동 시점에 막는다.
            require(it.size == checkers.size) {
                "FileAccessChecker의 ownerType이 중복됩니다: ${checkers.map { checker -> checker.ownerType }}"
            }
        }

    /**
     * [requesterId]가 [file]에 접근할 수 있는지 판정한다.
     *
     * 업로더 본인은 항상 받을 수 있다. 그 외에는 파일이 연결된 리소스를 소유한 Domain이
     * 판정하며, 아직 어떤 리소스에도 연결되지 않은 파일([FileStatus.UPLOADED])은 업로더만
     * 접근할 수 있다 -- 이것이 요구사항 §14의 "다른 사용자의 미연결 파일 다운로드" 방어다.
     *
     * 파일이 존재하는지, 사용자에게 보이는 상태인지([StoredFile.isVisible])는 호출 측이 먼저
     * 확인한다. 없는 파일과 권한 없는 파일은 응답이 다르기 때문이다(404 vs 403).
     */
    fun canAccess(
        file: StoredFile,
        requesterId: Long,
    ): Boolean {
        val ownerType = file.ownerType
        val ownerId = file.ownerId
        return when {
            file.uploaderMemberId == requesterId -> true

            // 아직 어떤 리소스에도 연결되지 않은 파일은 위임할 곳이 없다. 업로더만 접근한다.
            file.status != FileStatus.LINKED || ownerType == null || ownerId == null -> false

            else -> canDownload(ownerType = ownerType, ownerId = ownerId, requesterId = requesterId)
        }
    }

    fun canDownload(
        ownerType: FileOwnerType,
        ownerId: Long,
        requesterId: Long,
    ): Boolean {
        val checker = checkersByOwnerType[ownerType]
        if (checker == null) {
            log.warn(
                "접근 권한을 판정할 FileAccessChecker가 없어 거부합니다. ownerType={}, ownerId={}",
                ownerType,
                ownerId,
            )
            return false
        }
        return checker.canDownload(requesterId = requesterId, ownerId = ownerId)
    }
}
