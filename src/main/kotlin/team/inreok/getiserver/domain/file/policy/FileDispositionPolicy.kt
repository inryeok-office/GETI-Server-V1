package team.inreok.getiserver.domain.file.policy

import org.springframework.stereotype.Component
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * 다운로드 응답의 `Content-Disposition`과 `Content-Type`을 결정한다(요구사항 §27/§28).
 *
 * 클라이언트는 `inline`을 **요청**할 수 있을 뿐이고 허용 여부는 서버가 정한다. 브라우저가
 * inline으로 렌더링하는 순간 파일 내용이 우리 Origin에서 실행될 수 있기 때문이다 -- SVG는
 * `<script>`를 품을 수 있고 HTML은 그 자체가 XSS다. 안전하다고 확인한 이미지 형식만 inline을
 * 허용하고 나머지는 요청과 무관하게 첨부 파일로 강등한다.
 */
@Component
class FileDispositionPolicy {
    fun resolve(
        contentType: String,
        displayName: String,
        inlineRequested: Boolean,
    ): ResolvedDisposition {
        val inline = inlineRequested && contentType.lowercase() in INLINE_SAFE_CONTENT_TYPES
        return ResolvedDisposition(
            headerValue = contentDispositionHeader(inline, displayName),
            // inline이 아니면 Content-Type도 중립화한다. 브라우저가 Header를 무시하고 내용을
            // 스니핑해 렌더링하는 경우를 막는다(§27 Content-Type Sniffing).
            contentType = if (inline) contentType else OCTET_STREAM,
            inline = inline,
        )
    }

    /**
     * 파일명은 RFC 5987(`filename*=UTF-8''...`)로 Percent-Encoding한다. 원본 이름을 그대로 넣으면
     * 따옴표나 개행이 섞였을 때 Header Injection이 되고, 한글 파일명도 깨진다(§28).
     *
     * 구형 Client를 위한 `filename=`은 ASCII로만 채운다 -- 비ASCII를 넣으면 그 자체가 깨진 Header다.
     */
    private fun contentDispositionHeader(
        inline: Boolean,
        displayName: String,
    ): String {
        val type = if (inline) "inline" else "attachment"
        val asciiFallback =
            displayName
                .map { if (it.code in ASCII_PRINTABLE_RANGE && it != '"' && it != '\\') it else '_' }
                .joinToString("")
        val encoded = URLEncoder.encode(displayName, StandardCharsets.UTF_8).replace("+", "%20")
        return "$type; filename=\"$asciiFallback\"; filename*=UTF-8''$encoded"
    }

    private companion object {
        /**
         * inline 렌더링을 허용하는 형식. 프로필 이미지와 기업 로고를 `<img src>`로 표시하기
         * 위해 필요한 최소 집합이다. `image/svg+xml`은 스크립트를 품을 수 있어 제외한다.
         */
        private val INLINE_SAFE_CONTENT_TYPES =
            setOf("image/png", "image/jpeg", "image/webp", "image/gif")

        private const val OCTET_STREAM = "application/octet-stream"

        /** 공백(0x20)부터 물결(0x7E)까지. 제어 문자와 비ASCII를 제외한다. */
        private val ASCII_PRINTABLE_RANGE = 0x20..0x7E
    }
}

data class ResolvedDisposition(
    /** `Content-Disposition` Header 값 전체. */
    val headerValue: String,
    /** 응답에 쓸 Content-Type. inline이 아니면 `application/octet-stream`으로 중립화된다. */
    val contentType: String,
    val inline: Boolean,
)
