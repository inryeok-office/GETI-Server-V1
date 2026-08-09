package team.inreok.getiserver.domain.file.policy

import jakarta.annotation.PostConstruct
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.servlet.autoconfigure.MultipartProperties
import org.springframework.stereotype.Component

/**
 * 목적별 최대 크기가 Spring의 Multipart 한계보다 크면, 정책상 허용되는 파일이 Controller에
 * 도달하기도 전에 `MaxUploadSizeExceededException`으로 막힌다. 설정 실수를 Runtime이 아니라
 * 기동 시점에 드러낸다(CollectorSeedProdGuard와 같은 Fail-Fast 방식).
 *
 * `spring.servlet.multipart.max-file-size`를 미설정으로 두면 Spring 기본값이 1MB라 사실상 모든
 * 업로드가 막힌다. 이 검증이 그 상태도 함께 잡는다.
 */
@Component
@EnableConfigurationProperties(FilePolicyProperties::class)
class FilePolicyMultipartGuard(
    private val filePolicyProperties: FilePolicyProperties,
    private val multipartProperties: MultipartProperties,
) {
    @PostConstruct
    fun verify() {
        val maxFileSize = multipartProperties.maxFileSize.toBytes()
        val violations =
            filePolicyProperties.policies
                .filterValues { it.maxSizeBytes > maxFileSize }
                .map { (purpose, policy) -> "$purpose(${policy.maxSizeBytes}바이트)" }

        check(violations.isEmpty()) {
            "app.file.policies의 max-size-bytes가 spring.servlet.multipart.max-file-size" +
                "(${maxFileSize}바이트)를 초과합니다: ${violations.joinToString()}. " +
                "정책보다 Multipart 한계가 크거나 같아야 합니다."
        }
    }
}
