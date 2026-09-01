package team.inreok.getiserver.domain.program.service.impl

import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.application.query.ProgramFormLinkQueryPort
import team.inreok.getiserver.domain.file.entity.type.FileOwnerType
import team.inreok.getiserver.domain.file.entity.type.FilePurpose
import team.inreok.getiserver.domain.file.link.FileLinkPort
import team.inreok.getiserver.domain.file.link.FileSnapshot
import team.inreok.getiserver.domain.member.entity.type.RoleType
import team.inreok.getiserver.domain.member.query.MemberApplicantSnapshotQueryPort
import team.inreok.getiserver.domain.member.query.MemberRoleQueryPort
import team.inreok.getiserver.domain.program.access.canViewProgramFiles
import team.inreok.getiserver.domain.program.dto.ProgramApplicationActionRequest
import team.inreok.getiserver.domain.program.dto.ProgramApplicationActionResponse
import team.inreok.getiserver.domain.program.dto.ProgramApplicationAnswer
import team.inreok.getiserver.domain.program.dto.ProgramCreateRequest
import team.inreok.getiserver.domain.program.dto.ProgramCreateResponse
import team.inreok.getiserver.domain.program.dto.ProgramDetailResponse
import team.inreok.getiserver.domain.program.dto.ProgramFileResponse
import team.inreok.getiserver.domain.program.dto.ProgramListResponse
import team.inreok.getiserver.domain.program.dto.ProgramManagerSummary
import team.inreok.getiserver.domain.program.dto.ProgramStatusUpdateRequest
import team.inreok.getiserver.domain.program.dto.ProgramStatusUpdateResponse
import team.inreok.getiserver.domain.program.dto.ProgramSummaryResponse
import team.inreok.getiserver.domain.program.dto.ProgramUpdateRequest
import team.inreok.getiserver.domain.program.dto.ProgramUpdateResponse
import team.inreok.getiserver.domain.program.entity.Program
import team.inreok.getiserver.domain.program.entity.ProgramApplication
import team.inreok.getiserver.domain.program.entity.ProgramTargetGrade
import team.inreok.getiserver.domain.program.entity.ProgramTargetGradeId
import team.inreok.getiserver.domain.program.entity.type.ProgramApplicationAction
import team.inreok.getiserver.domain.program.entity.type.ProgramApplicationEligibilityReason
import team.inreok.getiserver.domain.program.entity.type.ProgramApplicationStatus
import team.inreok.getiserver.domain.program.entity.type.ProgramStatus
import team.inreok.getiserver.domain.program.entity.type.ProgramType
import team.inreok.getiserver.domain.program.event.ProgramApplicationAppliedEvent
import team.inreok.getiserver.domain.program.event.ProgramApplicationCanceledEvent
import team.inreok.getiserver.domain.program.event.ProgramDeletedEvent
import team.inreok.getiserver.domain.program.event.ProgramDiscordAction
import team.inreok.getiserver.domain.program.event.ProgramDiscordEvent
import team.inreok.getiserver.domain.program.exception.ActiveApplicationNotFoundException
import team.inreok.getiserver.domain.program.exception.AlreadyAppliedException
import team.inreok.getiserver.domain.program.exception.CapacityBelowCurrentApplicantsException
import team.inreok.getiserver.domain.program.exception.DiscordChannelNotAllowedException
import team.inreok.getiserver.domain.program.exception.NotEnrolledException
import team.inreok.getiserver.domain.program.exception.NotTargetGradeException
import team.inreok.getiserver.domain.program.exception.ProgramActionNotAvailableException
import team.inreok.getiserver.domain.program.exception.ProgramClosedException
import team.inreok.getiserver.domain.program.exception.ProgramFormNotLinkableException
import team.inreok.getiserver.domain.program.exception.ProgramFullException
import team.inreok.getiserver.domain.program.exception.ProgramManageForbiddenException
import team.inreok.getiserver.domain.program.exception.ProgramNotFoundException
import team.inreok.getiserver.domain.program.exception.ProgramNotOpenException
import team.inreok.getiserver.domain.program.exception.ProgramReopenNotAllowedException
import team.inreok.getiserver.domain.program.exception.ProgramStatusTransitionNotAllowedException
import team.inreok.getiserver.domain.program.exception.ProgramValidationFailedException
import team.inreok.getiserver.domain.program.repository.ProgramApplicationRepository
import team.inreok.getiserver.domain.program.repository.ProgramRepository
import team.inreok.getiserver.domain.program.repository.ProgramTargetGradeRepository
import team.inreok.getiserver.domain.program.service.ProgramService
import team.inreok.getiserver.domain.program.service.computeProgramEligibilityReason
import team.inreok.getiserver.domain.program.service.programEligibilityMessageOf
import team.inreok.getiserver.domain.program.service.validateProgramCommon
import team.inreok.getiserver.domain.program.service.validateProgramForPublish
import team.inreok.getiserver.global.discord.DiscordChannelResolver
import tools.jackson.databind.ObjectMapper
import java.time.LocalDateTime

// Program CRUD·상태 관리·목록·상세 Eligibility·신청/취소 동시성을 한 Service가 담당해(Phase
// 1~3 범위) Public Method 6개 + Private Helper가 detekt 기본 TooManyFunctions 임계값(11)을
// 넘는다. JobApplicationEligibility.kt가 이미 같은 방식으로 ReturnCount를 Suppress한 전례를
// 따른다(docs/development/code-quality.md가 "@Suppress를 쓰지 않았다"고 적은 시점 이후 실제
// 코드에 추가된 관례, AGENTS.md 우선순위상 실제 코드가 그 문서보다 우선한다). Issue #191이
// apply()/cancel()에 신청·취소 알림 Event 발행을 추가하면서 LargeClass 임계값도 넘겼다 --
// Service를 여러 Class로 쪼개는 것은 이 Issue 범위를 벗어난 구조 변경이라 같은 방식으로 Suppress한다.
@Suppress("TooManyFunctions", "LargeClass")
@Service
class ProgramServiceImpl(
    private val programRepository: ProgramRepository,
    private val programTargetGradeRepository: ProgramTargetGradeRepository,
    private val programApplicationRepository: ProgramApplicationRepository,
    private val programFormLinkQueryPort: ProgramFormLinkQueryPort,
    private val memberApplicantSnapshotQueryPort: MemberApplicantSnapshotQueryPort,
    private val memberRoleQueryPort: MemberRoleQueryPort,
    private val fileLinkPort: FileLinkPort,
    private val objectMapper: ObjectMapper,
    private val eventPublisher: ApplicationEventPublisher,
    private val discordChannelResolver: DiscordChannelResolver,
) : ProgramService {
    private val log = LoggerFactory.getLogger(ProgramServiceImpl::class.java)

    @Transactional
    override fun create(
        request: ProgramCreateRequest,
        createdByMemberId: Long,
    ): ProgramCreateResponse {
        if (request.status != ProgramStatus.DRAFT && request.status != ProgramStatus.PUBLISHED) {
            throw ProgramValidationFailedException("프로그램은 DRAFT 또는 PUBLISHED 상태로만 등록할 수 있습니다.")
        }
        val targetGrades = (request.targetGrades ?: emptyList()).distinct()
        validateProgramCommon(
            startAt = request.startAt,
            endAt = request.endAt,
            applicationStartAt = request.applicationStartAt,
            applicationEndAt = request.applicationEndAt,
            targetGrades = targetGrades,
            capacity = request.capacity,
        )
        requireAllowedDiscordChannelId(request.discordChannelId)

        val program = buildProgram(request, createdByMemberId)
        request.formId?.let { program.formId = linkForm(it, createdByMemberId) }

        if (request.status == ProgramStatus.PUBLISHED) {
            validateProgramForPublish(
                content = program.bodyMarkdown,
                location = program.location,
                startAt = program.eventStartedAt,
                endAt = program.eventEndedAt,
                applicationStartAt = program.applicationStartedAt,
                applicationEndAt = program.applicationEndedAt,
                targetGrades = targetGrades,
                discordChannelId = program.discordChannelId,
            )
            program.managerMemberId = createdByMemberId
        }

        val saved = programRepository.saveAndFlush(program)
        val programId = requireNotNull(saved.id)
        saveTargetGrades(programId, targetGrades)

        // 저장 이후에만 연결한다 -- 연결 대상(ownerId)으로 쓸 programId가 저장 전에는 없다
        // (InquiryServiceImpl.create와 동일한 순서). 소유권·목적·상태·개수 검증은
        // FileLinkPort.validateAndLink가 수행하며, 거부되면 예외가 Transaction을 되돌려
        // 프로그램도 함께 만들어지지 않는다.
        if (request.fileIds.isNotEmpty()) {
            fileLinkPort.validateAndLink(
                requesterId = createdByMemberId,
                fileIds = request.fileIds,
                purpose = FilePurpose.PROGRAM_ATTACHMENT,
                ownerId = programId,
            )
        }

        if (saved.status == ProgramStatus.PUBLISHED) {
            eventPublisher.publishEvent(ProgramDiscordEvent(programId, ProgramDiscordAction.PUBLISHED))
        }

        return ProgramCreateResponse(
            programId = requireNotNull(saved.id),
            programType = saved.type,
            targetGrades = targetGrades,
            capacity = saved.capacity,
            currentApplicants = 0,
            remainingCapacity = saved.capacity,
            firstComeServed = saved.firstComeServed,
            manager = saved.managerMemberId?.let { ProgramManagerSummary(it, memberName(it)) },
            status = saved.status,
            createdAt = saved.createdAt,
        )
    }

    private fun buildProgram(
        request: ProgramCreateRequest,
        createdByMemberId: Long,
    ): Program =
        Program(
            createdByMemberId = createdByMemberId,
            type = request.programType,
            title = request.title.trim(),
            status = request.status,
        ).apply {
            bodyMarkdown = request.content
            location = request.location
            eventStartedAt = request.startAt
            eventEndedAt = request.endAt
            applicationStartedAt = request.applicationStartAt
            applicationEndedAt = request.applicationEndAt
            capacity = request.capacity
            discordChannelId = request.discordChannelId
        }

    /**
     * `null`이면 검증하지 않는다 -- 채널을 지정하지 않고 등록하면 DRAFT로는 저장할 수 있고
     * 게시 시점에 `validateProgramForPublish`가 별도로 필수 여부를 검증한다. 값을 받는 시점에
     * 바로 허용 목록을 검증하는 이유는 게시 시점까지 미루면 잘못된 채널이 DRAFT로 저장된 뒤
     * 게시가 막히는 혼란스러운 실패로 이어지기 때문이다(요구사항 §10).
     */
    private fun requireAllowedDiscordChannelId(channelId: String?) {
        val normalized = channelId?.takeIf { it.isNotBlank() } ?: return
        if (!discordChannelResolver.isAllowedProgramChannelId(normalized)) {
            throw DiscordChannelNotAllowedException(normalized)
        }
    }

    @Transactional
    override fun update(
        programId: Long,
        requesterMemberId: Long,
        isDeveloper: Boolean,
        request: ProgramUpdateRequest,
    ): ProgramUpdateResponse {
        val program = findForUpdate(programId)
        requireManager(program, requesterMemberId, isDeveloper)

        program.apply {
            request.title?.trim()?.let { title = it }
            request.content?.let { bodyMarkdown = it }
            request.location?.let { location = it }
            request.startAt?.let { eventStartedAt = it }
            request.endAt?.let { eventEndedAt = it }
            request.applicationStartAt?.let { applicationStartedAt = it }
            request.applicationEndAt?.let { applicationEndedAt = it }
        }
        applyFormLinkUpdate(program, request, requesterMemberId)
        applyFileIdsUpdate(programId, requesterMemberId, request)

        // capacity "형식" 검증(0 이하 → INVALID_CAPACITY, 400)이 "정원 < 활성 신청자 수" 비교
        // (CapacityBelowCurrentApplicantsException, 409)보다 항상 먼저 실행되어야 한다(PR #81
        // 리뷰 MINOR 지적). validateProgramCommon을 아직 program에 반영하지 않은 후보 값
        // (request.capacity ?: 기존 값)으로 먼저 호출해 형식을 검증한 뒤에만 활성 신청자 수와
        // 비교하고 실제로 반영한다.
        val targetGrades = currentTargetGrades(programId)
        validateProgramCommon(
            startAt = program.eventStartedAt,
            endAt = program.eventEndedAt,
            applicationStartAt = program.applicationStartedAt,
            applicationEndAt = program.applicationEndedAt,
            targetGrades = targetGrades.toList(),
            capacity = request.capacity ?: program.capacity,
        )

        val currentApplicants = activeApplicantCount(programId)
        request.capacity?.let { newCapacity ->
            if (newCapacity < currentApplicants) throw CapacityBelowCurrentApplicantsException()
            program.capacity = newCapacity
        }

        // DRAFT는 Discord에 아직 메시지가 없어 발행하지 않는다(§4.2) -- 발행하면 Worker가
        // MISSING_DISCORD_MESSAGE_ID로 실패 처리할 Row만 쌓인다. publishEvent는 실제로는
        // AFTER_COMMIT에만 전달되므로 Flush 이전에 호출해도 무방하다.
        if (program.status == ProgramStatus.PUBLISHED) {
            validateProgramForPublish(
                content = program.bodyMarkdown,
                location = program.location,
                startAt = program.eventStartedAt,
                endAt = program.eventEndedAt,
                applicationStartAt = program.applicationStartedAt,
                applicationEndAt = program.applicationEndedAt,
                targetGrades = targetGrades.toList(),
                discordChannelId = program.discordChannelId,
            )
            eventPublisher.publishEvent(ProgramDiscordEvent(programId, ProgramDiscordAction.UPDATED))
        }

        programRepository.flush()
        val remainingCapacity = program.capacity?.let { it - currentApplicants }

        return ProgramUpdateResponse(
            programId = programId,
            capacity = program.capacity,
            currentApplicants = currentApplicants,
            remainingCapacity = remainingCapacity,
            // 정원 증가로 생긴 빈자리 알림은 빈자리 구독 기능(Phase 6)이 아직 없어 항상 0/false다
            // (허위 true 반환 금지, 원본 요구사항 문서 17절).
            vacancyNotificationCount = 0,
            notificationCreated = false,
            updatedAt = program.updatedAt,
        )
    }

    @Transactional
    override fun changeStatus(
        programId: Long,
        requesterMemberId: Long,
        isDeveloper: Boolean,
        request: ProgramStatusUpdateRequest,
    ): ProgramStatusUpdateResponse {
        val program = findForUpdate(programId)
        requireManager(program, requesterMemberId, isDeveloper)

        val target = request.status
        // allowedTransitions()의 모든 전이 대상은 PUBLISHED/DELETED뿐이라(DRAFT/CLOSED로의 전이는
        // 어떤 현재 상태에서도 이 Map에 없다), "이 API가 지정 가능한 목표 상태는 PUBLISHED/DELETED
        // 뿐이다(PUBLISHED->CLOSED는 Scheduler 전용, 원본 요구사항 문서 8절)"라는 별도 검사는
        // allowedTransitions 판정과 항상 같은 결과를 내 중복이었다(PR #81 리뷰 지적). Map 하나로
        // 통합해도 아래 분기(허용/거부 조합)는 기존과 동일하다 — ProgramServiceImplTest 상태 전이
        // Test 참고.
        if (target !in allowedTransitions(program.status)) {
            if (program.status == ProgramStatus.CLOSED && target == ProgramStatus.PUBLISHED) {
                throw ProgramReopenNotAllowedException()
            }
            throw ProgramStatusTransitionNotAllowedException(program.status, target)
        }
        val previousStatus = program.status

        val now = LocalDateTime.now()
        when (target) {
            ProgramStatus.PUBLISHED -> {
                val targetGrades = currentTargetGrades(programId)
                validateProgramForPublish(
                    content = program.bodyMarkdown,
                    location = program.location,
                    startAt = program.eventStartedAt,
                    endAt = program.eventEndedAt,
                    applicationStartAt = program.applicationStartedAt,
                    applicationEndAt = program.applicationEndedAt,
                    targetGrades = targetGrades.toList(),
                    discordChannelId = program.discordChannelId,
                )
                program.managerMemberId = program.createdByMemberId
            }

            // Soft Delete: 실제 행을 지우지 않아 신청·이력이 보존된다(요구사항 8절/21절).
            ProgramStatus.DELETED -> {
                program.deletedAt = now
                // Storage Binary는 지우지 않는다(FileLinkPort.unlinkAllOf KDoc 참고) -- 연결만
                // 해제해 이 Program이 더 이상 첨부파일을 "쓰고 있지 않은" 상태로 만든다. 물리
                // 삭제는 Cleanup(Phase 5)이 보존 기간을 보고 판단한다(Issue #127 §5 결정).
                // findForUpdate가 이미 이 Program Row를 Lock한 같은 Transaction 안에서 호출해
                // 새 Transaction을 열지 않는다.
                fileLinkPort.unlinkAllOf(FileOwnerType.PROGRAM, programId)
            }

            // allowedTransitions()는 어떤 현재 상태에서도 DRAFT/CLOSED를 target으로 반환하지 않아
            // (위 판정을 통과했다면 target은 항상 PUBLISHED/DELETED다) 실행되지 않지만, Kotlin이
            // enum when을 Exhaustive하게 요구해 분기 자체는 남겨둔다.
            ProgramStatus.DRAFT, ProgramStatus.CLOSED -> {
                Unit
            }
        }
        program.status = target

        programRepository.flush()
        publishProgramDiscordEventFor(programId, target, previousStatus)
        // 신청자에게 보낼 인앱 알림용 Event다(Issue #118). Discord Event와 달리 DRAFT였던
        // 프로그램도 거르지 않는다 -- 게시된 적 없는 프로그램에는 신청자가 있을 수 없어
        // (PUBLISHED로만 신청 가능, computeProgramEligibilityReason 참고) 구독 측이 수신자 0명으로
        // 자연히 아무 알림도 만들지 않으므로, 여기서 상태별 예외를 따로 두지 않는다.
        if (target == ProgramStatus.DELETED) {
            eventPublisher.publishEvent(ProgramDeletedEvent(programId, program.title))
        }

        return ProgramStatusUpdateResponse(
            programId = programId,
            status = program.status,
            manager = program.managerMemberId?.let { ProgramManagerSummary(it, memberName(it)) },
            // Notification/빈자리 구독 기능(Phase 6)이 아직 없어 항상 0이다.
            notificationCount = 0,
            expiredVacancySubscriptionCount = 0,
            updatedAt = program.updatedAt,
        )
    }

    @Transactional(readOnly = true)
    override fun list(
        programType: ProgramType?,
        status: ProgramStatus?,
        openOnly: Boolean,
        requesterMemberId: Long,
        pageable: Pageable,
    ): ProgramListResponse {
        val page =
            programRepository.search(
                type = programType,
                status = status,
                openOnly = openOnly,
                publishedStatus = ProgramStatus.PUBLISHED,
                now = LocalDateTime.now(),
                pageable = pageable,
            )
        val programIds = page.content.map { requireNotNull(it.id) }
        val currentApplicantsByProgramId = activeApplicantCounts(programIds)
        val appliedProgramIds = programIdsWithActiveApplication(programIds, requesterMemberId)
        val content =
            page.content.map { program ->
                val programId = requireNotNull(program.id)
                toSummary(
                    program = program,
                    currentApplicants = currentApplicantsByProgramId[programId] ?: 0,
                    applied = programId in appliedProgramIds,
                )
            }
        return ProgramListResponse(
            content = content,
            page = page.number,
            size = page.size,
            totalElements = page.totalElements,
            totalPages = page.totalPages,
            first = page.isFirst,
            last = page.isLast,
        )
    }

    @Transactional
    override fun getDetail(
        programId: Long,
        requesterMemberId: Long,
    ): ProgramDetailResponse {
        val program = programRepository.findById(programId).orElseThrow { ProgramNotFoundException(programId) }
        val targetGrades = currentTargetGrades(programId)
        val member = memberApplicantSnapshotQueryPort.findById(requesterMemberId)
        val latestApplication = latestApplication(programId, requesterMemberId)
        val hasActiveApplication = hasActiveApplication(programId, requesterMemberId)
        val currentApplicants = activeApplicantCount(programId)
        val now = LocalDateTime.now()
        val reason =
            computeProgramEligibilityReason(
                program = program,
                targetGrades = targetGrades,
                member = member,
                hasActiveApplication = hasActiveApplication,
                currentApplicants = currentApplicants,
                now = now,
            )
        val canApply = reason == ProgramApplicationEligibilityReason.AVAILABLE
        val applicationEndedAt = program.applicationEndedAt
        val canCancel =
            hasActiveApplication &&
                (applicationEndedAt == null || !now.isAfter(applicationEndedAt))
        val availableActions =
            buildList {
                if (canApply) add(ProgramApplicationAction.APPLY.name)
                if (canCancel) add(ProgramApplicationAction.CANCEL.name)
            }
        val files = programFilesFor(program, programId, requesterMemberId)

        val response =
            ProgramDetailResponse(
                programId = programId,
                title = program.title,
                content = program.bodyMarkdown,
                location = program.location,
                programType = program.type,
                targetGrades = targetGrades.sorted(),
                startAt = program.eventStartedAt,
                endAt = program.eventEndedAt,
                applicationStartAt = program.applicationStartedAt,
                applicationEndAt = program.applicationEndedAt,
                applicationSubmittedAt = latestApplication?.appliedAt,
                applicationCancelledAt = latestApplication?.canceledAt,
                programDeletedAt = program.deletedAt,
                capacity = program.capacity,
                currentApplicants = currentApplicants,
                remainingCapacity = program.capacity?.let { it - currentApplicants },
                firstComeServed = program.firstComeServed,
                canApply = canApply,
                eligibilityReason = reason,
                eligibilityMessage = programEligibilityMessageOf(reason),
                availableActions = availableActions,
                // 빈자리 구독 기능(Phase 6)이 아직 없어 항상 false/false/null이다.
                canSubscribeVacancy = false,
                vacancySubscribed = false,
                vacancySubscriptionStatus = null,
                status = program.status,
                files = files,
            )
        programRepository.incrementViewCount(programId)
        return response
    }

    @Transactional
    override fun executeApplicationAction(
        programId: Long,
        studentMemberId: Long,
        request: ProgramApplicationActionRequest,
    ): ProgramApplicationActionResponse =
        when (request.action) {
            ProgramApplicationAction.APPLY -> apply(programId, studentMemberId, request)
            ProgramApplicationAction.CANCEL -> cancel(programId, studentMemberId)
        }

    private fun apply(
        programId: Long,
        studentMemberId: Long,
        request: ProgramApplicationActionRequest,
    ): ProgramApplicationActionResponse {
        // Program Row를 잠근 뒤 정원을 확인·반영해야 "조회 -> 비교 -> Insert" 3단계 분리로 인한
        // 동시 신청 초과를 막을 수 있다(원본 요구사항 문서 11절/22절 동시성).
        val program = programRepository.findByIdForUpdate(programId) ?: throw ProgramNotFoundException(programId)
        val targetGrades = currentTargetGrades(programId)
        val member = memberApplicantSnapshotQueryPort.findById(studentMemberId)
        val hasActiveApplication = hasActiveApplication(programId, studentMemberId)
        val currentApplicants = activeApplicantCount(programId)
        val now = LocalDateTime.now()

        val reason =
            computeProgramEligibilityReason(
                program = program,
                targetGrades = targetGrades,
                member = member,
                hasActiveApplication = hasActiveApplication,
                currentApplicants = currentApplicants,
                now = now,
            )
        throwIfNotApplicable(reason)

        val application =
            ProgramApplication(
                programId = programId,
                applicantMemberId = studentMemberId,
                status = ProgramApplicationStatus.APPLIED,
            ).apply {
                answers = objectMapper.writeValueAsString(request.answerData ?: emptyList<ProgramApplicationAnswer>())
                program.formId?.let { formId ->
                    val version = programFormLinkQueryPort.findActiveVersion(formId)
                    if (version != null) {
                        this.formId = formId
                        formVersion = version
                    }
                }
            }
        val saved = saveNewApplication(application)
        val savedApplicationId = requireNotNull(saved.id)
        eventPublisher.publishEvent(
            ProgramApplicationAppliedEvent(
                programId = programId,
                applicationId = savedApplicationId,
                applicantMemberId = studentMemberId,
                programTitle = program.title,
            ),
        )

        val newCurrentApplicants = currentApplicants + 1
        return ProgramApplicationActionResponse(
            applicationId = savedApplicationId,
            programId = programId,
            status = saved.status,
            currentApplicants = newCurrentApplicants,
            remainingCapacity = program.capacity?.let { it - newCurrentApplicants },
            availableActions = listOf(ProgramApplicationAction.CANCEL.name),
            vacancyNotificationCount = 0,
            updatedAt = saved.updatedAt,
        )
    }

    @Suppress("ThrowsCount")
    private fun cancel(
        programId: Long,
        studentMemberId: Long,
    ): ProgramApplicationActionResponse {
        val program = programRepository.findByIdForUpdate(programId) ?: throw ProgramNotFoundException(programId)
        val application =
            programApplicationRepository.findByProgramIdAndApplicantMemberIdAndStatus(
                programId,
                studentMemberId,
                ProgramApplicationStatus.APPLIED,
            ) ?: throw ActiveApplicationNotFoundException()

        val now = LocalDateTime.now()
        val applicationEndedAt = program.applicationEndedAt
        if (applicationEndedAt != null && now.isAfter(applicationEndedAt)) {
            throw ProgramActionNotAvailableException("신청 종료 후에는 취소할 수 없습니다.")
        }

        application.status = ProgramApplicationStatus.CANCELED
        application.canceledAt = now
        programApplicationRepository.flush()
        eventPublisher.publishEvent(
            ProgramApplicationCanceledEvent(
                programId = programId,
                applicationId = requireNotNull(application.id),
                applicantMemberId = studentMemberId,
                programTitle = program.title,
            ),
        )

        val currentApplicants = activeApplicantCount(programId)
        return ProgramApplicationActionResponse(
            applicationId = requireNotNull(application.id),
            programId = programId,
            status = application.status,
            currentApplicants = currentApplicants,
            remainingCapacity = program.capacity?.let { it - currentApplicants },
            availableActions = listOf(ProgramApplicationAction.APPLY.name),
            // 취소로 새로 생긴 빈자리 알림은 빈자리 구독 기능(Phase 6)이 아직 없어 항상 0이다.
            vacancyNotificationCount = 0,
            updatedAt = application.updatedAt,
        )
    }

    @Suppress("ThrowsCount")
    private fun throwIfNotApplicable(reason: ProgramApplicationEligibilityReason) {
        when (reason) {
            ProgramApplicationEligibilityReason.AVAILABLE -> Unit

            ProgramApplicationEligibilityReason.NOT_ENROLLED -> throw NotEnrolledException()

            ProgramApplicationEligibilityReason.NOT_TARGET_GRADE -> throw NotTargetGradeException()

            ProgramApplicationEligibilityReason.PROGRAM_NOT_PUBLISHED,
            ProgramApplicationEligibilityReason.PROGRAM_NOT_OPEN,
            -> throw ProgramNotOpenException()

            ProgramApplicationEligibilityReason.PROGRAM_CLOSED -> throw ProgramClosedException()

            ProgramApplicationEligibilityReason.PROGRAM_FULL -> throw ProgramFullException()

            ProgramApplicationEligibilityReason.ALREADY_APPLIED -> throw AlreadyAppliedException()
        }
    }

    // hasActiveApplication() 확인과 이 저장 사이에는 DB 잠금이 없다고 여겨질 수 있으나, 이미
    // findByIdForUpdate로 Program Row를 잠근 상태에서 같은 Transaction 안에 있으므로 같은
    // Program에 대한 동시 요청은 순차적으로 실행된다. uk_program_applications_active_singleton
    // (V15 Migration)은 여러 WAS 인스턴스 등 예상치 못한 경합에 대한 최종 방어선이다
    // (JobApplicationServiceImpl.saveNewApplication과 동일한 패턴).
    private fun saveNewApplication(application: ProgramApplication): ProgramApplication =
        try {
            programApplicationRepository.saveAndFlush(application)
        } catch (ex: DataIntegrityViolationException) {
            log.warn("프로그램 신청 동시 요청이 DB 제약으로 차단됨(uk_program_applications_active_singleton)", ex)
            throw AlreadyAppliedException()
        }

    private fun findForUpdate(programId: Long): Program =
        programRepository.findByIdForUpdate(programId) ?: throw ProgramNotFoundException(programId)

    private fun requireManager(
        program: Program,
        requesterMemberId: Long,
        isDeveloper: Boolean,
    ) {
        val isManager = requesterMemberId == program.createdByMemberId || requesterMemberId == program.managerMemberId
        if (!isDeveloper && !isManager) throw ProgramManageForbiddenException()
    }

    // 다른 교사의 개인 Form을 연결하지 못하게 한다(개발자도 우회하지 못한다, 원본 요구사항 문서
    // 20절 + JobApplicationFormLinkServiceImpl.validateJobManager와 동일한 원칙).
    private fun linkForm(
        formId: Long,
        ownerMemberId: Long,
    ): Long =
        programFormLinkQueryPort.findLinkableProgramForm(formId, ownerMemberId)?.formId
            ?: throw ProgramFormNotLinkableException()

    // update()의 Cyclomatic Complexity를 낮추기 위해 분리했다(PR #81 리뷰로 clearFormId 분기가
    // 추가되며 detekt CyclomaticComplexMethod 임계값을 넘어섬). clearFormId=true가 formId보다
    // 우선한다(명시적 해제 의도, ProgramUpdateRequest KDoc 참고). 그 외에는 기존 formId?.let 그대로
    // "전달 안 함 = 유지"다.
    private fun applyFormLinkUpdate(
        program: Program,
        request: ProgramUpdateRequest,
        requesterMemberId: Long,
    ) {
        if (request.clearFormId) {
            program.formId = null
        } else {
            request.formId?.let { program.formId = linkForm(it, requesterMemberId) }
        }
    }

    /**
     * `fileIds`를 전달하지 않으면(null) 기존 첨부파일을 그대로 둔다. 전달하면(빈 List 포함) 그
     * 목록을 최종 상태로 취급해 기존 연결을 모두 해제한 뒤 다시 연결한다(2단계 결정 A/B안,
     * `ProgramUpdateRequest` KDoc 참고). 항상 먼저 `unlinkAllOf`로 끊는 이유는
     * `FileLinkPortImpl.verifyCount`가 "현재 연결된 개수 + 새로 연결할 개수"로 상한을 검사하기
     * 때문이다 -- 먼저 끊지 않으면 교체가 아니라 추가로 계산되어 상한을 잘못 초과 판정한다.
     */
    private fun applyFileIdsUpdate(
        programId: Long,
        requesterMemberId: Long,
        request: ProgramUpdateRequest,
    ) {
        val fileIds = request.fileIds ?: return
        fileLinkPort.unlinkAllOf(FileOwnerType.PROGRAM, programId)
        if (fileIds.isNotEmpty()) {
            fileLinkPort.validateAndLink(
                requesterId = requesterMemberId,
                fileIds = fileIds,
                purpose = FilePurpose.PROGRAM_ATTACHMENT,
                ownerId = programId,
            )
        }
    }

    private fun saveTargetGrades(
        programId: Long,
        targetGrades: List<Int>,
    ) {
        if (targetGrades.isEmpty()) return
        programTargetGradeRepository.saveAll(
            targetGrades.map { grade -> ProgramTargetGrade(ProgramTargetGradeId(programId, grade)) },
        )
    }

    private fun currentTargetGrades(programId: Long): Set<Int> =
        programTargetGradeRepository.findAllByIdProgramId(programId).map { it.id.grade }.toSet()

    /**
     * 상태 전이에 대응하는 Discord Event를 발행한다(`docs/notification/discord-event-wiring-plan.md`
     * §4.2). `allowedTransitions()`가 `target`을 PUBLISHED/DELETED로만 허용하므로 이 두 값만
     * 처리한다. `DRAFT`였던 프로그램이 삭제되면 Discord에 지울 메시지가 없으므로 발행하지 않는다.
     */
    private fun publishProgramDiscordEventFor(
        programId: Long,
        target: ProgramStatus,
        previousStatus: ProgramStatus,
    ) {
        val action =
            when (target) {
                ProgramStatus.PUBLISHED -> {
                    ProgramDiscordAction.PUBLISHED
                }

                ProgramStatus.DELETED -> {
                    if (previousStatus != ProgramStatus.DRAFT) ProgramDiscordAction.DELETED else null
                }

                ProgramStatus.DRAFT, ProgramStatus.CLOSED -> {
                    null
                }
            } ?: return
        eventPublisher.publishEvent(ProgramDiscordEvent(programId, action))
    }

    /**
     * DRAFT 상태에서 조회 권한이 없는 요청자에게는 빈 목록을 반환한다 -- `ProgramFileAccessChecker`
     * 의 다운로드 권한 판정과 같은 규칙(`canViewProgramFiles`)을 써야 상세 응답이 실제 다운로드
     * 가능 여부보다 더 많은 첨부파일 메타데이터(파일명 등)를 흘리지 않는다.
     *
     * DEVELOPER 판정은 `ProgramFileAccessChecker.canDownload`와 동일하게
     * [memberRoleQueryPort]로 DB에서 직접 조회한다 -- Controller가 넘기는 JWT 기반 `isDeveloper`
     * (`requireManager`가 쓰는 값, `update`/`changeStatus` 전용)를 여기서 재사용하면 두 판정 경로가
     * 서로 다른 Role 출처(JWT 발급 시점 스냅샷 vs 현재 DB 상태)를 쓰게 되어, 토큰 발급 이후 역할이
     * 바뀐 사용자에게 다운로드 권한과 상세 응답의 파일 목록 노출 여부가 어긋날 수 있다.
     */
    private fun programFilesFor(
        program: Program,
        programId: Long,
        requesterMemberId: Long,
    ): List<ProgramFileResponse> {
        val isDeveloper = { RoleType.DEVELOPER in memberRoleQueryPort.findRoles(requesterMemberId) }
        return if (canViewProgramFiles(program, requesterMemberId, isDeveloper)) {
            fileLinkPort.linkedFilesOf(FileOwnerType.PROGRAM, programId).map { it.toResponse() }
        } else {
            emptyList()
        }
    }

    private fun FileSnapshot.toResponse(): ProgramFileResponse =
        ProgramFileResponse(
            fileId = fileId,
            originalName = originalName,
            contentType = contentType,
            size = size,
            downloadUrl = "/api/v1/files/$fileId/download",
        )

    private fun activeApplicantCount(programId: Long): Int =
        programApplicationRepository.countByProgramIdAndStatus(programId, ProgramApplicationStatus.APPLIED).toInt()

    private fun hasActiveApplication(
        programId: Long,
        memberId: Long,
    ): Boolean =
        programApplicationRepository.findByProgramIdAndApplicantMemberIdAndStatus(
            programId,
            memberId,
            ProgramApplicationStatus.APPLIED,
        ) != null

    private fun latestApplication(
        programId: Long,
        memberId: Long,
    ): ProgramApplication? =
        programApplicationRepository.findFirstByProgramIdAndApplicantMemberIdOrderByAppliedAtDescIdDesc(
            programId,
            memberId,
        )

    // list()가 Page 항목마다 activeApplicantCount()를 호출하면 N+1 쿼리가 발생하므로(PR #81 리뷰
    // 지적), programId 목록을 한 번에 넘겨 단일 Query로 가져온다. 신청이 없는 Program은 결과에
    // 없으므로 호출 측에서 없는 Key를 0으로 처리한다.
    private fun activeApplicantCounts(programIds: List<Long>): Map<Long, Int> {
        if (programIds.isEmpty()) return emptyMap()
        return programApplicationRepository
            .countActiveApplicantsByProgramIds(programIds, ProgramApplicationStatus.APPLIED)
            .associate { it.programId to it.activeApplicantCount.toInt() }
    }

    // list()가 Page 항목마다 hasActiveApplication()을 호출하면 N+1 쿼리가 발생하므로(PR #81 리뷰
    // 지적), 활성 신청이 있는 programId만 한 번에 조회한다.
    private fun programIdsWithActiveApplication(
        programIds: List<Long>,
        memberId: Long,
    ): Set<Long> {
        if (programIds.isEmpty()) return emptySet()
        return programApplicationRepository
            .findProgramIdsWithActiveApplication(programIds, memberId, ProgramApplicationStatus.APPLIED)
            .toSet()
    }

    private fun memberName(memberId: Long): String? = memberApplicantSnapshotQueryPort.findById(memberId)?.name

    private fun toSummary(
        program: Program,
        currentApplicants: Int,
        applied: Boolean,
    ): ProgramSummaryResponse =
        ProgramSummaryResponse(
            programId = requireNotNull(program.id),
            title = program.title,
            programType = program.type,
            status = program.status,
            location = program.location,
            startAt = program.eventStartedAt,
            endAt = program.eventEndedAt,
            applicationStartAt = program.applicationStartedAt,
            applicationEndAt = program.applicationEndedAt,
            capacity = program.capacity,
            currentApplicants = currentApplicants,
            remainingCapacity = program.capacity?.let { it - currentApplicants },
            firstComeServed = program.firstComeServed,
            applied = applied,
        )

    private fun allowedTransitions(current: ProgramStatus): Set<ProgramStatus> =
        when (current) {
            ProgramStatus.DRAFT -> setOf(ProgramStatus.PUBLISHED, ProgramStatus.DELETED)

            ProgramStatus.PUBLISHED -> setOf(ProgramStatus.DELETED)

            ProgramStatus.CLOSED -> setOf(ProgramStatus.DELETED)

            // Soft Delete로 deletedAt이 채워지면 findByIdForUpdate가 먼저 걸러내(PROGRAM_NOT_FOUND)
            // 실제로는 도달하지 않지만, 전이표를 한곳에서 읽을 수 있게 남겨둔다.
            ProgramStatus.DELETED -> emptySet()
        }
}
