package team.inreok.getiserver.domain.file.service

import org.springframework.stereotype.Component
import team.inreok.getiserver.domain.file.entity.type.FilePurpose
import java.time.LocalDate
import java.util.UUID

/**
 * Object Storage Key를 만든다.
 *
 * `{purpose}/{yyyy}/{MM}/{UUID}` 형태이며 원본 파일명을 절대 쓰지 않는다(요구사항 §3/§42).
 * - 파일명이 같아도 충돌하지 않는다.
 * - Key만으로는 누구의 어떤 파일인지 알 수 없고 다른 Key를 추측할 수도 없다.
 * - `purpose` Prefix가 있어 S3 Lifecycle Rule을 목적별로 다르게 걸 수 있다.
 *
 * 확장자를 Key에 붙이지 않는다. 형식은 Metadata(`content_type`)가 관리하고 Key는 순수한
 * 식별자로 둔다.
 */
@Component
class FileObjectKeyGenerator {
    fun generate(
        purpose: FilePurpose,
        today: LocalDate = LocalDate.now(),
    ): String = "$purpose/${today.year}/%02d/%s".format(today.monthValue, UUID.randomUUID())
}
