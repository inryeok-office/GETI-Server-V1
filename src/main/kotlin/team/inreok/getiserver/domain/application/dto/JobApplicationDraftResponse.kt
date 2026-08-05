package team.inreok.getiserver.domain.application.dto

import io.swagger.v3.oas.annotations.media.Schema
import team.inreok.getiserver.domain.application.entity.type.JobApplicationStatus
import java.time.LocalDateTime

@Schema(description = "지원서 초안·임시저장 결과")
data class JobApplicationDraftResponse(
    @param:Schema(description = "지원서 ID", example = "1")
    val applicationId: Long,
    @param:Schema(description = "공고 ID", example = "1")
    val jobId: Long,
    @param:Schema(description = "적용된 양식 ID. 공고에 연결된 양식이 없으면 null.", nullable = true)
    val formId: Long?,
    @param:Schema(description = "적용된 Form Version", nullable = true)
    val formVersion: Int?,
    @param:Schema(description = "지원 상태", example = "DRAFT")
    val status: JobApplicationStatus,
    @param:Schema(description = "상태 사유", nullable = true)
    val statusReason: String?,
    @param:Schema(description = "연락처 이메일(회원 계정 이메일 스냅샷)", example = "student@example.com")
    val contactEmail: String,
    @param:Schema(description = "연락처 전화번호", nullable = true, example = "010-1234-5678")
    val contactPhone: String?,
    @param:Schema(description = "개인정보 수집·이용 동의 여부", example = "false")
    val privacyConsent: Boolean,
    @param:Schema(description = "지원자 이름 스냅샷", nullable = true)
    val applicantName: String?,
    @param:Schema(description = "지원자 기수 스냅샷", nullable = true)
    val applicantCohort: Int?,
    @param:Schema(description = "지원자 학과 스냅샷", nullable = true)
    val applicantDepartment: String?,
    @param:Schema(description = "지원자 전공 스냅샷")
    val applicantMajors: List<String>,
    @param:Schema(description = "지원자 희망 직무 스냅샷", nullable = true)
    val applicantDesiredJob: String?,
    @param:Schema(description = "지원자 기술 스택 스냅샷")
    val applicantTechStacks: List<String>,
    @param:Schema(description = "답변 목록")
    val answers: List<ApplicationAnswer>,
    @param:Schema(description = "제출 일시. 아직 제출 전(DRAFT)이면 null.", nullable = true)
    val submittedAt: LocalDateTime?,
    @param:Schema(description = "생성 일시")
    val createdAt: LocalDateTime,
    @param:Schema(description = "마지막 수정 일시")
    val updatedAt: LocalDateTime,
)
