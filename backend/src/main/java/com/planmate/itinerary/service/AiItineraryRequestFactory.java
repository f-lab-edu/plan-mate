package com.planmate.itinerary.service;

import com.planmate.itinerary.domain.GenerationInputSnapshot;
import com.planmate.itinerary.dto.AiItineraryRequest;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AiItineraryRequestFactory {

    public AiItineraryRequest create(
            Long generationId,
            GenerationInputSnapshot snapshot
    ) {
        return new AiItineraryRequest(
                generationId.toString(),
                snapshot.tripId().toString(),
                destination(snapshot.destination()),
                snapshot.startDate(),
                snapshot.endDate(),
                companion(snapshot.companion()),
                budget(snapshot.budget()),
                snapshot.preference().travelPace(),
                snapshot.preference().interests(),
                transportation(snapshot.transportation()),
                accommodation(snapshot.accommodation()),
                mustVisitPlaces(snapshot),
                snapshot.avoidConditions(),
                snapshot.freeRequest(),
                planningRules()
        );
    }

    private AiItineraryRequest.Destination destination(GenerationInputSnapshot.Destination destination) {
        return new AiItineraryRequest.Destination(
                destination.placeId(),
                destination.displayName(),
                destination.formattedAddress(),
                destination.latitude(),
                destination.longitude(),
                destination.types(),
                destination.primaryType()
        );
    }

    private AiItineraryRequest.Companion companion(GenerationInputSnapshot.Companion companion) {
        return new AiItineraryRequest.Companion(
                companion.companionCount(),
                companion.companionType(),
                companion.hasChildren(),
                companion.childCount(),
                companion.childAgeGroup(),
                companion.hasSeniors(),
                companion.seniorCount()
        );
    }

    private AiItineraryRequest.Budget budget(GenerationInputSnapshot.Budget budget) {
        return new AiItineraryRequest.Budget(
                budget.currencyCode(),
                budget.amount(),
                budget.level(),
                budget.includedItems()
        );
    }

    private AiItineraryRequest.Transportation transportation(GenerationInputSnapshot.Transportation transportation) {
        return new AiItineraryRequest.Transportation(
                transportation.primaryMode(),
                transportation.secondaryModes()
        );
    }

    private AiItineraryRequest.Accommodation accommodation(GenerationInputSnapshot.Accommodation accommodation) {
        return new AiItineraryRequest.Accommodation(
                accommodation.accommodationMode(),
                accommodation.preferredArea(),
                accommodation.name(),
                accommodation.checkInTime(),
                accommodation.checkOutTime()
        );
    }

    private List<AiItineraryRequest.MustVisitPlace> mustVisitPlaces(GenerationInputSnapshot snapshot) {
        return snapshot.mustVisitPlaces()
                .stream()
                .filter(GenerationInputSnapshot.MustVisitPlace::isResolved)
                .map(this::mustVisitPlace)
                .toList();
    }

    private AiItineraryRequest.MustVisitPlace mustVisitPlace(GenerationInputSnapshot.MustVisitPlace place) {
        return new AiItineraryRequest.MustVisitPlace(
                place.placeId(),
                place.name(),
                place.formattedAddress(),
                place.latitude(),
                place.longitude()
        );
    }

    private List<String> planningRules() {
        return List.of(
                "Markdown 코드 블록이나 설명 없이 GroundedItineraryDraft 형식의 JSON만 반환한다.",
                "모든 일정 항목은 sequence, placeId, startTime, durationMinutes를 포함해야 한다.",
                "응답에는 placeName, address, latitude, longitude, rating, routeInfo, sourceLinks, 추천 이유를 포함하지 않는다.",
                "여행 일수와 같은 개수의 day 항목을 만들고, day 번호는 1부터 시작한다.",
                "여행 기간상 불가능한 경우가 아니라면 mustVisitPlaces를 모두 포함한다.",
                "여행 날짜, 여행 속도, 동행자, 예산 수준, 이동수단, 회피 조건, freeRequest를 반영한다."
        );
    }
}
