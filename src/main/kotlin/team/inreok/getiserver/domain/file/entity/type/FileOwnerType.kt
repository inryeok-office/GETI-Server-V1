package team.inreok.getiserver.domain.file.entity.type

import org.springframework.modulith.NamedInterface

/**
 * 파일이 연결된 리소스의 종류다. `files.owner_type` + `files.owner_id`는 다형적 참조라 물리
 * FK가 없다(docs/architecture/erd.md "다형적 참조" 참고).
 *
 * 다른 Domain이 [team.inreok.getiserver.domain.file.link.FileLinkPort]를 호출할 때 넘기고
 * [team.inreok.getiserver.domain.file.access.FileAccessChecker]를 구현할 때 선언하므로 Named
 * Interface로 공개한다.
 */
@NamedInterface
enum class FileOwnerType {
    /** 회원 프로필 이미지. `owner_id`는 `members.id`. */
    MEMBER,

    /** 기업 로고. `owner_id`는 `companies.id`. */
    COMPANY,

    /** 공고 본문 첨부파일. `owner_id`는 `jobs.id`. */
    JOB,

    /** 프로그램 본문 첨부파일. `owner_id`는 `programs.id`. */
    PROGRAM,

    /** 내부 지원서 첨부 서류. `owner_id`는 `job_applications.id`. */
    JOB_APPLICATION,

    /** 프로그램 신청 첨부 서류. `owner_id`는 `program_applications.id`. */
    PROGRAM_APPLICATION,

    /** 문의 첨부파일. `owner_id`는 `inquiries.id`. */
    INQUIRY,

    /**
     * 문의 답변 첨부파일. `owner_id`는 `inquiry_answers.id`. [INQUIRY]와 분리한 이유는 문의 본문과
     * 그에 달린 여러 답변이 같은 `owner_id`(문의 ID) 아래에서는 서로 구분되지 않기 때문이다
     * (GETI Inquiry 도메인 개발 요구사항 45절, 사용자 확인 완료).
     */
    INQUIRY_ANSWER,

    /** 포트폴리오 제출물. `owner_id`는 `portfolio_submissions.id`(요청이 아니라 실제 제출물). */
    PORTFOLIO_SUBMISSION,
}
