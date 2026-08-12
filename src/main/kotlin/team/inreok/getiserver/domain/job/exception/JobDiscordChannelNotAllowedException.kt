package team.inreok.getiserver.domain.job.exception

import team.inreok.getiserver.global.error.BusinessException

/**
 * 클라이언트가 지정한 `discordChannelKey`가 허용 채널 목록에 없다(Notification 후속 요구사항
 * 문서 §10 "사용자가 직접 Discord Channel ID를 입력하게 만들면 안 된다"). 사용자가 임의로 채널을
 * 지정할 수 없게 하는 서버 측 방어선이다.
 */
class JobDiscordChannelNotAllowedException(
    key: String,
) : BusinessException(
        JobErrorCode.JOB_DISCORD_CHANNEL_NOT_ALLOWED,
        "허용되지 않은 Discord 채널입니다. (discordChannelKey=$key)",
    )
