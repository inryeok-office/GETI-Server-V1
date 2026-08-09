package team.inreok.getiserver.domain.member.service.impl

import org.springframework.stereotype.Service
import team.inreok.getiserver.domain.file.entity.type.FileOwnerType
import team.inreok.getiserver.domain.file.entity.type.FilePurpose
import team.inreok.getiserver.domain.file.link.FileLinkPort
import team.inreok.getiserver.domain.file.link.FileUrlPort
import team.inreok.getiserver.domain.member.entity.Member
import team.inreok.getiserver.domain.member.exception.MemberProfileValidationException
import team.inreok.getiserver.domain.member.service.MemberProfileImageService
import tools.jackson.databind.JsonNode

@Service
class MemberProfileImageServiceImpl(
    private val fileLinkPort: FileLinkPort,
    private val fileUrlPort: FileUrlPort,
) : MemberProfileImageService {
    /**
     * 같은 File ID의 Binary를 덮어쓰는 수정은 없다(File 도메인 지시서 §21). 클라이언트는 새 파일을
     * 업로드해 새 `fileId`를 받아 보내고, 서버는 기존 연결을 해제한 뒤 새 파일을 연결한다. 해제된
     * 파일의 Storage Binary는 즉시 지우지 않고 Cleanup(Phase 5)이 보존 기간을 보고 판단한다.
     *
     * 소유권·목적·상태 검증은 [FileLinkPort.validateAndLink]가 수행한다. 남의 fileId를 보내면
     * `FILE_NOT_OWNED`, 다른 용도로 올린 파일이면 `FILE_PURPOSE_MISMATCH`로 거부된다.
     */
    override fun applyChange(
        member: Member,
        body: JsonNode,
    ) {
        if (!body.has(PROFILE_IMAGE_FILE_ID)) return
        val memberId = requireNotNull(member.id) { "저장된 Member는 id를 가져야 합니다." }
        val newFileId = readFileId(body)
        if (newFileId == member.profileImageFileId) return

        // 해제가 먼저다. unlinkAllOf는 이 회원에게 연결된 파일을 모두 풀기 때문에, 순서가 뒤바뀌면
        // 방금 붙인 새 이미지까지 함께 풀려 프로필 이미지가 사라진다.
        if (member.profileImageFileId != null) fileLinkPort.unlinkAllOf(FileOwnerType.MEMBER, memberId)
        if (newFileId != null) {
            fileLinkPort.validateAndLink(
                requesterId = memberId,
                fileIds = listOf(newFileId),
                purpose = FilePurpose.PROFILE_IMAGE,
                ownerId = memberId,
            )
        }
        member.profileImageFileId = newFileId
    }

    override fun urlOf(
        fileId: Long?,
        requesterId: Long,
    ): String? {
        if (fileId == null) return null
        return fileUrlPort.presignedImageUrls(requesterId, listOf(fileId))[fileId]
    }

    private fun readFileId(body: JsonNode): Long? {
        val node = body.get(PROFILE_IMAGE_FILE_ID)
        if (node.isNull) return null
        if (!node.isIntegralNumber) {
            throw MemberProfileValidationException("profileImageFileId는 정수여야 합니다.")
        }
        return node.asLong()
    }

    private companion object {
        private const val PROFILE_IMAGE_FILE_ID = "profileImageFileId"
    }
}
