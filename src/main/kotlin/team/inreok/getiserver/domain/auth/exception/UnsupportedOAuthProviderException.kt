package team.inreok.getiserver.domain.auth.exception

import team.inreok.getiserver.global.error.BusinessException

class UnsupportedOAuthProviderException(
    provider: String,
) : BusinessException(AuthErrorCode.UNSUPPORTED_OAUTH_PROVIDER, "지원하지 않는 로그인 방식입니다: $provider")
