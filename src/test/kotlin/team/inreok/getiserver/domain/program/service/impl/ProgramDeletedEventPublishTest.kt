package team.inreok.getiserver.domain.program.service.impl

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.atLeast
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import org.springframework.context.ApplicationEventPublisher
import team.inreok.getiserver.domain.application.query.ProgramFormLinkQueryPort
import team.inreok.getiserver.domain.file.link.FileLinkPort
import team.inreok.getiserver.domain.member.query.MemberApplicantSnapshot
import team.inreok.getiserver.domain.member.query.MemberApplicantSnapshotQueryPort
import team.inreok.getiserver.domain.member.query.MemberRoleQueryPort
import team.inreok.getiserver.domain.program.dto.ProgramStatusUpdateRequest
import team.inreok.getiserver.domain.program.entity.Program
import team.inreok.getiserver.domain.program.entity.ProgramTargetGrade
import team.inreok.getiserver.domain.program.entity.ProgramTargetGradeId
import team.inreok.getiserver.domain.program.entity.type.ProgramStatus
import team.inreok.getiserver.domain.program.entity.type.ProgramType
import team.inreok.getiserver.domain.program.event.ProgramDeletedEvent
import team.inreok.getiserver.domain.program.exception.ProgramReopenNotAllowedException
import team.inreok.getiserver.domain.program.repository.ProgramApplicationRepository
import team.inreok.getiserver.domain.program.repository.ProgramRepository
import team.inreok.getiserver.domain.program.repository.ProgramTargetGradeRepository
import team.inreok.getiserver.domain.program.service.ProgramService
import team.inreok.getiserver.global.discord.DiscordChannelResolver
import tools.jackson.databind.json.JsonMapper
import java.time.LocalDateTime

/**
 * 신청자 인앱 알림용 [ProgramDeletedEvent] 발행 규칙을 검증한다(Issue #118). [ProgramServiceImplTest]가
 * detekt LargeClass 임계값에 이미 근접해 있어 별도 Class로 분리했다.
 *
 * Discord Event([team.inreok.getiserver.domain.program.event.ProgramDiscordEvent])와 달리 DRAFT를
 * 거르지 않는다 -- 게시된 적 없는 프로그램에는 신청자가 있을 수 없어 구독 측이 수신자 0명으로
 * 아무 알림도 만들지 않으므로, 발행 단계에 상태 예외를 두지 않는다(`ProgramServiceImpl.changeStatus`
 * 주석 참고).
 */
@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProgramDeletedEventPublishTest {
    @Mock
    private lateinit var programRepository: ProgramRepository

    @Mock
    private lateinit var programTargetGradeRepository: ProgramTargetGradeRepository

    @Mock
    private lateinit var programApplicationRepository: ProgramApplicationRepository

    @Mock
    private lateinit var programFormLinkQueryPort: ProgramFormLinkQueryPort

    @Mock
    private lateinit var memberApplicantSnapshotQueryPort: MemberApplicantSnapshotQueryPort

    @Mock
    private lateinit var memberRoleQueryPort: MemberRoleQueryPort

    @Mock
    private lateinit var fileLinkPort: FileLinkPort

    @Mock
    private lateinit var eventPublisher: ApplicationEventPublisher

    @Mock
    private lateinit var discordChannelResolver: DiscordChannelResolver

    // 위치 인자로 두면 ProgramServiceImpl 생성자에 Parameter가 추가/재배치될 때마다(Issue #127의
    // memberRoleQueryPort/fileLinkPort 추가가 실제로 이 Test를 깨뜨렸다) 자리만 밀려 엉뚱한 Mock이
    // 엉뚱한 위치에 들어가도 컴파일이 통과해버릴 수 있다. Named Argument는 Parameter 이름이
    // 바뀌지 않는 한 순서가 바뀌어도 안전하다.
    private val service: ProgramService by lazy {
        ProgramServiceImpl(
            programRepository = programRepository,
            programTargetGradeRepository = programTargetGradeRepository,
            programApplicationRepository = programApplicationRepository,
            programFormLinkQueryPort = programFormLinkQueryPort,
            memberApplicantSnapshotQueryPort = memberApplicantSnapshotQueryPort,
            memberRoleQueryPort = memberRoleQueryPort,
            fileLinkPort = fileLinkPort,
            objectMapper = JsonMapper(),
            eventPublisher = eventPublisher,
            discordChannelResolver = discordChannelResolver,
        )
    }

    private val now = LocalDateTime.of(2026, 8, 5, 12, 0, 0)

    @Test
    fun `게시된 프로그램을 삭제하면 제목을 담은 ProgramDeletedEvent를 발행한다`() {
        given(programRepository.findByIdForUpdate(1L)).willReturn(programOf(ProgramStatus.PUBLISHED))

        service.changeStatus(1L, requesterMemberId = 7L, isDeveloper = false, request = deleteRequest())

        assertThat(publishedDeletedEvents()).containsExactly(ProgramDeletedEvent(1L, "특강"))
    }

    @Test
    fun `DRAFT를 삭제해도 ProgramDeletedEvent를 발행한다`() {
        given(programRepository.findByIdForUpdate(1L)).willReturn(programOf(ProgramStatus.DRAFT))

        service.changeStatus(1L, requesterMemberId = 7L, isDeveloper = false, request = deleteRequest())

        assertThat(publishedDeletedEvents()).containsExactly(ProgramDeletedEvent(1L, "특강"))
    }

    @Test
    fun `삭제가 아닌 상태 전이에서는 ProgramDeletedEvent를 발행하지 않는다`() {
        given(programRepository.findByIdForUpdate(1L)).willReturn(publishableDraft())
        given(programTargetGradeRepository.findAllByIdProgramId(1L))
            .willReturn(listOf(ProgramTargetGrade(ProgramTargetGradeId(1L, 2))))
        given(memberApplicantSnapshotQueryPort.findById(7L)).willReturn(teacherSnapshot())

        service.changeStatus(
            1L,
            requesterMemberId = 7L,
            isDeveloper = false,
            request = ProgramStatusUpdateRequest(status = ProgramStatus.PUBLISHED),
        )

        assertThat(publishedDeletedEvents()).isEmpty()
    }

    @Test
    fun `상태 전이가 거부되면 ProgramDeletedEvent를 발행하지 않는다`() {
        given(programRepository.findByIdForUpdate(1L)).willReturn(programOf(ProgramStatus.CLOSED))

        assertThatThrownBy {
            service.changeStatus(
                1L,
                requesterMemberId = 7L,
                isDeveloper = false,
                request = ProgramStatusUpdateRequest(status = ProgramStatus.PUBLISHED),
            )
        }.isInstanceOf(ProgramReopenNotAllowedException::class.java)

        assertThat(publishedDeletedEvents()).isEmpty()
    }

    /**
     * 발행된 Event 중 [ProgramDeletedEvent]만 골라낸다. Event 종류를 구분하지 않으면 함께 발행되는
     * `ProgramDiscordEvent`까지 섞여 검증이 흐려진다.
     */
    private fun publishedDeletedEvents(): List<ProgramDeletedEvent> {
        val captor = ArgumentCaptor.forClass(Any::class.java)
        verify(eventPublisher, atLeast(0)).publishEvent(captor.capture() ?: Any())
        return captor.allValues.filterIsInstance<ProgramDeletedEvent>()
    }

    private fun deleteRequest() = ProgramStatusUpdateRequest(status = ProgramStatus.DELETED)

    private fun programOf(status: ProgramStatus) =
        Program(
            createdByMemberId = 7L,
            type = ProgramType.SPECIAL_LECTURE,
            title = "특강",
            status = status,
        ).apply { id = 1L }

    /** 게시 필수값을 모두 갖춘 DRAFT다 -- PUBLISHED 전이가 게시 검증을 수행한다. */
    private fun publishableDraft() =
        programOf(ProgramStatus.DRAFT).apply {
            bodyMarkdown = "본문"
            location = "장소"
            eventStartedAt = now
            eventEndedAt = now.plusHours(1)
            applicationStartedAt = now.minusDays(1)
            applicationEndedAt = now.plusDays(1)
            discordChannelId = "channel-1"
        }

    private fun teacherSnapshot() =
        MemberApplicantSnapshot(
            memberId = 7L,
            name = "홍길동",
            email = "teacher@example.com",
            phone = null,
            academicStatus = null,
            grade = null,
            cohort = null,
            department = null,
            majors = emptyList(),
            techStacks = emptyList(),
            desiredJob = null,
        )
}
