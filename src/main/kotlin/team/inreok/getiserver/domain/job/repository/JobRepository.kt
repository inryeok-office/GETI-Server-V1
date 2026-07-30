package team.inreok.getiserver.domain.job.repository

import org.springframework.data.jpa.repository.JpaRepository
import team.inreok.getiserver.domain.job.entity.Job

interface JobRepository : JpaRepository<Job, Long> {
    fun findBySourceNameAndExternalJobId(
        sourceName: String,
        externalJobId: String,
    ): Job?
}
