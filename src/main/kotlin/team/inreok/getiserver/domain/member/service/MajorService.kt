package team.inreok.getiserver.domain.member.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.member.dto.MajorListResponse
import team.inreok.getiserver.domain.member.dto.MajorResponse
import team.inreok.getiserver.domain.member.repository.MajorRepository

@Service
class MajorService(
    private val majorRepository: MajorRepository,
) {
    @Transactional(readOnly = true)
    fun search(activeOnly: Boolean?): MajorListResponse {
        val majors =
            if (activeOnly == true) {
                majorRepository.findAllByActiveTrueOrderByNameAsc()
            } else {
                majorRepository.findAllByOrderByNameAsc()
            }
        return MajorListResponse(majors.map(MajorResponse::from))
    }
}
