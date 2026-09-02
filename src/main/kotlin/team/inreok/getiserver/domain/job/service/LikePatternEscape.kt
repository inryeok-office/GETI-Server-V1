package team.inreok.getiserver.domain.job.service

/** JPQL LIKE에서 검색어가 와일드카드로 해석되지 않도록 이스케이프한다. */
internal fun escapeLikePattern(value: String): String =
    value
        .replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_")
