package team.inreok.getiserver.domain.system.dto

import io.swagger.v3.oas.annotations.media.Schema
import team.inreok.getiserver.domain.operation.entity.type.OperationJobType
import java.time.LocalDateTime

@Schema(description = "관리자 정기 작업 상태")
data class OperationJobResponse(
    @param:Schema(description = "화면용 작업 식별자", example = "JOB_COLLECTION")
    val taskId: OperationJobType,
    @param:Schema(description = "작업 업무 식별자", example = "JOB_COLLECTION")
    val jobType: OperationJobType,
    @param:Schema(description = "화면에 표시할 작업 이름", example = "채용 공고 수집")
    val name: String,
    @param:Schema(description = "작업 설명", example = "외부 채용 공고를 수집합니다.")
    val description: String,
    @param:Schema(description = "실제 Scheduler 설정", example = "cron: 0 0 3 * * *")
    val schedule: String,
    @param:Schema(description = "가장 최근 실행 시각. 영속 이력이 없으면 null", nullable = true)
    val lastRunAt: LocalDateTime?,
    @param:Schema(description = "계산 가능한 cron 작업의 다음 실행 시각. 계산할 수 없으면 null", nullable = true)
    val nextRunAt: LocalDateTime?,
    @param:Schema(description = "기존 영속 실행 이력 ID. 이력이 없으면 null", nullable = true)
    val operationId: String?,
    @param:Schema(description = "작업 실행 상태", example = "SUCCESS")
    val status: String,
    @param:Schema(description = "처리 대상 건수")
    val processedCount: Long,
    @param:Schema(description = "성공 건수")
    val successCount: Long,
    @param:Schema(description = "실패 건수")
    val failureCount: Long,
    @param:Schema(description = "부분 성공 또는 품질 경고 건수")
    val partialSuccessCount: Long,
    @param:Schema(description = "실행 시작 시각. 이력이 없으면 null", nullable = true)
    val startedAt: LocalDateTime?,
    @param:Schema(description = "실행 종료 시각. 진행 중이거나 이력이 없으면 null", nullable = true)
    val finishedAt: LocalDateTime?,
    @param:Schema(description = "마지막 오류. 없으면 null", nullable = true)
    val lastError: String?,
    @param:Schema(description = "수동 Action 지원 여부", example = "SUPPORTED")
    val actionStatus: OperationJobActionStatus,
)

@Schema(description = "관리자 정기 작업 목록")
data class OperationJobListResponse(
    @param:Schema(description = "정기 작업 목록")
    val content: List<OperationJobResponse>,
    @param:Schema(description = "현재 Page 번호(0부터 시작)")
    val page: Int,
    @param:Schema(description = "Page당 개수")
    val size: Int,
    @param:Schema(description = "전체 결과 개수")
    val totalElements: Long,
    @param:Schema(description = "전체 Page 수")
    val totalPages: Int,
    @param:Schema(description = "첫 Page 여부")
    val first: Boolean,
    @param:Schema(description = "마지막 Page 여부")
    val last: Boolean,
)

@Schema(description = "정기 작업 수동 Action 상태")
enum class OperationJobActionStatus {
    SUPPORTED,
    UNSUPPORTED,
}
