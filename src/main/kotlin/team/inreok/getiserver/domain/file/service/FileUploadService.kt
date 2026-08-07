package team.inreok.getiserver.domain.file.service

import org.springframework.web.multipart.MultipartFile
import team.inreok.getiserver.domain.file.dto.FileUploadResponse
import team.inreok.getiserver.domain.file.entity.type.FilePurpose

interface FileUploadService {
    /**
     * 파일 하나를 검증하고 Object Storage에 저장한 뒤 Metadata를 남긴다.
     *
     * 반환된 `fileId`는 아직 어떤 리소스에도 연결되지 않은 상태다. 실제 연결은 각 Domain이
     * [team.inreok.getiserver.domain.file.link.FileLinkPort]로 수행한다.
     */
    fun upload(
        uploaderMemberId: Long,
        file: MultipartFile,
        purpose: FilePurpose,
    ): FileUploadResponse
}
