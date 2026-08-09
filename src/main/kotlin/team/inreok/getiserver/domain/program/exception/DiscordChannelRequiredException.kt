package team.inreok.getiserver.domain.program.exception

import team.inreok.getiserver.global.error.BusinessException

class DiscordChannelRequiredException : BusinessException(ProgramErrorCode.DISCORD_CHANNEL_REQUIRED)
