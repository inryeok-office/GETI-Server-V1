-- 이름과 기업 유형이 같은 기업이 중복 등록되는 것을 DB 수준에서 차단한다(Issue #56).
--
-- Application 계층(CompanyServiceImpl)의 사전 중복 검사는 사용자에게 친절한 409 응답을 주기 위해
-- 그대로 유지하지만, 조회와 저장 사이에 다른 Transaction이 끼어드는 경쟁 조건(Check-Then-Act)까지는
-- 막지 못한다. 이 Index를 최종 방어선으로 두어 동시 요청에서도 중복 행이 생기지 않게 한다.
--
-- 조건이 두 가지 있어 일반 UNIQUE 제약 대신 부분 함수 Index를 사용한다.
--   1. Soft Delete된 기업(deleted_at IS NOT NULL)은 중복 판정 대상이 아니므로 제외한다.
--      같은 이름의 기업을 삭제한 뒤 다시 등록할 수 있어야 한다.
--   2. 이름 비교는 대소문자를 무시한다(Repository의 IgnoreCase 조회와 동일한 기준).
--      앞뒤 공백은 Service에서 제거한 뒤 저장하므로 여기서는 다루지 않는다.
CREATE UNIQUE INDEX uk_companies_name_type_active
    ON companies (LOWER(name), type)
    WHERE deleted_at IS NULL;
