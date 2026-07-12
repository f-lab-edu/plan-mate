ALTER TABLE itinerary_generations
    ADD COLUMN IF NOT EXISTS provider VARCHAR(60),
    ADD COLUMN IF NOT EXISTS model VARCHAR(120),
    ADD COLUMN IF NOT EXISTS schema_version VARCHAR(80) NOT NULL DEFAULT 'grounded-itinerary-draft-v1',
    ADD COLUMN IF NOT EXISTS request_fingerprint VARCHAR(64),
    ADD COLUMN IF NOT EXISTS latency_millis BIGINT,
    ADD COLUMN IF NOT EXISTS failure_code VARCHAR(80);

CREATE INDEX IF NOT EXISTS itinerary_generations_trip_fingerprint_status_idx
    ON itinerary_generations (trip_id, request_fingerprint, status, created_at DESC);

COMMENT ON COLUMN itinerary_generations.provider IS '일정 초안을 생성한 provider 식별자. 예: manual, gemini-maps-grounding.';
COMMENT ON COLUMN itinerary_generations.model IS '일정 초안 생성에 사용한 모델명. API key나 요청/응답 본문은 저장하지 않는다.';
COMMENT ON COLUMN itinerary_generations.schema_version IS 'AI 일정 응답 검증에 사용하는 GroundedItineraryDraft schema version.';
COMMENT ON COLUMN itinerary_generations.request_fingerprint IS '같은 여행 조건의 중복 외부 호출을 막기 위한 요청 fingerprint. 원본 입력값은 저장하지 않는다.';
COMMENT ON COLUMN itinerary_generations.latency_millis IS '외부 AI provider 호출에 걸린 시간(ms).';
COMMENT ON COLUMN itinerary_generations.failure_code IS '내부 로그와 운영 구분에 사용하는 실패 코드. 사용자 노출 메시지는 failure_reason에 저장한다.';
