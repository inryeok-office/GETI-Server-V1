package team.inreok.getiserver.domain.file.link

import org.springframework.modulith.NamedInterface

/**
 * 이미지 파일을 브라우저가 바로 표시할 수 있는 URL로 바꾼다.
 *
 * 왜 `/api/v1/files/{id}/download` 경로를 그대로 내려주지 않는가: 저장소의 인증은 Authorization
 * Header의 Bearer Token만 읽고 Cookie 인증이 없는데, 브라우저는 `<img src>` 요청에 Token을
 * 붙이지 않는다. 그래서 프로필 이미지와 기업 로고는 서버가 **권한을 검증한 뒤** 이미 서명된
 * 짧은 유효기간의 URL을 응답 Body에 담아 준다.
 *
 * 문서 다운로드와 방식이 다른 것이 아니다 -- 둘 다 Presigned URL을 쓰고 전달 위치만 다르다
 * (다운로드 API는 `Location` Header, 이미지는 응답 Body).
 *
 * Bucket은 Private이고 Object Key는 UUID이며 URL은 설정된 시간 뒤 만료되므로, 요구사항 §42가
 * 금지하는 "영구 Public URL"이나 "Presigned URL 영구 저장"에 해당하지 않는다.
 */
@NamedInterface
interface FileUrlPort {
    /**
     * 이미지 파일 ID를 표시용 URL로 바꾼다.
     *
     * 존재하지 않거나, 사용자에게 보이지 않는 상태이거나, 이미지가 아닌 파일의 ID는 결과 Map에서
     * 빠진다(예외를 던지지 않는다) -- 호출 측은 "URL이 없으면 이미지가 없는 것"으로 다루면 된다.
     *
     * 회원 목록처럼 여러 명의 이미지를 한 번에 그리는 화면이 있으므로 배치로 둔다(N+1 방지).
     */
    fun presignedImageUrls(fileIds: Collection<Long>): Map<Long, String>
}
