package team.inreok.getiserver.domain.program.scheduler

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.eq
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.willThrow
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import team.inreok.getiserver.domain.program.entity.type.ProgramStatus
import team.inreok.getiserver.domain.program.repository.ProgramRepository
import team.inreok.getiserver.domain.program.service.ProgramCloseService
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
class ProgramCloseSchedulerTest {
    @Mock
    private lateinit var programRepository: ProgramRepository

    @Mock
    private lateinit var programCloseService: ProgramCloseService

    private val scheduler: ProgramCloseScheduler by lazy {
        ProgramCloseScheduler(programRepository, programCloseService)
    }

    private fun anyDateTime(): LocalDateTime = any(LocalDateTime::class.java) ?: LocalDateTime.now()

    private fun anyProgramStatus(): ProgramStatus = any(ProgramStatus::class.java) ?: ProgramStatus.PUBLISHED

    @Test
    fun `대상이 없으면 아무것도 하지 않는다`() {
        given(programRepository.findExpiredPublishedIds(anyProgramStatus(), anyDateTime()))
            .willReturn(emptyList())

        scheduler.closeExpiredPrograms()

        verify(programCloseService, never()).closeIfExpired(anyLong(), anyDateTime())
    }

    @Test
    fun `대상 각각을 CLOSED 전이 시도한다`() {
        given(programRepository.findExpiredPublishedIds(anyProgramStatus(), anyDateTime()))
            .willReturn(listOf(1L, 2L))

        scheduler.closeExpiredPrograms()

        verify(programCloseService, times(2)).closeIfExpired(anyLong(), anyDateTime())
        verify(programCloseService).closeIfExpired(eq(1L), anyDateTime())
        verify(programCloseService).closeIfExpired(eq(2L), anyDateTime())
    }

    @Test
    fun `한 건이 처리 중 예외를 던져도 나머지 전이는 계속 진행한다`() {
        given(programRepository.findExpiredPublishedIds(anyProgramStatus(), anyDateTime()))
            .willReturn(listOf(1L, 2L))
        willThrow(RuntimeException("Unexpected")).given(programCloseService).closeIfExpired(eq(1L), anyDateTime())

        scheduler.closeExpiredPrograms()

        verify(programCloseService).closeIfExpired(eq(2L), anyDateTime())
    }
}
