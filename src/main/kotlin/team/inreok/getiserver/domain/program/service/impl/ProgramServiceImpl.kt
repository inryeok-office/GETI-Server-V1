package team.inreok.getiserver.domain.program.service.impl

import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.application.query.ProgramFormLinkQueryPort
import team.inreok.getiserver.domain.member.query.MemberApplicantSnapshotQueryPort
import team.inreok.getiserver.domain.program.dto.DiscordDeliveryResult
import team.inreok.getiserver.domain.program.dto.ProgramApplicationActionRequest
import team.inreok.getiserver.domain.program.dto.ProgramApplicationActionResponse
import team.inreok.getiserver.domain.program.dto.ProgramApplicationAnswer
import team.inreok.getiserver.domain.program.dto.ProgramCreateRequest
import team.inreok.getiserver.domain.program.dto.ProgramCreateResponse
import team.inreok.getiserver.domain.program.dto.ProgramDetailResponse
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
import team.inreok.getiserver.domain.program.exception.ActiveApplicationNotFoundException
import team.inreok.getiserver.domain.program.exception.AlreadyAppliedException
import team.inreok.getiserver.domain.program.exception.CapacityBelowCurrentApplicantsException
import team.inreok.getiserver.domain.program.exception.NotEnrolledException
import team.inreok.getiserver.domain.program.exception.NotTargetGradeException
import team.inreok.getiserver.domain.program.exception.ProgramActionNotAvailableException
import team.inreok.getiserver.domain.program.exception.ProgramClosedException
import team.inreok.getiserver.domain.program.exception.ProgramDeletedException
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
import tools.jackson.databind.ObjectMapper
import java.time.LocalDateTime

// Program CRUD·상태 관리·목록·상세 Eligibility·신청/취소 동시성을 한 Service가 담당해(Phase
// 1~3 범위) Public Method 6개 + Private Helper가 detekt 기본 TooManyFunctions 임계값(11)을
// 넘는다. JobApplicationEligibility.kt가 이미 같은 방식으로 ReturnCount를 Suppress한 전례를
// 따른다(docs/development/code-quality.md가 "@Suppress를 쓰지 않았다"고 적은 시점 이후 실제
// 코드에 추가된 관례, AGENTS.md 우선순위상 실제 코드가 그 문서보다 우선한다).
@Suppress("TooManyFunctions")
@Service
class ProgramServiceImpl(
    private val programRepository: ProgramRepository,
    private val programTargetGradeRepository: ProgramTargetGradeRepository,
    private val programApplicationRepository: ProgramApplicationRepository,
    private val programFormLinkQueryPort: ProgramFormLinkQueryPort,
    private val memberApplicantSnapshotQueryPort: MemberApplicantSnapshotQueryPort,
    private val objectMapper: ObjectMapper,
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

        val program =
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
        saveTargetGrades(requireNotNull(saved.id), targetGrades)

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
            discordDelivery = DiscordDeliveryResult.SKIPPED_NOT_IMPLEMENTED,
            createdAt = saved.createdAt,
        )
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
        request.formId?.let { program.formId = linkForm(it, requesterMemberId) }

        val currentApplicants = activeApplicantCount(programId)
        request.capacity?.let { newCapacity ->
            if (newCapacity < currentApplicants) throw CapacityBelowCurrentApplicantsException()
            program.capacity = newCapacity
        }

        val targetGrades = currentTargetGrades(programId)
        validateProgramCommon(
            startAt = program.eventStartedAt,
            endAt = program.eventEndedAt,
            applicationStartAt = program.applicationStartedAt,
            applicationEndAt = program.applicationEndedAt,
            targetGrades = targetGrades.toList(),
            capacity = program.capacity,
        )
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
            discordDelivery = DiscordDeliveryResult.SKIPPED_NOT_IMPLEMENTED,
            updatedAt = program.updatedAt,
        )
    }

    @Suppress("ThrowsCount")
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
        // 이 API로 지정할 수 있는 목표 상태는 PUBLISHED/DELETED뿐이다(PUBLISHED->CLOSED는
        // Scheduler 전용, 원본 요구사항 문서 8절).
        if (target != ProgramStatus.PUBLISHED && target != ProgramStatus.DELETED) {
            throw ProgramStatusTransitionNotAllowedException(program.status, target)
        }
        if (target !in allowedTransitions(program.status)) {
            if (program.status == ProgramStatus.CLOSED && target == ProgramStatus.PUBLISHED) {
                throw ProgramReopenNotAllowedException()
            }
            throw ProgramStatusTransitionNotAllowedException(program.status, target)
        }

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
            }
        }
        program.status = target

        programRepository.flush()

        return ProgramStatusUpdateResponse(
            programId = programId,
            status = program.status,
            manager = program.managerMemberId?.let { ProgramManagerSummary(it, memberName(it)) },
            // Notification/빈자리 구독 기능(Phase 6)이 아직 없어 항상 0이다.
            notificationCount = 0,
            expiredVacancySubscriptionCount = 0,
            discordDelivery = DiscordDeliveryResult.SKIPPED_NOT_IMPLEMENTED,
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
        val content =
            page.content.map { program ->
                val currentApplicants = activeApplicantCount(requireNotNull(program.id))
                toSummary(program, currentApplicants, requesterMemberId)
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
        if (program.deletedAt != null) throw ProgramDeletedException()

        val targetGrades = currentTargetGrades(programId)
        val member = memberApplicantSnapshotQueryPort.findById(requesterMemberId)
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

        val response =
            ProgramDetailResponse(
                programId = programId,
                title = program.title,
                content = program.bodyMarkdown,
                programType = program.type,
                targetGrades = targetGrades.sorted(),
                startAt = program.eventStartedAt,
                endAt = program.eventEndedAt,
                applicationStartAt = program.applicationStartedAt,
                applicationEndAt = program.applicationEndedAt,
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

        val newCurrentApplicants = currentApplicants + 1
        return ProgramApplicationActionResponse(
            applicationId = requireNotNull(saved.id),
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

    private fun memberName(memberId: Long): String? = memberApplicantSnapshotQueryPort.findById(memberId)?.name

    private fun toSummary(
        program: Program,
        currentApplicants: Int,
        requesterMemberId: Long,
    ): ProgramSummaryResponse =
        ProgramSummaryResponse(
            programId = requireNotNull(program.id),
            title = program.title,
            programType = program.type,
            status = program.status,
            startAt = program.eventStartedAt,
            endAt = program.eventEndedAt,
            applicationStartAt = program.applicationStartedAt,
            applicationEndAt = program.applicationEndedAt,
            capacity = program.capacity,
            currentApplicants = currentApplicants,
            remainingCapacity = program.capacity?.let { it - currentApplicants },
            firstComeServed = program.firstComeServed,
            applied = hasActiveApplication(requireNotNull(program.id), requesterMemberId),
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
