package team.inreok.getiserver.domain.audit.exception

import team.inreok.getiserver.global.error.BusinessException

class AuditLogNotFoundException(
    auditLogId: Long,
) : BusinessException(
        AuditErrorCode.AUDIT_LOG_NOT_FOUND,
        "요청한 감사 로그를 찾을 수 없습니다. (auditLogId=$auditLogId)",
    )
