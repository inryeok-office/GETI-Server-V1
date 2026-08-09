package team.inreok.getiserver.domain.file.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import team.inreok.getiserver.domain.file.entity.type.FileOwnerType
import team.inreok.getiserver.domain.file.entity.type.FilePurpose
import team.inreok.getiserver.domain.file.entity.type.FileStatus
import java.time.LocalDateTime

/**
 * Object Storage에 저장된 파일 하나의 Metadata다. Binary는 DB에 저장하지 않는다 -- [objectKey]가
 * Storage 안의 실제 Object를 가리킨다.
 *
 * Entity 이름이 `File`이 아니라 `StoredFile`인 것은 `java.io.File`과의 이름 충돌을 피하기
 * 위함이다(docs/architecture/erd.md).
 *
 * [uploaderMemberId]는 `members`를 가리키는 느슨한 FK이고(Domain 경계 유지, JPA 연관관계 없음),
 * [ownerType]+[ownerId]는 연결된 리소스를 가리키는 다형적 참조라 물리 FK가 없다.
 *
 * [originalName]은 사용자에게 보여주기 위해서만 보존하며 [objectKey]로 절대 쓰지 않는다
 * (지시서 §3/§42). Key는 `{purpose}/{yyyy}/{MM}/{UUID}` 형태라 파일명이 같아도 충돌하지 않고
 * 외부에서 추측할 수 없다.
 *
 * 업로드 이후 파일의 내용과 이름은 바뀌지 않는다(지시서 §21 -- 같은 File ID의 Binary를 덮어쓰지
 * 않는다). 바뀌는 것은 연결 상태([status]/[ownerType]/[ownerId]/[linkedAt])뿐이라 나머지는 `val`이다.
 */
@Entity
@Table(name = "files")
class StoredFile(
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    val purpose: FilePurpose,
    @Column(name = "object_key", nullable = false, length = 1000)
    val objectKey: String,
    @Column(name = "original_name", nullable = false, length = 500)
    val originalName: String,
    @Column(name = "content_type", nullable = false, length = 255)
    val contentType: String,
    @Column(name = "size_bytes", nullable = false)
    val sizeBytes: Long,
    @Column(name = "uploader_member_id")
    val uploaderMemberId: Long? = null,
    @Column(length = 20)
    val extension: String? = null,
    @Column(name = "contains_personal_information", nullable = false)
    val containsPersonalInformation: Boolean = false,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: FileStatus = FileStatus.PENDING
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type", length = 100)
    var ownerType: FileOwnerType? = null
        protected set

    @Column(name = "owner_id")
    var ownerId: Long? = null
        protected set

    @Column(name = "linked_at")
    var linkedAt: LocalDateTime? = null
        protected set

    // V2 Schema에 있지만 이번 범위에서는 채우지 않는다(보존 정책·판단 주체 미확정, 명세 §17).
    // Mapping만 유지해 ddl-auto=validate와 어긋나지 않게 한다.
    @Column(name = "expires_at")
    var expiresAt: LocalDateTime? = null

    // 실제 삭제 처리(status=DELETED, deletedAt 기록)는 Cleanup Scheduler(Phase 5) 범위다.
    // 이번 PR에는 파일을 삭제하는 경로가 없어 이 Column을 채우는 코드도 없다.
    @Column(name = "deleted_at")
    var deletedAt: LocalDateTime? = null

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime? = null

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime? = null

    /** Storage 업로드가 성공했다. 아직 어떤 리소스에도 연결되지 않은 상태다. */
    fun markUploaded() {
        check(status == FileStatus.PENDING) { "PENDING 상태에서만 UPLOADED로 전환할 수 있습니다. (status=$status)" }
        status = FileStatus.UPLOADED
    }

    /** Storage 업로드에 실패했다. 보상 삭제를 시도한 뒤 호출한다. */
    fun markFailed() {
        check(status == FileStatus.PENDING) { "PENDING 상태에서만 FAILED로 전환할 수 있습니다. (status=$status)" }
        status = FileStatus.FAILED
    }

    /**
     * 리소스에 연결한다. 이미 다른 리소스가 쓰고 있는 파일을 다시 연결하지 않도록
     * [FileStatus.UPLOADED]에서만 허용한다 -- 이 검사가 지시서 §14의 "이미 사용된 파일 재사용"
     * 방어다.
     */
    fun linkTo(
        ownerType: FileOwnerType,
        ownerId: Long,
        linkedAt: LocalDateTime,
    ) {
        check(status == FileStatus.UPLOADED) { "UPLOADED 상태의 파일만 연결할 수 있습니다. (status=$status)" }
        require(purpose.ownerType == ownerType) {
            "파일의 Purpose가 허용하지 않는 대상입니다. (purpose=$purpose, ownerType=$ownerType)"
        }
        this.ownerType = ownerType
        this.ownerId = ownerId
        this.linkedAt = linkedAt
        status = FileStatus.LINKED
    }

    /**
     * 연결을 해제해 [FileStatus.UPLOADED]로 되돌린다. 리소스 수정으로 첨부가 빠질 때 사용한다.
     *
     * Binary를 즉시 지우지 않는 이유는 지시서 §18/§39다 -- 리소스 삭제와 파일 물리 삭제는 다른
     * 사건이고 보존해야 할 이력이 남아 있을 수 있다. 실제 Storage 삭제는 Cleanup(Phase 5)이
     * 보존 기간을 보고 판단한다.
     */
    fun unlink() {
        check(status == FileStatus.LINKED) { "연결된 파일만 연결 해제할 수 있습니다. (status=$status)" }
        ownerType = null
        ownerId = null
        linkedAt = null
        status = FileStatus.UPLOADED
    }

    /**
     * 조회·연결·다운로드 대상이 될 수 있는 상태인지. PENDING/FAILED/DELETED는 사용자에게 없는
     * 것과 같이 다룬다(업로드가 끝나지 않았거나 실패했거나 이미 지워진 파일이다).
     */
    fun isVisible(): Boolean = status == FileStatus.UPLOADED || status == FileStatus.LINKED
}
