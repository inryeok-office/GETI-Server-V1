package team.inreok.getiserver.domain.program.service.impl

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyList
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.isNull
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.atLeast
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import team.inreok.getiserver.domain.application.query.ProgramFormLinkQueryPort
import team.inreok.getiserver.domain.file.entity.type.FileOwnerType
import team.inreok.getiserver.domain.file.entity.type.FilePurpose
import team.inreok.getiserver.domain.file.exception.FileNotOwnedException
import team.inreok.getiserver.domain.file.link.FileLinkPort
import team.inreok.getiserver.domain.file.link.FileSnapshot
import team.inreok.getiserver.domain.member.entity.type.RoleType
import team.inreok.getiserver.domain.member.query.MemberApplicantSnapshot
import team.inreok.getiserver.domain.member.query.MemberApplicantSnapshotQueryPort
import team.inreok.getiserver.domain.member.query.MemberRoleQueryPort
import team.inreok.getiserver.domain.program.dto.ProgramApplicationActionRequest
import team.inreok.getiserver.domain.program.dto.ProgramCreateRequest
import team.inreok.getiserver.domain.program.dto.ProgramStatusUpdateRequest
import team.inreok.getiserver.domain.program.dto.ProgramUpdateRequest
import team.inreok.getiserver.domain.program.entity.Program
import team.inreok.getiserver.domain.program.entity.ProgramApplication
import team.inreok.getiserver.domain.program.entity.ProgramTargetGrade
import team.inreok.getiserver.domain.program.entity.ProgramTargetGradeId
import team.inreok.getiserver.domain.program.entity.type.ProgramApplicationAction
import team.inreok.getiserver.domain.program.entity.type.ProgramApplicationStatus
import team.inreok.getiserver.domain.program.entity.type.ProgramStatus
import team.inreok.getiserver.domain.program.entity.type.ProgramType
import team.inreok.getiserver.domain.program.event.ProgramDiscordAction
import team.inreok.getiserver.domain.program.event.ProgramDiscordEvent
import team.inreok.getiserver.domain.program.exception.ActiveApplicationNotFoundException
import team.inreok.getiserver.domain.program.exception.AlreadyAppliedException
import team.inreok.getiserver.domain.program.exception.CapacityBelowCurrentApplicantsException
import team.inreok.getiserver.domain.program.exception.DiscordChannelNotAllowedException
import team.inreok.getiserver.domain.program.exception.DiscordChannelRequiredException
import team.inreok.getiserver.domain.program.exception.InvalidCapacityException
import team.inreok.getiserver.domain.program.exception.NotEnrolledException
import team.inreok.getiserver.domain.program.exception.ProgramActionNotAvailableException
import team.inreok.getiserver.domain.program.exception.ProgramClosedException
import team.inreok.getiserver.domain.program.exception.ProgramDeletedException
import team.inreok.getiserver.domain.program.exception.ProgramFormNotLinkableException
import team.inreok.getiserver.domain.program.exception.ProgramFullException
import team.inreok.getiserver.domain.program.exception.ProgramManageForbiddenException
import team.inreok.getiserver.domain.program.exception.ProgramNotFoundException
import team.inreok.getiserver.domain.program.exception.ProgramReopenNotAllowedException
import team.inreok.getiserver.domain.program.exception.ProgramStatusTransitionNotAllowedException
import team.inreok.getiserver.domain.program.exception.ProgramValidationFailedException
import team.inreok.getiserver.domain.program.repository.ProgramApplicantCount
import team.inreok.getiserver.domain.program.repository.ProgramApplicationRepository
import team.inreok.getiserver.domain.program.repository.ProgramRepository
import team.inreok.getiserver.domain.program.repository.ProgramTargetGradeRepository
import team.inreok.getiserver.domain.program.service.ProgramService
import team.inreok.getiserver.global.discord.DiscordChannelResolver
import tools.jackson.databind.json.JsonMapper
import java.time.LocalDateTime
import java.util.Optional

// ProgramServiceImpl 자체가 등록·수정·상태 변경·목록·상세·신청/취소를 한 Class에 담당해(Class
// KDoc 참고) 이 Class를 검증하는 Test도 자연히 커진다. Service Method 단위로 여러 Test Class로
// 쪼개면 Fixture(programOf/draftRequest 등)를 중복 정의하거나 별도 Test Support Package가
// 필요해져(docs/architecture/modularity.md "실제 공유 Fixture가 필요해지기 전에는 Test Support
// Package를 만들지 않는다"), Production Class와 Test Class의 1:1 대응을 깨는 비용이 더 크다.
@Suppress("LargeClass")
@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProgramServiceImplTest {
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

    private val service: ProgramService by lazy {
        // create() Test 대부분이 publishableRequest()의 기본 채널("channel-1")을 그대로 쓴다.
        given(discordChannelResolver.isAllowedProgramChannelId("channel-1")).willReturn(true)
        ProgramServiceImpl(
            programRepository,
            programTargetGradeRepository,
            programApplicationRepository,
            programFormLinkQueryPort,
            memberApplicantSnapshotQueryPort,
            memberRoleQueryPort,
            fileLinkPort,
            JsonMapper(),
            eventPublisher,
            discordChannelResolver,
        )
    }

    private val now = LocalDateTime.of(2026, 8, 5, 12, 0, 0)

    // --- 등록 ---

    @Test
    fun `DRAFT는 본문·장소·일정이 없어도 임시저장된다`() {
        givenSaveAssignsId()

        val response = service.create(draftRequest(), createdByMemberId = 7L)

        assertThat(response.status).isEqualTo(ProgramStatus.DRAFT)
        assertThat(response.manager).isNull()
    }

    @Test
    fun `PUBLISHED로 등록하면 등록자가 담당 교사가 된다`() {
        givenSaveAssignsId()
        given(memberApplicantSnapshotQueryPort.findById(7L)).willReturn(teacherSnapshot(7L))

        val response = service.create(publishableRequest(), createdByMemberId = 7L)

        assertThat(response.status).isEqualTo(ProgramStatus.PUBLISHED)
        assertThat(response.manager?.memberId).isEqualTo(7L)
    }

    @Test
    fun `PUBLISHED 등록 시 본문이 없으면 거부한다`() {
        assertThatThrownBy { service.create(publishableRequest(content = null), 7L) }
            .isInstanceOf(ProgramValidationFailedException::class.java)
    }

    @Test
    fun `PUBLISHED 등록 시 Discord 채널이 없으면 거부한다`() {
        assertThatThrownBy { service.create(publishableRequest(discordChannelId = null), 7L) }
            .isInstanceOf(DiscordChannelRequiredException::class.java)
    }

    @Test
    fun `연결할 수 없는 양식을 지정하면 거부한다`() {
        given(programFormLinkQueryPort.findLinkableProgramForm(99L, 7L)).willReturn(null)

        assertThatThrownBy { service.create(draftRequest(formId = 99L), 7L) }
            .isInstanceOf(ProgramFormNotLinkableException::class.java)
    }

    @Test
    fun `등록 시 fileIds가 있으면 저장된 programId로 FileLinkPort를 호출한다`() {
        givenSaveAssignsId()

        service.create(draftRequest(fileIds = listOf(1L, 2L)), createdByMemberId = 7L)

        verify(fileLinkPort).validateAndLink(
            requesterId = 7L,
            fileIds = listOf(1L, 2L),
            purpose = FilePurpose.PROGRAM_ATTACHMENT,
            ownerId = 1L,
        )
    }

    @Test
    fun `등록 시 fileIds가 비어 있으면 FileLinkPort를 호출하지 않는다`() {
        givenSaveAssignsId()

        service.create(draftRequest(), createdByMemberId = 7L)

        verify(fileLinkPort, never()).validateAndLink(anyLong(), anyList(), anyPurpose(), anyLong())
    }

    @Test
    fun `등록 시 타인이 업로드한 파일이면 FILE_NOT_OWNED로 등록이 실패한다`() {
        givenSaveAssignsId()
        given(
            fileLinkPort.validateAndLink(
                requesterId = 7L,
                fileIds = listOf(1L),
                purpose = FilePurpose.PROGRAM_ATTACHMENT,
                ownerId = 1L,
            ),
        ).willThrow(FileNotOwnedException(1L))

        assertThatThrownBy { service.create(draftRequest(fileIds = listOf(1L)), createdByMemberId = 7L) }
            .isInstanceOf(FileNotOwnedException::class.java)
    }

    // --- 수정 ---

    @Test
    fun `등록자·담당 교사가 아니면 수정을 거부한다`() {
        given(programRepository.findByIdForUpdate(1L)).willReturn(programOf(createdByMemberId = 7L))

        assertThatThrownBy {
            service.update(1L, requesterMemberId = 99L, isDeveloper = false, request = ProgramUpdateRequest())
        }.isInstanceOf(ProgramManageForbiddenException::class.java)
    }

    @Test
    fun `개발자는 등록자·담당 교사가 아니어도 수정할 수 있다`() {
        given(programRepository.findByIdForUpdate(1L)).willReturn(programOf(createdByMemberId = 7L))
        given(programApplicationRepository.countByProgramIdAndStatus(1L, ProgramApplicationStatus.APPLIED))
            .willReturn(0L)

        val response =
            service.update(
                1L,
                requesterMemberId = 99L,
                isDeveloper = true,
                request = ProgramUpdateRequest(title = "변경된 제목"),
            )

        assertThat(response.programId).isEqualTo(1L)
    }

    @Test
    fun `정원을 현재 활성 신청 인원보다 적게 수정하면 거부한다`() {
        given(programRepository.findByIdForUpdate(1L)).willReturn(programOf(createdByMemberId = 7L, capacity = 20))
        given(programApplicationRepository.countByProgramIdAndStatus(1L, ProgramApplicationStatus.APPLIED))
            .willReturn(15L)

        assertThatThrownBy {
            service.update(
                1L,
                requesterMemberId = 7L,
                isDeveloper = false,
                request = ProgramUpdateRequest(capacity = 10),
            )
        }.isInstanceOf(CapacityBelowCurrentApplicantsException::class.java)
    }

    @Test
    fun `수정 시 fileIds를 전달하지 않으면 기존 첨부파일을 유지한다`() {
        given(programRepository.findByIdForUpdate(1L)).willReturn(programOf(createdByMemberId = 7L))
        given(programApplicationRepository.countByProgramIdAndStatus(1L, ProgramApplicationStatus.APPLIED))
            .willReturn(0L)

        service.update(1L, requesterMemberId = 7L, isDeveloper = false, request = ProgramUpdateRequest(title = "새 제목"))

        verify(fileLinkPort, never()).unlinkAllOf(FileOwnerType.PROGRAM, 1L)
        verify(fileLinkPort, never()).validateAndLink(anyLong(), anyList(), anyPurpose(), anyLong())
    }

    @Test
    fun `수정 시 fileIds를 전달하면 기존 연결을 해제한 뒤 다시 연결한다`() {
        given(programRepository.findByIdForUpdate(1L)).willReturn(programOf(createdByMemberId = 7L))
        given(programApplicationRepository.countByProgramIdAndStatus(1L, ProgramApplicationStatus.APPLIED))
            .willReturn(0L)

        service.update(
            1L,
            requesterMemberId = 7L,
            isDeveloper = false,
            request = ProgramUpdateRequest(fileIds = listOf(3L)),
        )

        val ordered = inOrder(fileLinkPort)
        ordered.verify(fileLinkPort).unlinkAllOf(FileOwnerType.PROGRAM, 1L)
        ordered.verify(fileLinkPort).validateAndLink(
            requesterId = 7L,
            fileIds = listOf(3L),
            purpose = FilePurpose.PROGRAM_ATTACHMENT,
            ownerId = 1L,
        )
    }

    @Test
    fun `수정 시 fileIds로 빈 배열을 전달하면 전체 해제만 하고 다시 연결하지 않는다`() {
        given(programRepository.findByIdForUpdate(1L)).willReturn(programOf(createdByMemberId = 7L))
        given(programApplicationRepository.countByProgramIdAndStatus(1L, ProgramApplicationStatus.APPLIED))
            .willReturn(0L)

        service.update(
            1L,
            requesterMemberId = 7L,
            isDeveloper = false,
            request = ProgramUpdateRequest(fileIds = emptyList()),
        )

        verify(fileLinkPort).unlinkAllOf(FileOwnerType.PROGRAM, 1L)
        verify(fileLinkPort, never()).validateAndLink(anyLong(), anyList(), anyPurpose(), anyLong())
    }

    @Test
    fun `수정 시 타인이 업로드한 파일을 재전송하면 FILE_NOT_OWNED로 거부한다`() {
        given(programRepository.findByIdForUpdate(1L)).willReturn(programOf(createdByMemberId = 7L))
        given(programApplicationRepository.countByProgramIdAndStatus(1L, ProgramApplicationStatus.APPLIED))
            .willReturn(0L)
        given(
            fileLinkPort.validateAndLink(
                requesterId = 99L,
                fileIds = listOf(3L),
                purpose = FilePurpose.PROGRAM_ATTACHMENT,
                ownerId = 1L,
            ),
        ).willThrow(FileNotOwnedException(3L))

        assertThatThrownBy {
            service.update(
                1L,
                requesterMemberId = 99L,
                isDeveloper = true,
                request = ProgramUpdateRequest(fileIds = listOf(3L)),
            )
        }.isInstanceOf(FileNotOwnedException::class.java)
    }

    // PR #81 리뷰 MINOR 지적: capacity "형식" 검증(0 이하 → INVALID_CAPACITY, 400)이 "정원 <
    // 활성 신청자 수" 비교(CapacityBelowCurrentApplicantsException, 409)보다 먼저 실행돼야 한다.
    // 활성 신청자 수를 15명으로 스텁해 -5 < 15가 먼저 걸리는 예전 순서였다면
    // CapacityBelowCurrentApplicantsException(409)이 던져졌을 상황에서, 여전히 형식 오류(400)가
    // 먼저 반환되는지 확인한다.
    @Test
    fun `정원이 0 이하면 활성 신청자 수와 무관하게 형식 오류를 먼저 반환한다`() {
        given(programRepository.findByIdForUpdate(1L)).willReturn(programOf(createdByMemberId = 7L, capacity = 20))
        given(programApplicationRepository.countByProgramIdAndStatus(1L, ProgramApplicationStatus.APPLIED))
            .willReturn(15L)

        assertThatThrownBy {
            service.update(
                1L,
                requesterMemberId = 7L,
                isDeveloper = false,
                request = ProgramUpdateRequest(capacity = -5),
            )
        }.isInstanceOf(InvalidCapacityException::class.java)
    }

    @Test
    fun `clearFormId가 true면 formId 값과 무관하게 기존 Form 연결을 해제한다`() {
        val program = programOf(createdByMemberId = 7L).apply { formId = 5L }
        given(programRepository.findByIdForUpdate(1L)).willReturn(program)
        given(programApplicationRepository.countByProgramIdAndStatus(1L, ProgramApplicationStatus.APPLIED))
            .willReturn(0L)

        service.update(
            1L,
            requesterMemberId = 7L,
            isDeveloper = false,
            // formId를 함께 보내도 clearFormId가 우선한다(ProgramUpdateRequest KDoc 참고).
            request = ProgramUpdateRequest(formId = 99L, clearFormId = true),
        )

        assertThat(program.formId).isNull()
        verify(programFormLinkQueryPort, never()).findLinkableProgramForm(anyLong(), anyLong())
    }

    // --- 상태 변경 ---

    @Test
    fun `DRAFT에서 PUBLISHED로 전이하면 등록자가 담당 교사가 된다`() {
        val program = programOf(status = ProgramStatus.DRAFT, createdByMemberId = 7L)
        given(programRepository.findByIdForUpdate(1L)).willReturn(program)
        given(programTargetGradeRepository.findAllByIdProgramId(1L)).willReturn(targetGradesOf(1L, 2, 3))
        given(memberApplicantSnapshotQueryPort.findById(7L)).willReturn(teacherSnapshot(7L))
        program.apply {
            bodyMarkdown = "본문"
            location = "장소"
            eventStartedAt = now
            eventEndedAt = now.plusHours(1)
            applicationStartedAt = now.minusDays(1)
            applicationEndedAt = now.plusDays(1)
            discordChannelId = "channel"
        }

        val response =
            service.changeStatus(
                1L,
                requesterMemberId = 7L,
                isDeveloper = false,
                request = ProgramStatusUpdateRequest(status = ProgramStatus.PUBLISHED),
            )

        assertThat(response.status).isEqualTo(ProgramStatus.PUBLISHED)
        assertThat(response.manager?.memberId).isEqualTo(7L)
    }

    @Test
    fun `CLOSED에서 PUBLISHED로 전이는 재게시 불가로 거부한다`() {
        given(
            programRepository.findByIdForUpdate(1L),
        ).willReturn(programOf(status = ProgramStatus.CLOSED, createdByMemberId = 7L))

        assertThatThrownBy {
            service.changeStatus(
                1L,
                requesterMemberId = 7L,
                isDeveloper = false,
                request = ProgramStatusUpdateRequest(status = ProgramStatus.PUBLISHED),
            )
        }.isInstanceOf(ProgramReopenNotAllowedException::class.java)
    }

    @Test
    fun `PUBLISHED에서 DRAFT로의 전이 요청은 상태 전이 오류로 거부한다`() {
        given(
            programRepository.findByIdForUpdate(1L),
        ).willReturn(programOf(status = ProgramStatus.PUBLISHED, createdByMemberId = 7L))

        assertThatThrownBy {
            service.changeStatus(
                1L,
                requesterMemberId = 7L,
                isDeveloper = false,
                request = ProgramStatusUpdateRequest(status = ProgramStatus.DRAFT),
            )
        }.isInstanceOf(ProgramStatusTransitionNotAllowedException::class.java)
    }

    @Test
    fun `DELETED로 전이하면 Soft Delete로 처리된다`() {
        val program = programOf(status = ProgramStatus.PUBLISHED, createdByMemberId = 7L)
        given(programRepository.findByIdForUpdate(1L)).willReturn(program)

        val response =
            service.changeStatus(
                1L,
                requesterMemberId = 7L,
                isDeveloper = false,
                request = ProgramStatusUpdateRequest(status = ProgramStatus.DELETED),
            )

        assertThat(response.status).isEqualTo(ProgramStatus.DELETED)
        assertThat(program.deletedAt).isNotNull()
        verify(fileLinkPort).unlinkAllOf(FileOwnerType.PROGRAM, 1L)
    }

    @Test
    fun `PUBLISHED로 전이하면 첨부파일 연결을 해제하지 않는다`() {
        val program = programOf(status = ProgramStatus.DRAFT, createdByMemberId = 7L)
        program.bodyMarkdown = "본문"
        program.location = "장소"
        program.eventStartedAt = now
        program.eventEndedAt = now.plusHours(1)
        program.applicationStartedAt = now.minusDays(1)
        program.applicationEndedAt = now.plusDays(1)
        program.discordChannelId = "channel-1"
        given(programRepository.findByIdForUpdate(1L)).willReturn(program)
        given(programTargetGradeRepository.findAllByIdProgramId(1L)).willReturn(targetGradesOf(1L, 2))

        service.changeStatus(
            1L,
            requesterMemberId = 7L,
            isDeveloper = false,
            request = ProgramStatusUpdateRequest(status = ProgramStatus.PUBLISHED),
        )

        verify(fileLinkPort, never()).unlinkAllOf(FileOwnerType.PROGRAM, 1L)
    }

    // --- Discord Event 발행 (docs/notification/discord-event-wiring-plan.md §4.2) ---
    //
    // 한 번도 게시되지 않은 DRAFT는 Discord에 메시지가 없어, UPDATED/DELETED를 발행하면 Worker가
    // MISSING_DISCORD_MESSAGE_ID로 실패 처리할 Row만 쌓인다. 그래서 발행 단계에서 거른다.

    @Test
    fun `PUBLISHED로 등록하면 Discord PUBLISHED Event를 발행한다`() {
        givenSaveAssignsId()
        given(memberApplicantSnapshotQueryPort.findById(7L)).willReturn(teacherSnapshot(7L))

        service.create(publishableRequest(), createdByMemberId = 7L)

        assertThat(publishedDiscordEvents())
            .containsExactly(ProgramDiscordEvent(1L, ProgramDiscordAction.PUBLISHED))
    }

    @Test
    fun `DRAFT로 등록하면 Discord Event를 발행하지 않는다`() {
        givenSaveAssignsId()

        service.create(draftRequest(), createdByMemberId = 7L)

        assertThat(publishedDiscordEvents()).isEmpty()
    }

    @Test
    fun `허용 목록에 없는 Discord 채널로 등록하면 거부한다`() {
        given(discordChannelResolver.isAllowedProgramChannelId("random-channel")).willReturn(false)

        assertThatThrownBy { service.create(publishableRequest(discordChannelId = "random-channel"), 7L) }
            .isInstanceOf(DiscordChannelNotAllowedException::class.java)
    }

    @Test
    fun `게시된 프로그램을 수정하면 Discord UPDATED Event를 발행한다`() {
        given(programRepository.findByIdForUpdate(1L))
            .willReturn(publishedProgram())
        given(programApplicationRepository.countByProgramIdAndStatus(1L, ProgramApplicationStatus.APPLIED))
            .willReturn(0L)
        given(programTargetGradeRepository.findAllByIdProgramId(1L)).willReturn(targetGradesOf(1L, 2, 3))

        service.update(1L, requesterMemberId = 7L, isDeveloper = false, request = ProgramUpdateRequest(title = "변경"))

        assertThat(publishedDiscordEvents())
            .containsExactly(ProgramDiscordEvent(1L, ProgramDiscordAction.UPDATED))
    }

    @Test
    fun `DRAFT를 수정하면 Discord Event를 발행하지 않는다`() {
        given(programRepository.findByIdForUpdate(1L))
            .willReturn(programOf(status = ProgramStatus.DRAFT, createdByMemberId = 7L))
        given(programApplicationRepository.countByProgramIdAndStatus(1L, ProgramApplicationStatus.APPLIED))
            .willReturn(0L)

        service.update(1L, requesterMemberId = 7L, isDeveloper = false, request = ProgramUpdateRequest(title = "변경"))

        assertThat(publishedDiscordEvents()).isEmpty()
    }

    @Test
    fun `게시된 프로그램을 삭제하면 Discord DELETED Event를 발행한다`() {
        given(programRepository.findByIdForUpdate(1L))
            .willReturn(programOf(status = ProgramStatus.PUBLISHED, createdByMemberId = 7L))

        service.changeStatus(
            1L,
            requesterMemberId = 7L,
            isDeveloper = false,
            request = ProgramStatusUpdateRequest(status = ProgramStatus.DELETED),
        )

        assertThat(publishedDiscordEvents())
            .containsExactly(ProgramDiscordEvent(1L, ProgramDiscordAction.DELETED))
    }

    @Test
    fun `DRAFT를 삭제하면 Discord Event를 발행하지 않는다`() {
        given(programRepository.findByIdForUpdate(1L))
            .willReturn(programOf(status = ProgramStatus.DRAFT, createdByMemberId = 7L))

        service.changeStatus(
            1L,
            requesterMemberId = 7L,
            isDeveloper = false,
            request = ProgramStatusUpdateRequest(status = ProgramStatus.DELETED),
        )

        assertThat(publishedDiscordEvents()).isEmpty()
    }

    // 신청자 인앱 알림용 [ProgramDeletedEvent] 발행(Issue #118)은 이 Class가 detekt LargeClass
    // 임계값에 이미 근접해 있어 ProgramDeletedEventPublishTest로 분리했다.

    // --- 목록 조회 ---

    // list()가 Page 항목마다 activeApplicantCount()/hasActiveApplication()을 개별 호출하던 N+1
    // 쿼리를 배치 쿼리(countActiveApplicantsByProgramIds/findProgramIdsWithActiveApplication)로
    // 바꿨는지 검증한다(PR #81 리뷰 지적). 단건 Method는 Stub하지 않으므로, 만약 구현이 다시
    // 단건 Method를 호출하면 기본값(0/null)이 반환되어 아래 값 검증이 깨진다.
    @Test
    fun `목록 조회는 활성 신청자 수와 신청 여부를 배치 쿼리로 한 번에 가져온다`() {
        val program1 =
            programOf(status = ProgramStatus.PUBLISHED, createdByMemberId = 7L, capacity = 20).apply {
                id =
                    1L
            }
        val program2 =
            programOf(status = ProgramStatus.PUBLISHED, createdByMemberId = 7L, capacity = 10).apply {
                id =
                    2L
            }
        val page = PageImpl(listOf(program1, program2), PageRequest.of(0, 20), 2)
        given(
            programRepository.search(
                isNull(),
                isNull(),
                anyBoolean(),
                any(ProgramStatus::class.java) ?: ProgramStatus.PUBLISHED,
                any(LocalDateTime::class.java) ?: now,
                any(Pageable::class.java) ?: PageRequest.of(0, 20),
            ),
        ).willReturn(page)
        given(
            programApplicationRepository.countActiveApplicantsByProgramIds(
                listOf(1L, 2L),
                ProgramApplicationStatus.APPLIED,
            ),
        ).willReturn(listOf(programApplicantCountOf(programId = 1L, activeApplicantCount = 5L)))
        given(
            programApplicationRepository.findProgramIdsWithActiveApplication(
                listOf(1L, 2L),
                9L,
                ProgramApplicationStatus.APPLIED,
            ),
        ).willReturn(listOf(2L))

        val response = service.list(null, null, false, requesterMemberId = 9L, pageable = PageRequest.of(0, 20))

        val summary1 = response.content.single { it.programId == 1L }
        val summary2 = response.content.single { it.programId == 2L }
        // program1: 배치 Count 결과에 있음(5명), 배치 신청 목록에는 없음(applied=false)
        assertThat(summary1.currentApplicants).isEqualTo(5)
        assertThat(summary1.applied).isFalse()
        // program2: 배치 Count 결과에 없음(0명 취급), 배치 신청 목록에 있음(applied=true)
        assertThat(summary2.currentApplicants).isEqualTo(0)
        assertThat(summary2.applied).isTrue()
    }

    // --- 상세 조회 ---

    @Test
    fun `존재하지 않는 프로그램 상세 조회는 PROGRAM_NOT_FOUND다`() {
        given(programRepository.findById(1L)).willReturn(Optional.empty())

        assertThatThrownBy {
            service.getDetail(1L, 1L)
        }.isInstanceOf(ProgramNotFoundException::class.java)
    }

    @Test
    fun `삭제된 프로그램 상세 조회는 PROGRAM_DELETED다`() {
        val program = programOf(status = ProgramStatus.DELETED, createdByMemberId = 7L)
        program.deletedAt = now
        given(programRepository.findById(1L)).willReturn(Optional.of(program))

        assertThatThrownBy {
            service.getDetail(1L, 1L)
        }.isInstanceOf(ProgramDeletedException::class.java)
    }

    @Test
    fun `PUBLISHED 프로그램 상세는 등록자가 아니어도 첨부파일 목록을 반환한다`() {
        val program = programOf(status = ProgramStatus.PUBLISHED, createdByMemberId = 7L)
        given(programRepository.findById(1L)).willReturn(Optional.of(program))
        given(fileLinkPort.linkedFilesOf(FileOwnerType.PROGRAM, 1L)).willReturn(listOf(fileSnapshotOf(5L)))

        val response = service.getDetail(1L, requesterMemberId = 99L)

        assertThat(response.files).extracting("fileId").containsExactly(5L)
    }

    @Test
    fun `CLOSED 프로그램 상세는 등록자가 아니어도 첨부파일 목록을 반환한다`() {
        val program = programOf(status = ProgramStatus.CLOSED, createdByMemberId = 7L)
        given(programRepository.findById(1L)).willReturn(Optional.of(program))
        given(fileLinkPort.linkedFilesOf(FileOwnerType.PROGRAM, 1L)).willReturn(listOf(fileSnapshotOf(5L)))

        val response = service.getDetail(1L, requesterMemberId = 99L)

        assertThat(response.files).extracting("fileId").containsExactly(5L)
    }

    @Test
    fun `DRAFT 프로그램 상세는 등록자·담당교사·개발자가 아니면 빈 첨부파일 목록을 반환한다`() {
        val program = programOf(status = ProgramStatus.DRAFT, createdByMemberId = 7L)
        given(programRepository.findById(1L)).willReturn(Optional.of(program))
        given(memberRoleQueryPort.findRoles(99L)).willReturn(setOf(RoleType.STUDENT))

        val response = service.getDetail(1L, requesterMemberId = 99L)

        assertThat(response.files).isEmpty()
        verify(fileLinkPort, never()).linkedFilesOf(FileOwnerType.PROGRAM, 1L)
    }

    @Test
    fun `DRAFT 프로그램 상세는 등록자에게 첨부파일 목록을 반환한다`() {
        val program = programOf(status = ProgramStatus.DRAFT, createdByMemberId = 7L)
        given(programRepository.findById(1L)).willReturn(Optional.of(program))
        given(fileLinkPort.linkedFilesOf(FileOwnerType.PROGRAM, 1L)).willReturn(listOf(fileSnapshotOf(5L)))

        val response = service.getDetail(1L, requesterMemberId = 7L)

        assertThat(response.files).extracting("fileId").containsExactly(5L)
    }

    @Test
    fun `DRAFT 프로그램 상세는 MemberRoleQueryPort가 DEVELOPER를 반환하는 요청자에게 첨부파일 목록을 반환한다`() {
        val program = programOf(status = ProgramStatus.DRAFT, createdByMemberId = 7L)
        given(programRepository.findById(1L)).willReturn(Optional.of(program))
        given(fileLinkPort.linkedFilesOf(FileOwnerType.PROGRAM, 1L)).willReturn(listOf(fileSnapshotOf(5L)))
        given(memberRoleQueryPort.findRoles(99L)).willReturn(setOf(RoleType.DEVELOPER))

        val response = service.getDetail(1L, requesterMemberId = 99L)

        assertThat(response.files).extracting("fileId").containsExactly(5L)
    }

    // --- 신청·취소 ---

    @Test
    fun `신청 가능한 프로그램에 신청하면 성공한다`() {
        val program = publishedProgramForApply()
        given(programRepository.findByIdForUpdate(1L)).willReturn(program)
        given(programTargetGradeRepository.findAllByIdProgramId(1L)).willReturn(emptyList())
        given(memberApplicantSnapshotQueryPort.findById(1L)).willReturn(studentSnapshot())
        given(
            programApplicationRepository.findByProgramIdAndApplicantMemberIdAndStatus(
                1L,
                1L,
                ProgramApplicationStatus.APPLIED,
            ),
        ).willReturn(null)
        given(
            programApplicationRepository.countByProgramIdAndStatus(1L, ProgramApplicationStatus.APPLIED),
        ).willReturn(0L)
        given(programApplicationRepository.saveAndFlush(any(ProgramApplication::class.java)))
            .willAnswer { invocation -> (invocation.arguments[0] as ProgramApplication).apply { id = 1L } }

        val response =
            service.executeApplicationAction(
                1L,
                studentMemberId = 1L,
                request = ProgramApplicationActionRequest(action = ProgramApplicationAction.APPLY),
            )

        assertThat(response.status).isEqualTo(ProgramApplicationStatus.APPLIED)
        assertThat(response.currentApplicants).isEqualTo(1)
    }

    @Test
    fun `재학 중이 아니면 신청을 거부한다`() {
        val program = publishedProgramForApply()
        given(programRepository.findByIdForUpdate(1L)).willReturn(program)
        given(programTargetGradeRepository.findAllByIdProgramId(1L)).willReturn(emptyList())
        given(memberApplicantSnapshotQueryPort.findById(1L)).willReturn(studentSnapshot(academicStatus = "GRADUATED"))

        assertThatThrownBy {
            service.executeApplicationAction(
                1L,
                studentMemberId = 1L,
                request = ProgramApplicationActionRequest(action = ProgramApplicationAction.APPLY),
            )
        }.isInstanceOf(NotEnrolledException::class.java)
    }

    @Test
    fun `정원이 가득 찼으면 PROGRAM_FULL로 거부한다`() {
        val program = publishedProgramForApply(capacity = 1)
        given(programRepository.findByIdForUpdate(1L)).willReturn(program)
        given(programTargetGradeRepository.findAllByIdProgramId(1L)).willReturn(emptyList())
        given(memberApplicantSnapshotQueryPort.findById(1L)).willReturn(studentSnapshot())
        given(
            programApplicationRepository.countByProgramIdAndStatus(1L, ProgramApplicationStatus.APPLIED),
        ).willReturn(1L)

        assertThatThrownBy {
            service.executeApplicationAction(
                1L,
                studentMemberId = 1L,
                request = ProgramApplicationActionRequest(action = ProgramApplicationAction.APPLY),
            )
        }.isInstanceOf(ProgramFullException::class.java)
    }

    @Test
    fun `이미 활성 신청이 있으면 ALREADY_APPLIED로 거부한다`() {
        val program = publishedProgramForApply()
        given(programRepository.findByIdForUpdate(1L)).willReturn(program)
        given(programTargetGradeRepository.findAllByIdProgramId(1L)).willReturn(emptyList())
        given(memberApplicantSnapshotQueryPort.findById(1L)).willReturn(studentSnapshot())
        given(
            programApplicationRepository.findByProgramIdAndApplicantMemberIdAndStatus(
                1L,
                1L,
                ProgramApplicationStatus.APPLIED,
            ),
        ).willReturn(ProgramApplication(programId = 1L, applicantMemberId = 1L))
        given(
            programApplicationRepository.countByProgramIdAndStatus(1L, ProgramApplicationStatus.APPLIED),
        ).willReturn(1L)

        assertThatThrownBy {
            service.executeApplicationAction(
                1L,
                studentMemberId = 1L,
                request = ProgramApplicationActionRequest(action = ProgramApplicationAction.APPLY),
            )
        }.isInstanceOf(AlreadyAppliedException::class.java)
    }

    // apply()는 상태(status)만 보는 게 아니라 computeProgramEligibilityReason에 applicationEndedAt도
    // 함께 넘겨 별도로 비교한다(ProgramEligibility.kt 참고). 이 Test는 status=CLOSED인 경우를
    // 검증한다 -- ProgramCloseScheduler가 실제로 만들기 시작하는 상태이므로, CLOSED로 전이된
    // Program에 신청 시도가 여전히 차단되는지(PROGRAM_CLOSED로) 확인한다.
    @Test
    fun `CLOSED 상태 프로그램에 신청하면 PROGRAM_CLOSED로 거부한다`() {
        val program = programOf(status = ProgramStatus.CLOSED)
        given(programRepository.findByIdForUpdate(1L)).willReturn(program)

        assertThatThrownBy {
            service.executeApplicationAction(
                1L,
                studentMemberId = 1L,
                request = ProgramApplicationActionRequest(action = ProgramApplicationAction.APPLY),
            )
        }.isInstanceOf(ProgramClosedException::class.java)
    }

    @Test
    fun `활성 신청을 취소하면 성공한다`() {
        val program = publishedProgramForApply()
        val application = ProgramApplication(programId = 1L, applicantMemberId = 1L).apply { id = 5L }
        given(programRepository.findByIdForUpdate(1L)).willReturn(program)
        given(
            programApplicationRepository.findByProgramIdAndApplicantMemberIdAndStatus(
                1L,
                1L,
                ProgramApplicationStatus.APPLIED,
            ),
        ).willReturn(application)
        given(
            programApplicationRepository.countByProgramIdAndStatus(1L, ProgramApplicationStatus.APPLIED),
        ).willReturn(0L)

        val response =
            service.executeApplicationAction(
                1L,
                studentMemberId = 1L,
                request = ProgramApplicationActionRequest(action = ProgramApplicationAction.CANCEL),
            )

        assertThat(response.status).isEqualTo(ProgramApplicationStatus.CANCELED)
        assertThat(application.canceledAt).isNotNull()
    }

    @Test
    fun `취소할 활성 신청이 없으면 ACTIVE_APPLICATION_NOT_FOUND다`() {
        val program = publishedProgramForApply()
        given(programRepository.findByIdForUpdate(1L)).willReturn(program)
        given(
            programApplicationRepository.findByProgramIdAndApplicantMemberIdAndStatus(
                1L,
                1L,
                ProgramApplicationStatus.APPLIED,
            ),
        ).willReturn(null)

        assertThatThrownBy {
            service.executeApplicationAction(
                1L,
                studentMemberId = 1L,
                request = ProgramApplicationActionRequest(action = ProgramApplicationAction.CANCEL),
            )
        }.isInstanceOf(ActiveApplicationNotFoundException::class.java)
    }

    @Test
    fun `신청 종료 후에는 취소를 거부한다`() {
        val program = publishedProgramForApply().apply { applicationEndedAt = now.minusDays(1) }
        val application = ProgramApplication(programId = 1L, applicantMemberId = 1L)
        given(programRepository.findByIdForUpdate(1L)).willReturn(program)
        given(
            programApplicationRepository.findByProgramIdAndApplicantMemberIdAndStatus(
                1L,
                1L,
                ProgramApplicationStatus.APPLIED,
            ),
        ).willReturn(application)

        assertThatThrownBy {
            service.executeApplicationAction(
                1L,
                studentMemberId = 1L,
                request = ProgramApplicationActionRequest(action = ProgramApplicationAction.CANCEL),
            )
        }.isInstanceOf(ProgramActionNotAvailableException::class.java)
    }

    // --- Fixtures ---

    /**
     * 발행된 Event 중 [ProgramDiscordEvent]만 골라낸다. Event 종류를 구분하지 않으면 다른
     * Event가 함께 발행될 때 검증이 흐려진다.
     */
    private fun publishedDiscordEvents(): List<ProgramDiscordEvent> {
        val captor = ArgumentCaptor.forClass(Any::class.java)
        verify(eventPublisher, atLeast(0)).publishEvent(captor.capture() ?: Any())
        return captor.allValues.filterIsInstance<ProgramDiscordEvent>()
    }

    /** 게시 필수값을 모두 갖춘 PUBLISHED 프로그램이다 -- update()가 게시 검증을 다시 수행한다. */
    private fun publishedProgram() =
        programOf(status = ProgramStatus.PUBLISHED, createdByMemberId = 7L).apply {
            bodyMarkdown = "본문"
            location = "장소"
            eventStartedAt = now
            eventEndedAt = now.plusHours(1)
            applicationStartedAt = now.minusDays(1)
            applicationEndedAt = now.plusDays(1)
            discordChannelId = "channel-1"
        }

    private fun givenSaveAssignsId() {
        given(programRepository.saveAndFlush(any(Program::class.java))).willAnswer { invocation ->
            (invocation.arguments[0] as Program).apply { id = 1L }
        }
    }

    private fun programOf(
        status: ProgramStatus = ProgramStatus.DRAFT,
        createdByMemberId: Long = 7L,
        capacity: Int? = null,
    ) = Program(
        createdByMemberId = createdByMemberId,
        type = ProgramType.SPECIAL_LECTURE,
        title = "특강",
        status = status,
    ).apply {
        id = 1L
        this.capacity = capacity
    }

    private fun targetGradesOf(
        programId: Long,
        vararg grades: Int,
    ) = grades.map { grade -> ProgramTargetGrade(ProgramTargetGradeId(programId, grade)) }

    private fun programApplicantCountOf(
        programId: Long,
        activeApplicantCount: Long,
    ): ProgramApplicantCount =
        object : ProgramApplicantCount {
            override val programId: Long = programId
            override val activeApplicantCount: Long = activeApplicantCount
        }

    // apply()/cancel()은 고정된 `now` Fixture가 아니라 실제 LocalDateTime.now()로 신청 기간을
    // 판정한다. 신청 기간을 `now`(고정 Calendar 날짜) 기준으로 두면, 실제 벽시계 시각이 그
    // 고정된 날짜를 지나는 순간부터 이 Fixture로 만든 Program이 "신청 종료"로 취급돼 이 Method를
    // 쓰는 Test가 시간이 지나면 저절로 실패하는 Time-bomb이 된다(PR #81 코드 리뷰 중 발견).
    // 실제 실행 시각 기준으로 넉넉한 여유(1년)를 둬 언제 실행해도 항상 신청 기간 안에 들게 한다.
    private fun publishedProgramForApply(capacity: Int? = null) =
        programOf(status = ProgramStatus.PUBLISHED, capacity = capacity).apply {
            applicationStartedAt = LocalDateTime.now().minusYears(1)
            applicationEndedAt = LocalDateTime.now().plusYears(1)
        }

    private fun teacherSnapshot(memberId: Long) =
        MemberApplicantSnapshot(
            memberId = memberId,
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

    private fun studentSnapshot(
        academicStatus: String? = "ENROLLED",
        grade: Int? = 2,
    ) = MemberApplicantSnapshot(
        memberId = 1L,
        name = "학생",
        email = "student@example.com",
        phone = null,
        academicStatus = academicStatus,
        grade = grade,
        cohort = 10,
        department = "SW_DEVELOPMENT",
        majors = emptyList(),
        techStacks = emptyList(),
        desiredJob = null,
    )

    private fun draftRequest(
        title: String = "특강",
        formId: Long? = null,
        fileIds: List<Long> = emptyList(),
    ) = ProgramCreateRequest(
        title = title,
        programType = ProgramType.SPECIAL_LECTURE,
        status = ProgramStatus.DRAFT,
        formId = formId,
        fileIds = fileIds,
    )

    private fun anyPurpose(): FilePurpose = any(FilePurpose::class.java) ?: FilePurpose.PROGRAM_ATTACHMENT

    private fun fileSnapshotOf(fileId: Long) =
        FileSnapshot(fileId = fileId, originalName = "첨부.pdf", contentType = "application/pdf", size = 1024L)

    private fun publishableRequest(
        content: String? = "본문",
        discordChannelId: String? = "channel-1",
    ) = ProgramCreateRequest(
        title = "특강",
        programType = ProgramType.SPECIAL_LECTURE,
        status = ProgramStatus.PUBLISHED,
        content = content,
        startAt = now,
        endAt = now.plusHours(1),
        applicationStartAt = now.minusDays(1),
        applicationEndAt = now.plusDays(1),
        location = "장소",
        targetGrades = listOf(1, 2, 3),
        capacity = 20,
        discordChannelId = discordChannelId,
    )
}
