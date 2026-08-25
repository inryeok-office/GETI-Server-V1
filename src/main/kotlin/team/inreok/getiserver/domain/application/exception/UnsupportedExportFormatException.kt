package team.inreok.getiserver.domain.application.exception

import team.inreok.getiserver.domain.application.dto.ExportFormat
import team.inreok.getiserver.global.error.BusinessException

class UnsupportedExportFormatException(
    format: ExportFormat,
) : BusinessException(
        ApplicationErrorCode.UNSUPPORTED_EXPORT_FORMAT,
        "Export 포맷 $format 은 현재 지원하지 않습니다. XLSX를 사용해 주세요.",
    )
