package team.inreok.getiserver.domain.application.repository

import org.springframework.data.jpa.repository.JpaRepository
import team.inreok.getiserver.domain.application.entity.JobApplicationForm

interface JobApplicationFormRepository : JpaRepository<JobApplicationForm, Long>
