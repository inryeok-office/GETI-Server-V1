package team.inreok.getiserver.domain.file.policy

import org.springframework.boot.context.properties.ConfigurationProperties
import team.inreok.getiserver.domain.file.entity.type.FilePurpose

/**
 * 목적별 업로드 정책이다(요구사항 §7).
 *
 * 정확한 숫자(허용 확장자·MIME·최대 크기·최대 개수)는 아직 확정되지 않았으므로
 * (docs/file/file-domain-plan.md §17 DECISION_REQUIRED) 코드에 박지 않고 설정으로 분리했다.
 * 값이 확정되면 `application.yaml`만 고치면 되고 코드 변경·재배포가 필요 없다.
 *
 * `switch/when`을 Controller 여러 곳에 중복 작성하지 않도록 조회 창구를 [of] 하나로 둔다(§4).
 */
@ConfigurationProperties(prefix = "app.file")
data class FilePolicyProperties(
    val policies: Map<FilePurpose, FileUploadPolicy>,
) {
    init {
        // 정책이 빠진 Purpose가 있으면 그 목적의 업로드가 Runtime에 가서야 터진다. 기동 시점에
        // 막는 편이 낫다(CollectorSeedProdGuard와 같은 판단).
        val missing = FilePurpose.entries.filterNot { policies.containsKey(it) }
        require(missing.isEmpty()) {
            "app.file.policies에 정책이 없는 FilePurpose가 있습니다: ${missing.joinToString()}"
        }
    }

    fun of(purpose: FilePurpose): FileUploadPolicy = policies[purpose] ?: error("app.file.policies에 $purpose 정책이 없습니다.")
}

/**
 * 한 [FilePurpose]의 업로드 제약이다.
 *
 * [allowedExtensions]와 [allowedMimeTypes]를 모두 두는 이유는 요구사항 §8이다 -- 확장자만 보면
 * 이름만 바꾼 실행 파일을 막지 못하고, 선언된 MIME만 보면 클라이언트가 조작할 수 있다. 실제
 * 파일 내용에서 탐지한 MIME까지 세 값을 교차 검증한다
 * ([team.inreok.getiserver.domain.file.policy.FileContentTypeValidator] 참고).
 */
data class FileUploadPolicy(
    /** 소문자 확장자 목록(`.` 없이). 예: `png`, `pdf`. */
    val extensions: Set<String>,
    /** 허용 MIME Type. 실제 탐지된 값이 이 목록에 있어야 한다. */
    val mimeTypes: Set<String>,
    val maxSizeBytes: Long,
    /** 하나의 리소스에 연결할 수 있는 최대 파일 수(§24). 업로드가 아니라 연결 시점에 검사한다. */
    val maxCount: Int,
) {
    init {
        require(extensions.isNotEmpty()) { "extensions는 비어 있을 수 없습니다." }
        require(mimeTypes.isNotEmpty()) { "mimeTypes는 비어 있을 수 없습니다." }
        require(maxSizeBytes > 0) { "maxSizeBytes는 0보다 커야 합니다. (현재=$maxSizeBytes)" }
        require(maxCount > 0) { "maxCount는 0보다 커야 합니다. (현재=$maxCount)" }
        require(extensions.none { it.startsWith(".") }) {
            "extensions는 '.' 없이 확장자만 적습니다. (현재=$extensions)"
        }
        require(extensions.all { it == it.lowercase() }) {
            "extensions는 소문자로만 적습니다. (현재=$extensions)"
        }
    }

    fun allowsExtension(extension: String): Boolean = extension.lowercase() in extensions

    fun allowsMimeType(mimeType: String): Boolean = mimeType.lowercase() in mimeTypes
}
