package team.inreok.getiserver.domain.company.dto

import io.swagger.v3.oas.annotations.media.Schema
import team.inreok.getiserver.domain.company.entity.type.CompanyType
import team.inreok.getiserver.domain.company.entity.type.MouStatus
import java.time.LocalDate
import java.time.LocalDateTime

@Schema(description = "관리자 기업 상세 조회 응답")
data class AdminCompanyDetailResponse(
    val companyId: Long,
    val name: String,
    val companyType: CompanyType,
    val mouStatus: MouStatus,
    val sourceName: String?,
    val homepageUrl: String?,
    val logoUrl: String?,
    val description: String?,
    val industry: String?,
    val address: String?,
    val mouStartDate: LocalDate?,
    val mouEndDate: LocalDate?,
    val representativeEmail: String?,
    val representativePhone: String?,
    val memo: String?,
    val lastEditedBy: String?,
    val lastEditedAt: LocalDateTime?,
    val stats: AdminCompanyStatsResponse,
    val connectedJobs: List<AdminCompanyConnectedJobResponse>,
    val recentChanges: List<AdminCompanyAuditLogEntryResponse>,
    val createdAt: LocalDateTime?,
    val updatedAt: LocalDateTime?,
)

data class AdminCompanyStatsResponse(
    val totalConnectedJobs: Long,
    val activeJobCount: Long,
    val totalApplicationCount: Long,
)

data class AdminCompanyConnectedJobResponse(
    val jobId: Long,
    val title: String,
    val postingType: String,
    val status: String,
    val applicantCount: Long,
)

/** Client의 AdminCompanyAuditLogEntry 타입(id/title/actedAtWithActor)에 맞춘 표시용 계약이다. */
data class AdminCompanyAuditLogEntryResponse(
    val id: Long,
    val title: String,
    val actedAtWithActor: String,
)
