package team.inreok.getiserver.domain.collector.repository

import org.springframework.data.jpa.repository.JpaRepository
import team.inreok.getiserver.domain.collector.entity.JobSource
import team.inreok.getiserver.domain.collector.entity.type.JobSourceCode

interface JobSourceRepository : JpaRepository<JobSource, Long> {
    fun findBySourceCode(sourceCode: JobSourceCode): JobSource?

    fun findAllByOrderBySourceCodeAsc(): List<JobSource>
}
