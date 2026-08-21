package team.inreok.getiserver.domain.company.dto

import io.swagger.v3.oas.annotations.media.Schema
import team.inreok.getiserver.domain.company.entity.type.CompanyType
import team.inreok.getiserver.domain.company.entity.type.MouStatus
import java.time.LocalDate
import java.time.LocalDateTime

@Schema(description = "관리자 기업 상세 조회 응답")
data class AdminCompanyDetailResponse(
    @param:Schema(description = "기업 ID", example = "1")
    val companyId: Long,
    @param:Schema(description = "기업명", example = "인력개발원")
    val name: String,
    @param:Schema(description = "기업 유형", example = "GENERAL")
    val companyType: CompanyType,
    @param:Schema(description = "MOU 협약 상태", example = "ACTIVE")
    val mouStatus: MouStatus,
    @param:Schema(description = "정보 출처", example = "manual", nullable = true)
    val sourceName: String?,
    @param:Schema(description = "홈페이지 URL", example = "https://example.com", nullable = true)
    val homepageUrl: String?,
    @param:Schema(description = "로고 이미지 URL", example = "https://cdn.example.com/company-logo.png", nullable = true)
    val logoUrl: String?,
    @param:Schema(description = "기업 소개", example = "IT 인재를 양성하는 교육 기관입니다.", nullable = true)
    val description: String?,
    @param:Schema(description = "업종", example = "소프트웨어 개발", nullable = true)
    val industry: String?,
    @param:Schema(description = "주소", example = "대구광역시 남구 대명동", nullable = true)
    val address: String?,
    @param:Schema(description = "MOU 협약 시작일", example = "2026-03-01", nullable = true)
    val mouStartDate: LocalDate?,
    @param:Schema(description = "MOU 협약 종료일", example = "2027-02-28", nullable = true)
    val mouEndDate: LocalDate?,
    @param:Schema(description = "기업 대표 또는 채용 담당자 이메일", example = "recruiter@example.com", nullable = true)
    val representativeEmail: String?,
    @param:Schema(description = "기업 대표 또는 채용 담당자 연락처", example = "010-1234-5678", nullable = true)
    val representativePhone: String?,
    @param:Schema(description = "관리자 전용 내부 메모", example = "채용 담당자와 3월 중 후속 미팅 예정", nullable = true)
    val memo: String?,
    @param:Schema(description = "최근 수정자 이름", example = "홍길동", nullable = true)
    val lastEditedBy: String?,
    @param:Schema(description = "최근 수정 시각", example = "2026-03-02T09:00:00", nullable = true)
    val lastEditedAt: LocalDateTime?,
    @param:Schema(description = "연결 공고 통계", implementation = AdminCompanyStatsResponse::class)
    val stats: AdminCompanyStatsResponse,
    @param:Schema(description = "연결된 공고 목록", example = "[]")
    val connectedJobs: List<AdminCompanyConnectedJobResponse>,
    @param:Schema(description = "최근 기업 변경 이력", example = "[]")
    val recentChanges: List<AdminCompanyAuditLogEntryResponse>,
    @param:Schema(description = "기업 등록 시각", example = "2026-03-01T10:15:30", nullable = true)
    val createdAt: LocalDateTime?,
    @param:Schema(description = "기업 최근 수정 시각", example = "2026-03-02T09:00:00", nullable = true)
    val updatedAt: LocalDateTime?,
)

@Schema(description = "관리자 기업 상세 통계")
data class AdminCompanyStatsResponse(
    @param:Schema(description = "삭제되지 않은 연결 공고 전체 수", example = "3")
    val totalConnectedJobs: Long,
    @param:Schema(description = "현재 모집 중인 공고 수", example = "2")
    val activeJobCount: Long,
    @param:Schema(description = "DRAFT를 제외한 전체 지원 건수", example = "24")
    val totalApplicationCount: Long,
)

@Schema(description = "관리자 기업 상세의 연결 공고")
data class AdminCompanyConnectedJobResponse(
    @param:Schema(description = "공고 ID", example = "10")
    val jobId: Long,
    @param:Schema(description = "공고 제목", example = "Backend 개발자 채용")
    val title: String,
    @param:Schema(description = "공고 유형", example = "MOU")
    val postingType: String,
    @param:Schema(description = "공고 상태", example = "PUBLISHED")
    val status: String,
    @param:Schema(description = "DRAFT를 제외한 지원 건수", example = "7")
    val applicantCount: Long,
)

/** Client의 AdminCompanyAuditLogEntry 타입(id/title/actedAtWithActor)에 맞춘 표시용 계약이다. */
@Schema(description = "관리자 기업 상세의 최근 변경 이력")
data class AdminCompanyAuditLogEntryResponse(
    @param:Schema(description = "감사 로그 ID", example = "100")
    val id: Long,
    @param:Schema(description = "변경 작업", example = "COMPANY_UPDATED")
    val title: String,
    @param:Schema(description = "변경 시각과 수행자 표시 문자열", example = "2026-03-02T09:00:00 · 홍길동")
    val actedAtWithActor: String,
)
