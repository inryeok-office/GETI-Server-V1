package team.inreok.getiserver.domain.program.exception

import team.inreok.getiserver.global.error.BusinessException

class CapacityBelowCurrentApplicantsException :
    BusinessException(ProgramErrorCode.CAPACITY_BELOW_CURRENT_APPLICANTS)
