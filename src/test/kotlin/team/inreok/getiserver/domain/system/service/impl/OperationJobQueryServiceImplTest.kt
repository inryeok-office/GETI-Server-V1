package team.inreok.getiserver.domain.system.service.impl

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.data.domain.PageRequest
import team.inreok.getiserver.domain.collector.notification.query.JobNotificationSchedulerStatusQueryPort
import team.inreok.getiserver.domain.collector.query.CollectorSchedulerStatusQueryPort
import team.inreok.getiserver.domain.notification.query.DiscordDeliverySchedulerStatusQueryPort
import team.inreok.getiserver.domain.operation.query.OperationJobState
import team.inreok.getiserver.domain.program.query.ProgramCloseSchedulerStatusQueryPort
import team.inreok.getiserver.domain.recommendation.query.RecommendationSchedulerStatusQueryPort
import team.inreok.getiserver.domain.search.query.SearchIndexSchedulerStatusQueryPort
import team.inreok.getiserver.domain.system.dto.OperationJobActionStatus

@ExtendWith(MockitoExtension::class)
class OperationJobQueryServiceImplTest {
    @Mock
    private lateinit var collectorStatusPort: CollectorSchedulerStatusQueryPort

    @Mock
    private lateinit var jobNotificationStatusPort: JobNotificationSchedulerStatusQueryPort

    @Mock
    private lateinit var discordDeliveryStatusPort: DiscordDeliverySchedulerStatusQueryPort

    @Mock
    private lateinit var programCloseStatusPort: ProgramCloseSchedulerStatusQueryPort

    @Mock
    private lateinit var recommendationStatusPort: RecommendationSchedulerStatusQueryPort

    @Mock
    private lateinit var searchIndexStatusPort: SearchIndexSchedulerStatusQueryPort

    @Test
    fun `실제 여섯 Scheduler를 안정적인 업무 ID와 업무명으로 매핑한다`() {
        val service = service()

        val response = service.search(null, null, null, null, PageRequest.of(0, 10))

        assertThat(response.content).hasSize(6)
        assertThat(response.content.map { it.taskId.name })
            .containsExactly(
                "JOB_COLLECTION",
                "JOB_NOTIFICATION_RETRY",
                "DISCORD_DELIVERY_RETRY",
                "PROGRAM_CLOSE",
                "RECOMMENDATION_GENERATION",
                "SEARCH_INDEX_RETRY",
            )
        assertThat(response.content[0].name).isEqualTo("채용 공고 수집")
        assertThat(response.content[0].nextRunAt).isNotNull()
        assertThat(response.content[1].nextRunAt).isNull()
        assertThat(response.content[0].actionStatus).isEqualTo(OperationJobActionStatus.SUPPORTED)
        assertThat(response.content[3].actionStatus).isEqualTo(OperationJobActionStatus.UNSUPPORTED)
    }

    @Test
    fun `상태와 업무 유형 필터 및 페이지를 적용한다`() {
        val service = service()

        val response =
            service.search(
                jobType = team.inreok.getiserver.domain.operation.entity.type.OperationJobType.PROGRAM_CLOSE,
                status = OperationJobState.NO_HISTORY_STATUS,
                startAt = null,
                endAt = null,
                pageable = PageRequest.of(0, 1),
            )

        assertThat(response.totalElements).isEqualTo(1)
        assertThat(response.content).singleElement().extracting { it.taskId.name }.isEqualTo("PROGRAM_CLOSE")
    }

    private fun service() =
        run {
            given(collectorStatusPort.getStatus()).willReturn(OperationJobState())
            given(jobNotificationStatusPort.getStatus()).willReturn(OperationJobState())
            given(discordDeliveryStatusPort.getStatus()).willReturn(OperationJobState())
            given(programCloseStatusPort.getStatus()).willReturn(OperationJobState())
            given(recommendationStatusPort.getStatus()).willReturn(OperationJobState())
            given(searchIndexStatusPort.getStatus()).willReturn(OperationJobState())
            OperationJobQueryServiceImpl(
                collectorStatusPort,
                jobNotificationStatusPort,
                discordDeliveryStatusPort,
                programCloseStatusPort,
                recommendationStatusPort,
                searchIndexStatusPort,
                "0 0 3 * * *",
                300000,
                60000,
                60000,
                "0 0 4 * * *",
                60000,
            )
        }
}
