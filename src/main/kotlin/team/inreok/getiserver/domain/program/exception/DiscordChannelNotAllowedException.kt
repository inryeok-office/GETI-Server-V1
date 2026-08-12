package team.inreok.getiserver.domain.program.exception

import team.inreok.getiserver.global.error.BusinessException

/**
 * 클라이언트가 지정한 `discordChannelId`가 허용 채널 4개 중 하나가 아니다(Notification 후속
 * 요구사항 문서 §10 "등록 API에 discordChannelId가 존재하더라도 서버에서 반드시 허용 목록을
 * 검증해야 합니다").
 */
class DiscordChannelNotAllowedException(
    channelId: String,
) : BusinessException(
        ProgramErrorCode.DISCORD_CHANNEL_NOT_ALLOWED,
        "허용되지 않은 Discord 채널입니다. (discordChannelId=$channelId)",
    )
