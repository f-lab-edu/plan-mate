COMMENT ON TABLE trips IS '여행방 기본 정보';
COMMENT ON COLUMN trips.id IS '여행방 ID';
COMMENT ON COLUMN trips.title IS '여행방 제목';
COMMENT ON COLUMN trips.destination IS '대표 여행지 또는 숙소 기준 지역';
COMMENT ON COLUMN trips.start_date IS '여행 시작일';
COMMENT ON COLUMN trips.end_date IS '여행 종료일';
COMMENT ON COLUMN trips.created_by IS '여행방 생성자 사용자 ID';
COMMENT ON COLUMN trips.created_at IS '여행방 생성 시각';
COMMENT ON COLUMN trips.updated_at IS '여행방 수정 시각';

COMMENT ON TABLE trip_members IS '여행방 참여자 정보';
COMMENT ON COLUMN trip_members.id IS '여행방 참여자 ID';
COMMENT ON COLUMN trip_members.trip_id IS '참여 중인 여행방 ID';
COMMENT ON COLUMN trip_members.user_id IS '참여 사용자 ID';
COMMENT ON COLUMN trip_members.role IS '여행방 내 역할. OWNER 또는 MEMBER';
COMMENT ON COLUMN trip_members.created_at IS '여행방 참여 시각';

CREATE TABLE itineraries (
    id BIGSERIAL PRIMARY KEY,
    trip_id BIGINT NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    source_type VARCHAR(30) NOT NULL,
    source_sample_id VARCHAR(80),
    summary TEXT NOT NULL,
    budget_currency VARCHAR(10) NOT NULL,
    budget_total_min INTEGER NOT NULL,
    budget_total_max INTEGER NOT NULL,
    budget_per_person_min INTEGER NOT NULL,
    budget_per_person_max INTEGER NOT NULL,
    budget_notes TEXT NOT NULL,
    verification_warnings TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT itineraries_trip_unique UNIQUE (trip_id)
);

CREATE INDEX itineraries_trip_id_idx ON itineraries(trip_id);

COMMENT ON TABLE itineraries IS 'AI 또는 Mock AI가 생성한 여행 일정 전체';
COMMENT ON COLUMN itineraries.trip_id IS '일정이 속한 여행방 ID';
COMMENT ON COLUMN itineraries.source_type IS '일정 생성 출처. 예: MOCK, OPENAI';
COMMENT ON COLUMN itineraries.source_sample_id IS 'Mock 샘플로 생성한 경우 사용한 mockSampleId';
COMMENT ON COLUMN itineraries.summary IS 'AI가 생성한 일정 전체 요약';
COMMENT ON COLUMN itineraries.budget_currency IS '예산 통화 코드';
COMMENT ON COLUMN itineraries.budget_total_min IS '전체 예상 최소 비용';
COMMENT ON COLUMN itineraries.budget_total_max IS '전체 예상 최대 비용';
COMMENT ON COLUMN itineraries.budget_per_person_min IS '1인당 예상 최소 비용';
COMMENT ON COLUMN itineraries.budget_per_person_max IS '1인당 예상 최대 비용';
COMMENT ON COLUMN itineraries.budget_notes IS '예산 산정 주의사항. 줄바꿈으로 복수 값 저장';
COMMENT ON COLUMN itineraries.verification_warnings IS '방문 전 검증 필요 안내. 줄바꿈으로 복수 값 저장';

CREATE TABLE itinerary_days (
    id BIGSERIAL PRIMARY KEY,
    itinerary_id BIGINT NOT NULL REFERENCES itineraries(id) ON DELETE CASCADE,
    day_number INTEGER NOT NULL,
    date_label DATE NOT NULL,
    theme VARCHAR(120) NOT NULL,
    CONSTRAINT itinerary_days_itinerary_day_unique UNIQUE (itinerary_id, day_number)
);

CREATE INDEX itinerary_days_itinerary_id_idx ON itinerary_days(itinerary_id);

COMMENT ON TABLE itinerary_days IS '여행 일정의 일차별 묶음';
COMMENT ON COLUMN itinerary_days.itinerary_id IS '상위 일정 ID';
COMMENT ON COLUMN itinerary_days.day_number IS '여행 몇 일차인지 나타내는 번호';
COMMENT ON COLUMN itinerary_days.date_label IS '해당 일차의 실제 날짜';
COMMENT ON COLUMN itinerary_days.theme IS '해당 일차의 AI 생성 테마';

CREATE TABLE itinerary_items (
    id BIGSERIAL PRIMARY KEY,
    itinerary_day_id BIGINT NOT NULL REFERENCES itinerary_days(id) ON DELETE CASCADE,
    item_order INTEGER NOT NULL,
    start_time VARCHAR(5) NOT NULL,
    end_time VARCHAR(5) NOT NULL,
    place_name VARCHAR(120) NOT NULL,
    category VARCHAR(30) NOT NULL,
    area_hint VARCHAR(80) NOT NULL,
    description TEXT NOT NULL,
    cost_min INTEGER NOT NULL,
    cost_max INTEGER NOT NULL,
    cost_included TEXT NOT NULL,
    parking_required BOOLEAN NOT NULL,
    parking_cost_min INTEGER NOT NULL,
    parking_cost_max INTEGER NOT NULL,
    parking_notes TEXT NOT NULL,
    transport_mode VARCHAR(40) NOT NULL,
    transport_from_previous_minutes INTEGER NOT NULL,
    transport_cost_min INTEGER NOT NULL,
    transport_cost_max INTEGER NOT NULL,
    transport_notes TEXT NOT NULL,
    why_recommended TEXT NOT NULL,
    needs_verification BOOLEAN NOT NULL,
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    map_visible BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT itinerary_items_day_order_unique UNIQUE (itinerary_day_id, item_order),
    CONSTRAINT itinerary_items_map_coordinate_check CHECK (
        map_visible = FALSE OR (latitude IS NOT NULL AND longitude IS NOT NULL)
    )
);

CREATE INDEX itinerary_items_day_id_idx ON itinerary_items(itinerary_day_id);

COMMENT ON TABLE itinerary_items IS '일차 안에 포함되는 개별 시간대 일정';
COMMENT ON COLUMN itinerary_items.itinerary_day_id IS '상위 일차 ID';
COMMENT ON COLUMN itinerary_items.item_order IS '하루 안에서의 일정 순서';
COMMENT ON COLUMN itinerary_items.start_time IS '일정 시작 시간. HH:mm';
COMMENT ON COLUMN itinerary_items.end_time IS '일정 종료 시간. HH:mm';
COMMENT ON COLUMN itinerary_items.place_name IS '장소명 또는 이동 항목명';
COMMENT ON COLUMN itinerary_items.category IS '장소/일정 카테고리. 예: 식당, 관광, 카페, 이동';
COMMENT ON COLUMN itinerary_items.area_hint IS '장소 권역 힌트';
COMMENT ON COLUMN itinerary_items.description IS 'AI가 생성한 장소 설명';
COMMENT ON COLUMN itinerary_items.cost_min IS '해당 일정 예상 최소 비용';
COMMENT ON COLUMN itinerary_items.cost_max IS '해당 일정 예상 최대 비용';
COMMENT ON COLUMN itinerary_items.cost_included IS '비용에 포함된 항목. 줄바꿈으로 복수 값 저장';
COMMENT ON COLUMN itinerary_items.parking_required IS '주차 필요 여부';
COMMENT ON COLUMN itinerary_items.parking_cost_min IS '예상 최소 주차 비용';
COMMENT ON COLUMN itinerary_items.parking_cost_max IS '예상 최대 주차 비용';
COMMENT ON COLUMN itinerary_items.parking_notes IS '주차 관련 설명';
COMMENT ON COLUMN itinerary_items.transport_mode IS '이전 일정에서 이동하는 방식';
COMMENT ON COLUMN itinerary_items.transport_from_previous_minutes IS '이전 일정에서 이동 예상 시간';
COMMENT ON COLUMN itinerary_items.transport_cost_min IS '이전 일정에서 이동 예상 최소 비용';
COMMENT ON COLUMN itinerary_items.transport_cost_max IS '이전 일정에서 이동 예상 최대 비용';
COMMENT ON COLUMN itinerary_items.transport_notes IS '이동 관련 설명';
COMMENT ON COLUMN itinerary_items.why_recommended IS 'AI가 이 장소를 추천한 이유';
COMMENT ON COLUMN itinerary_items.needs_verification IS '영업시간/비용/운영 여부 검증 필요 여부';
COMMENT ON COLUMN itinerary_items.latitude IS '지도 마커 표시용 위도';
COMMENT ON COLUMN itinerary_items.longitude IS '지도 마커 표시용 경도';
COMMENT ON COLUMN itinerary_items.map_visible IS '지도 마커와 동선에 포함할지 여부';

CREATE TABLE itinerary_alternative_suggestions (
    id BIGSERIAL PRIMARY KEY,
    itinerary_id BIGINT NOT NULL REFERENCES itineraries(id) ON DELETE CASCADE,
    target VARCHAR(120) NOT NULL,
    reason TEXT NOT NULL,
    candidates_text TEXT NOT NULL
);

CREATE INDEX itinerary_alternative_suggestions_itinerary_id_idx
    ON itinerary_alternative_suggestions(itinerary_id);

COMMENT ON TABLE itinerary_alternative_suggestions IS 'AI 일정에서 혼잡/휴무 등에 대비한 대체 후보';
COMMENT ON COLUMN itinerary_alternative_suggestions.itinerary_id IS '상위 일정 ID';
COMMENT ON COLUMN itinerary_alternative_suggestions.target IS '대체가 필요한 원래 장소명';
COMMENT ON COLUMN itinerary_alternative_suggestions.reason IS '대체 후보를 둔 이유';
COMMENT ON COLUMN itinerary_alternative_suggestions.candidates_text IS '대체 후보 목록. 줄바꿈으로 복수 값 저장';
