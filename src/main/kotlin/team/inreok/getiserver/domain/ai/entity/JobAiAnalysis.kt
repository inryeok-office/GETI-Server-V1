package team.inreok.getiserver.domain.ai.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import team.inreok.getiserver.domain.ai.entity.type.AiDifficulty
import team.inreok.getiserver.domain.ai.entity.type.AiFitLevel
import team.inreok.getiserver.domain.ai.entity.type.AiStatus
import java.time.LocalDateTime

/**
 * 공고 AI 분석 결과다. `jobs.id`를 공유 PK로 사용한다(`docs/architecture/erd.md` 참고).
 *
 * [requiredSkills]/[preferredSkills]는 `{techStackId, name}` 목록을 담은 JSON 원문 Text다(V21
 * Migration). OpenAI는 기술 이름만 추출하고, GETI Tech Stack Master와의 정규화 매칭은
 * Application 계층(`AiAnalysisServiceImpl`)이 수행한다 — Entity는 이미 매칭된 결과만 보관한다.
 */
@Entity
@Table(name = "job_ai_analyses")
class JobAiAnalysis(
    @Id
    @Column(name = "job_id")
    val jobId: Long,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var status: AiStatus = AiStatus.PENDING,
    @Column(name = "reanalysis_count", nullable = false)
    var reanalysisCount: Int = 0,
    @Column(name = "requested_at", nullable = false)
    var requestedAt: LocalDateTime,
) {
    @Column(columnDefinition = "text")
    var summary: String? = null

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "required_skills", columnDefinition = "jsonb")
    var requiredSkills: String? = null

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "preferred_skills", columnDefinition = "jsonb")
    var preferredSkills: String? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "high_school_graduate_fit", length = 30)
    var highSchoolGraduateFit: AiFitLevel? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_level_fit", length = 30)
    var entryLevelFit: AiFitLevel? = null

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    var difficulty: AiDifficulty? = null

    /** 항상 `OPENAI`다(Phase 1 단일 Provider). 향후 Multi Provider가 생기면 이 값으로 구분한다. */
    @Column(length = 30)
    var provider: String? = null

    @Column(length = 100)
    var model: String? = null

    @Column(name = "prompt_version", length = 50)
    var promptVersion: String? = null

    @Column(name = "analysis_version")
    var analysisVersion: Int? = null

    @Column(name = "error_message", columnDefinition = "text")
    var errorMessage: String? = null

    @Column(name = "completed_at")
    var completedAt: LocalDateTime? = null
}
