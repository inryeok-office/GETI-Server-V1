package team.inreok.getiserver.domain.file.policy

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 요구사항 §27/§28. 클라이언트가 inline을 요청해도 허용 여부는 서버가 정한다는 것이 핵심이다.
 */
class FileDispositionPolicyTest {
    private val policy = FileDispositionPolicy()

    @Test
    fun `이미지는 inline 요청을 허용한다`() {
        val result = policy.resolve("image/png", "logo.png", inlineRequested = true)

        assertThat(result.inline).isTrue()
        assertThat(result.contentType).isEqualTo("image/png")
        assertThat(result.headerValue).startsWith("inline;")
    }

    @Test
    fun `inline을 요청하지 않으면 이미지도 attachment다`() {
        val result = policy.resolve("image/png", "logo.png", inlineRequested = false)

        assertThat(result.inline).isFalse()
        assertThat(result.headerValue).startsWith("attachment;")
        assertThat(result.contentType).isEqualTo("application/octet-stream")
    }

    @Test
    fun `SVG는 inline을 요청해도 attachment로 강등된다`() {
        // SVG는 script를 품을 수 있어 우리 Origin에서 렌더링되면 XSS가 된다.
        val result = policy.resolve("image/svg+xml", "logo.svg", inlineRequested = true)

        assertThat(result.inline).isFalse()
        assertThat(result.headerValue).startsWith("attachment;")
        assertThat(result.contentType).isEqualTo("application/octet-stream")
    }

    @Test
    fun `HTML과 PDF도 inline을 요청해도 attachment로 강등된다`() {
        assertThat(policy.resolve("text/html", "a.html", inlineRequested = true).inline).isFalse()
        assertThat(policy.resolve("application/pdf", "a.pdf", inlineRequested = true).inline).isFalse()
    }

    @Test
    fun `한글 파일명은 RFC 5987로 Encoding된다`() {
        val result = policy.resolve("application/pdf", "이력서.pdf", inlineRequested = false)

        assertThat(result.headerValue).contains("filename*=UTF-8''")
        // 원본 한글이 Header에 그대로 들어가지 않는다.
        assertThat(result.headerValue).doesNotContain("이력서")
    }

    @Test
    fun `따옴표와 개행이 섞인 이름은 Header Injection이 되지 않는다`() {
        val malicious = "a\";filename=\"evil.exe"

        val result = policy.resolve("application/pdf", malicious, inlineRequested = false)

        // ASCII Fallback에서 따옴표가 '_'로 치환되어 Header 구조가 깨지지 않는다.
        assertThat(result.headerValue).doesNotContain("filename=\"evil.exe\"")
        assertThat(result.headerValue.count { it == '"' }).isEqualTo(2)
    }

    @Test
    fun `비ASCII 이름은 ASCII Fallback에서 치환된다`() {
        val result = policy.resolve("application/pdf", "이력서.pdf", inlineRequested = false)

        val fallback = result.headerValue.substringAfter("filename=\"").substringBefore("\"")
        assertThat(fallback).isEqualTo("___.pdf")
    }
}
