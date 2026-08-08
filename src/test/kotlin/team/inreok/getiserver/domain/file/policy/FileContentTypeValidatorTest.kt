package team.inreok.getiserver.domain.file.policy

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import team.inreok.getiserver.domain.file.entity.type.FilePurpose
import team.inreok.getiserver.domain.file.exception.FileTypeNotAllowedException
import team.inreok.getiserver.domain.file.exception.MimeMismatchException
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 요구사항 §8. 확장자·선언 MIME·실제 내용의 교차 검증이 핵심이며, 특히 "이름만 바꾼 파일"이
 * 통과하지 않아야 한다.
 */
class FileContentTypeValidatorTest {
    private val validator = FileContentTypeValidator()

    private val imagePolicy =
        FileUploadPolicy(
            extensions = setOf("png", "jpg", "jpeg"),
            mimeTypes = setOf("image/png", "image/jpeg"),
            maxSizeBytes = 1024 * 1024,
            maxCount = 1,
        )

    private val documentPolicy =
        FileUploadPolicy(
            extensions = setOf("pdf"),
            mimeTypes = setOf("application/pdf"),
            maxSizeBytes = 1024 * 1024,
            maxCount = 1,
        )

    @Test
    fun `실제 PNG는 통과하고 탐지된 MIME을 돌려준다`() {
        val detected =
            validator.detectAndValidate(
                inputStream = stream(PNG_BYTES),
                sanitizedFileName = SanitizedFileName("logo.png", "png"),
                declaredContentType = "image/png",
                purpose = FilePurpose.PROFILE_IMAGE,
                policy = imagePolicy,
            )

        assertThat(detected).isEqualTo("image/png")
    }

    @Test
    fun `탐지 후 Stream이 처음으로 되감겨 그대로 업로드할 수 있다`() {
        val stream = stream(PNG_BYTES)

        validator.detectAndValidate(
            inputStream = stream,
            sanitizedFileName = SanitizedFileName("logo.png", "png"),
            declaredContentType = null,
            purpose = FilePurpose.PROFILE_IMAGE,
            policy = imagePolicy,
        )

        assertThat(stream.readBytes()).isEqualTo(PNG_BYTES)
    }

    @Test
    fun `허용되지 않은 확장자는 내용을 읽기 전에 거부한다`() {
        assertThatThrownBy {
            validator.detectAndValidate(
                inputStream = stream(PNG_BYTES),
                sanitizedFileName = SanitizedFileName("malware.exe", "exe"),
                declaredContentType = "image/png",
                purpose = FilePurpose.PROFILE_IMAGE,
                policy = imagePolicy,
            )
        }.isInstanceOf(FileTypeNotAllowedException::class.java)
    }

    @Test
    fun `이름만 pdf로 바꾼 PNG는 MIME_MISMATCH로 거부한다`() {
        // 확장자(pdf)도 정책 안이고 실제 형식을 속이려 declaredContentType까지 맞췄지만,
        // 내용이 PNG라 Tika가 잡아낸다.
        assertThatThrownBy {
            validator.detectAndValidate(
                inputStream = stream(PNG_BYTES),
                sanitizedFileName = SanitizedFileName("resume.pdf", "pdf"),
                declaredContentType = "application/pdf",
                purpose = FilePurpose.JOB_APPLICATION,
                policy =
                    FileUploadPolicy(
                        extensions = setOf("pdf", "png"),
                        mimeTypes = setOf("application/pdf", "image/png"),
                        maxSizeBytes = 1024 * 1024,
                        maxCount = 1,
                    ),
            )
        }.isInstanceOf(MimeMismatchException::class.java)
    }

    @Test
    fun `정책이 허용하지 않는 실제 형식은 FILE_TYPE_NOT_ALLOWED로 거부한다`() {
        // 확장자는 pdf(정책 안)지만 내용이 PNG이고 정책의 허용 MIME에 image/png가 없다.
        assertThatThrownBy {
            validator.detectAndValidate(
                inputStream = stream(PNG_BYTES),
                sanitizedFileName = SanitizedFileName("resume.pdf", "pdf"),
                declaredContentType = "application/pdf",
                purpose = FilePurpose.JOB_APPLICATION,
                policy = documentPolicy,
            )
        }.isInstanceOf(FileTypeNotAllowedException::class.java)
    }

    @Test
    fun `클라이언트가 보낸 Content-Type이 비표준이어도 실패하지 않는다`() {
        // 브라우저는 image/jpg, application/octet-stream 같은 값을 흔히 보낸다. 이걸로
        // 실패시키면 정상 업로드가 오탐으로 막힌다.
        val detected =
            validator.detectAndValidate(
                inputStream = stream(PNG_BYTES),
                sanitizedFileName = SanitizedFileName("logo.png", "png"),
                declaredContentType = "application/octet-stream",
                purpose = FilePurpose.PROFILE_IMAGE,
                policy = imagePolicy,
            )

        assertThat(detected).isEqualTo("image/png")
    }

    @Test
    fun `jpg와 jpeg는 같은 형식으로 취급한다`() {
        listOf("jpg", "jpeg").forEach { extension ->
            val detected =
                validator.detectAndValidate(
                    inputStream = stream(JPEG_BYTES),
                    sanitizedFileName = SanitizedFileName("photo.$extension", extension),
                    declaredContentType = null,
                    purpose = FilePurpose.PROFILE_IMAGE,
                    policy = imagePolicy,
                )

            assertThat(detected).describedAs("확장자 $extension").isEqualTo("image/jpeg")
        }
    }

    @Test
    fun `실제 PDF는 통과한다`() {
        val detected =
            validator.detectAndValidate(
                inputStream = stream(PDF_BYTES),
                sanitizedFileName = SanitizedFileName("resume.pdf", "pdf"),
                declaredContentType = "application/pdf",
                purpose = FilePurpose.JOB_APPLICATION,
                policy = documentPolicy,
            )

        assertThat(detected).isEqualTo("application/pdf")
    }

    @Test
    fun `내용이 ZIP인 파일은 docx 이름을 달아도 확장자 정책에 막힌다`() {
        // ZIP Container 형식을 내용으로 구분할 수 없는 것이 지금 의존성(tika-core 단독)의 한계라,
        // 방어선은 "허용 목록에 Container 형식을 넣지 않는 것" 하나뿐이다. 아래 Test가 그 이유다.
        assertThatThrownBy {
            validator.detectAndValidate(
                inputStream = stream(zipBytes()),
                sanitizedFileName = SanitizedFileName("resume.docx", "docx"),
                declaredContentType = OOXML_WORD,
                purpose = FilePurpose.JOB_APPLICATION,
                policy = documentPolicy,
            )
        }.isInstanceOf(FileTypeNotAllowedException::class.java)
    }

    @Test
    fun `docx를 허용하면 이름만 바꾼 ZIP이 통과한다 - tika-core의 한계를 고정한다`() {
        // 내용은 그냥 ZIP인데 Word 문서로 판정된다. tika-core에는 ZipContainerDetector가 없어
        // 파일명 Hint(glob) 결과가 그대로 채택되기 때문이다.
        //
        // 이 Test가 깨진다면 Container 판정기가 Classpath에 들어왔다는 뜻이다. 그때는
        // application.yaml의 허용 목록에 docx를 다시 넣을지 검토한다.
        val detected =
            validator.detectAndValidate(
                inputStream = stream(zipBytes()),
                sanitizedFileName = SanitizedFileName("resume.docx", "docx"),
                declaredContentType = OOXML_WORD,
                purpose = FilePurpose.JOB_APPLICATION,
                policy =
                    FileUploadPolicy(
                        extensions = setOf("docx"),
                        mimeTypes = setOf(OOXML_WORD),
                        maxSizeBytes = 1024 * 1024,
                        maxCount = 1,
                    ),
            )

        assertThat(detected).isEqualTo(OOXML_WORD)
    }

    private fun stream(bytes: ByteArray) = BufferedInputStream(ByteArrayInputStream(bytes))

    /** Entry 하나짜리 실제 ZIP. `PK`로 시작해 Tika가 application/zip으로 인식한다. */
    private fun zipBytes(): ByteArray =
        ByteArrayOutputStream()
            .also { out ->
                ZipOutputStream(out).use { zip ->
                    zip.putNextEntry(ZipEntry("hello.txt"))
                    zip.write("hello".toByteArray())
                    zip.closeEntry()
                }
            }.toByteArray()

    private companion object {
        /** PNG Signature + 최소 IHDR. Tika가 image/png로 판정하기에 충분하다. */
        private val PNG_BYTES =
            byteArrayOf(
                0x89.toByte(),
                0x50,
                0x4E,
                0x47,
                0x0D,
                0x0A,
                0x1A,
                0x0A,
                0x00,
                0x00,
                0x00,
                0x0D,
                0x49,
                0x48,
                0x44,
                0x52,
                0x00,
                0x00,
                0x00,
                0x01,
                0x00,
                0x00,
                0x00,
                0x01,
                0x08,
                0x06,
                0x00,
                0x00,
                0x00,
                0x1F,
                0x15,
                0xC4.toByte(),
                0x89.toByte(),
            )

        /** JFIF Header가 들어간 JPEG 시작 바이트. */
        private val JPEG_BYTES =
            byteArrayOf(
                0xFF.toByte(),
                0xD8.toByte(),
                0xFF.toByte(),
                0xE0.toByte(),
                0x00,
                0x10,
                0x4A,
                0x46,
                0x49,
                0x46,
                0x00,
                0x01,
                0x01,
                0x00,
                0x00,
                0x01,
                0x00,
                0x01,
                0x00,
                0x00,
            )

        private val PDF_BYTES = "%PDF-1.4\n1 0 obj\n<<>>\nendobj\ntrailer\n%%EOF\n".toByteArray()

        private const val OOXML_WORD =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    }
}
