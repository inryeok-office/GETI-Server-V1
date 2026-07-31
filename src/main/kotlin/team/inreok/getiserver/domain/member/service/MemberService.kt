package team.inreok.getiserver.domain.member.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.member.dto.MemberProfileResponse
import team.inreok.getiserver.domain.member.dto.MemberProfileUpdateResponse
import team.inreok.getiserver.domain.member.dto.MyProfileResponse
import team.inreok.getiserver.domain.member.entity.Member
import team.inreok.getiserver.domain.member.entity.type.DepartmentType
import team.inreok.getiserver.domain.member.exception.MemberNotFoundException
import team.inreok.getiserver.domain.member.exception.MemberProfileNotFoundException
import team.inreok.getiserver.domain.member.exception.MemberProfileValidationException
import team.inreok.getiserver.domain.member.repository.MemberRepository
import team.inreok.getiserver.domain.member.repository.MemberRoleRepository
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper

@Service
class MemberService(
    private val memberRepository: MemberRepository,
    private val memberRoleRepository: MemberRoleRepository,
    private val memberSelectionQueryService: MemberSelectionQueryService,
    private val objectMapper: ObjectMapper,
) {
    @Transactional(readOnly = true)
    fun getProfile(memberId: Long): MemberProfileResponse {
        val member = memberRepository.findById(memberId).orElseThrow { MemberNotFoundException(memberId) }
        return toProfileResponse(member)
    }

    @Transactional(readOnly = true)
    fun getMyProfile(memberId: Long): MyProfileResponse {
        val member = memberRepository.findById(memberId).orElseThrow { MemberProfileNotFoundException(memberId) }
        val roles = memberRoleRepository.findAllByIdMemberId(memberId).map { it.id.role }
        return MyProfileResponse(
            memberId = requireNotNull(member.id) { "저장된 Member는 id를 가져야 합니다." },
            name = member.name.orEmpty(),
            email = member.email,
            roles = roles,
            status = member.status,
            academicStatus = member.academicStatus,
            cohort = member.cohort,
            department = member.department,
            phone = member.phoneNumber,
            profileImageUrl = null,
            desiredJob = readStringList(member.desiredPositions).firstOrNull(),
            bio = member.introduction,
            githubUrl = member.githubUrl,
            isPublic = member.profilePublic,
            majors = memberSelectionQueryService.getMajorNames(memberId),
            techStacks = memberSelectionQueryService.getTechStackNames(memberId),
        )
    }

    // 요청 Body의 각 Field가 "전달되지 않음"과 "명시적으로 null"을 구분해야 하는 부분 수정(PATCH)이라
    // 고정된 Request DTO 대신 JsonNode로 받아 Key 존재 여부(has)를 직접 확인한다.
    @Transactional
    fun updateProfile(
        memberId: Long,
        body: JsonNode,
    ): MemberProfileUpdateResponse {
        val member = memberRepository.findById(memberId).orElseThrow { MemberNotFoundException(memberId) }
        applyDepartment(member, body)
        if (body.has("phone")) member.phoneNumber = readNullableText(body, "phone", maxLength = 30)
        if (body.has("bio")) member.introduction = readNullableText(body, "bio", maxLength = 1000)
        if (body.has("githubUrl")) member.githubUrl = readNullableText(body, "githubUrl", maxLength = 500)
        applyDesiredJob(member, body)
        applyIsPublic(member, body)
        // profileImageUrl은 요청/응답 모두 명세에 있지만, 현재 Schema는 File 업로드 결과인
        // profile_image_file_id(Long)만 가지고 있어 문자열 URL을 저장할 Column이 없다. File
        // Domain 연동 전까지는 값을 받아도 저장하지 않고 무시한다.
        return toUpdateResponse(member)
    }

    private fun applyDepartment(
        member: Member,
        body: JsonNode,
    ) {
        if (!body.has("department")) return
        val node = body.get("department")
        if (node.isNull) {
            member.department = null
            return
        }
        member.department =
            runCatching { DepartmentType.valueOf(node.asString()) }
                .getOrElse { throw MemberProfileValidationException("department 값이 올바르지 않습니다.") }
    }

    private fun applyDesiredJob(
        member: Member,
        body: JsonNode,
    ) {
        if (!body.has("desiredJob")) return
        val desiredJob = readNullableText(body, "desiredJob", maxLength = null)
        member.desiredPositions = desiredJob?.let { objectMapper.writeValueAsString(listOf(it)) }
    }

    private fun applyIsPublic(
        member: Member,
        body: JsonNode,
    ) {
        if (!body.has("isPublic")) return
        val node = body.get("isPublic")
        if (node.isNull) {
            throw MemberProfileValidationException("isPublic은 null로 설정할 수 없습니다.")
        }
        if (!node.isBoolean) {
            throw MemberProfileValidationException("isPublic은 Boolean 값이어야 합니다.")
        }
        member.profilePublic = node.asBoolean()
    }

    private fun readNullableText(
        body: JsonNode,
        field: String,
        maxLength: Int?,
    ): String? {
        val node = body.get(field)
        if (node.isNull) return null
        if (!node.isString) {
            throw MemberProfileValidationException("$field 은 문자열이어야 합니다.")
        }
        val value = node.asString()
        if (maxLength != null && value.length > maxLength) {
            throw MemberProfileValidationException("$field 은 최대 ${maxLength}자까지 입력할 수 있습니다.")
        }
        return value
    }

    private fun toProfileResponse(member: Member): MemberProfileResponse {
        val memberId = requireNotNull(member.id) { "저장된 Member는 id를 가져야 합니다." }
        return MemberProfileResponse(
            memberId = memberId,
            name = member.name.orEmpty(),
            profileImageUrl = null,
            cohort = member.cohort,
            department = member.department,
            majors = memberSelectionQueryService.getMajorNames(memberId),
            techStacks = memberSelectionQueryService.getTechStackNames(memberId),
            desiredJob = readStringList(member.desiredPositions).firstOrNull(),
            bio = member.introduction,
            isPublic = member.profilePublic,
            profileRestricted = !member.profilePublic,
        )
    }

    private fun toUpdateResponse(member: Member): MemberProfileUpdateResponse =
        MemberProfileUpdateResponse(
            memberId = requireNotNull(member.id) { "저장된 Member는 id를 가져야 합니다." },
            name = member.name.orEmpty(),
            department = member.department,
            phone = member.phoneNumber,
            desiredJob = readStringList(member.desiredPositions).firstOrNull(),
            bio = member.introduction,
            githubUrl = member.githubUrl,
            isPublic = member.profilePublic,
            profileImageUrl = null,
            updatedAt = requireNotNull(member.updatedAt) { "저장된 Member는 updatedAt을 가져야 합니다." },
        )

    private fun readStringList(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        return objectMapper.readValue(json, Array<String>::class.java).toList()
    }
}
