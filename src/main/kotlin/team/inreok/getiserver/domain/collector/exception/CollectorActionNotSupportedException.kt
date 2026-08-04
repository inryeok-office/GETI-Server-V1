package team.inreok.getiserver.domain.collector.exception

import team.inreok.getiserver.global.error.BusinessException

// CollectorAction Enum 값이 Notion API 명세서와 저장소 어디에도 확정되어 있지 않아 수동 실행
// API를 완성할 수 없다(Issue #62 최종 보고의 "문서 불일치 및 결정" 참고). 임의의 Enum 값으로
// 컴파일만 통과시키는 대신 이 예외로 501을 반환한다.
class CollectorActionNotSupportedException : BusinessException(CollectorErrorCode.COLLECTOR_ACTION_NOT_SUPPORTED)
