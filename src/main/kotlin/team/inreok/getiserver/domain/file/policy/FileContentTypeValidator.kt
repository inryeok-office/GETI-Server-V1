package team.inreok.getiserver.domain.file.policy

import org.apache.tika.Tika
import org.apache.tika.metadata.Metadata
import org.apache.tika.metadata.TikaCoreProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import team.inreok.getiserver.domain.file.entity.type.FilePurpose
import team.inreok.getiserver.domain.file.exception.FileTypeNotAllowedException
import team.inreok.getiserver.domain.file.exception.MimeMismatchException
import java.io.BufferedInputStream

/**
 * 확장자, 클라이언트가 선언한 MIME, **실제 파일 내용에서 탐지한 MIME**을 교차 검증한다
 * (요구사항 §8).
 *
 * 확장자만 보면 `malware.exe`의 이름을 `resume.pdf`로 바꾼 업로드를 막지 못하고, 클라이언트가
 * 보낸 `Content-Type`도 마찬가지로 조작할 수 있다. 그래서 Tika로 파일 앞부분을 읽어 실제 형식을
 * 판단하고 그 값을 신뢰한다.
 *
 * Tika는 파일 전체가 아니라 Magic Number가 있는 앞부분만 읽으므로 큰 파일도 메모리에 올리지
 * 않는다(§8/§42). `mark`/`reset`으로 되감아 같은 Stream을 그대로 Storage에 흘려보낸다.
 *
 * **한계 -- ZIP/OLE2 Container 형식은 내용으로 판정되지 않는다.** `tika-parsers` 없이 `tika-core`만
 * 있으면 `ZipContainerDetector` 같은 Container 판정기가 없어, docx/hwpx처럼 ZIP을 껍데기로 쓰는
 * 형식은 Magic Number가 모두 `application/zip`으로 같아 결국 파일명 Hint(glob) 결과가 채택된다.
 * 즉 임의의 ZIP을 `resume.docx`로 바꾸면 아래 세 검사를 모두 통과한다. 그래서 `application.yaml`의
 * 허용 목록에는 Container 형식을 넣지 않는다. 넣어야 한다면 `tika-parsers-standard-package` 도입을
 * 먼저 결정한다. PDF/PNG/JPEG처럼 고유 Magic Number가 있는 형식은 의도대로 내용으로 판정된다.
 */
@Component
class FileContentTypeValidator {
    private val log = LoggerFactory.getLogger(javaClass)
    private val tika = Tika()

    /**
     * [inputStream]은 `mark`를 지원해야 한다([BufferedInputStream]으로 감싸서 넘긴다). 탐지 후
     * Stream 위치를 처음으로 되돌리므로 호출자는 그대로 업로드에 쓸 수 있다.
     *
     * @return 탐지된 MIME Type. DB `content_type`에는 이 값을 저장한다.
     * @throws FileTypeNotAllowedException 확장자 또는 탐지된 형식이 정책 밖일 때
     * @throws MimeMismatchException 확장자와 탐지된 형식이 서로 다른 종류일 때
     */
    fun detectAndValidate(
        inputStream: BufferedInputStream,
        sanitizedFileName: SanitizedFileName,
        declaredContentType: String?,
        purpose: FilePurpose,
        policy: FileUploadPolicy,
    ): String {
        val extension = sanitizedFileName.extension
        validateExtension(extension, purpose, policy)

        val detected = detect(inputStream, sanitizedFileName.displayName)

        if (!policy.allowsMimeType(detected)) {
            throw FileTypeNotAllowedException(purpose, "실제 형식=$detected")
        }
        // 확장자는 허용 목록에 있고 탐지 형식도 허용 목록에 있지만, 둘이 서로 다른 파일을
        // 가리키는 경우다(예: 확장자 pdf + 실제 PNG). 정책이 둘 다 허용하더라도 이름과 내용이
        // 어긋난 파일은 받지 않는다.
        if (!matchesExtension(extension, detected)) {
            throw MimeMismatchException(extension, detected)
        }

        // 클라이언트가 보낸 Content-Type으로는 실패시키지 않는다. 브라우저가 image/jpg,
        // application/octet-stream 같은 비표준·불명 값을 흔히 보내 정상 업로드가 오탐으로
        // 막히기 때문이다. 값이 어긋나면 로그만 남기고 판단은 탐지 결과로 한다.
        if (declaredContentType != null && !declaredContentType.equals(detected, ignoreCase = true)) {
            log.debug(
                "선언된 Content-Type과 탐지 결과가 다릅니다. declared={}, detected={}",
                declaredContentType,
                detected,
            )
        }
        return detected
    }

    private fun validateExtension(
        extension: String,
        purpose: FilePurpose,
        policy: FileUploadPolicy,
    ) {
        if (!policy.allowsExtension(extension)) {
            throw FileTypeNotAllowedException(purpose, "확장자=$extension")
        }
    }

    private fun detect(
        inputStream: BufferedInputStream,
        fileName: String,
    ): String {
        inputStream.mark(DETECTION_BUFFER_BYTES)
        return try {
            // 파일명을 Hint로 함께 준다. Magic Number가 같은 계열 안에서 갈리는 형식(text/plain
            // 계열 등)을 Tika가 이름까지 보고 좁혀 준다. Container 형식에는 이 Hint가 그대로
            // 결론이 되어 버리므로 허용 목록에서 제외한다(Class 주석의 "한계" 참고).
            val metadata = Metadata().apply { set(TikaCoreProperties.RESOURCE_NAME_KEY, fileName) }
            tika.detect(inputStream, metadata)
        } finally {
            inputStream.reset()
        }
    }

    /**
     * 확장자가 가리키는 형식과 탐지된 형식이 같은 파일을 말하는지 본다.
     *
     * Tika가 확장자로부터 얻는 MIME과 내용으로부터 얻는 MIME을 비교하는 방식이라, 새 확장자를
     * 정책에 추가할 때마다 이 코드를 고칠 필요가 없다.
     */
    private fun matchesExtension(
        extension: String,
        detectedContentType: String,
    ): Boolean {
        val fromExtension = tika.detect("dummy.$extension")
        return when {
            fromExtension.equals(detectedContentType, ignoreCase = true) -> true

            // Tika가 확장자만으로 판단하지 못하면(application/octet-stream) 비교할 근거가 없다.
            // 이 경우 정책의 허용 MIME 목록 검사(위)만으로 판단한다.
            fromExtension.equals(OCTET_STREAM, ignoreCase = true) -> true

            // JPEG는 jpg/jpeg 두 확장자가 같은 형식을 가리키는 등, 확장자별 별칭이 있다.
            // Tika가 이미 정규화된 MIME을 돌려주므로 대소문자만 무시하면 충분하다.
            else -> false
        }
    }

    private companion object {
        /** Magic Number 판정에 필요한 앞부분 크기. 파일 전체를 읽지 않는다. */
        private const val DETECTION_BUFFER_BYTES = 64 * 1024

        private const val OCTET_STREAM = "application/octet-stream"
    }
}
