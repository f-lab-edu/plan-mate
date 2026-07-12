package com.planmate.itinerary.generation;

import com.planmate.place.dto.GeoPoint;
import com.planmate.place.dto.GeoViewport;
import com.planmate.trip.domain.AccommodationArea;
import com.planmate.trip.domain.AccommodationMode;
import com.planmate.trip.domain.AvoidCondition;
import com.planmate.trip.domain.BudgetItem;
import com.planmate.trip.domain.BudgetLevel;
import com.planmate.trip.domain.ChildAgeGroup;
import com.planmate.trip.domain.CompanionType;
import com.planmate.trip.domain.CurrencyCode;
import com.planmate.trip.domain.MustVisitPlaceSnapshot;
import com.planmate.trip.domain.TransportMode;
import com.planmate.trip.domain.TravelPace;
import com.planmate.trip.domain.TripInterest;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record ItineraryGenerationContext(
        Long generationId,
        Long tripId,
        String tripTitle,
        String promptVersion,
        String schemaVersion,
        String requestFingerprint,
        Destination destination,
        LocalDate startDate,
        LocalDate endDate,
        Profile profile
) {

    public record Destination(
            String placeId,
            String displayName,
            String formattedAddress,
            GeoPoint location,
            GeoViewport viewport,
            List<String> types,
            String primaryType
    ) {
    }

    public record Profile(
            int companionCount,
            CompanionType companionType,
            boolean hasChildren,
            int childCount,
            ChildAgeGroup childAgeGroup,
            boolean hasSeniors,
            int seniorCount,
            CurrencyCode currencyCode,
            Long budgetAmount,
            BudgetLevel budgetLevel,
            List<BudgetItem> includedBudgetItems,
            TravelPace travelPace,
            List<TripInterest> interests,
            TransportMode primaryTransportMode,
            List<TransportMode> secondaryTransportModes,
            AccommodationMode accommodationMode,
            AccommodationArea accommodationArea,
            String accommodationPlaceId,
            String accommodationName,
            String accommodationFormattedAddress,
            GeoPoint accommodationLocation,
            LocalTime checkInTime,
            LocalTime checkOutTime,
            LocalTime dailyStartTime,
            LocalTime dailyEndTime,
            List<MustVisitPlaceSnapshot> mustVisitPlaces,
            List<AvoidCondition> avoidConditions,
            String freeRequest
    ) {
    }
}
