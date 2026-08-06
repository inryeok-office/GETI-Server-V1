package team.inreok.getiserver.domain.program.service

import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import team.inreok.getiserver.domain.program.exception.DiscordChannelRequiredException
import team.inreok.getiserver.domain.program.exception.InvalidCapacityException
import team.inreok.getiserver.domain.program.exception.InvalidProgramPeriodException
import team.inreok.getiserver.domain.program.exception.InvalidTargetGradeException
import team.inreok.getiserver.domain.program.exception.ProgramValidationFailedException
import team.inreok.getiserver.domain.program.exception.TargetGradeRequiredException
import java.time.LocalDateTime

class ProgramValidationTest {
    private val now = LocalDateTime.of(2026, 8, 5, 12, 0, 0)

    @Test
    fun `모든 값이 비어 있어도 임시저장 형식 검증은 통과한다`() {
        assertThatCode {
            validateProgramCommon(
                startAt = null,
                endAt = null,
                applicationStartAt = null,
                applicationEndAt = null,
                targetGrades = emptyList(),
                capacity = null,
            )
        }.doesNotThrowAnyException()
    }

    @Test
    fun `행사 시작이 종료보다 늦으면 INVALID_PROGRAM_PERIOD다`() {
        assertThatThrownBy {
            validateProgramCommon(
                startAt = now.plusDays(1),
                endAt = now,
                applicationStartAt = null,
                applicationEndAt = null,
                targetGrades = emptyList(),
                capacity = null,
            )
        }.isInstanceOf(InvalidProgramPeriodException::class.java)
    }

    @Test
    fun `신청 시작이 종료보다 늦으면 INVALID_PROGRAM_PERIOD다`() {
        assertThatThrownBy {
            validateProgramCommon(
                startAt = null,
                endAt = null,
                applicationStartAt = now.plusDays(1),
                applicationEndAt = now,
                targetGrades = emptyList(),
                capacity = null,
            )
        }.isInstanceOf(InvalidProgramPeriodException::class.java)
    }

    @Test
    fun `대상 학년에 1~3 범위 밖 값이 있으면 INVALID_TARGET_GRADE다`() {
        assertThatThrownBy {
            validateProgramCommon(
                startAt = null,
                endAt = null,
                applicationStartAt = null,
                applicationEndAt = null,
                targetGrades = listOf(1, 4),
                capacity = null,
            )
        }.isInstanceOf(InvalidTargetGradeException::class.java)
    }

    @Test
    fun `대상 학년이 중복되면 INVALID_TARGET_GRADE다`() {
        assertThatThrownBy {
            validateProgramCommon(
                startAt = null,
                endAt = null,
                applicationStartAt = null,
                applicationEndAt = null,
                targetGrades = listOf(2, 2),
                capacity = null,
            )
        }.isInstanceOf(InvalidTargetGradeException::class.java)
    }

    @Test
    fun `정원이 0 이하면 INVALID_CAPACITY다`() {
        assertThatThrownBy {
            validateProgramCommon(
                startAt = null,
                endAt = null,
                applicationStartAt = null,
                applicationEndAt = null,
                targetGrades = emptyList(),
                capacity = 0,
            )
        }.isInstanceOf(InvalidCapacityException::class.java)
    }

    @Test
    fun `게시 필수값이 모두 있으면 통과한다`() {
        assertThatCode {
            validateProgramForPublish(
                content = "본문",
                location = "장소",
                startAt = now,
                endAt = now.plusHours(1),
                applicationStartAt = now.minusDays(1),
                applicationEndAt = now.plusDays(1),
                targetGrades = listOf(1, 2),
                discordChannelId = "channel-1",
            )
        }.doesNotThrowAnyException()
    }

    @Test
    fun `게시 시 본문이 비어 있으면 PROGRAM_VALIDATION_FAILED다`() {
        assertThatThrownBy {
            validateProgramForPublish(
                content = " ",
                location = "장소",
                startAt = now,
                endAt = now.plusHours(1),
                applicationStartAt = now.minusDays(1),
                applicationEndAt = now.plusDays(1),
                targetGrades = listOf(1),
                discordChannelId = "channel-1",
            )
        }.isInstanceOf(ProgramValidationFailedException::class.java)
    }

    @Test
    fun `게시 시 대상 학년이 비어 있으면 TARGET_GRADE_REQUIRED다`() {
        assertThatThrownBy {
            validateProgramForPublish(
                content = "본문",
                location = "장소",
                startAt = now,
                endAt = now.plusHours(1),
                applicationStartAt = now.minusDays(1),
                applicationEndAt = now.plusDays(1),
                targetGrades = emptyList(),
                discordChannelId = "channel-1",
            )
        }.isInstanceOf(TargetGradeRequiredException::class.java)
    }

    @Test
    fun `게시 시 Discord 채널이 없으면 DISCORD_CHANNEL_REQUIRED다`() {
        assertThatThrownBy {
            validateProgramForPublish(
                content = "본문",
                location = "장소",
                startAt = now,
                endAt = now.plusHours(1),
                applicationStartAt = now.minusDays(1),
                applicationEndAt = now.plusDays(1),
                targetGrades = listOf(1),
                discordChannelId = null,
            )
        }.isInstanceOf(DiscordChannelRequiredException::class.java)
    }

    @Test
    fun `게시 시 일정이 없으면 INVALID_PROGRAM_PERIOD다`() {
        assertThatThrownBy {
            validateProgramForPublish(
                content = "본문",
                location = "장소",
                startAt = null,
                endAt = null,
                applicationStartAt = now.minusDays(1),
                applicationEndAt = now.plusDays(1),
                targetGrades = listOf(1),
                discordChannelId = "channel-1",
            )
        }.isInstanceOf(InvalidProgramPeriodException::class.java)
    }
}
