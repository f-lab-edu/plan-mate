# 0008 Maps Grounding Lite Auto Itinerary Generation

## Status

Proposed

## Context

PR #76 이후 PlanMate의 일정 저장 경계는 `placeId`와 일정 구조 중심으로 정리되었다.
장소명, 주소, 좌표, 평점, 영업시간, route 정보, source link, AI 추천 이유, grounded output은
영구 저장하지 않고 조회 시점 resolve로 화면에 내려준다.

기존 manual handoff는 prompt와 AI request JSON을 사용자가 직접 ChatGPT에 넣고 응답 JSON을 제출하는 개발용 흐름이다.
이 흐름은 저장 계약 검증에는 유효하지만, 실제 사용자 흐름에서는 여행방 생성 후 backend worker가 비동기로 일정을 생성해야 한다.

## Decision

itinerary generation worker가 LLM/Maps Grounding Lite 기반 자동 생성 흐름을 실행하도록 한다.

* HTTP 요청에서는 generation row와 outbox event만 만들고 외부 AI 호출을 기다리지 않는다.
* 기존 outbox/RabbitMQ worker 흐름을 재사용한다.
* `ItineraryDraftGenerator` provider abstraction을 둔다.
* 기본 provider는 `gemini-maps-grounding`으로 두고, `manual` provider는 fallback/legacy 경로로 남긴다.
* prompt builder는 trip, planning profile, destination/accommodation/must-visit placeId, 날짜, 예산, 취향, 이동수단, 요청사항을 context로 만든다.
* provider 응답은 `GroundedItineraryDraft` JSON으로 파싱한다.
* 저장 전 `GroundedItineraryDraftValidator`가 day 수, day/sequence 중복, placeId, startTime, durationMinutes, mustVisit 포함 여부, 반복 장소 수를 검증한다.
* 검증된 draft만 `ItineraryGenerationPersistenceService`를 통해 저장한다.
* 저장 필드는 itinerary/generation/day/item 구조와 `placeId`, `startTime`, `durationMinutes`, `createdSource=AI_DRAFT`로 제한한다.
* raw prompt, raw response, grounded output, source link, AI 추천 이유, Google Places 상세정보는 영구 저장하지 않는다.
* `requestFingerprint`를 저장해 같은 trip/profile 입력의 중복 외부 호출을 줄인다.
* generation status 변경은 기존 realtime event 흐름을 유지한다.

## Consequences

* 사용자는 manual prompt 복사 없이 여행방 생성 후 자동 일정 생성을 기다릴 수 있다.
* 외부 AI 호출 비용과 실패 가능성이 생기므로 timeout, retry, failureCode, 한국어 사용자 메시지가 필요하다.
* 원문 prompt/response를 저장하지 않기 때문에 장애 분석은 provider, model, promptVersion, schemaVersion, requestFingerprint, latencyMillis, failureCode 중심으로 해야 한다.
* Maps Grounding Lite grounded output을 DB에 저장하지 않으므로 정책상 장기 저장 경계가 유지된다.
* manual handoff는 운영 기본 UX가 아니라 fallback/개발 검증 경로가 된다.
* 실제 Google Map 마커 UI, route duration/distance 저장, Gemini 외 provider 확장은 별도 PR 범위로 남는다.

## Evidence

* `backend/src/main/java/com/planmate/itinerary/generation/ItineraryDraftGenerator.java`
* `backend/src/main/java/com/planmate/itinerary/generation/GeminiMapsGroundedItineraryDraftGenerator.java`
* `backend/src/main/java/com/planmate/itinerary/generation/GeminiMapsGroundingClient.java`
* `backend/src/main/java/com/planmate/itinerary/generation/ItineraryDraftPromptBuilder.java`
* `backend/src/main/java/com/planmate/itinerary/service/GroundedItineraryDraftValidator.java`
* `backend/src/main/java/com/planmate/itinerary/service/ItineraryGenerationPersistenceService.java`
* `backend/src/main/java/com/planmate/itinerary/service/ItineraryGenerationWorkerService.java`
* `backend/src/main/resources/prompts/itinerary-grounded-plan-v1.txt`
* `backend/src/main/resources/db/migration/V17__add_ai_itinerary_generation_metadata.sql`
* `frontend/src/pages/trip/TripCreatePage.tsx`
* `frontend/src/pages/trip/TripDetailPage.tsx`

## Confidence

Medium for provider abstraction, validation, persistence boundary, and worker orchestration.

Medium-low for exact Gemini/Maps Grounding Lite request shape until a real API key and quota 환경에서 manual local 검증을 완료한다.
