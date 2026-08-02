package team.inreok.getiserver.domain.company.exception

import org.springframework.http.HttpStatus
import team.inreok.getiserver.global.error.ErrorCode

enum class CompanyErrorCode(
    override val status: HttpStatus,
    override val defaultMessage: String,
) : ErrorCode {
    COMPANY_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 기업을 찾을 수 없습니다."),
    COMPANY_NAME_REQUIRED(HttpStatus.BAD_REQUEST, "기업명을 입력해야 합니다."),
    DUPLICATE_COMPANY(HttpStatus.CONFLICT, "이미 등록된 기업입니다."),
    MOU_PERIOD_INVALID(HttpStatus.BAD_REQUEST, "MOU 협약 기간이 올바르지 않습니다."),
    ;

    override val code: String get() = name
}
