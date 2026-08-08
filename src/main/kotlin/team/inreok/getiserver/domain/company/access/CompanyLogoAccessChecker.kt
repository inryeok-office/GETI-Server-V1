package team.inreok.getiserver.domain.company.access

import org.springframework.stereotype.Component
import team.inreok.getiserver.domain.file.access.FileAccessChecker
import team.inreok.getiserver.domain.file.entity.type.FileOwnerType

/**
 * 기업 로고의 다운로드 권한을 판정한다. `ownerId`는 로고가 연결된 기업의 ID다.
 *
 * 인증된 사용자면 모두 허용한다. 로고는 `GET /api/v1/companies`가 이미 인증만 요구하는 기업
 * 목록에 그대로 표시되는 정보이고, 회원 프로필과 달리 공개/비공개 구분이 없다.
 *
 * 그래도 [FileAccessChecker]를 구현하는 이유는 `FileAccessResolver`가 **구현체가 없는 리소스
 * 종류를 거부**하기 때문이다. 이 Class가 없으면 로고 다운로드가 전부 403이 된다.
 */
@Component
class CompanyLogoAccessChecker : FileAccessChecker {
    override val ownerType: FileOwnerType = FileOwnerType.COMPANY

    override fun canDownload(
        requesterId: Long,
        ownerId: Long,
    ): Boolean = true
}
