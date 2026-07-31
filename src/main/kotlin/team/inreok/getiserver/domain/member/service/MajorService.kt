package team.inreok.getiserver.domain.member.service

import team.inreok.getiserver.domain.member.dto.MajorListResponse

interface MajorService {
    fun search(activeOnly: Boolean?): MajorListResponse
}
