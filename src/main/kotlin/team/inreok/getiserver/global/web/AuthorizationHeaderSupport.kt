package team.inreok.getiserver.global.web

import org.springframework.modulith.NamedInterface
import team.inreok.getiserver.global.error.BusinessException
import team.inreok.getiserver.global.error.CommonErrorCode

// Authorization 값 자체는 Spring Security/JWT 도입 전이라 아직 검증하지 않고 존재 여부만 확인한다.
// 여러 Domain Controller가 공통으로 사용해야 하므로 global Module 밖으로 명시적으로 공개한다.
@NamedInterface
object AuthorizationHeaderSupport {
    fun require(authorization: String) {
        if (authorization.isBlank()) {
            throw BusinessException(CommonErrorCode.INVALID_REQUEST, "Authorization Header가 필요합니다.")
        }
    }
}
