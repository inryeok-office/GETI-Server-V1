package team.inreok.getiserver.domain.file.entity.type

/**
 * 파일의 생명주기 상태다. DB와 Object Storage는 하나의 ACID Transaction이 아니므로(지시서 §10)
 * "DB Row는 있는데 Storage Object가 없는" 중간 상태가 실재한다. 이 Enum은 그 상태를 숨기지 않고
 * 드러내 Cleanup과 운영 조회가 고아를 찾을 수 있게 한다.
 *
 * 업로드는 [PENDING]을 먼저 커밋해 `object_key`를 선점한 뒤 Storage에 올리고 [UPLOADED]로
 * 전환한다. 어느 단계에서 실패해도 DB에 흔적이 남는다.
 *
 * 이 Enum은 공개하지 않는다 -- 다른 Domain은 [team.inreok.getiserver.domain.file.link.FileSnapshot]만
 * 받고 파일의 내부 상태를 알 필요가 없다.
 */
enum class FileStatus {
    /**
     * DB Row만 만들어졌고 Storage 업로드가 아직 끝나지 않았다. 조회·연결·다운로드가 모두
     * 불가능해 사용자에게 노출되지 않는다. 여기서 응답이 끊기면 Storage에 Object가 있는데 DB는
     * PENDING인 고아가 되지만, DB에 흔적이 있으므로 Cleanup이 수거할 수 있다.
     */
    PENDING,

    /** Storage 업로드까지 성공했고 아직 어떤 리소스에도 연결되지 않았다. 업로더 본인만 접근한다. */
    UPLOADED,

    /** 리소스에 연결됐다. `owner_type`/`owner_id`/`linked_at`이 채워져 있다. */
    LINKED,

    /**
     * Storage 업로드에 실패해 보상 삭제까지 마쳤다. PENDING과 구분해 두어야 "업로드가 실패한
     * 것"과 "업로드 도중 서버가 죽은 것"을 운영에서 구별할 수 있다.
     */
    FAILED,

    /** 논리 삭제. Storage Binary는 지웠지만 운영 추적을 위해 Metadata Row는 남긴다(지시서 §38). */
    DELETED,
}
