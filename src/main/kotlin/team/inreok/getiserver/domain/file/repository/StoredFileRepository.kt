package team.inreok.getiserver.domain.file.repository

import org.springframework.data.jpa.repository.JpaRepository
import team.inreok.getiserver.domain.file.entity.StoredFile

interface StoredFileRepository : JpaRepository<StoredFile, Long>
