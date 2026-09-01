package team.inreok.getiserver.global.discord

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.modulith.NamedInterface
import org.springframework.stereotype.Component

/**
 * [DiscordChannelProperties]를 근거로 채널 허용 여부를 검증하고, Mention Role을 계산하는
 * 단일 지점이다. Job·Program 등록 시점의 입력 검증과, Notification이 Delivery를 만들 때의
 * 최종 채널·Role 결정에 **같은 로직**을 쓴다 — 두 곳이 각자 판정 규칙을 흉내 내면 어긋날 수
 * 있기 때문이다.
 *
 * `domain.job`·`domain.program`·`domain.notification`이 모두 이 Class를 참조하므로 `@NamedInterface`로
 * 공개한다 -- `global`도 `ApplicationModules.of(...)`가 인식하는 Module 중 하나라, 다른 Module이
 * 참조하는 `global`의 Type도 `global.error.BusinessException`/`ErrorCode`와 같은 방식으로
 * 공개해야 `ModularityTest`(`modules.verify()`)를 통과한다.
 */
@NamedInterface
@Component
@EnableConfigurationProperties(DiscordChannelProperties::class)
class DiscordChannelResolver(
    private val properties: DiscordChannelProperties,
) {
    /** Job 등록·수정 요청이 지정한 채널 Key가 허용 목록에 있고 실제 채널이 설정됐는지다. */
    fun isAllowedJobChannelKey(key: String): Boolean = channelIdOf(key) != null

    /** Program 등록 요청이 지정한 채널 Snowflake가 허용 채널 4개 중 하나인지다. */
    fun isAllowedProgramChannelId(channelId: String): Boolean =
        properties.channels.values.any { it.channelId.isNotBlank() && it.channelId == channelId }

    /**
     * Job Delivery에 실제로 쓸 채널 Snowflake다. `key`가 없으면 기본 채널로 대체한다. 어느 쪽도
     * 설정돼 있지 않으면 `null`이다 — 호출부(Notification Listener)는 이 경우 Delivery를 만들지
     * 않고 경고만 남긴다.
     */
    fun resolveJobChannelId(key: String?): String? =
        channelIdOf(key?.takeIf { it.isNotBlank() } ?: properties.defaultJobChannelKey)

    /**
     * Program은 등록 시점에 이미 원시 Snowflake를 저장하므로 조회가 필요 없다. 빈 문자열은
     * `null`로 정규화만 한다.
     */
    fun resolveProgramChannelId(channelId: String?): String? = channelId?.takeIf { it.isNotBlank() }

    fun resolveInquiryChannelId(): String? = channelIdOf(properties.inquiryChannelKey)

    /**
     * 학년 목록을 Mention Role Snowflake 목록으로 바꾼다. 매핑이 없는 학년은 조용히 빠진다 —
     * Role이 없다고 Delivery 생성 자체를 막을 이유는 없다(Mention은 부가 기능).
     */
    fun roleIdsForGrades(grades: Collection<Int>): List<String> =
        grades
            .mapNotNull { grade -> properties.gradeRoles[grade.toString()]?.takeIf { it.isNotBlank() } }
            .distinct()

    private fun channelIdOf(key: String?): String? =
        key
            ?.takeIf { it.isNotBlank() }
            ?.let { properties.channels[it]?.channelId }
            ?.takeIf { it.isNotBlank() }
}
