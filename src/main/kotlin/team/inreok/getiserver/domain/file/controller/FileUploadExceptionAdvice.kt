package team.inreok.getiserver.domain.file.controller

import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.core.annotation.Order
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.multipart.MaxUploadSizeExceededException
import team.inreok.getiserver.domain.file.exception.FileErrorCode
import team.inreok.getiserver.global.error.ErrorResponse

/**
 * `spring.servlet.multipart.max-file-size`를 넘는 요청은 Controller에 도달하기 전에 Spring이
 * [MaxUploadSizeExceededException]으로 끊는다. 이 예외를 그대로 두면
 * [GlobalExceptionHandler][team.inreok.getiserver.global.error.GlobalExceptionHandler]의
 * 마지막 Handler가 받아 500이 되는데, API 명세는 413 `FILE_TOO_LARGE`를 요구한다.
 *
 * 이 Advice를 `global`이 아니라 `domain.file`에 두고 [FileController]로 범위를 좁힌 이유는
 * Module 경계다 -- `FileErrorCode`는 `domain.file` 소유이고 `global`은 특정 Domain을 알지
 * 못한다(`ErrorCode`의 Class 주석 참고).
 *
 * 응답을 직접 만들어 돌려준다. `@ExceptionHandler` 안에서 다른 예외를 던지면 그 예외는 다른
 * Advice로 다시 전달되지 않고 그대로 Container까지 올라가 본문 없는 500이 된다.
 */
@Order(0)
@RestControllerAdvice(assignableTypes = [FileController::class])
class FileUploadExceptionAdvice {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(MaxUploadSizeExceededException::class)
    fun handleMaxUploadSizeExceeded(
        ex: MaxUploadSizeExceededException,
        request: HttpServletRequest,
    ): ResponseEntity<ErrorResponse> {
        val errorCode = FileErrorCode.FILE_TOO_LARGE
        log.warn("Multipart 최대 크기를 초과했습니다. maxUploadSize={}", ex.maxUploadSize, ex)
        // 실제 크기는 알 수 없다 -- Spring은 한계 초과를 감지한 시점에 요청 본문을 끝까지 읽지
        // 않는다. 사용자에게 필요한 정보는 "얼마까지 되는가"이므로 한계값만 알린다.
        val body =
            ErrorResponse.of(
                errorCode = errorCode,
                path = request.requestURI,
                message = "허용된 파일 크기를 초과했습니다. (최대=${ex.maxUploadSize}바이트)",
            )
        return ResponseEntity.status(errorCode.status).body(body)
    }
}
