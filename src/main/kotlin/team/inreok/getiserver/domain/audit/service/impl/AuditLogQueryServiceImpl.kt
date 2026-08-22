package team.inreok.getiserver.domain.audit.service.impl

import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.audit.dto.AdminAuditLogChangeResponse
import team.inreok.getiserver.domain.audit.dto.AdminAuditLogDetailResponse
import team.inreok.getiserver.domain.audit.dto.AdminAuditLogListItemResponse
import team.inreok.getiserver.domain.audit.dto.AdminAuditLogListResponse
import team.inreok.getiserver.domain.audit.entity.AuditLog
import team.inreok.getiserver.domain.audit.entity.type.AuditResult
import team.inreok.getiserver.domain.audit.exception.AuditLogNotFoundException
import team.inreok.getiserver.domain.audit.repository.AuditLogRepository
import team.inreok.getiserver.domain.audit.service.AuditLogQueryService
import team.inreok.getiserver.domain.member.query.InquiryMemberSnapshot
import team.inreok.getiserver.domain.member.query.InquiryMemberSnapshotQueryPort
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.time.LocalDateTime

@Service
class AuditLogQueryServiceImpl(
    private val auditLogRepository: AuditLogRepository,
    private val memberSnapshotQueryPort: InquiryMemberSnapshotQueryPort,
    private val objectMapper: ObjectMapper,
) : AuditLogQueryService {
    @Transactional(readOnly = true)
    override fun list(
        actorId: Long?,
        actionType: String?,
        targetType: String?,
        targetId: Long?,
        result: AuditResult?,
        startAt: LocalDateTime?,
        endAt: LocalDateTime?,
        pageable: Pageable,
    ): AdminAuditLogListResponse {
        val page =
            auditLogRepository.searchForAdmin(
                actorId = actorId,
                actionType = actionType,
                targetType = targetType,
                targetId = targetId,
                result = result,
                startAt = startAt,
                endAt = endAt,
                pageable = PageRequest.of(pageable.pageNumber, pageable.pageSize),
            )
        return page.toListResponse(loadActors(page.content))
    }

    @Transactional(readOnly = true)
    override fun get(auditLogId: Long): AdminAuditLogDetailResponse {
        val auditLog =
            auditLogRepository.findById(auditLogId).orElseThrow {
                AuditLogNotFoundException(auditLogId)
            }
        return auditLog.toDetailResponse(loadActors(listOf(auditLog)))
    }

    private fun loadActors(auditLogs: List<AuditLog>): Map<Long, InquiryMemberSnapshot> =
        memberSnapshotQueryPort.findAllByIds(auditLogs.mapNotNull { it.actorMemberId }.toSet())

    private fun Page<AuditLog>.toListResponse(actors: Map<Long, InquiryMemberSnapshot>) =
        AdminAuditLogListResponse(
            content = content.map { it.toListItemResponse(actors) },
            page = number,
            size = size,
            totalElements = totalElements,
            totalPages = totalPages,
            first = isFirst,
            last = isLast,
        )

    private fun AuditLog.toListItemResponse(actors: Map<Long, InquiryMemberSnapshot>) =
        AdminAuditLogListItemResponse(
            auditLogId = requireNotNull(id) { "저장된 AuditLog는 id를 가져야 합니다." },
            actorId = actorMemberId,
            actorName = actorMemberId?.let { actors[it]?.name },
            actionType = action,
            targetType = targetType,
            targetId = targetId,
            result = result,
            requestPath = requestPath,
            maskedDetail = maskDetail(resultMessage),
            createdAt = requireNotNull(createdAt) { "저장된 AuditLog는 createdAt을 가져야 합니다." },
        )

    private fun AuditLog.toDetailResponse(actors: Map<Long, InquiryMemberSnapshot>) =
        AdminAuditLogDetailResponse(
            auditLogId = requireNotNull(id) { "저장된 AuditLog는 id를 가져야 합니다." },
            actorId = actorMemberId,
            actorName = actorMemberId?.let { actors[it]?.name },
            actionType = action,
            targetType = targetType,
            targetId = targetId,
            result = result,
            requestPath = requestPath,
            maskedDetail = maskDetail(resultMessage),
            createdAt = requireNotNull(createdAt) { "저장된 AuditLog는 createdAt을 가져야 합니다." },
            changes = parseChanges(changedData),
        )

    private fun parseChanges(changedData: String?): List<AdminAuditLogChangeResponse> {
        if (changedData.isNullOrBlank()) return emptyList()
        return runCatching { objectMapper.readTree(changedData) }
            .getOrNull()
            ?.let { node ->
                when {
                    node.isArray -> {
                        node.mapNotNull { it.toChange() }
                    }

                    node.isObject && node.has("changes") && node.get("changes").isArray -> {
                        node.get("changes").mapNotNull { it.toChange() }
                    }

                    node.isObject -> {
                        node
                            .properties()
                            .asSequence()
                            .mapNotNull { (field, value) ->
                                if (!value.isObject || (!value.has("before") && !value.has("after"))) {
                                    return@mapNotNull null
                                }
                                val before = value.get("before").safeValue(field)
                                val after = value.get("after").safeValue(field)
                                AdminAuditLogChangeResponse(
                                    field = field,
                                    before = before,
                                    after = after,
                                )
                            }.toList()
                    }

                    else -> {
                        emptyList()
                    }
                }
            }
            ?: emptyList()
    }

    private fun JsonNode.toChange(): AdminAuditLogChangeResponse? {
        val field = get("field")?.takeIf { it.isTextual }?.asText() ?: return null
        return AdminAuditLogChangeResponse(
            field = field,
            before = get("before").safeValue(field),
            after = get("after").safeValue(field),
        )
    }

    private fun JsonNode?.safeValue(field: String): String? {
        if (field.contains(SENSITIVE_FIELD_PATTERN)) return null
        if (this == null || isNull) return null
        if (!isValueNode) return null
        return maskDetail(asText())
    }

    private fun maskDetail(value: String?): String? {
        if (value.isNullOrBlank()) return value
        return value
            .replace(EMAIL_PATTERN, "[MASKED_EMAIL]")
            .replace(PHONE_PATTERN, "[MASKED_PHONE]")
            .replace(TOKEN_PATTERN, "${'$'}1[MASKED]")
    }

    private companion object {
        val EMAIL_PATTERN = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
        val PHONE_PATTERN = Regex("(?<!\\d)\\d{2,3}[- ]\\d{3,4}[- ]\\d{4}(?!\\d)")
        val TOKEN_PATTERN = Regex("(?i)(bearer\\s+|token[=: ]+)[A-Za-z0-9._~-]+")
        val SENSITIVE_FIELD_PATTERN =
            Regex(
                "(?i)(authorization|jwt|refresh.?token|password|secret|oauth.?code|provider.?secret|raw.?body|file.?binary)",
            )
    }
}
