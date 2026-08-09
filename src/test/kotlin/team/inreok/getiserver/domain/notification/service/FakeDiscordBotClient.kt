package team.inreok.getiserver.domain.notification.service

/**
 * Test에서 실제 GETI-Bot-V1을 호출하지 않게 하는 Fake다(후속 요구사항 문서 §44).
 *
 * **Production Source에 두지 않는다.** 이 Fake는 Test에서만 필요하고, `src/main`에 두면 운영
 * Context에 Bean 후보가 하나 더 생겨 실수로 선택될 위험이 있다.
 *
 * 덕분에 Test에 Discord Bot Token이나 Internal API Key가 전혀 필요 없고 CI가 외부 Network에
 * 의존하지 않는다.
 */
class FakeDiscordBotClient(
    private var nextResult: DiscordBotResult = DiscordBotResult.Success("discord-message-1"),
) : DiscordBotClient {
    val createCommands = mutableListOf<DiscordCreateCommand>()
    val patchCommands = mutableListOf<DiscordPatchCommand>()

    override fun create(command: DiscordCreateCommand): DiscordBotResult {
        createCommands += command
        return nextResult
    }

    override fun patch(command: DiscordPatchCommand): DiscordBotResult {
        patchCommands += command
        return nextResult
    }

    fun willReturn(result: DiscordBotResult) {
        nextResult = result
    }

    fun callCount(): Int = createCommands.size + patchCommands.size
}
