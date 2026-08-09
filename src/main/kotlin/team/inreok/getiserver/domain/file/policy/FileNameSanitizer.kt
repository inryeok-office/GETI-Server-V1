package team.inreok.getiserver.domain.file.policy

import org.springframework.stereotype.Component
import team.inreok.getiserver.domain.file.exception.InvalidFileNameException
import java.text.Normalizer

/**
 * 클라이언트가 보낸 `originalFilename`을 안전한 표시용 이름으로 정규화한다(요구사항 §9/§27).
 *
 * `MultipartFile.originalFilename`은 클라이언트가 임의로 조작할 수 있는 값이다. 여기서 정규화한
 * 이름은 **표시와 다운로드 Header에만** 쓰이고 Storage Key로는 절대 쓰지 않는다 -- Key는
 * `{purpose}/{yyyy}/{MM}/{UUID}` 형태로 서버가 따로 만든다(§3/§42).
 */
@Component
class FileNameSanitizer {
    /**
     * @throws InvalidFileNameException 정규화 결과가 사용할 수 없는 이름일 때
     */
    fun sanitize(rawName: String?): SanitizedFileName {
        val normalized = normalize(rawName)
        val extension = extractExtension(normalized)
        val base = extractBase(normalized, extension)

        // 표시용 이름에는 원본의 대소문자를 그대로 둔다("이력서.PDF"를 "이력서.pdf"로 바꾸지
        // 않는다, §9 "원본 파일명은 사용자 표시용으로 보존"). 소문자 [extension]은 정책 대조
        // 전용이다.
        val displayExtension = normalized.substringAfterLast('.')

        // files.original_name이 VARCHAR(500)이라 그 안에 들어가야 한다. 확장자가 잘리면 형식
        // 판단이 달라지므로 앞쪽(base)만 줄인다.
        val maxBaseLength = MAX_FILE_NAME_LENGTH - displayExtension.length - 1
        val safeBase = base.take(maxBaseLength)

        return SanitizedFileName(displayName = "$safeBase.$displayExtension", extension = extension)
    }

    /** 제어 문자와 Path 구분자를 걷어내고 유니코드를 합성형(NFC)으로 맞춘다. */
    private fun normalize(rawName: String?): String {
        // 제어 문자 제거. Null Byte(\u0000)도 isISOControl에 포함되며, "resume.pdf\u0000.exe"처럼
        // 뒤쪽을 숨기려는 입력을 막는다.
        val withoutControlChars = rawName.orEmpty().filterNot { it.isISOControl() }

        // Path 구분자 기준 마지막 segment만 취한다. Windows와 Unix를 모두 처리해야
        // "../../secret.txt"와 "C:\Users\user\secret.pdf"가 함께 걸린다. 남은 Drive 표기
        // ("C:secret.pdf")는 ':' 뒤만 취해 없앤다.
        val lastSegment =
            withoutControlChars
                .split('/', '\\')
                .last()
                .substringAfterLast(':')

        // macOS가 보내는 자소 분리 한글(NFD)을 합성형으로 맞춰 저장·표시·비교가 어긋나지 않게 한다.
        val normalized = Normalizer.normalize(lastSegment, Normalizer.Form.NFC).trim()

        val reason =
            when {
                rawName.isNullOrBlank() -> "이름이 비어 있습니다"

                normalized.isBlank() -> "사용할 수 있는 문자가 없습니다"

                // "."과 ".."만 남은 경우
                normalized.all { it == '.' } -> "사용할 수 없는 이름입니다"

                else -> null
            }
        if (reason != null) throw InvalidFileNameException(reason)
        return normalized
    }

    /**
     * 마지막 '.' 이후만 확장자로 인정한다. `resume.pdf.exe`의 확장자는 `exe`이며, 허용 목록에
     * 없으면 [FileContentTypeValidator]가 거부한다(§27 Double Extension).
     */
    private fun extractExtension(normalized: String): String {
        val extension =
            normalized
                .substringAfterLast('.', missingDelimiterValue = "")
                .lowercase()

        val reason =
            when {
                extension.isBlank() -> "확장자가 없습니다"
                extension.length > MAX_EXTENSION_LENGTH -> "확장자가 너무 깁니다"
                !extension.all { it.isLetterOrDigit() } -> "확장자에 사용할 수 없는 문자가 있습니다"
                else -> null
            }
        if (reason != null) throw InvalidFileNameException(reason)
        return extension
    }

    private fun extractBase(
        normalized: String,
        extension: String,
    ): String {
        val base = normalized.substringBeforeLast('.')
        if (base.isBlank()) throw InvalidFileNameException("확장자($extension)만으로 이루어진 이름입니다")
        return base
    }

    private companion object {
        /** `files.original_name`이 VARCHAR(500)이다. */
        private const val MAX_FILE_NAME_LENGTH = 500

        /** `files.extension`이 VARCHAR(20)이다. */
        private const val MAX_EXTENSION_LENGTH = 20
    }
}

data class SanitizedFileName(
    /** 사용자에게 보여주고 다운로드 Header에 넣을 이름. Storage Key로는 쓰지 않는다. */
    val displayName: String,
    /** 소문자 확장자(`.` 없이). */
    val extension: String,
)
