package team.inreok.getiserver.domain.application.dto

import io.swagger.v3.oas.annotations.media.Schema
import team.inreok.getiserver.domain.application.entity.type.JobApplicationStatus
import java.time.LocalDateTime

@Schema(description = "지원서 초안·임시저장·Action(제출·수정요청·재제출·철회) 결과")
data class JobApplicationDraftResponse(
    @param:Schema(description = "지원서 ID", example = "1")
    val applicationId: Long,
    @param:Schema(description = "공고 ID", example = "1")
    val jobId: Long,
    // 아래 네 Field는 교사·개발자용 조회(JobApplicationAdminServiceImpl, Issue #172)에서만 값을
    // 채운다. 학생용 초안·Action 결과(JobApplicationServiceImpl)는 이 DTO를 그대로 재사용하되
    // 기본값 null을 그대로 둔다(toJobApplicationDraftResponse KDoc 참고) -- 기존 학생용 생성
    // 호출부와 Test가 이 Field들을 몰라도 되도록 기본값을 둔다.
    @param:Schema(description = "공고명. 교사·개발자용 조회에서만 채워지고, 공고를 조회할 수 없으면 null.", nullable = true)
    val jobTitle: String? = null,
    @param:Schema(description = "기업명. 교사·개발자용 조회에서만 채워지고, 조회할 수 없으면 null.", nullable = true)
    val companyName: String? = null,
    @param:Schema(description = "담당 교사 회원 ID. 교사·개발자용 조회에서만 채워지고, 담당자가 없으면 null.", nullable = true)
    val managerMemberId: Long? = null,
    @param:Schema(description = "담당 교사 이름. 교사·개발자용 조회에서만 채워지고, 담당자가 없거나 조회할 수 없으면 null.", nullable = true)
    val managerName: String? = null,
    // Phase 2에서는 AVAILABLE 판정(활성 양식 연결 필수, §6.5 8번)을 통과해야만 지원서가 생성되므로
    // 이 Endpoint 응답에서 formId는 항상 값을 가진다. nullable로 둔 것은 Phase 3 이후 양식 연결
    // 해제 등으로 null이 될 수 있는 경로가 생길 가능성을 열어두기 위함이다(PR #79 Review 반영).
    @param:Schema(description = "적용된 양식 ID", nullable = true)
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
    @param:Schema(description = "현재 연결된 첨부파일 목록(Issue #134). 아직 제출한 적이 없으면 빈 목록.")
    val files: List<JobApplicationFileResponse>,
    @param:Schema(description = "제출 일시. 아직 제출 전(DRAFT)이면 null.", nullable = true)
    val submittedAt: LocalDateTime?,
    @param:Schema(description = "철회 일시. 철회 전이면 null.", nullable = true)
    val withdrawnAt: LocalDateTime?,
    @param:Schema(description = "생성 일시")
    val createdAt: LocalDateTime,
    @param:Schema(description = "마지막 수정 일시")
    val updatedAt: LocalDateTime,
)
