package com.planmate.trip.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.planmate.auth.security.JwtTokenProvider;
import com.planmate.place.dto.GeoPoint;
import com.planmate.place.dto.GeoViewport;
import com.planmate.place.dto.ResolvedDestination;
import com.planmate.place.service.GooglePlacesService;
import com.planmate.trip.domain.TripInterest;
import com.planmate.trip.entity.TripMemberRole;
import com.planmate.trip.repository.TripMemberRepository;
import com.planmate.trip.repository.TripPlanningProfileRepository;
import com.planmate.user.domain.UserRole;
import com.planmate.user.entity.UserEntity;
import com.planmate.user.repository.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TripControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TripMemberRepository tripMemberRepository;

    @Autowired
    private TripPlanningProfileRepository tripPlanningProfileRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private GooglePlacesService googlePlacesService;

    @BeforeEach
    void setUp() {
        given(googlePlacesService.resolveDestination(anyString(), any()))
                .willAnswer(invocation -> resolvedDestination(invocation.getArgument(0)));
    }

    @Test
    void createTripCreatesOwnerMembershipAndPlanningProfile() throws Exception {
        UserEntity user = createUser();
        String accessToken = accessToken(user);
        LocalDate startDate = LocalDate.now().plusDays(10);
        LocalDate endDate = startDate.plusDays(2);

        MvcResult result = mockMvc.perform(post("/api/trips")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tripRequestJson("Kyoto autumn", "Kyoto", "place-kyoto", startDate, endDate)))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, org.hamcrest.Matchers.startsWith("/api/trips/")))
                .andExpect(jsonPath("$.title").value("Kyoto autumn"))
                .andExpect(jsonPath("$.destination").value("Resolved place-kyoto"))
                .andExpect(jsonPath("$.destinationPlaceId").value("place-kyoto"))
                .andExpect(jsonPath("$.status").value("UPCOMING"))
                .andExpect(jsonPath("$.memberCount").value(1))
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        Long tripId = response.get("id").asLong();

        assertThat(tripMemberRepository.findByUser_IdOrderByTrip_CreatedAtDesc(user.getId()))
                .hasSize(1)
                .first()
                .extracting(member -> member.getRole())
                .isEqualTo(TripMemberRole.OWNER);

        assertThat(tripPlanningProfileRepository.findByTrip_Id(tripId))
                .isPresent()
                .get()
                .satisfies(profile -> {
                    assertThat(profile.getCompanionCount()).isEqualTo(3);
                    assertThat(profile.getInterests()).contains(TripInterest.FOOD, TripInterest.SIGHTSEEING);
                    assertThat(profile.getMustVisitPlaces()).containsExactly("Kiyomizu-dera");
                });
    }

    @Test
    void listMineReturnsOnlyMyTrips() throws Exception {
        UserEntity me = createUser();
        UserEntity other = createUser();
        String myToken = accessToken(me);
        String otherToken = accessToken(other);

        createTrip(myToken, "My trip", "Seoul", LocalDate.now().plusDays(1), LocalDate.now().plusDays(3));
        createTrip(otherToken, "Other trip", "Busan", LocalDate.now().plusDays(5), LocalDate.now().plusDays(6));

        mockMvc.perform(get("/api/trips")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + myToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("My trip"))
                .andExpect(jsonPath("$[0].memberCount").value(1));
    }

    @Test
    void getDetailReturnsTripProfileMembersAndItineraries() throws Exception {
        UserEntity user = createUser();
        String accessToken = accessToken(user);
        String tripId = createTrip(
                accessToken,
                "Detail trip",
                "Jeju",
                LocalDate.now().minusDays(1),
                LocalDate.now().plusDays(1)
        );

        mockMvc.perform(get("/api/trips/{tripId}", tripId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(tripId))
                .andExpect(jsonPath("$.title").value("Detail trip"))
                .andExpect(jsonPath("$.destinationPlaceId").value("place-Jeju"))
                .andExpect(jsonPath("$.destinationInfo.displayName").value("Resolved place-Jeju"))
                .andExpect(jsonPath("$.planningProfile.companionCount").value(3))
                .andExpect(jsonPath("$.planningProfile.interests[0]").value("FOOD"))
                .andExpect(jsonPath("$.itineraries.length()").value(0))
                .andExpect(jsonPath("$.status").value("PLANNING"))
                .andExpect(jsonPath("$.memberCount").value(1))
                .andExpect(jsonPath("$.members.length()").value(1))
                .andExpect(jsonPath("$.members[0].userId").value(user.getId()))
                .andExpect(jsonPath("$.members[0].role").value("OWNER"));
    }

    @Test
    void getDetailReturnsNotFoundForNonMember() throws Exception {
        UserEntity owner = createUser();
        UserEntity other = createUser();
        String tripId = createTrip(
                accessToken(owner),
                "Private trip",
                "Sokcho",
                LocalDate.now().plusDays(1),
                LocalDate.now().plusDays(2)
        );

        mockMvc.perform(get("/api/trips/{tripId}", tripId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(other)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRIP_NOT_FOUND"));
    }

    @Test
    void createTripRejectsInvalidDateRange() throws Exception {
        UserEntity user = createUser();
        String accessToken = accessToken(user);

        mockMvc.perform(post("/api/trips")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tripRequestJson(
                                "Invalid trip",
                                "Seoul",
                                "place-seoul",
                                LocalDate.of(2026, 8, 10),
                                LocalDate.of(2026, 8, 1)
                        )))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    private String createTrip(
            String accessToken,
            String title,
            String destination,
            LocalDate startDate,
            LocalDate endDate
    ) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/trips")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tripRequestJson(title, destination, "place-" + destination, startDate, endDate)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        return response.get("id").asText();
    }

    private String tripRequestJson(
            String title,
            String destination,
            String destinationPlaceId,
            LocalDate startDate,
            LocalDate endDate
    ) {
        return """
                {
                  "title": "%s",
                  "destination": "%s",
                  "destinationPlaceId": "%s",
                  "startDate": "%s",
                  "endDate": "%s",
                  "companion": {
                    "count": 3,
                    "type": "FRIENDS",
                    "hasChildren": false,
                    "childCount": 0,
                    "childAgeGroup": null,
                    "hasSeniors": false,
                    "seniorCount": 0
                  },
                  "budget": {
                    "currencyCode": "KRW",
                    "amount": 1000000,
                    "level": "BALANCED",
                    "includedItems": ["LODGING", "TRANSPORT", "FOOD"]
                  },
                  "preferences": {
                    "travelPace": "BALANCED",
                    "interests": ["FOOD", "SIGHTSEEING", "CAFE"]
                  },
                  "transportation": {
                    "primaryMode": "PUBLIC_TRANSIT",
                    "secondaryModes": ["WALK"]
                  },
                  "accommodation": {
                    "mode": "UNDECIDED",
                    "preferredArea": "TRANSIT",
                    "name": null,
                    "checkInTime": null,
                    "checkOutTime": null
                  },
                  "additionalRequest": {
                    "mustVisitPlaces": ["Kiyomizu-dera"],
                    "avoidConditions": ["LONG_WALK"],
                    "freeRequest": "Keep lunch flexible."
                  }
                }
                """.formatted(title, destination, destinationPlaceId, startDate, endDate);
    }

    private ResolvedDestination resolvedDestination(String placeId) {
        return new ResolvedDestination(
                placeId,
                "Resolved " + placeId,
                "Resolved address",
                new GeoPoint(35.0, 135.0),
                new GeoViewport(new GeoPoint(34.8, 134.8), new GeoPoint(35.2, 135.2)),
                List.of("locality", "political"),
                "locality"
        );
    }

    private UserEntity createUser() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Instant now = Instant.now();
        return userRepository.save(UserEntity.createOauthUser(
                "trip-" + suffix + "@example.com",
                "trip-" + suffix + "@example.com",
                "trip-user-" + suffix,
                true,
                now
        ));
    }

    private String accessToken(UserEntity user) {
        return jwtTokenProvider.issueAccessToken(user.getId(), UserRole.USER, Instant.now()).value();
    }
}
