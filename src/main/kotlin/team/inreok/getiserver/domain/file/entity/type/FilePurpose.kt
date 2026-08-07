package team.inreok.getiserver.domain.file.entity.type

import org.springframework.modulith.NamedInterface

/**
 * 파일의 사용 목적이다. 업로드 시점에 클라이언트가 지정하며 이후 **바뀌지 않는다**. 목적별로
 * 허용 확장자·MIME·최대 크기·최대 개수 정책이 달라진다
 * ([team.inreok.getiserver.domain.file.policy.FilePolicyProperties] 참고).
 *
 * [ownerType]과의 관계: `purpose`는 업로드 시점에 정해지는 불변 정책 키이고 `ownerType`은 연결
 * 이후에 채워지는 가변 상태다. 값이 1:1로 대응하지만 역할이 다르므로 Column을 함께 둔다.
 *
 * 지시서 §4의 후보 6개에 `PROFILE_IMAGE`/`COMPANY_LOGO`를 더한 8개다. 뒤 둘은
 * `members.profile_image_file_id`/`companies.logo_file_id` FK가 V2부터 이미 존재해 추측이
 * 아니다. `async_operations.result_file_id`에 대응하는 목적(서버가 생성하는 ZIP 결과물)은 이
 * 업로드 API를 경유하지 않으므로 여기에 넣지 않는다 -- 일괄 다운로드(Phase 6)에서 추가한다.
 *
 * 다른 Domain이 [team.inreok.getiserver.domain.file.link.FileLinkPort]를 호출할 때 넘기므로
 * Named Interface로 공개한다.
 */
@NamedInterface
enum class FilePurpose(
    val ownerType: FileOwnerType,
) {
    PROFILE_IMAGE(FileOwnerType.MEMBER),
    COMPANY_LOGO(FileOwnerType.COMPANY),
    JOB_ATTACHMENT(FileOwnerType.JOB),
    PROGRAM_ATTACHMENT(FileOwnerType.PROGRAM),
    JOB_APPLICATION(FileOwnerType.JOB_APPLICATION),
    PROGRAM_APPLICATION(FileOwnerType.PROGRAM_APPLICATION),
    INQUIRY_ATTACHMENT(FileOwnerType.INQUIRY),
    PORTFOLIO(FileOwnerType.PORTFOLIO_SUBMISSION),
}
