package team.inreok.getiserver.domain.collector.provider

import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * 공공데이터포털 등 일부 Provider는 인증키를 이미 URL Encoding된 상태로 배포한다(`%2F`, `%3D` 등).
 * 그 값을 `UriComponentsBuilder.queryParam`에 그대로 넘기면 Builder가 다시 Encoding해
 * `%252F`, `%253D`처럼 이중 Encoding된다. 이 Object는 원문이 이미 Encoding됐는지 판정해 정확히
 * 한 번만 Decoding한다(이미 Decoding된 값이 들어오면 손대지 않는다).
 */
object ServiceKeyCodec {
    private val PERCENT_ENCODED = Regex("%[0-9A-Fa-f]{2}")

    /** 이미 Encoding된 것으로 보이면 한 번만 Decoding하고, 아니면 원문을 그대로 반환한다. */
    fun decodeOnce(raw: String): String =
        if (PERCENT_ENCODED.containsMatchIn(raw)) URLDecoder.decode(raw, StandardCharsets.UTF_8) else raw

    /** Decoding된 값을 Query Parameter 값으로 안전하게 다시 Encoding한다. */
    fun encode(decoded: String): String = URLEncoder.encode(decoded, StandardCharsets.UTF_8)
}
