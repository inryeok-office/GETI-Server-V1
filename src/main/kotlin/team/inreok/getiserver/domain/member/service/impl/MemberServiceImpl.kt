package team.inreok.getiserver.domain.member.service.impl

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.member.dto.MemberProfileResponse
import team.inreok.getiserver.domain.member.dto.MemberProfileUpdateResponse
import team.inreok.getiserver.domain.member.dto.MyProfileResponse
import team.inreok.getiserver.domain.member.entity.Member
import team.inreok.getiserver.domain.member.entity.type.DepartmentType
import team.inreok.getiserver.domain.member.entity.type.RoleType
import team.inreok.getiserver.domain.member.exception.MemberNotFoundException
import team.inreok.getiserver.domain.member.exception.MemberProfileNotFoundException
import team.inreok.getiserver.domain.member.exception.MemberProfileValidationException
import team.inreok.getiserver.domain.member.repository.MemberRepository
import team.inreok.getiserver.domain.member.repository.MemberRoleRepository
import team.inreok.getiserver.domain.member.service.MemberSelectionQueryService
import team.inreok.getiserver.domain.member.service.MemberService
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper

@Service
class MemberServiceImpl(
    private val memberRepository: MemberRepository,
    private val memberRoleRepository: MemberRoleRepository,
    private val memberSelectionQueryService: MemberSelectionQueryService,
    private val objectMapper: ObjectMapper,
) : MemberService {
    @Transactional(readOnly = true)
    override fun getProfile(memberId: Long): MemberProfileResponse {
        val member = memberRepository.findById(memberId).orElseThrow { MemberNotFoundException(memberId) }
        // 이 API는 학생 프로필 조회 용도이므로, 조회 대상이 STUDENT Role이 아니면(교사/개발자 등)
        // 해당 프로필이 존재하지 않는 것과 동일하게 처리한다(코드 리뷰 Major 반영).
        val roles = memberRoleRepository.findAllByIdMemberId(memberId).map { it.id.role }
        if (RoleType.STUDENT !in roles) throw MemberNotFoundException(memberId)
        return toProfileResponse(member)
    }

    @Transactional(readOnly = true)
    override fun getMyProfile(memberId: Long): MyProfileResponse {
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
    override fun updateProfile(
        memberId: Long,
        body: JsonNode,
    ): MemberProfileUpdateResponse {
        validateUpdateRequest(body)
        val member = memberRepository.findById(memberId).orElseThrow { MemberProfileNotFoundException(memberId) }
        applyDepartment(member, body)
        if (body.has("phone")) member.phoneNumber = readNullableText(body, "phone", maxLength = 30)
        if (body.has("bio")) member.introduction = readNullableText(body, "bio", maxLength = 1000)
        if (body.has("githubUrl")) member.githubUrl = readNullableText(body, "githubUrl", maxLength = 500)
        applyDesiredJob(member, body)
        applyIsPublic(member, body)
        // @UpdateTimestamp는 Flush 시점에 Entity의 updatedAt 값을 갱신한다. 명시적으로 Flush하지
        // 않으면 응답의 updatedAt이 이번 수정 이전 값일 수 있어(코드 리뷰 Major 반영), 응답을
        // 만들기 전에 강제로 Flush해 실제 갱신된 값을 반환한다.
        memberRepository.flush()
        return toUpdateResponse(member)
    }

    private fun validateUpdateRequest(body: JsonNode) {
        val unknown = body.propertyNames().filterNot { it in ALLOWED_PROFILE_UPDATE_FIELDS }
        if (unknown.isNotEmpty()) {
            throw MemberProfileValidationException("알 수 없는 요청 Field입니다: ${unknown.joinToString()}")
        }
        // profileImageUrl은 요청/응답 모두 명세에 있지만, 현재 Schema는 File 업로드 결과인
        // profile_image_file_id(Long)만 가지고 있어 문자열 URL을 저장할 Column이 없다. File
        // Domain 연동 전까지는 조용히 무시하지 않고 명확히 거부한다(코드 리뷰 Major 반영).
        if (body.has("profileImageUrl")) {
            throw MemberProfileValidationException(
                "profileImageUrl은 아직 지원하지 않습니다. File 업로드 API 연동 이후 다시 시도해주세요.",
            )
        }
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
        val isPublic = node.asBoolean()
        if (!isPublic) requireNotPrivacyRestrictedRole(member)
        member.profilePublic = isPublic
    }

    // 교사(TEACHER)/개발자(DEVELOPER)는 학생이 문의·연락할 수 있어야 하므로 프로필을 비공개로
    // 전환할 수 없다(코드 리뷰 Major 반영).
    private fun requireNotPrivacyRestrictedRole(member: Member) {
        val memberId = requireNotNull(member.id) { "저장된 Member는 id를 가져야 합니다." }
        val roles = memberRoleRepository.findAllByIdMemberId(memberId).map { it.id.role }
        if (roles.any { it == RoleType.TEACHER || it == RoleType.DEVELOPER }) {
            throw MemberProfileValidationException("교사/개발자 회원은 프로필을 비공개로 설정할 수 없습니다.")
        }
    }

    private fun toProfileResponse(member: Member): MemberProfileResponse {
        val memberId = requireNotNull(member.id) { "저장된 Member는 id를 가져야 합니다." }
        val isPublic = member.profilePublic
        // isPublic=false인 비공개 프로필은 profileRestricted=true만 표시하고, 전공/기술 스택/희망
        // 직무/자기소개 같은 상세 Field는 다른 회원에게 노출하지 않는다(코드 리뷰 Blocker 반영).
        return MemberProfileResponse(
            memberId = memberId,
            name = member.name.orEmpty(),
            profileImageUrl = null,
            cohort = member.cohort,
            department = member.department,
            majors = if (isPublic) memberSelectionQueryService.getMajorNames(memberId) else emptyList(),
            techStacks = if (isPublic) memberSelectionQueryService.getTechStackNames(memberId) else emptyList(),
            desiredJob = if (isPublic) readStringList(member.desiredPositions).firstOrNull() else null,
            bio = if (isPublic) member.introduction else null,
            isPublic = isPublic,
            profileRestricted = !isPublic,
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

    companion object {
        private val ALLOWED_PROFILE_UPDATE_FIELDS =
            setOf("department", "phone", "desiredJob", "bio", "githubUrl", "isPublic", "profileImageUrl")
    }
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
