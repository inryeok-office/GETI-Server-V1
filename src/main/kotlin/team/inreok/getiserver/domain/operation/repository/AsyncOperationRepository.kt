package team.inreok.getiserver.domain.operation.repository

import org.springframework.data.jpa.repository.JpaRepository
import team.inreok.getiserver.domain.operation.entity.AsyncOperation
import java.util.UUID

interface AsyncOperationRepository : JpaRepository<AsyncOperation, UUID>
