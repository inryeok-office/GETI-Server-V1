package team.inreok.getiserver.domain.ai.repository

import org.springframework.data.jpa.repository.JpaRepository
import team.inreok.getiserver.domain.ai.entity.JobAiAnalysis

interface JobAiAnalysisRepository : JpaRepository<JobAiAnalysis, Long>
