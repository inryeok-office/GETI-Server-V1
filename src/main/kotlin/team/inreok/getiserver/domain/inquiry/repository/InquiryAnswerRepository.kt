package team.inreok.getiserver.domain.inquiry.repository

import org.springframework.data.jpa.repository.JpaRepository
import team.inreok.getiserver.domain.inquiry.entity.InquiryAnswer

interface InquiryAnswerRepository : JpaRepository<InquiryAnswer, Long> {
    /** 문의 상세 응답의 답변 목록. 등록 순(시간순)으로 고정한다(요구사항 20절). */
    fun findAllByInquiryIdOrderByCreatedAtAscIdAsc(inquiryId: Long): List<InquiryAnswer>

    fun existsByInquiryId(inquiryId: Long): Boolean
}
