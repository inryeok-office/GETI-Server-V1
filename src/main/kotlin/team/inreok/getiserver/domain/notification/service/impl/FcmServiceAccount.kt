package team.inreok.getiserver.domain.notification.service.impl

import java.security.KeyFactory
import java.security.PrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64

/** Google Service Account JSON Key에서 FCM 전송에 필요한 최소 필드만 뽑은 값이다(Issue #190). */
internal data class FcmServiceAccount(
    val projectId: String,
    val clientEmail: String,
    val privateKey: PrivateKey,
    val tokenUri: String,
)

/**
 * PEM 형식(`-----BEGIN PRIVATE KEY-----...`) RSA Private Key 문자열을 [PrivateKey]로 변환한다.
 * Google Service Account JSON의 `private_key` 필드가 이 형식이다. 새 Dependency를 추가하지 않고
 * JDK 표준 `java.security`만 사용한다.
 */
internal fun parseRsaPrivateKey(pem: String): PrivateKey {
    val base64Body =
        pem
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\\s".toRegex(), "")
    val decoded = Base64.getDecoder().decode(base64Body)
    val keySpec = PKCS8EncodedKeySpec(decoded)
    return KeyFactory.getInstance("RSA").generatePrivate(keySpec)
}
