package team.inreok.getiserver.domain.program.service.impl

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import team.inreok.getiserver.domain.program.entity.Program
import team.inreok.getiserver.domain.program.entity.type.ProgramStatus
import team.inreok.getiserver.domain.program.entity.type.ProgramType
import team.inreok.getiserver.domain.program.repository.ProgramRepository
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
class ProgramCloseServiceImplTest {
    @Mock
    private lateinit var programRepository: ProgramRepository

    private val service: ProgramCloseServiceImpl by lazy { ProgramCloseServiceImpl(programRepository) }

    private val now = LocalDateTime.of(2026, 8, 13, 12, 0, 0)

    private fun programOf(
        status: ProgramStatus = ProgramStatus.PUBLISHED,
        applicationEndedAt: LocalDateTime? = now.minusDays(1),
    ) = Program(
        createdByMemberId = 7L,
        type = ProgramType.SPECIAL_LECTURE,
        title = "특강",
        status = status,
    ).apply {
        id = 1L
        this.applicationEndedAt = applicationEndedAt
    }

    @Test
    fun `신청 종료 시각이 지난 PUBLISHED Program은 CLOSED로 전이한다`() {
        val program = programOf(applicationEndedAt = now.minusSeconds(1))
        given(programRepository.findByIdForUpdate(1L)).willReturn(program)

        val closed = service.closeIfExpired(1L, now)

        assertThat(closed).isTrue()
        assertThat(program.status).isEqualTo(ProgramStatus.CLOSED)
        verify(programRepository).flush()
    }

    // ProgramEligibility.computeProgramEligibilityReason의 `now.isAfter(applicationEndedAt)`와
    // 동일한 경계다 -- applicationEndedAt과 정확히 같은 시각에는 아직 마감이 아니다.
    @Test
    fun `applicationEndedAt과 정확히 같은 시각에는 아직 전이하지 않는다`() {
        val program = programOf(applicationEndedAt = now)
        given(programRepository.findByIdForUpdate(1L)).willReturn(program)

        val closed = service.closeIfExpired(1L, now)

        assertThat(closed).isFalse()
        assertThat(program.status).isEqualTo(ProgramStatus.PUBLISHED)
        verify(programRepository, never()).flush()
    }

    @Test
    fun `신청 종료 시각이 남은 PUBLISHED Program은 전이하지 않는다(조기 마감 불가)`() {
        val program = programOf(applicationEndedAt = now.plusDays(1))
        given(programRepository.findByIdForUpdate(1L)).willReturn(program)

        val closed = service.closeIfExpired(1L, now)

        assertThat(closed).isFalse()
        assertThat(program.status).isEqualTo(ProgramStatus.PUBLISHED)
    }

    @Test
    fun `PUBLISHED가 아닌 Program은 건드리지 않는다`() {
        val program = programOf(status = ProgramStatus.DRAFT, applicationEndedAt = now.minusDays(1))
        given(programRepository.findByIdForUpdate(1L)).willReturn(program)

        val closed = service.closeIfExpired(1L, now)

        assertThat(closed).isFalse()
        assertThat(program.status).isEqualTo(ProgramStatus.DRAFT)
    }

    @Test
    fun `applicationEndedAt이 없는 Program은 건드리지 않는다`() {
        val program = programOf(applicationEndedAt = null)
        given(programRepository.findByIdForUpdate(1L)).willReturn(program)

        val closed = service.closeIfExpired(1L, now)

        assertThat(closed).isFalse()
    }

    @Test
    fun `대상 Program을 찾지 못하면 아무것도 하지 않는다`() {
        given(programRepository.findByIdForUpdate(1L)).willReturn(null)

        val closed = service.closeIfExpired(1L, now)

        assertThat(closed).isFalse()
    }
}
