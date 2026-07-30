package team.inreok.getiserver.domain.collector.repository

import org.springframework.data.jpa.repository.JpaRepository
import team.inreok.getiserver.domain.collector.entity.JobCollectionRun

interface JobCollectionRunRepository : JpaRepository<JobCollectionRun, Long>
