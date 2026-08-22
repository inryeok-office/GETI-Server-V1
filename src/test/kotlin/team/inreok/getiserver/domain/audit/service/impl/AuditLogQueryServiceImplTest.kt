package team.inreok.getiserver.domain.audit.service.impl

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import team.inreok.getiserver.domain.audit.entity.AuditLog
import team.inreok.getiserver.domain.audit.entity.type.AuditResult
import team.inreok.getiserver.domain.audit.repository.AuditLogRepository
import team.inreok.getiserver.domain.member.query.InquiryMemberSnapshot
import team.inreok.getiserver.domain.member.query.InquiryMemberSnapshotQueryPort
import tools.jackson.databind.json.JsonMapper
import java.time.LocalDateTime
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class AuditLogQueryServiceImplTest {
    @Mock
    private lateinit var auditLogRepository: AuditLogRepository

    @Mock
    private lateinit var memberSnapshotQueryPort: InquiryMemberSnapshotQueryPort

    private val service: AuditLogQueryServiceImpl by lazy {
        AuditLogQueryServiceImpl(auditLogRepository, memberSnapshotQueryPort, JsonMapper())
    }

    @Test
    fun `목록은 DB 페이지와 배치 actor snapshot을 사용한다`() {
        val auditLog = auditLog()
        given(auditLogRepository.searchForAdmin(null, null, null, null, null, null, null, PageRequest.of(0, 20)))
            .willReturn(PageImpl(listOf(auditLog), PageRequest.of(0, 20), 1))
        given(memberSnapshotQueryPort.findAllByIds(setOf(7L))).willReturn(mapOf(7L to snapshot()))

        val response = service.list(null, null, null, null, null, null, null, PageRequest.of(0, 20))

        val item = response.content.single()
        assertThat(item.auditLogId).isEqualTo(10L)
        assertThat(item.actorName).isEqualTo("홍길동")
        assertThat(item.result).isEqualTo(AuditResult.FAILURE)
        assertThat(item.maskedDetail).isEqualTo("실패한 요청 [MASKED_EMAIL]")
        verify(memberSnapshotQueryPort).findAllByIds(setOf(7L))
    }

    @Test
    fun `상세는 변경 전후 값만 노출하고 민감 필드는 비운다`() {
        val auditLog =
            auditLog().apply {
                changedData =
                    """[{"field":"name","before":"이전","after":"이후"},{"field":"password","before":"a","after":"b"}]"""
            }
        given(auditLogRepository.findById(10L)).willReturn(Optional.of(auditLog))
        given(memberSnapshotQueryPort.findAllByIds(setOf(7L))).willReturn(mapOf(7L to snapshot()))

        val response = service.get(10L)

        assertThat(response.changes).hasSize(2)
        assertThat(response.changes[0].field).isEqualTo("name")
        assertThat(response.changes[0].before).isEqualTo("이전")
        assertThat(response.changes[0].after).isEqualTo("이후")
        assertThat(response.changes[1].before).isNull()
        assertThat(response.changes[1].after).isNull()
    }

    private fun auditLog() =
        AuditLog(action = "COMPANY_UPDATED", targetType = "COMPANY").apply {
            id = 10L
            actorMemberId = 7L
            targetId = 12L
            result = AuditResult.FAILURE
            resultMessage = "실패한 요청 test@example.com"
            requestPath = "/api/v1/admin/companies/12"
            createdAt = LocalDateTime.of(2026, 8, 22, 10, 15)
        }

    private fun snapshot() =
        InquiryMemberSnapshot(
            memberId = 7L,
            name = "홍길동",
            profileImageFileId = null,
            cohort = null,
            department = null,
            isPublic = true,
        )
}
