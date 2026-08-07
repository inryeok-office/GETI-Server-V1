package team.inreok.getiserver.domain.file.policy

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import team.inreok.getiserver.domain.file.exception.InvalidFileNameException

/**
 * 요구사항 §9/§27의 파일명 공격 벡터를 검증한다. 여기서 걸러진 이름은 표시와 다운로드
 * Header에만 쓰이고 Storage Key로는 쓰이지 않지만, 그렇다고 조작된 이름을 그대로 저장할
 * 이유는 없다.
 */
class FileNameSanitizerTest {
    private val sanitizer = FileNameSanitizer()

    @Test
    fun `평범한 파일명은 그대로 유지하고 확장자를 소문자로 뽑는다`() {
        val result = sanitizer.sanitize("이력서.PDF")

        assertThat(result.displayName).isEqualTo("이력서.PDF")
        assertThat(result.extension).isEqualTo("pdf")
    }

    @Test
    fun `Unix Path Traversal은 마지막 segment만 남는다`() {
        val result = sanitizer.sanitize("../../secret.txt")

        assertThat(result.displayName).isEqualTo("secret.txt")
    }

    @Test
    fun `Windows 절대 경로는 Drive와 Directory가 제거된다`() {
        val result = sanitizer.sanitize("C:\\Users\\user\\secret.pdf")

        assertThat(result.displayName).isEqualTo("secret.pdf")
    }

    @Test
    fun `Null Byte로 뒤쪽 확장자를 숨기려는 이름은 실제 확장자가 드러난다`() {
        // "resume.pdf\u0000.exe"는 일부 하위 계층에서 resume.pdf로 잘려 보이지만, 제어 문자를
        // 제거하면 실제 확장자가 exe라는 사실이 남는다(정책이 exe를 허용하지 않아 뒤에서 거부).
        val result = sanitizer.sanitize("resume.pdf\u0000.exe")

        assertThat(result.extension).isEqualTo("exe")
    }

    @Test
    fun `이중 확장자는 마지막 것을 확장자로 본다`() {
        val result = sanitizer.sanitize("resume.pdf.exe")

        assertThat(result.extension).isEqualTo("exe")
    }

    @Test
    fun `제어 문자는 제거된다`() {
        val result = sanitizer.sanitize("re\nsu\tme.pdf")

        assertThat(result.displayName).isEqualTo("resume.pdf")
    }

    @Test
    fun `지나치게 긴 이름은 확장자를 보존한 채 잘린다`() {
        val longBase = "가".repeat(600)

        val result = sanitizer.sanitize("$longBase.pdf")

        assertThat(result.extension).isEqualTo("pdf")
        assertThat(result.displayName).endsWith(".pdf")
        // files.original_name이 VARCHAR(500)이라 그 안에 들어가야 한다.
        assertThat(result.displayName.length).isLessThanOrEqualTo(500)
    }

    @Test
    fun `null이거나 빈 이름은 거부한다`() {
        assertThatThrownBy { sanitizer.sanitize(null) }
            .isInstanceOf(InvalidFileNameException::class.java)
        assertThatThrownBy { sanitizer.sanitize("   ") }
            .isInstanceOf(InvalidFileNameException::class.java)
    }

    @Test
    fun `확장자가 없으면 거부한다`() {
        assertThatThrownBy { sanitizer.sanitize("resume") }
            .isInstanceOf(InvalidFileNameException::class.java)
    }

    @Test
    fun `점만으로 이루어진 이름은 거부한다`() {
        assertThatThrownBy { sanitizer.sanitize("..") }
            .isInstanceOf(InvalidFileNameException::class.java)
    }

    @Test
    fun `확장자만 있고 이름이 없으면 거부한다`() {
        assertThatThrownBy { sanitizer.sanitize(".pdf") }
            .isInstanceOf(InvalidFileNameException::class.java)
    }

    @Test
    fun `확장자가 지나치게 길면 거부한다`() {
        assertThatThrownBy { sanitizer.sanitize("resume.${"a".repeat(21)}") }
            .isInstanceOf(InvalidFileNameException::class.java)
    }

    @Test
    fun `확장자에 문자와 숫자가 아닌 값이 있으면 거부한다`() {
        assertThatThrownBy { sanitizer.sanitize("resume.pd f") }
            .isInstanceOf(InvalidFileNameException::class.java)
    }
}
