package team.inreok.getiserver.domain.file.access

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import team.inreok.getiserver.domain.file.entity.type.FileOwnerType

/**
 * 등록된 [FileAccessChecker]들을 [FileOwnerType]별로 모아 두고 판정을 위임한다.
 *
 * **구현체가 없는 리소스 종류는 거부가 기본값이다.** 아직 파일을 붙이는 기능이 없는 Domain의
 * 파일이 실수로 노출되는 것을 막기 위해서다. 나중에 각 Domain이 파일 첨부를 구현하면서
 * Checker를 함께 등록하면 그때부터 허용된다.
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
