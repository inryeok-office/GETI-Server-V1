package team.inreok.getiserver.domain.file.service

import java.net.URI

interface FileDownloadService {
    /**
     * 권한을 검증하고 제한 시간 동안만 유효한 다운로드 URL을 만든다.
     *
     * @param inlineRequested 브라우저에서 바로 보여 달라는 **요청**이다. 실제 허용 여부는 파일의
     *   실제 형식을 보고 서버가 정한다(요구사항 §27/§28).
     */
    fun createDownloadUrl(
        requesterId: Long,
        fileId: Long,
        inlineRequested: Boolean,
    ): URI
}
