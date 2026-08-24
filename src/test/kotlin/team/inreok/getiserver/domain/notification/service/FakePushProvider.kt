package team.inreok.getiserver.domain.notification.service

/**
 * Test에서 실제 FCM을 호출하지 않게 하는 Fake다(`FakeDiscordBotClient`와 같은 이유·같은 구조).
 *
 * **Production Source에 두지 않는다.** Test에는 이 Fake로 충분하고, `src/main`에 두면 운영
 * Context에 Bean 후보가 하나 더 생겨 실수로 선택될 위험이 있다.
 */
class FakePushProvider(
    private var nextResult: PushSendResult = PushSendResult.Success("push-message-1"),
) : PushProvider {
    val sentCommands = mutableListOf<PushSendCommand>()

    override fun send(command: PushSendCommand): PushSendResult {
        sentCommands += command
        return nextResult
    }

    fun willReturn(result: PushSendResult) {
        nextResult = result
    }

    fun callCount(): Int = sentCommands.size
}
