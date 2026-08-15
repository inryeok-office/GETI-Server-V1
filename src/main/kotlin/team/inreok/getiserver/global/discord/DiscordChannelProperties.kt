package team.inreok.getiserver.global.discord

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 학교 Discord 서버의 허용 채널·학년별 Mention Role 목록이다(`app.discord.channel-policy.*`).
 *
 * ## 왜 `global`에 있는가
 *
 * 이 값은 `domain.job`·`domain.program`(등록 API가 받은 채널 값을 허용 목록으로 검증)과
 * `domain.notification`(Delivery를 만들 때 실제 채널·Role을 최종 결정)이 **함께 읽어야** 한다.
 * `domain.notification`은 이미 `job.query`/`program.query`를 소비하므로, 이 설정을
 * `domain.notification`에 두고 Job·Program이 그것을 읽으면 반대 방향 의존이 생겨
 * `ModularityTest`(`modules.verify()`)가 순환으로 실패한다. `job`이나 `program`에 두어도
 * 서로를 참조할 수 없는 것은 마찬가지다. `global`은 어떤 `domain.*`도 참조하지 않고 모든
 * Domain이 자유롭게 참조할 수 있는 유일한 위치다(`docs/architecture/modularity.md` "global
 * Package 책임" — `global.security.JwtProperties`, `global.web.CorsProperties`와 같은 방식으로
 * `@ConfigurationProperties`를 Domain 밖 공통 기반에 두는 저장소 관례를 따른다).
 *
 * ## 실제 값은 비워 둔다
 *
 * 허용 채널 4개와 학년별 Role의 실제 Snowflake가 아직 확정되지 않았다(후속 요구사항 문서 §10).
 * "코드 상수로 임의 지정하지 않는다"는 요구에 따라 여기 채널 Key 3개(`job-notice`,
 * `program-notice`, `inquiry-alert`)는 구조를 보여주기 위한 임시 이름이고, `channel-id` 값은
 * 전부 기본값(빈 문자열)이다. 값이 비어 있으면 [DiscordChannelResolver]가 `null`을 돌려주고,
 * 그 결과 Job/Program 등록 시점의 허용 목록 검증은 항상 실패하며 Notification의 Delivery
 * 생성은 조용히 건너뛴다(Fail-Fast 아님, `DiscordBotProperties.isConfigured()`와 같은 방식).
 * 실제 값이 정해지면 환경변수만 채우면 되고 재배포가 필요 없다.
 */
@ConfigurationProperties(prefix = "app.discord.channel-policy")
data class DiscordChannelProperties(
    val channels: Map<String, DiscordChannelConfig> = emptyMap(),
    /** Key는 학년 숫자를 문자열로 표현한 값이다(예: `"1"`, `"2"`, `"3"`). Value는 Role Snowflake다. */
    val gradeRoles: Map<String, String> = emptyMap(),
    /** 등록 요청에 `discordChannelKey`가 없는 Job이 Delivery를 만들 때 쓰는 기본 채널 Key다. */
    val defaultJobChannelKey: String = "",
    val defaultProgramChannelKey: String = "",
    /** 문의 접수 알림은 사용자가 채널을 선택하지 않고 이 Key로 고정된다(요구사항 §9). */
    val inquiryChannelKey: String = "",
)

data class DiscordChannelConfig(
    val channelId: String = "",
    val displayName: String = "",
)
