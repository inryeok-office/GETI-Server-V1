-- ============================================================
-- Portfolio Domain Phase 2 (학생 제출) — portfolio_submissions.note 추가
-- ============================================================
-- 학생 제출 API(PATCH /api/v1/portfolio-requests/{requestId}/submission)의 확정 계약에는
-- 제출물 URL(portfolio_url, V2부터 존재)과 함께 자유 서술 메모(note)가 포함된다. V2 스키마에는
-- note 컬럼이 없어 이번 Phase에서 추가한다. 이미 병합된 V2/V27은 수정하지 않고 새 버전으로만 바꾼다.
--
-- 자유 서술이라 길이 상한을 두지 않고 TEXT로 둔다(portfolio_requests.description과 같은 관례).
-- 임시저장 단계에서 비어 있을 수 있으므로 NULL을 허용한다.

ALTER TABLE portfolio_submissions ADD COLUMN note TEXT;
