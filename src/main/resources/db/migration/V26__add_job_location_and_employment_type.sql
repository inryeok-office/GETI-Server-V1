-- Job/Search 공고 카드(Issue #169): 공고 카드가 표시해야 하는 근무지역과 고용형태를 jobs에
-- 추가한다. 기존 Migration(V25 이하)은 수정하지 않는다.
--
-- 두 Column 모두 Enum이 아니라 자유 문자열이다. 고용형태의 실제 값 집합(인턴/정규직/계약직
-- 등)이 명세로 확정되지 않았고, Collector가 Provider에서 실제로 받아오는 값도 "일반정규직",
-- "주5일"(근무형태), "공공기관 채용"(채용유형)처럼 서로 다른 축이 섞여 있어 지금 Enum으로
-- 좁히면 값을 잃는다. 확정 뒤 Enum으로 좁히는 것은 가능하지만 반대 방향은 API 계약을 깬다.
--
-- 길이 255는 job_notification_deliveries.employment_type(V9)과 같은 기준이다. Nullable 신규
-- Column이라 Backfill하지 않는다 -- 외부 수집 공고는 다음 수집 주기에 Upsert가 채우고,
-- MOU/Internal 공고는 등록·수정 Form 입력으로 채운다.
ALTER TABLE jobs ADD COLUMN location VARCHAR(255);
ALTER TABLE jobs ADD COLUMN employment_type VARCHAR(255);
