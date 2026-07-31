package team.inreok.getiserver.domain.member.service

/**
 * Member의 전공/기술 스택 선택(member_majors, member_tech_stacks)을 이름 목록으로 조회한다.
 * Profile 조회 응답(GET /members/{id}, GET /me/profile 등)이 공통으로 필요로 하는 조회 전용 로직이다.
 */
interface MemberSelectionQueryService {
    fun getMajorNames(memberId: Long): List<String>

    fun getTechStackNames(memberId: Long): List<String>
}
