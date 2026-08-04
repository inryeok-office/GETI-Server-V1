package team.inreok.getiserver.domain.search.scheduler

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.willThrow
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import team.inreok.getiserver.domain.search.entity.SearchIndexFailure
import team.inreok.getiserver.domain.search.entity.type.SearchIndexOperation
import team.inreok.getiserver.domain.search.repository.SearchIndexFailureRepository
import team.inreok.getiserver.domain.search.service.JobIndexSyncService
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
class SearchIndexRetrySchedulerTest {
    @Mock
    private lateinit var failureRepository: SearchIndexFailureRepository

    @Mock
    private lateinit var jobIndexSyncService: JobIndexSyncService

    @Captor
    private lateinit var jobIdCaptor: ArgumentCaptor<Long>

    private val scheduler: SearchIndexRetryScheduler by lazy {
        SearchIndexRetryScheduler(failureRepository, jobIndexSyncService)
    }

    private fun anyDateTime(): LocalDateTime =
        org.mockito.ArgumentMatchers.any(LocalDateTime::class.java) ?: LocalDateTime.now()

    @Test
    fun `재시도 대상이 없으면 아무것도 하지 않는다`() {
        given(failureRepository.findDueForRetry(anyDateTime())).willReturn(emptyList())

        scheduler.retryDueFailures()

        verify(jobIndexSyncService, org.mockito.Mockito.never()).syncOne(anyLong())
    }

    @Test
    fun `재시도 대상 각각을 다시 동기화한다`() {
        val failures =
            listOf(
                SearchIndexFailure(1L, SearchIndexOperation.UPSERT).apply { nextRetryAt = LocalDateTime.now() },
                SearchIndexFailure(2L, SearchIndexOperation.DELETE).apply { nextRetryAt = LocalDateTime.now() },
            )
        given(failureRepository.findDueForRetry(anyDateTime())).willReturn(failures)

        scheduler.retryDueFailures()

        verify(jobIndexSyncService, times(2)).syncOne(jobIdCaptor.capture() ?: 0L)
        assertThat(jobIdCaptor.allValues).containsExactly(1L, 2L)
    }

    @Test
    fun `한 건이 처리 중 예외를 던져도 나머지 재시도는 계속 진행한다`() {
        val failures =
            listOf(
                SearchIndexFailure(1L, SearchIndexOperation.UPSERT).apply { nextRetryAt = LocalDateTime.now() },
                SearchIndexFailure(2L, SearchIndexOperation.UPSERT).apply { nextRetryAt = LocalDateTime.now() },
            )
        given(failureRepository.findDueForRetry(anyDateTime())).willReturn(failures)
        willThrow(RuntimeException("Unexpected")).given(jobIndexSyncService).syncOne(1L)

        scheduler.retryDueFailures()

        verify(jobIndexSyncService).syncOne(2L)
    }
}
