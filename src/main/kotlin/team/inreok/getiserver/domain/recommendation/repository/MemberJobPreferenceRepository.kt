package team.inreok.getiserver.domain.recommendation.repository

import org.springframework.data.jpa.repository.JpaRepository
import team.inreok.getiserver.domain.recommendation.entity.MemberJobPreference
import team.inreok.getiserver.domain.recommendation.entity.MemberJobPreferenceId

interface MemberJobPreferenceRepository : JpaRepository<MemberJobPreference, MemberJobPreferenceId>
