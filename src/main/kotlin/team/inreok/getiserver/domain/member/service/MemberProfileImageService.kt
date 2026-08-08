package team.inreok.getiserver.domain.member.service

import team.inreok.getiserver.domain.member.entity.Member
import tools.jackson.databind.JsonNode

/**
 * 회원 프로필 이미지와 File 도메인 사이의 연결을 전담한다.
 *
 * `MemberServiceImpl`에서 분리한 이유는 `profileImageFileId` 하나를 다루는 데 요청 Body 파싱,
 * 연결 교체, 표시용 URL 발급이 모두 필요해 성격이 다른 책임이 한 Class에 쌓이기 때문이다.
 * 이 Interface가 그 Field를 처음부터 끝까지 소유한다.
 */
interface MemberProfileImageService {
    /**
     * 요청 Body에 `profileImageFileId`가 있으면 연결을 갱신하고 [member]에 반영한다. Key가 없으면
     * 아무 일도 하지 않는다(부분 수정에서 "전달하지 않음"은 기존 값 유지다).
     *
     * 값이 `null`이면 이미지를 제거하고, 새 File ID면 기존 연결을 해제한 뒤 연결한다. 같은 File
     * ID를 다시 보내면 건드리지 않는다.
     */
    fun applyChange(
        member: Member,
        body: JsonNode,
    )

    /**
     * 표시용 Presigned URL. 이미지가 없거나 [requesterId]에게 볼 권한이 없으면 `null`이다.
     *
     * 권한 판정은 `MemberProfileImageAccessChecker`(`domain.member.access`)가 담당한다 -- 호출
     * 측에서 `profilePublic`을 다시 검사하면 규칙이 두 곳으로 갈라진다.
     */
    fun urlOf(
        fileId: Long?,
        requesterId: Long,
    ): String?
}
