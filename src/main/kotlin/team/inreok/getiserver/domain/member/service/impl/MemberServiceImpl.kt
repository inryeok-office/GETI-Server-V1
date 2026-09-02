package team.inreok.getiserver.domain.member.service.impl

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.member.access.PrivilegedProfileViewer
import team.inreok.getiserver.domain.member.dto.MemberProfileLinkResponse
import team.inreok.getiserver.domain.member.dto.MemberProfileResponse
import team.inreok.getiserver.domain.member.dto.MemberProfileUpdateResponse
import team.inreok.getiserver.domain.member.dto.MyProfileResponse
import team.inreok.getiserver.domain.member.entity.Member
import team.inreok.getiserver.domain.member.entity.MemberProfileLink
import team.inreok.getiserver.domain.member.entity.type.DepartmentType
import team.inreok.getiserver.domain.member.entity.type.RoleType
import team.inreok.getiserver.domain.member.exception.MemberNotFoundException
import team.inreok.getiserver.domain.member.exception.MemberProfileNotFoundException
import team.inreok.getiserver.domain.member.exception.MemberProfileValidationException
import team.inreok.getiserver.domain.member.repository.MemberProfileLinkRepository
import team.inreok.getiserver.domain.member.repository.MemberRepository
import team.inreok.getiserver.domain.member.repository.MemberRoleRepository
import team.inreok.getiserver.domain.member.service.MemberProfileImageService
import team.inreok.getiserver.domain.member.service.MemberSelectionQueryService
import team.inreok.getiserver.domain.member.service.MemberService
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.net.URI

@Service
class MemberServiceImpl(
    private val memberRepository: MemberRepository,
    private val memberRoleRepository: MemberRoleRepository,
    private val memberSelectionQueryService: MemberSelectionQueryService,
    private val memberProfileImageService: MemberProfileImageService,
    private val privilegedProfileViewer: PrivilegedProfileViewer,
    private val memberProfileLinkRepository: MemberProfileLinkRepository,
    private val objectMapper: ObjectMapper,
) : MemberService {
    @Transactional(readOnly = true)
    override fun getProfile(
        memberId: Long,
        requesterId: Long,
    ): MemberProfileResponse {
        val member = memberRepository.findById(memberId).orElseThrow { MemberNotFoundException(memberId) }
        // 이 API는 학생 프로필 조회 용도이므로, 조회 대상이 STUDENT Role이 아니면(교사/개발자 등)
        // 해당 프로필이 존재하지 않는 것과 동일하게 처리한다(코드 리뷰 Major 반영).
        val roles = memberRoleRepository.findAllByIdMemberId(memberId).map { it.id.role }
        if (RoleType.STUDENT !in roles) throw MemberNotFoundException(memberId)
        return toProfileResponse(member, requesterId)
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
            profileImageUrl = memberProfileImageService.urlOf(member.profileImageFileId, requesterId = memberId),
            desiredJob = readStringList(objectMapper, member.desiredPositions).firstOrNull(),
            bio = member.introduction,
            githubUrl = member.githubUrl,
            isPublic = member.profilePublic,
            majors = memberSelectionQueryService.getMajorNames(memberId),
            links = getLinksResponse(memberId),
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
        if (body.has("links")) applyLinks(memberId, body)
        applyDesiredJob(member, body)
        applyIsPublic(member, body)
        memberProfileImageService.applyChange(member, body)
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
        // profileImageUrl은 Notion API 명세에 요청 Field로 남아 있지만 서버가 받는 것은 문자열
        // URL이 아니라 File 업로드 결과인 profileImageFileId다(Issue #86 CONTRACT_MISMATCH,
        // Notion 갱신 필요). 조용히 무시하면 이미지가 저장된 줄 알게 되므로 명확히 거부하고
        // 대체 Field를 알려준다.
        if (body.has("profileImageUrl")) {
            throw MemberProfileValidationException(
                "profileImageUrl은 지원하지 않습니다. 파일 업로드 API로 받은 profileImageFileId를 보내주세요.",
            )
        }
    }

    private fun applyDepartment(
        member: Member,
        body: JsonNode,
    ) {
        if (body.has("cohort")) {
            val cohort = body.get("cohort")
            if (cohort.isNull) {
                member.cohort = null
            } else {
                val value = cohort.asLong()
                if (!cohort.isIntegralNumber || value !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) {
                    val message =
                        if (!cohort.isIntegralNumber) {
                            "cohort는 정수여야 합니다."
                        } else {
                            "cohort의 값 범위가 올바르지 않습니다."
                        }
                    throw MemberProfileValidationException(message)
                }
                member.cohort = value.toInt()
            }
        }
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

    // 교사(TEACHER)/개발자(DEVELOPER)는 학생이 문의·연락할 수 있어야 하므로 프로필을 비공개로
    // 전환할 수 없다(코드 리뷰 Major 반영).
    private fun applyIsPublic(
        member: Member,
        body: JsonNode,
    ) {
        if (!body.has("isPublic")) return
        val isPublic = requirePublicFlag(body.get("isPublic"))
        val memberId = requireNotNull(member.id) { "저장된 Member는 id를 가져야 합니다." }
        val roles = memberRoleRepository.findAllByIdMemberId(memberId).map { it.id.role }
        if (!isPublic && roles.any { it == RoleType.TEACHER || it == RoleType.DEVELOPER }) {
            throw MemberProfileValidationException("교사/개발자 회원은 프로필을 비공개로 설정할 수 없습니다.")
        }
        member.profilePublic = isPublic
    }

    private fun toProfileResponse(
        member: Member,
        requesterId: Long,
    ): MemberProfileResponse {
        val memberId = requireNotNull(member.id) { "저장된 Member는 id를 가져야 합니다." }
        val isPublic = member.profilePublic
        // isPublic=false인 비공개 프로필은 전공/기술 스택/희망 직무/자기소개 같은 상세 Field를 다른
        // 회원에게 노출하지 않는다(코드 리뷰 Blocker 반영). 단 교사·개발자는 학생 관리·상담 목적으로
        // 비공개 프로필도 전체를 본다(Issue #114, #89 결정).
        //
        // isPublic은 대상 회원이 설정한 값을 그대로 두고, 이번 응답에서 실제로 가렸는지는
        // profileRestricted가 나타낸다 -- 교사가 비공개 학생을 조회하면 isPublic=false이면서
        // profileRestricted=false다. 두 Field를 묶어 두면 값은 채워 보내면서 "가렸다"고 말하는
        // 모순이 생기고, 프론트가 그 신호를 보고 받은 데이터를 다시 숨기게 된다.
        val disclosed = isPublic || privilegedProfileViewer.canViewPrivateProfile()
        return MemberProfileResponse(
            memberId = memberId,
            name = member.name.orEmpty(),
            profileImageUrl = memberProfileImageService.urlOf(member.profileImageFileId, requesterId),
            cohort = member.cohort,
            department = member.department,
            majors = if (disclosed) memberSelectionQueryService.getMajorNames(memberId) else emptyList(),
            techStacks = if (disclosed) memberSelectionQueryService.getTechStackNames(memberId) else emptyList(),
            desiredJob = if (disclosed) readStringList(objectMapper, member.desiredPositions).firstOrNull() else null,
            bio = if (disclosed) member.introduction else null,
            links = if (disclosed) getLinksResponse(memberId) else emptyList(),
            isPublic = isPublic,
            profileRestricted = !disclosed,
        )
    }

    private fun toUpdateResponse(member: Member): MemberProfileUpdateResponse {
        val memberId = requireNotNull(member.id) { "저장된 Member는 id를 가져야 합니다." }
        return MemberProfileUpdateResponse(
            memberId = memberId,
            name = member.name.orEmpty(),
            cohort = member.cohort,
            department = member.department,
            phone = member.phoneNumber,
            desiredJob = readStringList(objectMapper, member.desiredPositions).firstOrNull(),
            bio = member.introduction,
            githubUrl = member.githubUrl,
            links = getLinksResponse(memberId),
            isPublic = member.profilePublic,
            // 수정 응답은 항상 본인 요청이므로 비공개 여부와 무관하게 URL이 나간다.
            profileImageUrl = memberProfileImageService.urlOf(member.profileImageFileId, memberId),
            updatedAt = requireNotNull(member.updatedAt) { "저장된 Member는 updatedAt을 가져야 합니다." },
        )
    }

    private fun getLinksResponse(memberId: Long): List<MemberProfileLinkResponse> =
        memberProfileLinkRepository
            .findAllByMemberIdOrderByDisplayOrderAsc(memberId)
            .map { MemberProfileLinkResponse(label = it.label, url = it.url) }

    // links는 "전달되지 않음"이면 기존 유지, "[]"면 전체 삭제, "[...]"면 전체 교체다(Issue #220
    // 결정). 배열 안 순서를 그대로 표시 순서(displayOrder)로 저장해 별도 order Field를 요청받지
    // 않는다 -- Client가 실제 label + url만 다루므로 order까지 입력받으면 과설계다.
    private fun applyLinks(
        memberId: Long,
        body: JsonNode,
    ) {
        val node = requireLinksArrayNode(body.get("links"))
        requireLinksWithinLimit(node)
        val links =
            (0 until node.size()).map { index ->
                toMemberProfileLink(memberId, node.get(index), displayOrder = index)
            }
        memberProfileLinkRepository.deleteAllByMemberId(memberId)
        memberProfileLinkRepository.saveAll(links)
    }

    companion object {
        private val ALLOWED_PROFILE_UPDATE_FIELDS =
            setOf(
                "cohort",
                "department",
                "phone",
                "desiredJob",
                "bio",
                "githubUrl",
                "links",
                "isPublic",
                "profileImageFileId",
                // 지원하지 않지만 "알 수 없는 Field"가 아니라 전용 안내로 거부하기 위해 남겨 둔다.
                "profileImageUrl",
            )
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

private fun readStringList(
    objectMapper: ObjectMapper,
    json: String?,
): List<String> {
    if (json.isNullOrBlank()) return emptyList()
    return objectMapper.readValue(json, Array<String>::class.java).toList()
}

private fun requirePublicFlag(node: JsonNode): Boolean {
    if (node.isNull) {
        throw MemberProfileValidationException("isPublic은 null로 설정할 수 없습니다.")
    }
    if (!node.isBoolean) {
        throw MemberProfileValidationException("isPublic은 Boolean 값이어야 합니다.")
    }
    return node.asBoolean()
}

// 명확한 Product 요구값이 없어 안전한 기본 상한으로 정했다(Issue #220 PR 근거). 개수는 my-profile
// 화면이 실제로 필요로 하는 GitHub 외 블로그/포트폴리오 등 소수 링크를 넉넉히 덮으면서 단일 PATCH
// 요청으로 대량 Row를 생성하는 남용을 막는 값이고, 길이는 URL/Label의 일반적인 실사용 범위를 넘는
// 값을 막기 위한 상한이다(Table Column 길이와 동일).
private const val MAX_LINK_COUNT = 20
private const val LINK_LABEL_MAX_LENGTH = 100
private const val LINK_URL_MAX_LENGTH = 2000
private val ALLOWED_LINK_URL_SCHEMES = setOf("http", "https")

private fun requireLinksArrayNode(node: JsonNode): JsonNode {
    if (node.isNull) {
        throw MemberProfileValidationException("links는 null일 수 없습니다. 전체 삭제하려면 빈 배열을 보내주세요.")
    }
    if (!node.isArray) {
        throw MemberProfileValidationException("links는 배열이어야 합니다.")
    }
    return node
}

private fun requireLinksWithinLimit(node: JsonNode) {
    if (node.size() > MAX_LINK_COUNT) {
        throw MemberProfileValidationException("links는 최대 ${MAX_LINK_COUNT}개까지 등록할 수 있습니다.")
    }
}

private fun toMemberProfileLink(
    memberId: Long,
    element: JsonNode,
    displayOrder: Int,
): MemberProfileLink {
    if (!element.isObject) {
        throw MemberProfileValidationException("links의 각 원소는 label과 url을 가진 객체여야 합니다.")
    }
    val label = readRequiredLinkText(element, "label", LINK_LABEL_MAX_LENGTH)
    val url = readRequiredLinkText(element, "url", LINK_URL_MAX_LENGTH)
    validateLinkUrlScheme(url)
    return MemberProfileLink(memberId = memberId, label = label, url = url, displayOrder = displayOrder)
}

private fun readRequiredLinkText(
    element: JsonNode,
    field: String,
    maxLength: Int,
): String {
    val node = element.get(field)
    if (node == null || node.isNull || !node.isString) {
        throw MemberProfileValidationException("links의 $field 은 필수 문자열입니다.")
    }
    val value = node.asString()
    if (value.isBlank() || value.length > maxLength) {
        throw MemberProfileValidationException("links의 $field 은 1자 이상 ${maxLength}자 이하의 문자열이어야 합니다.")
    }
    return value
}

// javascript:/data:/file: 같은 위험 Scheme을 차단하고 http/https만 허용한다(Issue #220 보안 요구사항).
// 상대 경로 등 Scheme이 없는 값도 함께 거부한다.
private fun validateLinkUrlScheme(url: String) {
    val scheme =
        runCatching { URI(url).scheme }
            .getOrElse { throw MemberProfileValidationException("links의 url 형식이 올바르지 않습니다.") }
    if (scheme?.lowercase() !in ALLOWED_LINK_URL_SCHEMES) {
        throw MemberProfileValidationException("links의 url은 http 또는 https만 허용합니다.")
    }
}
