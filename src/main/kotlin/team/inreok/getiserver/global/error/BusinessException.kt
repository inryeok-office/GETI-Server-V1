package team.inreok.getiserver.global.error

import org.springframework.modulith.NamedInterface

/**
 * Application/Domain 계층에서 발생하는 예외의 공통 기반이다. [GlobalExceptionHandler]가
 * [errorCode]의 HTTP 상태와 Message를 그대로 응답으로 변환하므로, 각 Domain은 이 Class를
 * 상속한 전용 예외를 정의해 사용한다. `global` Package는 특정 Domain을 알지 못하므로 이 Class
 * 자체는 어떤 Domain 예외도 미리 정의하지 않는다. Domain Module이 상속해야 하므로 Named
 * Interface로 공개한다.
 */
@NamedInterface
open class BusinessException(
    val errorCode: ErrorCode,
    message: String = errorCode.defaultMessage,
) : RuntimeException(message)
