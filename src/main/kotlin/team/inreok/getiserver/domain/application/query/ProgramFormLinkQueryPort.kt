package team.inreok.getiserver.domain.application.query

import org.springframework.modulith.NamedInterface

/**
 * `program` Module이 자신의 신청 양식(FormType=PROGRAM)을 조회·검증할 때 쓰는 유일한 공개
 * 계약이다(원본 요구사항 문서 "GETI-Server Program 도메인 전체 개발 요구사항" 20절, Form 연동
 * 방식은 사용자 확인 완료 — 기존 Form 도메인 실연동). `program`은 이 Interface를 통해서만 Form을
 * 읽고, `Form`/`FormVersion` Entity나 `FormRepository`/`FormVersionRepository`를 직접
 * 참조하지 않는다. `job`의 [team.inreok.getiserver.domain.job.query.JobApplicationSnapshotQueryPort]와
 * 같은 목적의 계약을 Application 쪽에서 공개하는 대응 관계다.
 */
@NamedInterface
interface ProgramFormLinkQueryPort {
    /**
     * Program 생성·수정 시 formId 연결을 검증한다. FormType=PROGRAM, FormStatus=ACTIVE이고
     * `ownerMemberId`가 소유한 Form만 반환한다. 그 외(존재하지 않음, 다른 유형, 비활성 상태,
     * 다른 소유자)에는 null을 반환해 Program 쪽에서 단일 오류로 처리하게 한다(다른 교사의 개인
     * Form 연결 금지, 원본 요구사항 20절).
     */
    fun findLinkableProgramForm(
        formId: Long,
        ownerMemberId: Long,
    ): ProgramFormSnapshot?

    /**
     * Program 신청(APPLY) 시점에 현재 활성 버전을 Snapshot으로 남기기 위해 사용한다. Form이
     * 존재하지 않거나 ACTIVE가 아니면 null이다. 소유권은 이미 연결(생성·수정) 시점에 검증했으므로
     * 여기서는 다시 확인하지 않는다.
     */
    fun findActiveVersion(formId: Long): Int?
}

@NamedInterface
data class ProgramFormSnapshot(
    val formId: Long,
    val currentVersion: Int,
)
