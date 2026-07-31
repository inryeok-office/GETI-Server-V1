package team.inreok.getiserver.global.error

import jakarta.validation.ConstraintViolationException
import org.springframework.beans.TypeMismatchException
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.validation.BindException
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.ServletWebRequest
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler
import org.springframework.web.servlet.resource.NoResourceFoundException

/**
 * [ResponseEntityExceptionHandler]는 Spring MVC의 표준 예외(Validation, Malformed JSON,
 * Method/Media Type 미지원, 404 등 20종)를 이미 `handleException(...)`(final)로 잡아
 * [handleExceptionInternal]로 위임한다. 이 Class는 그 위임 지점 하나만 재정의해 모든 표준
 * 예외를 공통 [ErrorResponse] 형식으로 변환한다. `BindException`(단독 사용 시),
 * `ConstraintViolationException`, [BusinessException], 그 외 예상하지 못한 예외는
 * [ResponseEntityExceptionHandler]가 다루지 않으므로 별도 Handler로 추가하고 동일한
 * [handleExceptionInternal]로 위임한다. [BusinessException]의 Message는 개발자가 직접
 * 정의한 안전한 사용자 노출용 문구이므로 그대로 사용하고, 그 외 예상하지 못한 예외는 항상
 * [ErrorCode]의 기본 Message만 노출해 내부 정보(Stack Trace, 원인 Class 등)가 새어나가지
 * 않도록 한다.
 */
@RestControllerAdvice
class GlobalExceptionHandler : ResponseEntityExceptionHandler() {
    @ExceptionHandler(BusinessException::class)
    fun handleBusinessException(
        ex: BusinessException,
        request: WebRequest,
    ): ResponseEntity<Any> = handleExceptionInternal(ex, null, HttpHeaders.EMPTY, ex.errorCode.status, request)

    @ExceptionHandler(BindException::class)
    fun handleBindException(
        ex: BindException,
        request: WebRequest,
    ): ResponseEntity<Any> = handleExceptionInternal(ex, null, HttpHeaders.EMPTY, HttpStatus.BAD_REQUEST, request)

    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolation(
        ex: ConstraintViolationException,
        request: WebRequest,
    ): ResponseEntity<Any> = handleExceptionInternal(ex, null, HttpHeaders.EMPTY, HttpStatus.BAD_REQUEST, request)

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(
        ex: Exception,
        request: WebRequest,
    ): ResponseEntity<Any> =
        handleExceptionInternal(ex, null, HttpHeaders.EMPTY, HttpStatus.INTERNAL_SERVER_ERROR, request)

    override fun handleExceptionInternal(
        ex: Exception,
        body: Any?,
        headers: HttpHeaders,
        statusCode: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any> {
        val status = HttpStatus.valueOf(statusCode.value())
        val path = resolvePath(request)
        logException(status, path, ex)
        val errorCode = resolveErrorCode(ex, status)
        val errorResponse =
            ErrorResponse.of(
                errorCode = errorCode,
                path = path,
                message = resolveMessage(ex, errorCode),
                fieldErrors = extractFieldErrors(ex),
            )
        return ResponseEntity.status(status).headers(headers).body(errorResponse)
    }

    private fun resolveErrorCode(
        ex: Exception,
        status: HttpStatus,
    ): ErrorCode =
        when (ex) {
            is BusinessException -> ex.errorCode
            is MethodArgumentNotValidException -> CommonErrorCode.VALIDATION_FAILED
            is BindException -> CommonErrorCode.VALIDATION_FAILED
            is ConstraintViolationException -> CommonErrorCode.VALIDATION_FAILED
            is HttpMessageNotReadableException -> CommonErrorCode.MALFORMED_JSON
            is MissingServletRequestParameterException -> CommonErrorCode.MISSING_REQUEST_PARAMETER
            is TypeMismatchException -> CommonErrorCode.TYPE_MISMATCH
            is HttpRequestMethodNotSupportedException -> CommonErrorCode.METHOD_NOT_ALLOWED
            is HttpMediaTypeNotSupportedException -> CommonErrorCode.UNSUPPORTED_MEDIA_TYPE
            is NoResourceFoundException -> CommonErrorCode.RESOURCE_NOT_FOUND
            else -> CommonErrorCode.fromStatus(status)
        }

    private fun resolveMessage(
        ex: Exception,
        errorCode: ErrorCode,
    ): String = if (ex is BusinessException) (ex.message ?: errorCode.defaultMessage) else errorCode.defaultMessage

    private fun extractFieldErrors(ex: Exception): List<FieldErrorResponse> =
        when (ex) {
            is MethodArgumentNotValidException -> {
                ex.bindingResult.fieldErrors.map {
                    FieldErrorResponse(
                        field = it.field,
                        reason =
                            it.defaultMessage ?: CommonErrorCode.VALIDATION_FAILED.defaultMessage,
                    )
                }
            }

            is BindException -> {
                ex.fieldErrors.map {
                    FieldErrorResponse(
                        field = it.field,
                        reason =
                            it.defaultMessage ?: CommonErrorCode.VALIDATION_FAILED.defaultMessage,
                    )
                }
            }

            is ConstraintViolationException -> {
                ex.constraintViolations.map {
                    FieldErrorResponse(field = it.propertyPath.toString(), reason = it.message)
                }
            }

            else -> {
                emptyList()
            }
        }

    private fun resolvePath(request: WebRequest): String =
        (request as? ServletWebRequest)?.request?.requestURI ?: request.getDescription(false)

    private fun logException(
        status: HttpStatus,
        path: String,
        ex: Exception,
    ) {
        if (status.is5xxServerError) {
            logger.error("처리하지 못한 오류가 발생했습니다. path=$path, status=${status.value()}", ex)
        } else if (logger.isWarnEnabled) {
            logger.warn("요청 처리 중 오류가 발생했습니다. path=$path, status=${status.value()}, exception=${ex::class.simpleName}")
        }
    }
}
