package team.inreok.getiserver.domain.job.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Size
import team.inreok.getiserver.domain.job.entity.type.ApplicationMethod
import team.inreok.getiserver.domain.job.entity.type.JobStatus
import team.inreok.getiserver.domain.job.entity.type.PostingType
import java.time.LocalDateTime

/**
 * 공고 등록·임시저장 요청이다.
 *
 * `status = DRAFT`면 아래 선택 Field가 비어 있어도 저장되고, `status = PUBLISHED`면 게시
 * 필수값을 모두 검증한다(`JobServiceImpl.validateForPublish`).
 *
 * `sourceId`, `formId`, `targetGrades`(복수), `techStackIds`는 이번 범위에서 제외했다(Issue #60).
 * 해당 Column이나 연결 Table이 아직 없다.
 *
 * `discordChannelKey`는 원시 Discord Snowflake가 아니라 **논리 채널 Key**다(Notification 후속
 * 요구사항 문서 §10, Issue #97). 운영 Channel Id를 API·DB에 노출하지 않기 위해 Program의
 * `discordChannelId`(원시 Snowflake, 기존 계약)와 다른 방식을 택했다. 생략하면 게시 시
 * 기본 채널을 쓴다.
 *
 * `fileIds`(첨부파일 연결)는 File 도메인의 공개 Port(`FileLinkPort`, Issue #85)로 소유권·목적·
 * 상태·개수를 검증한 뒤 연결한다(Issue #126, `ProgramCreateRequest.fileIds`와 동일한 방식).
 */
@Schema(description = "공고 등록·임시저장 요청")
data class JobCreateRequest(
    @param:Schema(description = "공고를 등록할 기업 ID(필수). 삭제된 기업이면 COMPANY_NOT_FOUND", example = "1")
    val companyId: Long,
    @param:Schema(description = "공고 유형(필수)", example = "MOU")
    val postingType: PostingType,
    @param:Schema(
        description =
            "지원 방식(필수). EXTERNAL은 게시 시 externalUrl이 필수다. " +
                "INTERNAL은 지원서 양식 연동 전이라 DRAFT로만 저장할 수 있고 게시하면 JOB_FORM_REQUIRED로 거부된다.",
        example = "EXTERNAL",
    )
    val applicationMethod: ApplicationMethod,
    // 공백 검증을 @NotBlank로 하면 VALIDATION_FAILED가 반환되어 JOB_VALIDATION_FAILED와 어긋난다.
    // 빈 값 판정은 Service에서 수행한다(수정 요청과 동일한 경로, CompanyCreateRequest와 같은 이유).
    @field:Size(max = 500, message = "공고 제목은 500자를 넘을 수 없습니다.")
    @param:Schema(description = "공고 제목(필수, 최대 500자)", example = "2026 상반기 백엔드 채용", maxLength = 500)
    val title: String,
    @param:Schema(
        description = "저장할 상태(필수). DRAFT 또는 PUBLISHED만 지정할 수 있다. CLOSED·DELETED는 상태 변경 API를 사용한다.",
        example = "DRAFT",
        allowableValues = ["DRAFT", "PUBLISHED"],
    )
    val status: JobStatus,
    @param:Schema(
        description = "Markdown 본문. PUBLISHED로 저장하려면 비어 있으면 안 된다.",
        example = "## 모집 부문\n- 백엔드 개발자",
        nullable = true,
    )
    val content: String? = null,
    @field:Size(max = 2000, message = "외부 지원 URL은 2000자를 넘을 수 없습니다.")
    @param:Schema(
        description = "외부 지원 URL. http 또는 https만 허용한다. EXTERNAL 공고를 게시하려면 필수다.",
        example = "https://example.com/apply",
        nullable = true,
        maxLength = 2000,
    )
    val externalUrl: String? = null,
    @param:Schema(description = "모집 시작 시각", example = "2026-08-01T00:00:00", nullable = true)
    val startDate: LocalDateTime? = null,
    @param:Schema(
        description = "모집 종료 시각. 생략하면 마감 없는 공고로 취급되어 openOnly=true 목록에도 계속 노출된다.",
        example = "2026-08-31T23:59:59",
        nullable = true,
    )
    val endDate: LocalDateTime? = null,
    @param:Schema(description = "지원 대상 학년(1~3). 생략하면 학년 제한 없음", example = "3", nullable = true)
    val targetGrade: Int? = null,
    @param:Schema(description = "모집 인원(1 이상)", example = "2", nullable = true)
    val capacity: Int? = null,
    // 근무지역과 고용형태는 정해진 값 집합이 없는 표시 전용 자유 문자열이다(Issue #169, Job Entity
    // 주석 참고). 길이만 검증하고 값 자체는 해석하지 않는다.
    @field:Size(max = 255, message = "근무지역은 255자를 넘을 수 없습니다.")
    @param:Schema(
        description = "근무지역. 정해진 값 집합이 없는 표시 전용 문자열이다.",
        example = "서울특별시 중구",
        nullable = true,
        maxLength = 255,
    )
    val location: String? = null,
    @field:Size(max = 255, message = "고용형태는 255자를 넘을 수 없습니다.")
    @param:Schema(
        description = "고용형태. 정해진 값 집합이 없는 표시 전용 문자열이다.",
        example = "인턴",
        nullable = true,
        maxLength = 255,
    )
    val employmentType: String? = null,
    @param:Schema(description = "선착순 모집 여부", example = "false", defaultValue = "false")
    val firstComeServed: Boolean = false,
    @field:Size(max = 255, message = "Discord 채널 Key는 255자를 넘을 수 없습니다.")
    @param:Schema(
        description = "게시할 Discord 채널의 논리 Key. 허용 목록에 없으면 거부된다. 생략하면 기본 채널을 쓴다.",
        example = "job-notice",
        nullable = true,
        maxLength = 255,
    )
    val discordChannelKey: String? = null,
    @param:Schema(
        description =
            "연결할 첨부파일 ID 목록(선택). FilePurpose=JOB_ATTACHMENT로 업로드하고 본인이 " +
                "소유한 파일만 연결할 수 있다. 개수 상한은 app.file.policies 설정을 따른다.",
        example = "[1, 2]",
    )
    val fileIds: List<Long> = emptyList(),
)
