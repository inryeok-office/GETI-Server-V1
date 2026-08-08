package team.inreok.getiserver.domain.company.access

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import team.inreok.getiserver.domain.file.entity.type.FileOwnerType

/**
 * 로고는 인증된 사용자에게 모두 공개된다. 그래도 이 Class가 필요한 이유는
 * `FileAccessResolver`가 **구현체가 없는 리소스 종류를 기본 거부**하기 때문이다 -- 이 Test가
 * 그 기본 거부에 걸리지 않는 상태를 고정한다.
 */
class CompanyLogoAccessCheckerTest {
    private val checker = CompanyLogoAccessChecker()

    @Test
    fun `COMPANY 종류의 파일을 판정한다`() {
        assertThat(checker.ownerType).isEqualTo(FileOwnerType.COMPANY)
    }

    @Test
    fun `기업을 등록하지 않은 사용자도 로고를 볼 수 있다`() {
        assertThat(checker.canDownload(requesterId = 2L, ownerId = 1L)).isTrue()
    }
}
