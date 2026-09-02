package team.inreok.getiserver.domain.inquiry.service.impl

import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.file.entity.type.FileOwnerType
import team.inreok.getiserver.domain.file.entity.type.FilePurpose
import team.inreok.getiserver.domain.file.link.FileLinkPort
import team.inreok.getiserver.domain.file.link.FileSnapshot
import team.inreok.getiserver.domain.file.link.FileUrlPort
import team.inreok.getiserver.domain.inquiry.dto.InquiryAdminAuthorResponse
import team.inreok.getiserver.domain.inquiry.dto.InquiryAdminListItemResponse
import team.inreok.getiserver.domain.inquiry.dto.InquiryAdminListResponse
import team.inreok.getiserver.domain.inquiry.dto.InquiryAnswerCreateRequest
import team.inreok.getiserver.domain.inquiry.dto.InquiryAnswerCreateResponse
import team.inreok.getiserver.domain.inquiry.dto.InquiryAnswerItemResponse
import team.inreok.getiserver.domain.inquiry.dto.InquiryAssigneeResponse
import team.inreok.getiserver.domain.inquiry.dto.InquiryAssigneeUpdateRequest
import team.inreok.getiserver.domain.inquiry.dto.InquiryAssigneeUpdateResponse
import team.inreok.getiserver.domain.inquiry.dto.InquiryAuthorResponse
import team.inreok.getiserver.domain.inquiry.dto.InquiryCreateRequest
import team.inreok.getiserver.domain.inquiry.dto.InquiryCreateResponse
import team.inreok.getiserver.domain.inquiry.dto.InquiryDetailResponse
import team.inreok.getiserver.domain.inquiry.dto.InquiryFileResponse
import team.inreok.getiserver.domain.inquiry.dto.InquiryListItemResponse
import team.inreok.getiserver.domain.inquiry.dto.InquiryListResponse
import team.inreok.getiserver.domain.inquiry.dto.InquiryStatusUpdateRequest
import team.inreok.getiserver.domain.inquiry.dto.InquiryStatusUpdateResponse
import team.inreok.getiserver.domain.inquiry.entity.Inquiry
import team.inreok.getiserver.domain.inquiry.entity.InquiryAnswer
import team.inreok.getiserver.domain.inquiry.entity.type.InquiryStatus
import team.inreok.getiserver.domain.inquiry.entity.type.InquiryType
import team.inreok.getiserver.domain.inquiry.event.InquiryAnsweredEvent
import team.inreok.getiserver.domain.inquiry.event.InquiryCreatedEvent
import team.inreok.getiserver.domain.inquiry.exception.InquiryAccessDeniedException
import team.inreok.getiserver.domain.inquiry.exception.InquiryAlreadyClosedException
import team.inreok.getiserver.domain.inquiry.exception.InquiryAssigneeMemberNotFoundException
import team.inreok.getiserver.domain.inquiry.exception.InquiryNotFoundException
import team.inreok.getiserver.domain.inquiry.exception.InquiryStatusInvalidException
import team.inreok.getiserver.domain.inquiry.exception.InvalidInquiryAssigneeException
import team.inreok.getiserver.domain.inquiry.repository.InquiryAnswerRepository
import team.inreok.getiserver.domain.inquiry.repository.InquiryRepository
import team.inreok.getiserver.domain.inquiry.service.InquiryService
import team.inreok.getiserver.domain.inquiry.service.escapeLikePattern
import team.inreok.getiserver.domain.member.query.InquiryAssigneeCandidateQueryPort
import team.inreok.getiserver.domain.member.query.InquiryAuthorSearchQueryPort
import team.inreok.getiserver.domain.member.query.InquiryMemberSnapshot
import team.inreok.getiserver.domain.member.query.InquiryMemberSnapshotQueryPort

// Inquiry Core·Admin(목록/검색·담당자·상태 변경·답변 등록)을 한 Service가 담당해(Phase 2~4 범위)
// Public Method 7개 + Private Helper가 detekt 기본 TooManyFunctions 임계값(11)을 넘는다.
// ProgramServiceImpl.kt가 같은 이유로 이미 Suppress한 전례를 따른다.
@Suppress("TooManyFunctions")
@Service
class InquiryServiceImpl(
    private val inquiryRepository: InquiryRepository,
    private val inquiryAnswerRepository: InquiryAnswerRepository,
    private val inquiryMemberSnapshotQueryPort: InquiryMemberSnapshotQueryPort,
    private val inquiryAssigneeCandidateQueryPort: InquiryAssigneeCandidateQueryPort,
    private val inquiryAuthorSearchQueryPort: InquiryAuthorSearchQueryPort,
    private val fileLinkPort: FileLinkPort,
    private val fileUrlPort: FileUrlPort,
    private val eventPublisher: ApplicationEventPublisher,
) : InquiryService {
    @Transactional
    override fun create(
        request: InquiryCreateRequest,
        authorMemberId: Long,
    ): InquiryCreateResponse {
        val inquiry =
            Inquiry(
                authorMemberId = authorMemberId,
                type = request.inquiryType,
                title = request.title.trim(),
                content = request.content,
            )
        // @CreationTimestamp/@UpdateTimestamp는 Flush 시점에 채워진다. 응답의 createdAt/updatedAt이
        // null로 나가지 않도록 저장과 동시에 Flush한다(CompanyServiceImpl.create와 동일한 이유).
        val saved = inquiryRepository.saveAndFlush(inquiry)
        val inquiryId = requireNotNull(saved.id) { "저장된 Inquiry는 id를 가져야 합니다." }

        // Inquiry 저장 이후에만 연결한다 -- 연결 대상(ownerId)으로 쓸 inquiryId가 저장 전에는 없다
        // (CompanyServiceImpl.linkLogo와 동일한 순서). 소유권·목적·상태 검증은
        // FileLinkPort.validateAndLink가 수행하며, 거부되면 예외가 Transaction을 되돌려 문의도
        // 함께 만들어지지 않는다.
        val fileIds = request.fileIds
        val fileSnapshots =
            if (fileIds.isNullOrEmpty()) {
                emptyList()
            } else {
                fileLinkPort.validateAndLink(
                    requesterId = authorMemberId,
                    fileIds = fileIds,
                    purpose = FilePurpose.INQUIRY_ATTACHMENT,
                    ownerId = inquiryId,
                )
            }

        val createdAt = requireNotNull(saved.createdAt) { "저장된 Inquiry는 createdAt을 가져야 합니다." }

        // domain.notification의 InquiryDiscordEventListener가 이 Event를 구독해 Discord 접수
        // 알림 Delivery를 예약한다(Issue #97). Commit 이후에만 반영돼야 하는 Hook Point이므로
        // Phase 4의 InquiryAnsweredEvent와 동일하게 Transaction 안에서 발행한다. publishEvent
        // 자체가 예외를 던질 이유가 없지만, 이 발행이 문의 등록 자체를 막지 않도록 방어적으로
        // 감싼다.
        runCatching {
            eventPublisher.publishEvent(
                InquiryCreatedEvent(
                    inquiryId = inquiryId,
                    inquiryType = saved.type,
                    title = saved.title,
                    authorMemberId = authorMemberId,
                    occurredAt = createdAt,
                ),
            )
        }

        return InquiryCreateResponse(
            inquiryId = inquiryId,
            inquiryType = saved.type,
            title = saved.title,
            content = saved.content,
            status = saved.status,
            author = authorResponseOf(authorMemberId, requesterId = authorMemberId),
            files = fileSnapshots.map { it.toResponse() },
            answers = emptyList(),
            createdAt = createdAt,
            updatedAt = requireNotNull(saved.updatedAt) { "저장된 Inquiry는 updatedAt을 가져야 합니다." },
        )
    }

    @Transactional(readOnly = true)
    override fun listMine(
        status: InquiryStatus?,
        requesterMemberId: Long,
        pageable: Pageable,
    ): InquiryListResponse {
        val page =
            if (status == null) {
                inquiryRepository.findAllByAuthorMemberIdOrderByCreatedAtDescIdDesc(requesterMemberId, pageable)
            } else {
                inquiryRepository.findAllByAuthorMemberIdAndStatusOrderByCreatedAtDescIdDesc(
                    requesterMemberId,
                    status,
                    pageable,
                )
            }
        return InquiryListResponse(
            content = page.content.map { it.toListItem() },
            page = page.number,
            size = page.size,
            totalElements = page.totalElements,
            totalPages = page.totalPages,
            first = page.isFirst,
            last = page.isLast,
        )
    }

    @Transactional(readOnly = true)
    override fun getDetail(
        inquiryId: Long,
        requesterMemberId: Long,
        isDeveloper: Boolean,
    ): InquiryDetailResponse {
        val inquiry = findOrThrow(inquiryId)
        // Controller Annotation/SecurityConfig는 "인증된 사용자"까지만 보장한다. 본인 문의인지는
        // Role로 알 수 없어 여기서 별도로 검증한다(요구사항 16절/52절).
        if (!isDeveloper && inquiry.authorMemberId != requesterMemberId) {
            throw InquiryAccessDeniedException()
        }

        val files = fileLinkPort.linkedFilesOf(FileOwnerType.INQUIRY, inquiryId).map { it.toResponse() }
        val answerEntities = inquiryAnswerRepository.findAllByInquiryIdOrderByCreatedAtAscIdAsc(inquiryId)
        // 답변마다 첨부파일을 개별 조회하면 답변 수만큼 Query가 늘어난다(N+1). 이 상세 응답에
        // 등장하는 모든 answerId를 한 번에 모아 배치로 조회한다(listAdmin의 Member Snapshot 배치
        // 조회와 동일한 원칙).
        val answerIds = answerEntities.mapNotNull { it.id }
        val filesByAnswerId =
            if (answerIds.isEmpty()) emptyMap() else fileLinkPort.linkedFilesOf(FileOwnerType.INQUIRY_ANSWER, answerIds)
        val answers = answerEntities.map { it.toResponse(filesByAnswerId) }
        // 담당 개발자의 운영 정보는 개발자 응답에만 노출한다(요구사항 17절).
        val assignee = if (isDeveloper) assigneeResponseOf(inquiry.assigneeMemberId) else null

        return InquiryDetailResponse(
            inquiryId = inquiryId,
            inquiryType = inquiry.type,
            title = inquiry.title,
            content = inquiry.content,
            status = inquiry.status,
            author = authorResponseOf(inquiry.authorMemberId, requesterId = requesterMemberId),
            files = files,
            assignee = assignee,
            answers = answers,
            createdAt = requireNotNull(inquiry.createdAt) { "저장된 Inquiry는 createdAt을 가져야 합니다." },
            updatedAt = requireNotNull(inquiry.updatedAt) { "저장된 Inquiry는 updatedAt을 가져야 합니다." },
        )
    }

    @Transactional(readOnly = true)
    override fun listAdmin(
        inquiryType: InquiryType?,
        status: InquiryStatus?,
        query: String?,
        assigneeId: Long?,
        mineOnly: Boolean,
        requesterMemberId: Long,
        pageable: Pageable,
        answered: Boolean?,
    ): InquiryAdminListResponse {
        // 검색어를 보내지 않은 경우와 공백만 보낸 경우를 모두 "검색어 없음"으로 취급한다
        // (CompanyServiceImpl.search와 동일한 관례).
        val trimmedQuery = query?.trim()?.takeIf { it.isNotEmpty() }
        // 검색 대상은 title/content/author name이다(요구사항 28절, studentNumber 제외). 작성자
        // 이름 매치는 Member 도메인 Port로 미리 id 집합만 받아 IN 조건으로 쓴다(Module 경계 유지,
        // InquiryAuthorSearchQueryPort KDoc 참고). 검색어가 없으면 이 Port 자체를 호출하지 않는다.
        // escapeLikePattern은 한 번만 계산해 searchForAdmin과 findIdsByNameContaining에 동일하게
        // 넘긴다(MemberRepository.findIdsByNameContaining KDoc 계약 -- 호출 측이 LIKE Wildcard를
        // 이스케이프해 전달해야 함). 두 곳에서 각각 이스케이프하면 값이 어긋날 여지가 생긴다.
        val escapedQuery = trimmedQuery?.let(::escapeLikePattern)
        val authorIds = escapedQuery?.let { inquiryAuthorSearchQueryPort.findIdsByNameContaining(it) } ?: emptySet()
        val mineOnlyMemberId = if (mineOnly) requesterMemberId else null

        val page =
            inquiryRepository.searchForAdmin(
                type = inquiryType,
                status = status,
                answered = answered,
                assigneeId = assigneeId,
                mineOnlyMemberId = mineOnlyMemberId,
                // :query를 null로 바인딩하지 않는 이유는 InquiryRepository.searchForAdmin KDoc 참고
                // (PostgreSQL의 NULL 문자열 Parameter Type 추론 문제, 실측으로 발견).
                hasQuery = trimmedQuery != null,
                query = escapedQuery ?: "",
                authorIds = authorIds,
                pageable = pageable,
            )

        // 목록 Row마다 작성자·담당자를 개별 조회하면 Row 수만큼 Query가 늘어난다(N+1). 이번
        // Page에 등장하는 모든 회원 id를 한 번에 모아 Snapshot을 배치로 조회한다
        // (MemberSearchServiceImpl.search의 FileUrlPort 배치 조회와 동일한 원칙).
        val memberIds = page.content.flatMap { listOfNotNull(it.authorMemberId, it.assigneeMemberId) }.toSet()
        val snapshots =
            if (memberIds.isEmpty()) emptyMap() else inquiryMemberSnapshotQueryPort.findAllByIds(memberIds)

        return InquiryAdminListResponse(
            content = page.content.map { it.toAdminListItem(snapshots) },
            page = page.number,
            size = page.size,
            totalElements = page.totalElements,
            totalPages = page.totalPages,
            first = page.isFirst,
            last = page.isLast,
        )
    }

    @Transactional
    override fun updateAssignee(
        inquiryId: Long,
        request: InquiryAssigneeUpdateRequest,
    ): InquiryAssigneeUpdateResponse {
        val inquiry = findOrThrow(inquiryId)
        val assigneeId = request.assigneeId
        // 담당자 지정은 문의 상태를 바꾸지 않는다 -- 배정과 Workflow 상태는 독립적이다(요구사항
        // 34절, 사용자 확인 완료). inquiry.status는 여기서 건드리지 않는다.
        if (assigneeId == null) {
            inquiry.assigneeMemberId = null
        } else {
            inquiry.assigneeMemberId = validatedAssigneeId(assigneeId)
        }
        inquiryRepository.flush()

        return InquiryAssigneeUpdateResponse(
            inquiryId = inquiryId,
            assignee = assigneeResponseOf(inquiry.assigneeMemberId),
            updatedAt = requireNotNull(inquiry.updatedAt) { "저장된 Inquiry는 updatedAt을 가져야 합니다." },
        )
    }

    @Transactional
    override fun changeStatus(
        inquiryId: Long,
        request: InquiryStatusUpdateRequest,
    ): InquiryStatusUpdateResponse {
        // 답변 등록(createAnswer)도 CLOSED 확인에 같은 findByIdForUpdate를 쓴다 -- 두 요청이
        // 동시에 들어와도 Row Lock으로 순차화되어 먼저 Commit되는 쪽이 이긴다(§CLOSED-답변
        // 동시성, 사용자 확인 완료). 여기서 Lock을 걸지 않으면 답변 등록만 안전해지고 CLOSED
        // 전환은 여전히 경합에 노출된다.
        val inquiry = findOrThrowForUpdate(inquiryId)
        val target = request.status
        if (target !in ALLOWED_TRANSITIONS.getOrDefault(inquiry.status, emptySet())) {
            throw InquiryStatusInvalidException(
                "허용되지 않은 상태 전이입니다. (${inquiry.status} -> $target)",
            )
        }
        // status=ANSWERED를 직접 지정할 때 답변이 하나도 없으면 "답변 없이 ANSWERED"라는 정합성
        // 모순이 생긴다(요구사항 37절, 사용자 확인 완료 -- 400으로 거부).
        if (target == InquiryStatus.ANSWERED && !inquiryAnswerRepository.existsByInquiryId(inquiryId)) {
            throw InquiryStatusInvalidException("답변이 없는 문의는 ANSWERED로 변경할 수 없습니다.")
        }

        inquiry.status = target
        inquiryRepository.flush()

        return InquiryStatusUpdateResponse(
            inquiryId = inquiryId,
            status = inquiry.status,
            updatedAt = requireNotNull(inquiry.updatedAt) { "저장된 Inquiry는 updatedAt을 가져야 합니다." },
        )
    }

    @Transactional
    override fun createAnswer(
        inquiryId: Long,
        request: InquiryAnswerCreateRequest,
        authorMemberId: Long,
    ): InquiryAnswerCreateResponse {
        // inquiry_answers.inquiry_id에는 실제 FK 제약이 있다(V18 Migration). 존재하지 않는
        // inquiryId로 InquiryAnswer를 저장하면 FK 위반(제어되지 않은 500)이 되므로 먼저 확인한다.
        // 이 조회는 Lock을 걸지 않는다 -- CLOSED 동시성 보호는 아래에서 findByIdForUpdate로 다시
        // 확인할 때 담당하고, 여기서는 순수히 "문의가 존재하는가"만 본다.
        if (!inquiryRepository.existsById(inquiryId)) throw InquiryNotFoundException(inquiryId)

        // Answer를 먼저 저장해 ownerId(answerId)를 확보한다 -- 파일 연결 대상 ID는 저장 전에는
        // 없다(InquiryServiceImpl.create의 File 연결과 동일한 순서). fileIds 소유권 검증(File
        // 도메인 Port 호출)을 Inquiry Row Lock을 잡기 전에 끝내 Lock 보유 시간을 최소화한다(§CLOSED-답변
        // 동시성, 사용자 확인 완료) -- CLOSED 검증보다도 이 검증이 먼저 실행된다. 아래에서 CLOSED로
        // 판명되면 이 INSERT를 포함한 Transaction 전체가 Rollback되므로 안전하다.
        val answer = InquiryAnswer(inquiryId = inquiryId, authorMemberId = authorMemberId, content = request.content)
        val savedAnswer = inquiryAnswerRepository.saveAndFlush(answer)
        val answerId = requireNotNull(savedAnswer.id) { "저장된 InquiryAnswer는 id를 가져야 합니다." }
        val answerCreatedAt = requireNotNull(savedAnswer.createdAt) { "저장된 InquiryAnswer는 createdAt을 가져야 합니다." }

        val fileIds = request.fileIds
        val fileSnapshots =
            if (fileIds.isNullOrEmpty()) {
                emptyList()
            } else {
                fileLinkPort.validateAndLink(
                    requesterId = authorMemberId,
                    fileIds = fileIds,
                    purpose = FilePurpose.INQUIRY_ANSWER_ATTACHMENT,
                    ownerId = answerId,
                )
            }

        // 여기서부터 Inquiry Row를 Pessimistic Write Lock으로 잠근다(InquiryRepository.findByIdForUpdate
        // KDoc 참고). CLOSED 확인 -> answeredAt/status 갱신만 Lock 보유 구간에 둔다.
        val inquiry = findOrThrowForUpdate(inquiryId)
        if (inquiry.status == InquiryStatus.CLOSED) {
            throw InquiryAlreadyClosedException(inquiryId)
        }

        // 최초 답변 시각만 기록하고 이후 답변이 추가돼도 갱신하지 않는다(요구사항 §83 5번, 사용자
        // 확인 완료).
        if (inquiry.answeredAt == null) {
            inquiry.answeredAt = answerCreatedAt
        }
        // 답변 등록은 문의 상태를 ANSWERED로 자동 전이한다(사용자 확인 완료) -- 담당자 지정과
        // 달리 답변은 문의의 진행 상태 자체를 나타내는 사건이다. 이미 ANSWERED인 문의에 추가
        // 답변이 달려도 그대로 ANSWERED를 유지하는 멱등 대입이라 별도 전이 Matrix 검사가
        // 필요하지 않다(CLOSED는 위에서 이미 거부했다).
        inquiry.status = InquiryStatus.ANSWERED
        inquiryRepository.flush()

        // Notification 생성은 이 Transaction의 Commit 이후에만 실행되는 별도 Listener
        // (domain.notification)가 담당한다(JobIndexSyncEventListener와 동일한 방식). 여기서는
        // Event 발행까지만 책임지고, Listener가 실패해도 이 Answer Transaction은 이미 Commit된
        // 뒤라 영향을 받지 않는다. notificationCreated는 Row 생성 여부가 아니라 이 발행 자체의
        // 성공 여부다(사용자 확인 완료, InquiryAnswerCreateResponse KDoc 참고).
        val notificationCreated =
            runCatching {
                eventPublisher.publishEvent(
                    InquiryAnsweredEvent(
                        inquiryId = inquiryId,
                        answerId = answerId,
                        inquiryAuthorMemberId = inquiry.authorMemberId,
                        inquiryTitle = inquiry.title,
                    ),
                )
            }.isSuccess

        return InquiryAnswerCreateResponse(
            answerId = answerId,
            inquiryId = inquiryId,
            authorMemberId = authorMemberId,
            content = savedAnswer.content,
            files = fileSnapshots.map { it.toResponse() },
            createdAt = answerCreatedAt,
            inquiryStatus = inquiry.status,
            notificationCreated = notificationCreated,
        )
    }

    private fun findOrThrow(inquiryId: Long): Inquiry =
        inquiryRepository.findById(inquiryId).orElseThrow { InquiryNotFoundException(inquiryId) }

    /**
     * [InquiryRepository.findByIdForUpdate]로 Pessimistic Write Lock을 걸고 조회한다. 이 Helper로
     * 감싸는 이유는 순수 기능 목적이 아니라 detekt `ThrowsCount`를 맞추기 위해서다 -- `changeStatus`/
     * `createAnswer`는 이미 각자 자기 Domain 예외(INQUIRY_STATUS_INVALID/INQUIRY_ALREADY_CLOSED)를
     * 직접 던지므로, `?: throw InquiryNotFoundException(...)`까지 그대로 인라인하면 허용치(2)를
     * 넘는다.
     */
    private fun findOrThrowForUpdate(inquiryId: Long): Inquiry =
        inquiryRepository.findByIdForUpdate(inquiryId) ?: throw InquiryNotFoundException(inquiryId)

    /**
     * 담당자 후보를 검증한다. role=DEVELOPER AND status=ACTIVE를 모두 만족해야 한다(요구사항
     * 32절). 회원 자체가 없으면 404, 있지만 조건을 만족하지 않으면 400이다 -- 두 실패를 같은
     * Code로 뭉치면 "존재하지 않는 회원"과 "지정할 수 없는 회원"을 클라이언트가 구분할 수 없다.
     */
    private fun validatedAssigneeId(assigneeId: Long): Long {
        val candidate =
            inquiryAssigneeCandidateQueryPort.findById(assigneeId)
                ?: throw InquiryAssigneeMemberNotFoundException(assigneeId)
        val isActiveDeveloper = DEVELOPER_ROLE in candidate.roles && candidate.status == ACTIVE_STATUS
        if (!isActiveDeveloper) throw InvalidInquiryAssigneeException()
        return assigneeId
    }

    private fun authorResponseOf(
        authorMemberId: Long,
        requesterId: Long,
    ): InquiryAuthorResponse {
        val snapshot = inquiryMemberSnapshotQueryPort.findAllByIds(setOf(authorMemberId))[authorMemberId]
        val profileImageUrl =
            snapshot?.profileImageFileId?.let { fileId ->
                fileUrlPort.presignedImageUrls(requesterId, listOf(fileId))[fileId]
            }
        return InquiryAuthorResponse(
            memberId = authorMemberId,
            name = snapshot?.name,
            profileImageUrl = profileImageUrl,
            cohort = snapshot?.cohort,
            department = snapshot?.department,
            isPublic = snapshot?.isPublic ?: false,
        )
    }

    private fun assigneeResponseOf(assigneeMemberId: Long?): InquiryAssigneeResponse? {
        if (assigneeMemberId == null) return null
        val snapshot = inquiryMemberSnapshotQueryPort.findAllByIds(setOf(assigneeMemberId))[assigneeMemberId]
        return InquiryAssigneeResponse(memberId = assigneeMemberId, name = snapshot?.name)
    }

    private fun Inquiry.toListItem(): InquiryListItemResponse =
        InquiryListItemResponse(
            inquiryId = requireNotNull(id) { "저장된 Inquiry는 id를 가져야 합니다." },
            inquiryType = type,
            title = title,
            status = status,
            createdAt = requireNotNull(createdAt) { "저장된 Inquiry는 createdAt을 가져야 합니다." },
            answeredAt = answeredAt,
        )

    private fun Inquiry.toAdminListItem(snapshots: Map<Long, InquiryMemberSnapshot>): InquiryAdminListItemResponse {
        val assigneeMemberId = assigneeMemberId
        val inquiryId = requireNotNull(id) { "저장된 Inquiry는 id를 가져야 합니다." }
        return InquiryAdminListItemResponse(
            inquiryId = inquiryId,
            inquiryType = type,
            title = title,
            status = status,
            author = InquiryAdminAuthorResponse(memberId = authorMemberId, name = snapshots[authorMemberId]?.name),
            assignee =
                assigneeMemberId?.let {
                    InquiryAssigneeResponse(memberId = it, name = snapshots[it]?.name)
                },
            createdAt = requireNotNull(createdAt) { "저장된 Inquiry는 createdAt을 가져야 합니다." },
            answeredAt = answeredAt,
        )
    }

    private fun InquiryAnswer.toResponse(filesByAnswerId: Map<Long, List<FileSnapshot>>): InquiryAnswerItemResponse {
        val answerId = requireNotNull(id) { "저장된 InquiryAnswer는 id를 가져야 합니다." }
        return InquiryAnswerItemResponse(
            answerId = answerId,
            inquiryId = inquiryId,
            authorMemberId = authorMemberId,
            content = content,
            files = filesByAnswerId[answerId].orEmpty().map { it.toResponse() },
            createdAt = requireNotNull(createdAt) { "저장된 InquiryAnswer는 createdAt을 가져야 합니다." },
        )
    }

    private fun FileSnapshot.toResponse(): InquiryFileResponse =
        InquiryFileResponse(
            fileId = fileId,
            originalName = originalName,
            contentType = contentType,
            size = size,
            downloadUrl = "/api/v1/files/$fileId/download",
        )

    private companion object {
        const val DEVELOPER_ROLE = "DEVELOPER"
        const val ACTIVE_STATUS = "ACTIVE"

        // 추가 전이(ANSWERED->IN_PROGRESS 재개, 답변 없이 CLOSED 등)는 사용자 확인 결과 채택하지
        // 않았다(Group A 결정, 아무것도 선택되지 않아 기본안만 적용). CLOSED는 최종 상태라 이
        // Map에 없다(값이 없으면 getOrDefault가 빈 Set을 돌려주므로 모든 전이가 거부된다).
        val ALLOWED_TRANSITIONS: Map<InquiryStatus, Set<InquiryStatus>> =
            mapOf(
                InquiryStatus.RECEIVED to setOf(InquiryStatus.IN_PROGRESS, InquiryStatus.ANSWERED),
                InquiryStatus.IN_PROGRESS to setOf(InquiryStatus.ANSWERED, InquiryStatus.CLOSED),
                InquiryStatus.ANSWERED to setOf(InquiryStatus.CLOSED),
            )
    }
}
