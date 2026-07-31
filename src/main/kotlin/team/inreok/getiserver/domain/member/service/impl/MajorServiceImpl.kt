package team.inreok.getiserver.domain.member.service.impl

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.member.dto.MajorListResponse
import team.inreok.getiserver.domain.member.dto.MajorResponse
import team.inreok.getiserver.domain.member.repository.MajorRepository
import team.inreok.getiserver.domain.member.service.MajorService

@Service
class MajorServiceImpl(
    private val majorRepository: MajorRepository,
) : MajorService {
    @Transactional(readOnly = true)
    override fun search(activeOnly: Boolean?): MajorListResponse {
        val majors =
            if (activeOnly == true) {
                majorRepository.findAllByActiveTrueOrderByNameAsc()
            } else {
                majorRepository.findAllByOrderByNameAsc()
            }
        return MajorListResponse(majors.map(MajorResponse::from))
    }
}
