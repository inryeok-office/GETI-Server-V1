package team.inreok.getiserver.domain.application.dto

/** Export API의 제품 포맷 계약이다. 현재 서버가 실제 생성하는 포맷은 XLSX다. */
enum class ExportFormat {
    PDF,
    XLSX,
    DOCX,
    HWPX,
}
