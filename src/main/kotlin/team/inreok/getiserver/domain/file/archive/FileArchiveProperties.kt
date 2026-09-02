package team.inreok.getiserver.domain.file.archive

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 일괄 다운로드(ZIP) 상한이다.
 *
 * 정확한 숫자는 아직 확정되지 않았다(`docs/file/file-domain-plan.md` §17 DECISION_REQUIRED
 * #10, `docs/file/file_domain_development.md` §44.11). 다른 File 정책과 같은 방식으로 코드에
 * 박지 않고 설정으로 분리했다 -- 값이 확정되면 `application.yaml`만 고치면 되고 코드 변경·재배포가
 * 필요 없다(`FilePolicyProperties`와 같은 판단).
 *
 * [maxTotalSizeBytes]는 각 File의 `sizeBytes`(압축 전 원본 크기) 합으로 검사한다. 실제 ZIP은
 * 압축으로 더 작아지지만, 이 상한이 막으려는 것은 "서버가 Storage에서 읽어 스트리밍하는 총
 * 바이트 양"이므로 압축 전 크기를 기준으로 삼는 것이 맞다.
 */
@ConfigurationProperties(prefix = "app.file.archive")
data class FileArchiveProperties(
    val maxFileCount: Int = DEFAULT_MAX_FILE_COUNT,
    val maxTotalSizeBytes: Long = DEFAULT_MAX_TOTAL_SIZE_BYTES,
) {
    init {
        require(maxFileCount > 0) { "app.file.archive.max-file-count는 0보다 커야 합니다. (현재=$maxFileCount)" }
        require(maxTotalSizeBytes > 0) {
            "app.file.archive.max-total-size-bytes는 0보다 커야 합니다. (현재=$maxTotalSizeBytes)"
        }
    }

    companion object {
        /** 잠정값. 지원서 첨부 정책(1건당 최대 5개 × 10MB, §7 참고) 기준 신청자 20명 분량이다. */
        const val DEFAULT_MAX_FILE_COUNT = 100

        /** 잠정값 500MB. */
        const val DEFAULT_MAX_TOTAL_SIZE_BYTES = 500L * 1024 * 1024
    }
}
