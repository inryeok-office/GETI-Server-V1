package team.inreok.getiserver.domain.inquiry.repository

import org.springframework.data.jpa.repository.JpaRepository
import team.inreok.getiserver.domain.inquiry.entity.Inquiry

interface InquiryRepository : JpaRepository<Inquiry, Long>
